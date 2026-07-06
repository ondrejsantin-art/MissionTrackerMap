package com.example.missiontrackermap.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun MapScreen(
    viewModel: MissionTrackerViewModel,
    modifier: Modifier = Modifier
) {
    val missionTracker = viewModel.missionTracker

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = missionTracker.title,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
