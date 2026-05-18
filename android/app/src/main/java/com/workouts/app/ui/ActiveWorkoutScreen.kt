package com.workouts.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workouts.app.data.ActiveExerciseEntity
import com.workouts.app.data.ActiveSetEntity
import com.workouts.app.data.UpsertExerciseSettingsRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    viewModel: WorkoutsViewModel,
    onFinished: () -> Unit
) {
    val workout by viewModel.activeWorkout.collectAsState()
    val exercises by viewModel.activeExercises.collectAsState()
    val sets by viewModel.activeSets.collectAsState()
    val completedWarmups by viewModel.completedWarmups.collectAsState()
    val elapsed by viewModel.elapsedSeconds.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val breakElapsed by viewModel.breakElapsed.collectAsState()
    val breakTarget by viewModel.breakTarget.collectAsState()
    val showDeload by viewModel.showDeloadSuggestion.collectAsState()
    val breakRunning by viewModel.breakRunning.collectAsState()
    val restExerciseId by viewModel.lastLoggedExerciseId.collectAsState()
    val bodyWeight by viewModel.bodyWeight.collectAsState()
    val focusManager = LocalFocusManager.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Working Sets, 1 = Warm-up
    var showFinishConfirm by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var showSettingsFor by remember { mutableStateOf<ActiveExerciseEntity?>(null) }
    var showBreakConfig by remember { mutableStateOf(false) }
    var bodyWeightText by remember { mutableStateOf(bodyWeight?.toString() ?: "") }

    val hours = elapsed / 3600
    val minutes = (elapsed % 3600) / 60
    val seconds = elapsed % 60
    val timerText = if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)


    Column(
        Modifier
            .fillMaxSize()
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                focusManager.clearFocus()
            }
    ) {
        // Header row with program name, timer, rest pill, and actions
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(workout?.programName ?: "Workout", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(timerText, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = { showCancelConfirm = true }) {
                Text("Cancel", color = MaterialTheme.colorScheme.error)
            }
        }

        // Tabs
        // Deload suggestion banner
        if (showDeload) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "It\u2019s been over a week \u2014 deload?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { viewModel.applyDeload() },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Deload", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { viewModel.dismissDeloadSuggestion() }) {
                    Text("Skip", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        PrimaryTabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("Working Sets", modifier = Modifier.padding(vertical = 12.dp))
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("Warm-up", modifier = Modifier.padding(vertical = 12.dp))
            }
        }

        // Tab content with subtle background
        Box(
            modifier = Modifier
                .weight(1f)
        ) {
            when (selectedTab) {
                0 -> WorkingTab(exercises, sets, viewModel) { showSettingsFor = it }
                1 -> WarmupTab(exercises, completedWarmups, viewModel) { selectedTab = 0 }
            }
        }

        // Rest timer bar (always visible)
        val restActive = breakRunning || breakElapsed > 0
        val overRest = restActive && breakElapsed >= breakTarget && breakTarget > 0
        val barColor = when {
            overRest -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.secondary
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Show the active target when timer running, otherwise the configured duration
            val activeDur = if (restActive) breakTarget else {
                val d by viewModel.breakDuration.collectAsState()
                d
            }
            val durText = if (activeDur >= 60) {
                val m = activeDur / 60
                val s = activeDur % 60
                if (s == 0) "$m min" else "$m.${s * 10 / 60} min"
            } else "${activeDur}s"

            Text("REST $durText", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = barColor)
            if (restActive) {
                Spacer(Modifier.width(8.dp))
                val bMin = breakElapsed / 60
                val bSec = breakElapsed % 60
                Text(
                    if (overRest) "${String.format("%d:%02d", bMin, bSec)} \u2022 GO!" else String.format("%d:%02d", bMin, bSec),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (overRest) barColor else barColor.copy(alpha = 0.7f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { viewModel.skipBreakTimer() },
                    border = BorderStroke(1.dp, barColor.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Dismiss", style = MaterialTheme.typography.labelSmall, color = barColor)
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showBreakConfig = true }) {
                Icon(Icons.Default.Timer, contentDescription = "Rest settings", tint = barColor)
            }
        }

        // Bottom bar: body weight + finish — transparent background
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rounded pill-style BW input
            BasicTextField(
                value = bodyWeightText,
                onValueChange = { v ->
                    bodyWeightText = v.filter { c -> c.isDigit() || c == '.' }
                    viewModel.setBodyWeight(bodyWeightText.toDoubleOrNull())
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(50.dp)) { innerTextField() }
                        Text(" kg", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { showFinishConfirm = true },
                enabled = !isLoading && exercises.all { ex ->
                    val count = sets.count { it.exerciseId == ex.exerciseId }
                    count >= ex.targetSets
                }
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                else Text("Finish")
            }
        }
    }

    // Exercise settings dialog
    showSettingsFor?.let { exercise ->
        ExerciseSettingsDialog(
            exercise = exercise,
            programId = workout?.programId ?: 0,
            viewModel = viewModel,
            onDismiss = { showSettingsFor = null }
        )
    }

    // Break duration config dialog
    if (showBreakConfig) {
        BreakDurationDialog(viewModel) { showBreakConfig = false }
    }

    if (showFinishConfirm) {
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text("Finish Workout?") },
            text = { Text("This will save your workout and record your progress.") },
            confirmButton = {
                TextButton(onClick = { showFinishConfirm = false; viewModel.finishWorkout(); onFinished() }) { Text("Finish") }
            },
            dismissButton = { TextButton(onClick = { showFinishConfirm = false }) { Text("Continue") } }
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel Workout?") },
            text = { Text("All progress for this session will be lost.") },
            confirmButton = {
                TextButton(onClick = { showCancelConfirm = false; viewModel.cancelWorkout() }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showCancelConfirm = false }) { Text("Keep Going") } }
        )
    }
}

