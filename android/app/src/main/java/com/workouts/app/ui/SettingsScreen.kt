package com.workouts.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.workouts.app.data.ApiService
import androidx.compose.ui.Modifier
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.workouts.app.data.ImportExercise
import com.workouts.app.data.ImportWorkout
import com.workouts.app.data.ImportWorkoutRequest
import com.workouts.app.data.WorkoutSetInput
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun SettingsScreen(
    viewModel: WorkoutsViewModel
) {
    var importStatus by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        importStatus = "Parsing..."
        try {
            val request = parseCsv(context, uri)
            importStatus = "Importing ${request.workouts.size} workouts..."
            viewModel.importWorkouts(request) { count ->
                importStatus = "Imported $count workouts"
            }
        } catch (e: Exception) {
            importStatus = "Error: ${e.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Google account section
        val scope = rememberCoroutineScope()
        val googleLink by viewModel.googleLink.collectAsState()
        var googleMessage by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) { viewModel.loadGoogleLink() }

        Icon(
            Icons.Default.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(8.dp))
        Text("Google Account", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(
            if (googleLink?.linked == true)
                "Linked to ${googleLink?.email ?: "your Google account"}. Your data can be restored on a new device by linking the same account."
            else
                "Optional: link a Google account so your data can be restored if you lose or switch devices.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        if (googleLink?.linked == true) {
            OutlinedButton(
                onClick = {
                    viewModel.unlinkGoogle { err ->
                        googleMessage = if (err != null) "Error: $err" else "Unlinked"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Unlink") }
        } else {
            Button(
                onClick = {
                    googleMessage = null
                    scope.launch {
                        try {
                            val credentialManager = CredentialManager.create(context)
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(
                                    GetSignInWithGoogleOption.Builder(ApiService.GOOGLE_SERVER_CLIENT_ID).build()
                                )
                                .build()
                            val credential = credentialManager.getCredential(context, request).credential
                            if (credential is CustomCredential &&
                                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                            ) {
                                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                                viewModel.linkGoogle(idToken) { status, err ->
                                    googleMessage = when {
                                        err != null -> "Error: $err"
                                        status?.restored == true -> "Account linked — existing data restored"
                                        else -> "Account linked"
                                    }
                                }
                            }
                        } catch (_: GetCredentialCancellationException) {
                            // user dismissed the account picker
                        } catch (e: Exception) {
                            googleMessage = "Error: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Link Google Account") }
        }
        if (googleMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(googleMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(24.dp))

        // Theme section
        Icon(
            Icons.Default.Palette,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(8.dp))
        Text("Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(themePresets) { preset ->
                val isSelected = activeTheme.value.name == preset.name
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        activeTheme.value = preset
                        // Persist
                        context.getSharedPreferences("workouts_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putString("theme", preset.name).apply()
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        // Three color bands
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            drawRect(preset.primary, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(w / 3, h))
                            drawRect(preset.secondary, topLeft = Offset(w / 3, 0f), size = androidx.compose.ui.geometry.Size(w / 3, h))
                            drawRect(preset.tertiary, topLeft = Offset(2 * w / 3, 0f), size = androidx.compose.ui.geometry.Size(w / 3, h))
                        }
                        // Theme name overlay
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                preset.name.first().toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = preset.background
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        preset.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(24.dp))

        // Import section
        Icon(
            Icons.Default.FileUpload,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(8.dp))
        Text("Import Workout History", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { filePicker.launch("text/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
            Text("Select CSV File")
        }
        if (importStatus != null) {
            Spacer(Modifier.height(8.dp))
            Text(importStatus!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun parseCsv(context: android.content.Context, uri: Uri): ImportWorkoutRequest {
    val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Cannot open file")
    val reader = BufferedReader(InputStreamReader(inputStream))

    val dateFormat = SimpleDateFormat("yyyy/MM/dd h:mm a", Locale.US)
    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // Group rows by workout (date + workout number)
    data class CsvRow(
        val date: String, val workoutNum: String, val workoutName: String,
        val bodyWeight: Double?, val exercise: String, val duration: Double,
        val startTime: String, val endTime: String,
        val sets: List<Pair<Int, Double>> // reps, weight
    )

    val rows = mutableListOf<CsvRow>()
    var header = true
    reader.forEachLine { line ->
        if (header) { header = false; return@forEachLine }
        val cols = parseCsvLine(line)
        if (cols.size < 17) return@forEachLine

        val sets = mutableListOf<Pair<Int, Double>>()
        var i = 17
        while (i + 1 < cols.size) {
            val reps = cols[i].trim().toIntOrNull()
            val weight = cols[i + 1].trim().toDoubleOrNull()
            if (reps != null && reps > 0 && weight != null) {
                sets.add(reps to weight)
            }
            i += 2
        }

        rows.add(CsvRow(
            date = cols[0].trim().trim('"'),
            workoutNum = cols[1].trim().trim('"'),
            workoutName = cols[2].trim().trim('"'),
            bodyWeight = cols[4].trim().trim('"').toDoubleOrNull(),
            exercise = cols[5].trim().trim('"'),
            duration = cols[13].trim().trim('"').toDoubleOrNull() ?: 0.0,
            startTime = cols[14].trim().trim('"'),
            endTime = cols[15].trim().trim('"'),
            sets = sets
        ))
    }
    reader.close()

    // Group by date + workout number
    val grouped = rows.groupBy { "${it.date}_${it.workoutNum}" }

    val workouts = grouped.map { (_, exerciseRows) ->
        val first = exerciseRows.first()
        val startStr = "${first.date} ${first.startTime}"
        val endStr = "${first.date} ${first.endTime}"
        val startDate = try { dateFormat.parse(startStr) } catch (_: Exception) { null }
        val endDate = try { dateFormat.parse(endStr) } catch (_: Exception) { null }
        val durationSeconds = (first.duration * 3600).toInt()

        ImportWorkout(
            program_name = first.workoutName,
            started_at = if (startDate != null) isoFormat.format(startDate) else "",
            finished_at = if (endDate != null) isoFormat.format(endDate) else "",
            duration_seconds = durationSeconds,
            body_weight = first.bodyWeight,
            exercises = exerciseRows.map { row ->
                ImportExercise(
                    exercise_name = row.exercise,
                    sets = row.sets.mapIndexed { idx, (reps, weight) ->
                        WorkoutSetInput(
                            exercise_id = 0, // resolved server-side by name
                            set_order = idx + 1,
                            reps = reps,
                            weight = weight
                        )
                    }
                )
            }
        )
    }.sortedBy { it.started_at }

    return ImportWorkoutRequest(workouts = workouts)
}

private fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var current = StringBuilder()
    var inQuotes = false
    for (c in line) {
        when {
            c == '"' -> inQuotes = !inQuotes
            c == ',' && !inQuotes -> { result.add(current.toString()); current = StringBuilder() }
            else -> current.append(c)
        }
    }
    result.add(current.toString())
    return result
}
