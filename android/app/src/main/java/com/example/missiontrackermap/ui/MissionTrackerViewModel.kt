package com.example.missiontrackermap.ui

import androidx.lifecycle.ViewModel
import com.example.missiontrackermap.model.MissionTrackerModel
import com.example.missiontrackermap.repository.MissionTrackerRepository

class MissionTrackerViewModel(
    private val repository: MissionTrackerRepository = MissionTrackerRepository()
) : ViewModel() {

    val missionTracker: MissionTrackerModel
        get() = repository.getMissionTracker()
}
