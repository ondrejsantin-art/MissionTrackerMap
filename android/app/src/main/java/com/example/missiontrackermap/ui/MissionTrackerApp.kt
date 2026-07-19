package com.example.missiontrackermap.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionTrackerApp(
    navController: NavHostController,
    viewModel: MissionTrackerViewModel
) {
    NavHost(navController = navController, startDestination = "map") {
        composable("map") {
            MapScreen(
                viewModel = viewModel,
                onNavigateToLogin = { navController.navigate("login") },
                onNavigateToEditMission = { navController.navigate("edit_mission") }
            )
        }
        composable("login") {
            LoginScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("edit_mission") {
            EditMissionScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
