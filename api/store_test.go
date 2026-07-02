package main

import (
	"context"
	"database/sql"
	"os"
	"testing"

	_ "github.com/jackc/pgx/v5/stdlib"
)

// Requires a live database; skipped unless DATABASE_URL is set.
func TestLinkGoogle(t *testing.T) {
	dbURL := os.Getenv("DATABASE_URL")
	if dbURL == "" {
		t.Skip("DATABASE_URL not set")
	}
	db, err := sql.Open("pgx", dbURL)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()

	store := &Store{db: db}
	ctx := context.Background()
	if err := store.Migrate(ctx); err != nil {
		t.Fatal(err)
	}

	userA, err := store.GetOrCreateUserByToken(ctx, "test-token-aaaaaaaaaaaa")
	if err != nil {
		t.Fatal(err)
	}
	userB, err := store.GetOrCreateUserByToken(ctx, "test-token-bbbbbbbbbbbb")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := store.CreateProgram(ctx, userA, "A program"); err != nil {
		t.Fatal(err)
	}
	if _, err := store.CreateProgram(ctx, userB, "B program"); err != nil {
		t.Fatal(err)
	}

	// First link: plain attach, nothing restored.
	restored, err := store.LinkGoogle(ctx, userA, "test-google-sub", "a@example.com")
	if err != nil {
		t.Fatal(err)
	}
	if restored {
		t.Fatal("first link should not report restored")
	}
	sub, email, err := store.GetGoogleLink(ctx, userA)
	if err != nil || sub != "test-google-sub" || email != "a@example.com" {
		t.Fatalf("link not recorded: sub=%q email=%q err=%v", sub, email, err)
	}

	// Re-link same account on same user: no-op, not a restore.
	restored, err = store.LinkGoogle(ctx, userA, "test-google-sub", "a@example.com")
	if err != nil || restored {
		t.Fatalf("idempotent relink failed: restored=%v err=%v", restored, err)
	}

	// "New device" (userB) links the same Google account: userB merges into userA.
	restored, err = store.LinkGoogle(ctx, userB, "test-google-sub", "a@example.com")
	if err != nil {
		t.Fatal(err)
	}
	if !restored {
		t.Fatal("cross-user link should report restored")
	}

	// B's device token must now resolve to userA.
	uid, err := store.GetOrCreateUserByToken(ctx, "test-token-bbbbbbbbbbbb")
	if err != nil {
		t.Fatal(err)
	}
	if uid != userA {
		t.Fatalf("device token not re-pointed: got user %d, want %d", uid, userA)
	}

	// userA now owns both programs; userB is gone.
	programs, err := store.ListPrograms(ctx, userA)
	if err != nil {
		t.Fatal(err)
	}
	if len(programs) != 2 {
		t.Fatalf("expected 2 programs after merge, got %d", len(programs))
	}
	var count int
	if err := db.QueryRowContext(ctx, "SELECT COUNT(*) FROM users WHERE id = $1", userB).Scan(&count); err != nil {
		t.Fatal(err)
	}
	if count != 0 {
		t.Fatal("merged user should be deleted")
	}

	// Unlink clears the identity.
	if err := store.UnlinkGoogle(ctx, userA); err != nil {
		t.Fatal(err)
	}
	sub, _, err = store.GetGoogleLink(ctx, userA)
	if err != nil || sub != "" {
		t.Fatalf("unlink failed: sub=%q err=%v", sub, err)
	}
}
