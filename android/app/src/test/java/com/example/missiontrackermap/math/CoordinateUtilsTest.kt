package com.example.missiontrackermap.math

import kotlin.test.Test
import kotlin.test.assertEquals

class CoordinateUtilsTest {

    @Test
    fun calculateNeedleRotation_north_returnsZero() {
        assertEquals(0f, CoordinateUtils.calculateNeedleRotation(0f))
        assertEquals(0f, CoordinateUtils.calculateNeedleRotation(360f))
    }

    @Test
    fun calculateNeedleRotation_east_returnsWest() {
        assertEquals(270f, CoordinateUtils.calculateNeedleRotation(90f))
    }

    @Test
    fun calculateNeedleRotation_south_returnsSouth() {
        assertEquals(180f, CoordinateUtils.calculateNeedleRotation(180f))
    }

    @Test
    fun calculateNeedleRotation_west_returnsEast() {
        assertEquals(90f, CoordinateUtils.calculateNeedleRotation(270f))
    }

    @Test
    fun calculateNeedleRotation_negativeAndOutOfBounds_handlesCorrectly() {
        assertEquals(270f, CoordinateUtils.calculateNeedleRotation(450f))
        assertEquals(90f, CoordinateUtils.calculateNeedleRotation(-90f))
        assertEquals(270f, CoordinateUtils.calculateNeedleRotation(-270f))
    }
}
