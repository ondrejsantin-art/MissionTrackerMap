package com.example.missiontrackermap.location

import android.content.ContextWrapper
import kotlin.test.Test
import kotlin.test.assertFalse

class FusedLocationProviderTest {
    @Test
    fun testDefaultOverrideDisabled() {
        val mockContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): android.content.Context = this
        }
        val provider = FusedLocationProvider(mockContext)
        assertFalse(provider.isOverrideEnabled.value, "GPS override should be disabled by default")
    }
}
