package dev.navisense

import dev.navisense.cloud.GeminiFlashClient
import org.junit.Assert.*
import org.junit.Test

class GeminiFlashClientTest {

    @Test
    fun testClientWithoutApiKeyReturnsNullCleanly() {
        val client = GeminiFlashClient(null)
        assertFalse(client.hasApiKey())
        val result = client.analyzeSceneForWalking(ByteArray(10))
        assertNull("Should return null cleanly when API key is not configured", result)
    }

    @Test
    fun testSetApiKey() {
        val client = GeminiFlashClient()
        assertFalse(client.hasApiKey())
        client.setApiKey("test-key-12345")
        assertTrue(client.hasApiKey())
    }
}
