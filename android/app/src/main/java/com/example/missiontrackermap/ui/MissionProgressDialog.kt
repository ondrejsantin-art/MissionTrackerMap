package com.example.missiontrackermap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.missiontrackermap.model.UserProgressEntry
import com.example.missiontrackermap.model.CalibrationPoint
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Owner-only dialog showing each participant's mission progress.
 * Triggers a Supabase fetch on open; updates when [allUserProgress] changes.
 */
@Composable
fun MissionProgressDialog(
    viewModel: MissionTrackerViewModel,
    missionId: String,
    totalPoints: Int,
    onDismiss: () -> Unit
) {
    val allProgress by viewModel.allUserProgress.collectAsState()
    val calibration by viewModel.calibration.collectAsState()
    val allPoints = calibration?.points ?: emptyList()

    // Fetch on open
    LaunchedEffect(missionId) {
        viewModel.fetchAllUserProgress(missionId)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.fetchAllUserProgress(missionId) }) {
                Text("Refresh")
            }
        },
        title = { Text("Participant Progress") },
        text = {
            Column {
                Text(
                    text = "Mission: $missionId  •  $totalPoints point${if (totalPoints != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (allProgress.isEmpty()) {
                    Text(
                        text = "No participants yet.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(allProgress) { entry ->
                            ParticipantRow(entry = entry, totalPoints = totalPoints, allPoints = allPoints)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun ParticipantRow(entry: UserProgressEntry, totalPoints: Int, allPoints: List<CalibrationPoint>) {
    val done = entry.completed_points.size
    val fraction = if (totalPoints > 0) done.toFloat() / totalPoints else 0f
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier
        .fillMaxWidth()
        .clickable { expanded = !expanded }
        .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.user_name.ifBlank { "Unknown" },
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "$done / $totalPoints",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth()
        )
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            allPoints.forEach { pt ->
                val timeMillis = entry.completed_points[pt.name]
                val statusText = if (timeMillis != null) {
                    val relativeTime = formatRelativeTime(timeMillis)
                    "Completed ($relativeTime)"
                } else {
                    "Pending"
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = pt.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (timeMillis != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (entry.completed_points.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.completed_points.keys.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

private fun formatRelativeTime(timeMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = kotlin.math.max(0L, now - timeMillis)
    
    val minutes = diff / (60 * 1000)
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        diff < 60 * 1000 -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}
