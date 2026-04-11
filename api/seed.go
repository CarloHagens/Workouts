package main

var seedExercises = []Exercise{
	// Chest
	{Name: "Barbell Bench Press", Category: "Barbell", MuscleGroup: "Chest"},
	{Name: "Incline Barbell Bench Press", Category: "Barbell", MuscleGroup: "Chest"},
	{Name: "Decline Barbell Bench Press", Category: "Barbell", MuscleGroup: "Chest"},
	{Name: "Dumbbell Bench Press", Category: "Dumbbell", MuscleGroup: "Chest"},
	{Name: "Incline Dumbbell Press", Category: "Dumbbell", MuscleGroup: "Chest"},
	{Name: "Dumbbell Flyes", Category: "Dumbbell", MuscleGroup: "Chest"},
	{Name: "Cable Crossover", Category: "Cable", MuscleGroup: "Chest"},
	{Name: "Push-Up", Category: "Bodyweight", MuscleGroup: "Chest"},
	{Name: "Chest Dip", Category: "Bodyweight", MuscleGroup: "Chest"},
	{Name: "Machine Chest Press", Category: "Machine", MuscleGroup: "Chest"},
	{Name: "Pec Deck", Category: "Machine", MuscleGroup: "Chest"},

	// Back
	{Name: "Deadlift", Category: "Barbell", MuscleGroup: "Back"},
	{Name: "Barbell Row", Category: "Barbell", MuscleGroup: "Back"},
	{Name: "T-Bar Row", Category: "Barbell", MuscleGroup: "Back"},
	{Name: "Dumbbell Row", Category: "Dumbbell", MuscleGroup: "Back"},
	{Name: "Pull-Up", Category: "Bodyweight", MuscleGroup: "Back"},
	{Name: "Chin-Up", Category: "Bodyweight", MuscleGroup: "Back"},
	{Name: "Lat Pulldown", Category: "Cable", MuscleGroup: "Back"},
	{Name: "Seated Cable Row", Category: "Cable", MuscleGroup: "Back"},
	{Name: "Face Pull", Category: "Cable", MuscleGroup: "Back"},
	{Name: "Machine Row", Category: "Machine", MuscleGroup: "Back"},

	// Shoulders
	{Name: "Overhead Press", Category: "Barbell", MuscleGroup: "Shoulders"},
	{Name: "Dumbbell Shoulder Press", Category: "Dumbbell", MuscleGroup: "Shoulders"},
	{Name: "Arnold Press", Category: "Dumbbell", MuscleGroup: "Shoulders"},
	{Name: "Lateral Raise", Category: "Dumbbell", MuscleGroup: "Shoulders"},
	{Name: "Front Raise", Category: "Dumbbell", MuscleGroup: "Shoulders"},
	{Name: "Rear Delt Fly", Category: "Dumbbell", MuscleGroup: "Shoulders"},
	{Name: "Upright Row", Category: "Barbell", MuscleGroup: "Shoulders"},
	{Name: "Cable Lateral Raise", Category: "Cable", MuscleGroup: "Shoulders"},
	{Name: "Machine Shoulder Press", Category: "Machine", MuscleGroup: "Shoulders"},

	// Legs
	{Name: "Barbell Squat", Category: "Barbell", MuscleGroup: "Legs"},
	{Name: "Front Squat", Category: "Barbell", MuscleGroup: "Legs"},
	{Name: "Romanian Deadlift", Category: "Barbell", MuscleGroup: "Legs"},
	{Name: "Hip Thrust", Category: "Barbell", MuscleGroup: "Legs"},
	{Name: "Bulgarian Split Squat", Category: "Dumbbell", MuscleGroup: "Legs"},
	{Name: "Walking Lunge", Category: "Dumbbell", MuscleGroup: "Legs"},
	{Name: "Goblet Squat", Category: "Dumbbell", MuscleGroup: "Legs"},
	{Name: "Leg Press", Category: "Machine", MuscleGroup: "Legs"},
	{Name: "Hack Squat", Category: "Machine", MuscleGroup: "Legs"},
	{Name: "Leg Curl", Category: "Machine", MuscleGroup: "Legs"},
	{Name: "Leg Extension", Category: "Machine", MuscleGroup: "Legs"},
	{Name: "Calf Raise", Category: "Machine", MuscleGroup: "Legs"},
	{Name: "Seated Calf Raise", Category: "Machine", MuscleGroup: "Legs"},

	// Arms
	{Name: "Barbell Curl", Category: "Barbell", MuscleGroup: "Arms"},
	{Name: "EZ-Bar Curl", Category: "Barbell", MuscleGroup: "Arms"},
	{Name: "Preacher Curl", Category: "Barbell", MuscleGroup: "Arms"},
	{Name: "Skull Crusher", Category: "Barbell", MuscleGroup: "Arms"},
	{Name: "Close-Grip Bench Press", Category: "Barbell", MuscleGroup: "Arms"},
	{Name: "Dumbbell Curl", Category: "Dumbbell", MuscleGroup: "Arms"},
	{Name: "Hammer Curl", Category: "Dumbbell", MuscleGroup: "Arms"},
	{Name: "Concentration Curl", Category: "Dumbbell", MuscleGroup: "Arms"},
	{Name: "Overhead Tricep Extension", Category: "Dumbbell", MuscleGroup: "Arms"},
	{Name: "Tricep Kickback", Category: "Dumbbell", MuscleGroup: "Arms"},
	{Name: "Tricep Pushdown", Category: "Cable", MuscleGroup: "Arms"},
	{Name: "Cable Curl", Category: "Cable", MuscleGroup: "Arms"},
	{Name: "Tricep Dip", Category: "Bodyweight", MuscleGroup: "Arms"},

	// Core
	{Name: "Plank", Category: "Bodyweight", MuscleGroup: "Core"},
	{Name: "Hanging Leg Raise", Category: "Bodyweight", MuscleGroup: "Core"},
	{Name: "Ab Wheel Rollout", Category: "Bodyweight", MuscleGroup: "Core"},
	{Name: "Russian Twist", Category: "Bodyweight", MuscleGroup: "Core"},
	{Name: "Decline Sit-Up", Category: "Bodyweight", MuscleGroup: "Core"},
	{Name: "Cable Crunch", Category: "Cable", MuscleGroup: "Core"},
	{Name: "Woodchop", Category: "Cable", MuscleGroup: "Core"},
}
