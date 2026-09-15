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
            "cancel",
            "halt",
            "freeze",
            "emergency stop",
            "please stop",
            "hey navisense stop"
        )
        for (phrase in phrases) {
            val cmd = VoiceCommandParser.parse(phrase)
            assertEquals("Expected Stop for '$phrase'", VoiceCommand.Stop, cmd)
        }
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
    fun testBlankAndUnknown() {
        assertTrue(VoiceCommandParser.parse(null) is VoiceCommand.Unknown)
        assertTrue(VoiceCommandParser.parse("") is VoiceCommand.Unknown)
        assertTrue(VoiceCommandParser.parse("   ") is VoiceCommand.Unknown)
        assertTrue(VoiceCommandParser.parse("sing a song") is VoiceCommand.Unknown)
    }
}
