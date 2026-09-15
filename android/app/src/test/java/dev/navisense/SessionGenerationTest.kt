package dev.navisense

import dev.navisense.contracts.AppMode
import dev.navisense.contracts.SessionGeneration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGenerationTest {

    @Test
    fun testInitialGeneration() {
        val session = SessionGeneration(1L)
        assertEquals(1L, session.get())
        assertTrue(session.isValid(1L))
        assertFalse(session.isValid(2L))
    }

    @Test
    fun testAdvanceInvalidatesPreviousGeneration() {
        val session = SessionGeneration(1L)
        val token1 = session.createToken(AppMode.MOBILITY)
        assertEquals(1L, token1.generation)
        assertEquals(AppMode.MOBILITY, token1.mode)

        val newGen = session.advance()
        assertEquals(2L, newGen)
        assertEquals(2L, session.get())

        assertFalse("Previous generation must become invalid", session.isValid(token1.generation))
        assertTrue("New generation must be valid", session.isValid(newGen))
    }

    @Test
    fun testMultipleAdvances() {
        val session = SessionGeneration(10L)
        session.advance()
        session.advance()
        val finalGen = session.advance()
        assertEquals(13L, finalGen)
        assertTrue(session.isValid(13L))
        assertFalse(session.isValid(12L))
    }
}
