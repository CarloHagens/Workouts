package com.workouts.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument

data class BottomTab(val route: String, val label: String, val icon: ImageVector)

val bottomTabs = listOf(
    BottomTab("workout", "Workout", Icons.Default.FitnessCenter),
    BottomTab("programs", "Programs", Icons.AutoMirrored.Filled.ListAlt),
    BottomTab("history", "History", Icons.Default.History),
    BottomTab("progress", "Progress", Icons.AutoMirrored.Filled.ShowChart),
    BottomTab("settings", "Settings", Icons.Default.Settings),
)

@Composable
fun FitnessNavGraph(
    navController: NavHostController,
    viewModel: WorkoutsViewModel
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val activeWorkout by viewModel.activeWorkout.collectAsState()

    // Auto-navigate to workout tab if there's an active workout on launch
    LaunchedEffect(activeWorkout) {
        if (activeWorkout != null && currentRoute == "programs") {
            navController.navigate("workout") {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
            }
        }
    }

    val showBottomBar = currentRoute in bottomTabs.map { it.route } || currentRoute == null
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "workout",
            modifier = Modifier.padding(padding)
        ) {
            composable("workout") {
                if (activeWorkout != null) {
                    ActiveWorkoutScreen(
                        viewModel = viewModel,
                        onFinished = {
                            navController.navigate("history") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                            }
                        }
                    )
                } else {
                    NoActiveWorkoutScreen(viewModel = viewModel)
                }
            }

            composable("programs") {
                ProgramListScreen(
                    viewModel = viewModel,
                    onProgramClick = { program ->
                        navController.navigate("programs/${program.id}")
                    }
                )
            }

            composable(
                route = "programs/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStackEntry ->
                val programId = backStackEntry.arguments?.getLong("id") ?: return@composable
                ProgramDetailScreen(
                    viewModel = viewModel,
                    programId = programId,
                    onAddExercises = { navController.navigate("programs/$programId/add-exercises") },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "programs/{id}/add-exercises",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStackEntry ->
                val programId = backStackEntry.arguments?.getLong("id") ?: return@composable
                AddExercisesScreen(
                    viewModel = viewModel,
                    programId = programId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("history") {
                WorkoutHistoryScreen(viewModel = viewModel)
            }

            composable("progress") {
                ProgressScreen(viewModel = viewModel)
            }

            composable("settings") {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun NoActiveWorkoutScreen(viewModel: WorkoutsViewModel) {
    val programs by viewModel.programs.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadPrograms() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Start Workout",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (programs.isEmpty() || programs.none { it.exercise_count > 0 }) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("No programs yet", style = MaterialTheme.typography.titleMedium)
                    Text("Create one in the Programs tab", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val startablePrograms = programs.filter { it.exercise_count > 0 }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(startablePrograms, key = { it.id }) { program ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.loadProgramAndStart(program.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(program.name, style = MaterialTheme.typography.titleMedium)
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
