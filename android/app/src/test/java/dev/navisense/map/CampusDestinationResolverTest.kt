package dev.navisense.map

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CampusDestinationResolverTest {

    private val testPois = listOf(
        MapPOI("poi_main_gate", "Main Entrance Gate", "gate", 12.8407, 80.1534, "Main entrance", 13294508649L, 17.2),
        MapPOI("poi_academic_block_1", "Academic Block 1 (AB1)", "academic", 12.8398, 80.1550, "Academic Block 1", 13521476190L, 23.6),
        MapPOI("poi_academic_block_2", "Academic Block 2 (AB2)", "academic", 12.8415, 80.1565, "Academic Block 2", 12320705259L, 38.0),
        MapPOI("poi_academic_block_3", "Academic Block 3 (AB3)", "academic", 12.8422, 80.1548, "Academic Block 3", 12187442623L, 79.5),
        MapPOI("poi_library", "Central Library", "facility", 12.8402, 80.1542, "Central Library", 7781104575L, 19.6),
        MapPOI("poi_food_court", "Food Court / Ambrosia Canteen", "food", 12.8420, 80.1555, "Food Court", 3433521046L, 45.6),
        MapPOI("poi_hostel_delta", "Delta Hostel Block", "hostel", 12.8430, 80.1570, "Delta Hostel", 7781021784L, 26.3),
        MapPOI("poi_hostel_gamma", "Gamma Hostel Block", "hostel", 12.8425, 80.1580, "Gamma Hostel", 7169454658L, 36.7),
        MapPOI("poi_admin_block", "Admin Block", "admin", 12.8405, 80.1538, "Admin Block", 11064265224L, 8.3),
        MapPOI("poi_sports_complex", "Sports Complex & Ground", "sports", 12.8385, 80.1560, "Sports Ground", 7732824117L, 7.1),
        MapPOI("poi_kelambakkam_road", "Vandalur-Kelambakkam Bus Stop", "transit", 12.8409, 80.1528, "Bus Stop", 12406042223L, 20.6)
    )

    @Test
    fun testAB1ExactAndAliasResolution() {
        val variations = listOf(
            "ab1",
            "AB1",
            "ab 1",
            "take me to ab1",
            "navigate to ab 1",
            "academic block 1",
            "block 1",
            "Academic Block 1 (AB1)"
        )
        for (query in variations) {
            val res = CampusDestinationResolver.resolve(query, testPois)
            assertTrue("Expected ExactMatch for '$query', got $res", res is CampusResolutionResult.ExactMatch)
            assertEquals("poi_academic_block_1", (res as CampusResolutionResult.ExactMatch).poi.id)
        }
    }

    @Test
    fun testAB2AndAB3DistinctResolution() {
        val resAb2 = CampusDestinationResolver.resolve("take me to ab2", testPois)
        assertTrue(resAb2 is CampusResolutionResult.ExactMatch)
        assertEquals("poi_academic_block_2", (resAb2 as CampusResolutionResult.ExactMatch).poi.id)

        val resAb3 = CampusDestinationResolver.resolve("walk to ab 3", testPois)
        assertTrue(resAb3 is CampusResolutionResult.ExactMatch)
        assertEquals("poi_academic_block_3", (resAb3 as CampusResolutionResult.ExactMatch).poi.id)
    }

    @Test
    fun testAmbrosiaAndLibraryShortcuts() {
        val ambrosia = CampusDestinationResolver.resolve("take me to ambrosia", testPois)
        assertTrue(ambrosia is CampusResolutionResult.ExactMatch)
        assertEquals("poi_food_court", (ambrosia as CampusResolutionResult.ExactMatch).poi.id)

        val canteen = CampusDestinationResolver.resolve("canteen", testPois)
        assertTrue(canteen is CampusResolutionResult.ExactMatch)
        assertEquals("poi_food_court", (canteen as CampusResolutionResult.ExactMatch).poi.id)

        val lib = CampusDestinationResolver.resolve("central library", testPois)
        assertTrue(lib is CampusResolutionResult.ExactMatch)
        assertEquals("poi_library", (lib as CampusResolutionResult.ExactMatch).poi.id)
    }

    @Test
    fun testHostelDisambiguationWhenUnspecified() {
        val res = CampusDestinationResolver.resolve("hostel", testPois)
        assertTrue("Expected DisambiguationRequired for 'hostel', got $res", res is CampusResolutionResult.DisambiguationRequired)
        val candidates = (res as CampusResolutionResult.DisambiguationRequired).candidates
        assertEquals(2, candidates.size)
        assertTrue(candidates.any { it.id == "poi_hostel_delta" })
        assertTrue(candidates.any { it.id == "poi_hostel_gamma" })
    }

    @Test
    fun testHostelSpecificResolution() {
        val delta = CampusDestinationResolver.resolve("delta hostel", testPois)
        assertTrue(delta is CampusResolutionResult.ExactMatch)
        assertEquals("poi_hostel_delta", (delta as CampusResolutionResult.ExactMatch).poi.id)

        val gamma = CampusDestinationResolver.resolve("gamma hostel", testPois)
        assertTrue(gamma is CampusResolutionResult.ExactMatch)
        assertEquals("poi_hostel_gamma", (gamma as CampusResolutionResult.ExactMatch).poi.id)
    }

    @Test
    fun testOffCampusQueryDetection() {
        val offCampusQueries = listOf(
            "phoenix mall",
            "navigate to Phoenix Mall",
            "Chennai airport",
            "Central railway station",
            "Marina Beach",
            "Tambaram hospital"
        )
        for (q in offCampusQueries) {
            val res = CampusDestinationResolver.resolve(q, testPois)
            assertTrue("Expected OffCampusQuery for '$q', got $res", res is CampusResolutionResult.OffCampusQuery)
        }
    }

    @Test
    fun testBlankOrUnknownQuery() {
        val blankRes = CampusDestinationResolver.resolve("", testPois)
        assertTrue(blankRes is CampusResolutionResult.UnknownDestination)

        val unknownRes = CampusDestinationResolver.resolve("random place that does not exist", testPois)
        assertTrue(unknownRes is CampusResolutionResult.UnknownDestination)
    }
}
