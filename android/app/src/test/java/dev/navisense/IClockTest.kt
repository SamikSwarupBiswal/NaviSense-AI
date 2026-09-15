package dev.navisense

import dev.navisense.contracts.SystemMonotonicClock
import org.junit.Assert.assertTrue
import org.junit.Test

class IClockTest {

    @Test
    fun testMonotonicProgression() {
        val clock = SystemMonotonicClock()
        val t1 = clock.nowMonotonicMs()
        val n1 = clock.nowMonotonicNanos()

        Thread.sleep(10)

        val t2 = clock.nowMonotonicMs()
        val n2 = clock.nowMonotonicNanos()

        assertTrue("Monotonic milliseconds must advance", t2 >= t1)
        assertTrue("Monotonic nanoseconds must advance", n2 > n1)
    }
}
