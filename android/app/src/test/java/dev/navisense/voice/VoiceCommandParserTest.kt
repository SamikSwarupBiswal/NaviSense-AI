package dev.navisense.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {

    @Test
    fun testFindWalletVariations() {
        val phrases = listOf(
            "find wallet",
            "find my wallet",
            "where is my wallet",
            "locate wallet",
            "search wallet",
            "wallet",
            "please find the wallet",
            "can you find my purse",
            "hey navisense find wallet"
        )
        for (phrase in phrases) {
            val cmd = VoiceCommandParser.parse(phrase)
            assertTrue("Expected FindTarget('wallet') for '$phrase', but got $cmd", cmd is VoiceCommand.FindTarget)
            assertEquals("wallet", (cmd as VoiceCommand.FindTarget).target)
        }
    }

    @Test
    fun testFindKeysVariations() {
        val phrases = listOf(
            "find keys",
            "find my keys",
            "where are my keys",
            "locate keys",
            "search keys",
            "keys",
            "key",
            "car keys",
            "where are my house keys",
            "hey navi sense find my keys"
        )
        for (phrase in phrases) {
            val cmd = VoiceCommandParser.parse(phrase)
            assertTrue("Expected FindTarget('keys') for '$phrase', but got $cmd", cmd is VoiceCommand.FindTarget)
            assertEquals("keys", (cmd as VoiceCommand.FindTarget).target)
        }
    }

    @Test
    fun testStartWalkingVariations() {
        val phrases = listOf(
            "start walking",
            "walk",
            "start walk",
            "begin walk",
            "start navigation",
            "navigate",
            "let's walk",
            "please start walking",
            "hey navisense start walking"
        )
        for (phrase in phrases) {
            val cmd = VoiceCommandParser.parse(phrase)
            assertEquals("Expected StartWalking for '$phrase'", VoiceCommand.StartWalking, cmd)
        }
    }

    @Test
    fun testStopVariations() {
        val phrases = listOf(
            "stop",
            "stop button",
            "cancel",
            "halt",
            "freeze",
            "emergency stop",
            "please stop",
            "can you stop",
            "hey navisense stop",
            "press stop",
            "press stop button",
            "press the stop button",
            "click stop",
            "click the stop button",
            "tap stop",
            "hit stop",
            "stop the button",
            "stop now",
            "stop walking",
            "stop navigation",
            "stop search",
            "stop please"
        )
        for (phrase in phrases) {
            val cmd = VoiceCommandParser.parse(phrase)
            assertEquals("Expected Stop for '$phrase'", VoiceCommand.Stop, cmd)
        }
    }

    @Test
    fun testBusStopIsNotEmergencyStop() {
        val cmd1 = VoiceCommandParser.parse("take me to bus stop")
        assertTrue("Expected NavigateTo for 'take me to bus stop', got $cmd1", cmd1 is VoiceCommand.NavigateTo)

        val cmd2 = VoiceCommandParser.parse("directions to bus stop")
        assertTrue("Expected NavigateTo for 'directions to bus stop', got $cmd2", cmd2 is VoiceCommand.NavigateTo)

        val cmd3 = VoiceCommandParser.parse("bus stop")
        assertTrue("Expected non-Stop for 'bus stop', got $cmd3", cmd3 !is VoiceCommand.Stop)
    }

    @Test
    fun testConfirmArrivalVariations() {
        val phrases = listOf(
            "confirm arrival",
            "arrived",
            "i am here",
            "i'm here"
        )
        for (phrase in phrases) {
            val cmd = VoiceCommandParser.parse(phrase)
            assertEquals("Expected ConfirmArrival for '$phrase'", VoiceCommand.ConfirmArrival, cmd)
        }
    }

    @Test
    fun testHelpAndStatus() {
        assertEquals(VoiceCommand.Help, VoiceCommandParser.parse("help"))
        assertEquals(VoiceCommand.Help, VoiceCommandParser.parse("what can i say"))
        assertEquals(VoiceCommand.Help, VoiceCommandParser.parse("commands"))
        assertEquals(VoiceCommand.Help, VoiceCommandParser.parse("open the app"))
        assertEquals(VoiceCommand.Help, VoiceCommandParser.parse("open app"))
        assertEquals(VoiceCommand.AppStatus, VoiceCommandParser.parse("status"))
        assertEquals(VoiceCommand.AppStatus, VoiceCommandParser.parse("sensor status"))
    }

    @Test
    fun testNavigateToVariations() {
        val testCases = mapOf(
            "take me to central library" to "Central Library",
            "navigate to cafeteria" to "Cafeteria",
            "directions to the park" to "The Park",
            "walk to train station" to "Train Station",
            "go to building b" to "Building B"
        )
        for ((phrase, expectedDest) in testCases) {
            val cmd = VoiceCommandParser.parse(phrase)
            assertTrue("Expected NavigateTo for '$phrase', got $cmd", cmd is VoiceCommand.NavigateTo)
            assertEquals(expectedDest, (cmd as VoiceCommand.NavigateTo).destination)
        }
    }

    @Test
    fun testMapModeAndDestinationCommands() {
        // Map mode
        assertEquals(VoiceCommand.OpenMapMode, VoiceCommandParser.parse("open map mode"))
        assertEquals(VoiceCommand.OpenMapMode, VoiceCommandParser.parse("map mode"))
        assertEquals(VoiceCommand.OpenMapMode, VoiceCommandParser.parse("show map"))
        assertEquals(VoiceCommand.OpenMapMode, VoiceCommandParser.parse("campus map"))

        // AB1
        val ab1Cmd = VoiceCommandParser.parse("take me to ab1 vit chennai")
        assertTrue(ab1Cmd is VoiceCommand.NavigateTo)
        assertTrue((ab1Cmd as VoiceCommand.NavigateTo).destination.contains("AB1"))

        // Ambrosia
        val ambrosiaCmd = VoiceCommandParser.parse("take me to ambrosia")
        assertTrue(ambrosiaCmd is VoiceCommand.NavigateTo)
        assertTrue((ambrosiaCmd as VoiceCommand.NavigateTo).destination.contains("Ambrosia"))

        // Central Library
        val libCmd = VoiceCommandParser.parse("directions to central library")
        assertTrue(libCmd is VoiceCommand.NavigateTo)
        assertTrue((libCmd as VoiceCommand.NavigateTo).destination.contains("Library"))

        // Search Nearby
        assertEquals(VoiceCommand.StartSearch, VoiceCommandParser.parse("start search"))
        assertEquals(VoiceCommand.StartSearch, VoiceCommandParser.parse("search nearby"))
    }

    @Test
    fun testBlankAndUnknown() {
        assertTrue(VoiceCommandParser.parse(null) is VoiceCommand.Unknown)
        assertTrue(VoiceCommandParser.parse("") is VoiceCommand.Unknown)
        assertTrue(VoiceCommandParser.parse("   ") is VoiceCommand.Unknown)
        assertTrue(VoiceCommandParser.parse("sing a song") is VoiceCommand.Unknown)
    }
}
