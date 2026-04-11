package com.fitness.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitness.app.data.Exercise
import com.fitness.app.data.ProgressPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(viewModel: FitnessViewModel) {
    var exercises by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    var selectedExerciseId by remember { mutableStateOf<Long?>(null) }
    var showBodyWeight by remember { mutableStateOf(true) }
    var points by remember { mutableStateOf<List<ProgressPoint>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var allPoints by remember { mutableStateOf<List<ProgressPoint>>(emptyList()) }
    var selectedRange by remember { mutableStateOf("3M") }

    val ranges = listOf("3M", "6M", "1Y", "2Y", "All")

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try { exercises = viewModel.getExercisesWithHistorySync() } catch (_: Exception) {}
        }
    }

    // Load data when selection changes
    LaunchedEffect(showBodyWeight, selectedExerciseId) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                allPoints = if (showBodyWeight) {
                    viewModel.getBodyWeightProgressSync()
                } else {
                    selectedExerciseId?.let { viewModel.getExerciseProgressSync(it) } ?: emptyList()
                }
            } catch (_: Exception) {
                allPoints = emptyList()
            }
        }
        isLoading = false
    }

    // Filter points by date range
    val cutoffMs = when (selectedRange) {
        "3M" -> System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        "6M" -> System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000
        "1Y" -> System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000
        "2Y" -> System.currentTimeMillis() - 730L * 24 * 60 * 60 * 1000
        else -> 0L
    }
    points = if (selectedRange == "All") allPoints else {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        allPoints.filter {
            try { (dateFormat.parse(it.date)?.time ?: 0L) >= cutoffMs } catch (_: Exception) { true }
        }
    }

    var selectedIdx by remember { mutableStateOf(-1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .pointerInput(Unit) { detectTapGestures { selectedIdx = -1 } }
    ) {
        Text(
            "Progress",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Selection chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(
                    selected = showBodyWeight,
                    onClick = { showBodyWeight = true; selectedExerciseId = null },
                    label = { Text("Body Weight") }
                )
            }
            items(exercises) { exercise ->
                FilterChip(
                    selected = !showBodyWeight && selectedExerciseId == exercise.id,
                    onClick = { showBodyWeight = false; selectedExerciseId = exercise.id },
                    label = { Text(exercise.name) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Date range selector
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ranges.forEach { range ->
                FilterChip(
                    selected = selectedRange == range,
                    onClick = { selectedRange = range; selectedIdx = -1 },
                    label = { Text(range) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (points.size < 2) {
            Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.Icon(
                        Icons.Default.ShowChart,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Not enough data yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            // Stats
            val minW = points.minOf { it.weight }
            val maxW = points.maxOf { it.weight }
            val startW = points.first().weight
            val endW = points.last().weight
            val change = endW - startW
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("Start", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${startW}kg", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Column {
                    Text("Current", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${endW}kg", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Column {
                    Text("Change", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val changeColor = if (change >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    Text("${if (change >= 0) "+" else ""}${String.format("%.1f", change)}kg", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = changeColor)
                }
            }
            Spacer(Modifier.height(16.dp))

            // Graph
            val lineColor = MaterialTheme.colorScheme.primary
            val selectedColor = MaterialTheme.colorScheme.secondary
            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            val textColor = MaterialTheme.colorScheme.onSurfaceVariant
            // Selected point info — always reserve space
            Row(
                modifier = Modifier.fillMaxWidth().height(24.dp).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedIdx in points.indices) {
                    val sp = points[selectedIdx]
                    Text(sp.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(12.dp))
                    Text("${sp.weight}kg", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = selectedColor)
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .pointerInput(points) {
                        detectTapGestures { tapOffset ->
                            val padLeft = 50f
                            val padRight = 10f
                            val chartW = size.width - padLeft - padRight
                            if (points.size < 2) return@detectTapGestures
                            // Find closest point to tap x
                            var closest = -1
                            var closestDist = Float.MAX_VALUE
                            points.forEachIndexed { i, _ ->
                                val px = padLeft + chartW * i / (points.size - 1)
                                val dist = kotlin.math.abs(tapOffset.x - px)
                                if (dist < closestDist) {
                                    closestDist = dist
                                    closest = i
                                }
                            }
                            if (closestDist < 60f) {
                                selectedIdx = if (selectedIdx == closest) -1 else closest
                            } else {
                                selectedIdx = -1
                            }
                        }
                    }
            ) {
                val padLeft = 50f
                val padBottom = 30f
                val padTop = 10f
                val padRight = 10f
                val chartW = size.width - padLeft - padRight
                val chartH = size.height - padTop - padBottom

                // Round y axis to 5kg increments
                val yMin = (kotlin.math.floor(minW / 5.0) * 5.0).coerceAtLeast(0.0)
                val yMax = kotlin.math.ceil(maxW / 5.0) * 5.0
                val yRange = if (yMax == yMin) 5.0 else (yMax - yMin)
                val gridStep = 5.0
                val gridCount = (yRange / gridStep).toInt()

                for (i in 0..gridCount) {
                    val value = yMin + i * gridStep
                    val y = padTop + chartH * (1f - ((value - yMin) / yRange).toFloat())
                    drawLine(gridColor, Offset(padLeft, y), Offset(padLeft + chartW, y))
                    drawContext.canvas.nativeCanvas.drawText(
                        "${value.toInt()}", 4f, y + 4f,
                        android.graphics.Paint().apply { color = textColor.hashCode(); textSize = 24f }
                    )
                }

                // Line
                val path = Path()
                points.forEachIndexed { i, p ->
                    val x = padLeft + chartW * i / (points.size - 1).coerceAtLeast(1)
                    val y = padTop + chartH * (1f - ((p.weight - yMin) / yRange).toFloat())
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lineColor, style = Stroke(width = 3f))

                // Dots
                points.forEachIndexed { i, p ->
                    val x = padLeft + chartW * i / (points.size - 1).coerceAtLeast(1)
                    val y = padTop + chartH * (1f - ((p.weight - yMin) / yRange).toFloat())
                    val isSelected = i == selectedIdx
                    drawCircle(if (isSelected) selectedColor else lineColor, radius = if (isSelected) 9f else 5f, center = Offset(x, y))
                    if (isSelected) {
                        // Vertical indicator line
                        drawLine(selectedColor.copy(alpha = 0.3f), Offset(x, padTop), Offset(x, padTop + chartH), strokeWidth = 1f)
                    }
                }
            }
        }
    }
}
