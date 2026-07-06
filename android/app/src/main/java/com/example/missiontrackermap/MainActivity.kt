package com.example.missiontrackermap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.missiontrackermap.ui.MissionTrackerApp
import com.example.missiontrackermap.ui.MissionTrackerTheme
import com.example.missiontrackermap.ui.MissionTrackerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MissionTrackerTheme {
                Surface {
                    val navController = rememberNavController()
                    val viewModel: MissionTrackerViewModel = viewModel()

                    MissionTrackerApp(navController = navController, viewModel = viewModel)
                }
            }
        }
    }
}
