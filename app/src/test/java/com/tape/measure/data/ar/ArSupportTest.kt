package com.tape.measure.data.ar

import com.google.ar.core.ArCoreApk.Availability
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ArSupportTest {

    /** Returns the given answers in order and repeats the last one. */
    private class ScriptedCheck(vararg answers: Availability) : () -> Availability {
        private val answers = answers.toList()
        var calls = 0
            private set

        override fun invoke(): Availability = answers[minOf(calls++, answers.lastIndex)]
    }

    @Test
    fun installed_isSupportedWithoutPolling() = runTest {
        val check = ScriptedCheck(Availability.SUPPORTED_INSTALLED)
        assertEquals(ArSupport.SUPPORTED, awaitArSupport(checkAvailability = check))
        assertEquals(1, check.calls)
    }

    @Test
    fun checking_isPolledUntilDefinite() = runTest {
        // The case that sent supported devices to the "unsupported" screen on first launch.
        val check = ScriptedCheck(
            Availability.UNKNOWN_CHECKING,
            Availability.UNKNOWN_CHECKING,
            Availability.SUPPORTED_NOT_INSTALLED,
        )
        assertEquals(ArSupport.SUPPORTED, awaitArSupport(checkAvailability = check))
        assertEquals(3, check.calls)
    }

    @Test
    fun outdatedArCore_countsAsSupported() = runTest {
        assertEquals(
            ArSupport.SUPPORTED,
            awaitArSupport(checkAvailability = ScriptedCheck(Availability.SUPPORTED_APK_TOO_OLD)),
        )
    }

    @Test
    fun incapableDevice_isUnsupported() = runTest {
        assertEquals(
            ArSupport.UNSUPPORTED,
            awaitArSupport(checkAvailability = ScriptedCheck(Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE)),
        )
    }

    @Test
    fun checkingPastTimeout_isUnknownNotUnsupported() = runTest {
        val check = ScriptedCheck(Availability.UNKNOWN_CHECKING)
        assertEquals(
            ArSupport.UNKNOWN,
            awaitArSupport(pollIntervalMs = 200, timeoutMs = 1_000, checkAvailability = check),
        )
        assertEquals(6, check.calls)
    }

    @Test
    fun errorsAndTimeouts_areUnknownNotUnsupported() = runTest {
        assertEquals(ArSupport.UNKNOWN, awaitArSupport(checkAvailability = ScriptedCheck(Availability.UNKNOWN_ERROR)))
        assertEquals(ArSupport.UNKNOWN, awaitArSupport(checkAvailability = ScriptedCheck(Availability.UNKNOWN_TIMED_OUT)))
        assertEquals(ArSupport.UNKNOWN, awaitArSupport(checkAvailability = { error("ARCore not reachable") }))
    }
}