@Composable
private fun WarmupTab(
    exercises: List<ActiveExerciseEntity>,
    completedWarmups: List<ActiveSetEntity>,
    viewModel: WorkoutsViewModel,
    onExerciseWarmupsComplete: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        exercises.forEach { exercise ->
            item(key = "warmup_${exercise.exerciseId}") {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                        if (exercise.workingWeight > 0) {
                            Text(
                                "Working weight: ${exercise.workingWeight}kg",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            val warmups = viewModel.getWarmupSets(exercise.workingWeight, exercise.targetReps, exercise.name)
                            val exerciseWarmups = completedWarmups.filter { it.exerciseId == exercise.exerciseId }
                            val doneCount = exerciseWarmups.size
                            val isLastWarmup = warmups.isNotEmpty() && doneCount == warmups.size - 1

                            warmups.forEachIndexed { i, wu ->
                                val isDone = i < doneCount
                                val isNext = !isDone && i == doneCount
                                val isUndoable = isDone && i == doneCount - 1
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .then(
                                            if (isNext) {
                                                Modifier.clickable {
                                                    viewModel.logWarmupSet(
                                                        exercise.exerciseId, exercise.name,
                                                        wu.reps, wu.weight
                                                    )
                                                    if (isLastWarmup) {
                                                        viewModel.startWarmupToWorkingBreak(exercise.exerciseId, exercise.name, exercise.workingWeight, exercise.targetReps, exercise.targetSets)
                                                        onExerciseWarmupsComplete()
                                                    }
                                                }
                                            } else if (isUndoable) {
                                                Modifier.clickable {
                                                    exerciseWarmups.lastOrNull()?.let { viewModel.undoWarmupSet(it.id) }
                                                }
                                            } else Modifier
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDone)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        else if (isNext)
                                            MaterialTheme.colorScheme.surfaceVariant
                                        else
                                            MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Set ${i + 1}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "${wu.weight}kg \u00d7 ${wu.reps}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isUndoable) {
                                            Text("UNDO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                        } else if (isDone) {
                                            Text("\u2713", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        } else if (isNext) {
                                            Text("TAP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        } else {
                                            Spacer(Modifier.width(24.dp))
                                        }
                                    }
                                }
                            }
                            if (warmups.isEmpty()) {
                                Text("Weight too light for warm-up", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else if (doneCount >= warmups.size) {
                                Spacer(Modifier.height(4.dp))
                                Text("Warm-up complete \u2014 switch to Working Sets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            }
                        } else {
                            Text("Configure working weight on the Working Sets tab first", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkingTab(
    exercises: List<ActiveExerciseEntity>,
    sets: List<ActiveSetEntity>,
    viewModel: WorkoutsViewModel,
    onExerciseSettings: (ActiveExerciseEntity) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        exercises.forEach { exercise ->
            val exerciseSets = sets.filter { it.exerciseId == exercise.exerciseId }
            val configured = exercise.workingWeight > 0

            item(key = "work_${exercise.exerciseId}") {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exercise.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
                                if (configured) {
                                    Text(
                                        "${exercise.workingWeight}kg",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (configured) {
                                Box(modifier = Modifier.width(120.dp).offset(x = (-10).dp), contentAlignment = Alignment.CenterStart) {
                                    PlateDisplay(exercise.workingWeight)
                                }
                            }
                            IconButton(onClick = { onExerciseSettings(exercise) }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }

                        if (!configured) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Tap the gear to set your working weight, reps, and progression",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Spacer(Modifier.height(8.dp))

                            // Set circles — evenly distributed rows, bigger, with reserved checkmark space
                            val setsRemaining = exercise.targetSets - exerciseSets.size
                            val totalSlots = exercise.targetSets
                            val circleSize = 44.dp
                            val maxPerRow = 6
                            val rows = (totalSlots + maxPerRow - 1) / maxPerRow
                            val perRow = if (rows > 0) (totalSlots + rows - 1) / rows else totalSlots
                            val completedCount = exerciseSets.size

                            val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
                                initialValue = 0.1f,
                                targetValue = 0.35f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulseAlpha"
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (row in 0 until rows) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val startIdx = row * perRow
                                        val endIdx = minOf(startIdx + perRow, totalSlots)
                                        for (i in startIdx until endIdx) {
                                            if (i < completedCount) {
                                                // Completed set
                                                val set = exerciseSets[i]
                                                val isSuccess = set.reps >= exercise.targetReps
                                                Box(
                                                    modifier = Modifier
                                                        .size(circleSize)
                                                        .background(
                                                            if (isSuccess) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                                            else MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                                            shape = androidx.compose.foundation.shape.CircleShape
                                                        )
                                                        .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { viewModel.decrementSetReps(set.id) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        "${set.reps}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            } else if (i == completedCount) {
                                                // Next set — pulsing
                                                Box(
                                                    modifier = Modifier
                                                        .size(circleSize)
                                                        .background(
                                                            MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                                                            shape = androidx.compose.foundation.shape.CircleShape
                                                        )
                                                        .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                                                            viewModel.logWorkingSet(exercise.exerciseId, exercise.name, exercise.targetReps, exercise.workingWeight)
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        "${exercise.targetReps}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = Color.White.copy(alpha = 0.7f)
                                                    )
                                                }
                                            } else {
                                                // Future set — dim
                                                Box(
                                                    modifier = Modifier
                                                        .size(circleSize)
                                                        .background(
                                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                                            shape = androidx.compose.foundation.shape.CircleShape
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        "${exercise.targetReps}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = Color.White.copy(alpha = 0.3f)
                                                    )
                                                }
                                            }
                                        }
                                        // Checkmark on first row, always reserved
                                        if (row == 0) {
                                            if (setsRemaining <= 0) {
                                                Text("\u2713", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
                                            } else {
                                                Spacer(Modifier.width(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseSettingsDialog(
    exercise: ActiveExerciseEntity,
    programId: Long,
    viewModel: WorkoutsViewModel,
    onDismiss: () -> Unit
) {
    var weight by remember { mutableStateOf(if (exercise.workingWeight > 0) exercise.workingWeight.toString() else "") }
    var reps by remember { mutableStateOf(exercise.targetReps.toString()) }
    var numSets by remember { mutableStateOf(exercise.targetSets.toString()) }
    var increment by remember { mutableStateOf(exercise.incrementAmount.toString()) }
    var deload by remember { mutableStateOf(exercise.deloadPercent.toString()) }
    var successThreshold by remember { mutableStateOf(exercise.successThreshold.toString()) }
    var failureThreshold by remember { mutableStateOf(exercise.failureThreshold.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exercise.name) },
        text = {
            Column {
                Text("Weight & Targets", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weight, onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("kg") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = numSets, onValueChange = { numSets = it.filter { c -> c.isDigit() } },
                        label = { Text("Sets") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = reps, onValueChange = { reps = it.filter { c -> c.isDigit() } },
                        label = { Text("Reps") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Progression", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = increment, onValueChange = { increment = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("+kg") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = deload, onValueChange = { deload = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("-%") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = successThreshold, onValueChange = { successThreshold = it.filter { c -> c.isDigit() } },
                        label = { Text("Win streak") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = failureThreshold, onValueChange = { failureThreshold = it.filter { c -> c.isDigit() } },
                        label = { Text("Fail streak") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Weight increases after ${successThreshold.ifEmpty { "?" }} successful workouts, decreases after ${failureThreshold.ifEmpty { "?" }} failures",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = weight.toDoubleOrNull() ?: return@TextButton
                val r = reps.toIntOrNull() ?: return@TextButton
                val s = numSets.toIntOrNull() ?: return@TextButton
                val inc = increment.toDoubleOrNull() ?: return@TextButton
                val dec = deload.toDoubleOrNull() ?: return@TextButton
                val st = successThreshold.toIntOrNull() ?: return@TextButton
                val ft = failureThreshold.toIntOrNull() ?: return@TextButton
                if (w > 0 && r > 0 && s > 0) {
                    viewModel.saveExerciseSettings(
                        programId, exercise.exerciseId,
                        UpsertExerciseSettingsRequest(w, r, s, inc, dec, st, ft)
                    )
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BreakDurationDialog(viewModel: WorkoutsViewModel, onDismiss: () -> Unit) {
    val current by viewModel.breakDuration.collectAsState()
    val currentFail by viewModel.failBreakDuration.collectAsState()
    var value by remember { mutableStateOf(current.toString()) }
    var failValue by remember { mutableStateOf(currentFail.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rest Timer Duration") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filter { c -> c.isDigit() } },
                    label = { Text("Rest after success (seconds)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = failValue,
                    onValueChange = { failValue = it.filter { c -> c.isDigit() } },
                    label = { Text("Rest after failure (seconds)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Longer rest kicks in when you tap down the reps on a set",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val secs = value.toIntOrNull()
                val failSecs = failValue.toIntOrNull()
                if (secs != null && secs > 0 && failSecs != null && failSecs > 0) {
                    viewModel.setBreakDuration(secs)
                    viewModel.setFailBreakDuration(failSecs)
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PlateDisplay(totalWeight: Double) {
    val barWeight = 20.0
    val perSide = (totalWeight - barWeight) / 2.0
    if (perSide <= 0) return

    val availablePlates = listOf(20.0, 10.0, 5.0, 2.5, 1.25)
    val plates = mutableListOf<Double>()
    var remaining = perSide
    for (plate in availablePlates) {
        while (remaining >= plate - 0.001) {
            plates.add(plate)
            remaining -= plate
        }
    }
    if (plates.isEmpty()) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Box(Modifier.width(8.dp).height(5.dp).background(Color(0xFF9CA3AF), shape = androidx.compose.foundation.shape.RoundedCornerShape(1.dp)))
        plates.forEach { plate ->
            val color = when (plate) {
                20.0 -> Color(0xFF3B82F6); 10.0 -> Color(0xFF22C55E); 5.0 -> Color(0xFF8B5CF6)
                2.5 -> Color(0xFFF97316); else -> Color(0xFF94A3B8)
            }
            val w = when (plate) { 20.0 -> 12.dp; 10.0 -> 9.dp; else -> 8.dp }
            val h = when { plate >= 5.0 -> 42.dp; plate >= 2.5 -> 36.dp; else -> 34.dp }
            val label = if (plate == plate.toLong().toDouble()) "${plate.toLong()}" else "$plate"
            Box(
                modifier = Modifier.width(w).height(h).background(color, shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer { rotationZ = 270f }
                        .requiredWidth(h),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 6.sp, lineHeight = 6.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
