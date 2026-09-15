package dev.navisense

import dev.navisense.contracts.NormalizedBoundingBox
import dev.navisense.navigation.WalkingCorridor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkingCorridorTest {

    private val corridor = WalkingCorridor() // default: left=0.30, top=0.30, right=0.70, bottom=1.00

    @Test
    fun testCenterObstacleOverlapsCorridor() {
        // Box centered directly in the corridor
        val box = NormalizedBoundingBox(left = 0.40f, top = 0.50f, right = 0.60f, bottom = 0.80f)
        assertTrue(corridor.isCorridorObstacle(box))
    }

    @Test
    fun testFarSideObstacleDoesNotOverlap() {
        // Box on far left side outside corridor (left=0.05, right=0.25)
        val box = NormalizedBoundingBox(left = 0.05f, top = 0.40f, right = 0.25f, bottom = 0.70f)
        assertFalse(corridor.isCorridorObstacle(box))
        assertEquals(0.0f, corridor.calculateIntersectionArea(box), 0.0001f)
    }

    @Test
    fun testMarginalOverlapBelowTwentyPercent() {
        // Box width=0.20 (0.28 to 0.48), height=0.20 (0.40 to 0.60), area=0.04
        // Corridor starts at x=0.30, so intersection width = 0.48 - 0.30 = 0.18.
        // Wait, 0.18 / 0.20 = 90% overlap!
        // To get < 20% overlap:
        // Box from 0.20 to 0.31 (width = 0.11), height = 0.20, area = 0.022.
        // Intersection from 0.30 to 0.31 (width = 0.01), area = 0.002.
        // Overlap ratio = 0.002 / 0.022 = 9.1% < 20%.
        val box = NormalizedBoundingBox(left = 0.20f, top = 0.40f, right = 0.31f, bottom = 0.60f)
        assertFalse("Overlap < 20% must not be classified as corridor obstacle", corridor.isCorridorObstacle(box))
    }

    @Test
    fun testOverlapAboveTwentyPercent() {
        // Box from 0.25 to 0.35 (width = 0.10), height = 0.20, area = 0.02
        // Intersection from 0.30 to 0.35 (width = 0.05), area = 0.01
        // Overlap ratio = 0.01 / 0.02 = 50% >= 20%.
        val box = NormalizedBoundingBox(left = 0.25f, top = 0.40f, right = 0.35f, bottom = 0.60f)
        assertTrue("Overlap >= 20% must be classified as corridor obstacle", corridor.isCorridorObstacle(box))
    }
}
