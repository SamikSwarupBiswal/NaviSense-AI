package dev.navisense

import dev.navisense.navigation.maps.GoogleRoutesService
import dev.navisense.navigation.maps.models.GeoPoint
import dev.navisense.navigation.maps.models.ManeuverType
import dev.navisense.voice.VoiceDestinationRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleRoutesParsingTest {

    private val routesService = GoogleRoutesService()

    @Test
    fun testManeuverMapping() {
        assertEquals(ManeuverType.RIGHT, ManeuverType.fromGoogleManeuver("turn-right"))
        assertEquals(ManeuverType.RIGHT, ManeuverType.fromGoogleManeuver("turn_right"))
        assertEquals(ManeuverType.SLIGHT_RIGHT, ManeuverType.fromGoogleManeuver("turn-slight-right"))
        assertEquals(ManeuverType.LEFT, ManeuverType.fromGoogleManeuver("turn-left"))
        assertEquals(ManeuverType.SHARP_LEFT, ManeuverType.fromGoogleManeuver("turn-sharp-left"))
        assertEquals(ManeuverType.U_TURN, ManeuverType.fromGoogleManeuver("uturn-left"))
        assertEquals(ManeuverType.STRAIGHT, ManeuverType.fromGoogleManeuver("straight"))
        assertEquals(ManeuverType.ARRIVE, ManeuverType.fromGoogleManeuver("arrive"))
        assertEquals(ManeuverType.STRAIGHT, ManeuverType.fromGoogleManeuver(null))
    }

    @Test
    fun testPolylineDecoding() {
        // Known polyline encoded string for Google headquarters path
        val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        val points = routesService.decodePolyline(encoded)

        assertEquals(3, points.size)
        // First point should approximate (38.5, -120.2)
        assertEquals(38.5, points[0].latitude, 0.01)
        assertEquals(-120.2, points[0].longitude, 0.01)
    }

    @Test
    fun testMockWalkingRouteGeneration() {
        val origin = GeoPoint(12.8406, 80.1534)
        val destination = GeoPoint(12.8442, 80.1549)
        val route = routesService.createMockWalkingRoute(origin, destination, "Central Library")

        assertEquals("Central Library", route.destinationName)
        assertEquals(3, route.steps.size)
        assertEquals(120, route.totalDistanceMeters)
        assertEquals(ManeuverType.DEPART, route.steps[0].maneuver)
        assertEquals(ManeuverType.RIGHT, route.steps[1].maneuver)
        assertEquals(ManeuverType.LEFT, route.steps[2].maneuver)
        assertEquals(origin, route.steps[0].startLocation)
        assertEquals(destination, route.steps[2].endLocation)
    }

    @Test
    fun testSpokenDestinationParsing() {
        assertEquals("Central Library", VoiceDestinationRecognizer.parseDestinationPhrase("take me to central library"))
        assertEquals("Main Cafeteria", VoiceDestinationRecognizer.parseDestinationPhrase("i want to go to main cafeteria"))
        assertEquals("Subway", VoiceDestinationRecognizer.parseDestinationPhrase("navigate to subway"))
        assertEquals("Academic Block 1", VoiceDestinationRecognizer.parseDestinationPhrase("walk to academic block 1"))
        assertEquals("Bus Stop", VoiceDestinationRecognizer.parseDestinationPhrase("directions to bus stop"))
        assertEquals("Auditorium", VoiceDestinationRecognizer.parseDestinationPhrase("auditorium"))
    }
}
