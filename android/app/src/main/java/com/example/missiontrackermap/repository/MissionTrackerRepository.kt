package com.example.missiontrackermap.repository

import android.content.Context
import com.example.missiontrackermap.model.MissionState

/**
 * Repository that provides mission data to the ViewModel.
 * Currently loads the default 'scarif' mission from assets.
 * Will be extended in Task G to support multiple missions from filesystem.
 */
class MissionTrackerRepository(private val context: Context) {

    private val loader = MissionPackageLoader(context)

    /** Default mission ID bundled in assets. */
    private val defaultMissionId = "scarif"

    fun loadDefaultMission(): MissionState? = loader.load(defaultMissionId)

    fun loadMission(missionId: String): MissionState? = loader.load(missionId)

    fun availableMissions(): List<String> = loader.availableMissions()
}
