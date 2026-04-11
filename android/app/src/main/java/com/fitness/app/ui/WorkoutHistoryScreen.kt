package com.fitness.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitness.app.data.WorkoutDetailResponse
import com.fitness.app.data.WorkoutSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun WorkoutHistoryScreen(
    viewModel: FitnessViewModel
) {
    val workouts by viewModel.workoutHistory.collectAsState()

    var currentMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var selectedWorkouts by remember { mutableStateOf<List<WorkoutSummary>>(emptyList()) }
    val loadedDetails = remember { androidx.compose.runtime.mutableStateMapOf<Long, WorkoutDetailResponse>() }
    var showDeleteId by remember { mutableStateOf<Long?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }
    var showBodyWeightFor by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) { viewModel.loadWorkoutHistory() }

    // Map dates to workouts
    val workoutDates = remember(workouts) {
        workouts.groupBy { it.started_at.take(10) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { showDeleteAll = true }) {
                Text("Clear All", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        }

        // Month navigation
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                currentMonth = (currentMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                selectedDate = null
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous month", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                monthFormat.format(currentMonth.time),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = {
                currentMonth = (currentMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                selectedDate = null
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next month", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Day headers
        val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        Row(modifier = Modifier.fillMaxWidth()) {
            dayLabels.forEach { day ->
                Text(
                    day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Calendar grid
        val cal = currentMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday=0
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = dateFormat.format(Calendar.getInstance().time)

        val totalCells = firstDayOfWeek + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val day = cellIndex - firstDayOfWeek + 1

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day in 1..daysInMonth) {
                            val dateCal = currentMonth.clone() as Calendar
                            dateCal.set(Calendar.DAY_OF_MONTH, day)
                            val dateStr = dateFormat.format(dateCal.time)
                            val hasWorkout = dateStr in workoutDates
                            val isSelected = dateStr == selectedDate
                            val isToday = dateStr == today

                            val bgColor = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                hasWorkout -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                            }
                            val textColor = when {
                                isSelected -> MaterialTheme.colorScheme.onSurface
                                isToday -> MaterialTheme.colorScheme.primary
                                hasWorkout -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(bgColor, CircleShape)
                                    .then(
                                        if (hasWorkout) Modifier.clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                                            selectedDate = dateStr
                                            selectedWorkouts = workoutDates[dateStr] ?: emptyList()
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$day",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (hasWorkout || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Selected day workouts
        if (selectedDate != null && selectedWorkouts.isNotEmpty()) {
            selectedWorkouts.forEach { workout ->
                val duration = workout.duration_seconds
                val hours = duration / 3600
                val minutes = (duration % 3600) / 60
                val durationText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

                val detail = loadedDetails[workout.id]
                LaunchedEffect(workout.id) {
                    if (detail == null) {
                        viewModel.loadWorkoutDetail(workout.id) { loadedDetails[workout.id] = it }
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(workout.program_name, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(durationText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    if (workout.body_weight != null) {
                                        Text("${workout.body_weight}kg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }
                        }

                        if (detail != null) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.height(8.dp))
                                val grouped = detail.sets.groupBy { it.exercise_id }
                                    grouped.forEach { (_, sets) ->
                                        Text(
                                            sets.first().exercise_name,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        sets.forEachIndexed { i, set ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Set ${i + 1}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("${set.weight}kg \u00d7 ${set.reps}", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        TextButton(onClick = { showDeleteId = workout.id }) {
                                            Text("Delete", color = MaterialTheme.colorScheme.error)
                                        }
                                        TextButton(onClick = { showBodyWeightFor = workout.id }) {
                                            Text("Add body weight", color = MaterialTheme.colorScheme.secondary)
                                        }
                                    }
                                }
                        } else {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                                Text("Loading...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else if (selectedDate == null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Tap a highlighted day to view workout details",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Delete single workout dialog
    showDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { showDeleteId = null },
            title = { Text("Delete Workout?") },
            text = { Text("This will permanently remove this workout from your history.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWorkout(id)
                    loadedDetails.remove(id)
                    selectedWorkouts = selectedWorkouts.filter { it.id != id }
                    showDeleteId = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteId = null }) { Text("Cancel") }
            }
        )
    }

    // Delete all dialog
    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text("Delete All History?") },
            text = { Text("This will permanently remove all workout history. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllWorkouts()
                    loadedDetails.clear()
                    selectedDate = null; selectedWorkouts = emptyList()
                    showDeleteAll = false
                }) { Text("Delete All", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll = false }) { Text("Cancel") }
            }
        )
    }

    // Body weight dialog
    showBodyWeightFor?.let { workoutId ->
        var bwText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBodyWeightFor = null },
            title = { Text("Body Weight") },
            text = {
                OutlinedTextField(
                    value = bwText,
                    onValueChange = { bwText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Weight (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val w = bwText.toDoubleOrNull()
                    if (w != null && w > 0) {
                        viewModel.updateWorkoutBodyWeight(workoutId, w)
                        showBodyWeightFor = null
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showBodyWeightFor = null }) { Text("Cancel") }
            }
        )
    }
}
