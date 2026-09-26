package com.tape.measure.data.ar

import com.google.ar.core.ArCoreApk
import kotlinx.coroutines.delay

/** ARCore availability reduced to what the navigation needs. */
enum class ArSupport { SUPPORTED, UNSUPPORTED, UNKNOWN }

/**
 * Checks whether ARCore can run on this device.
 *
 * On the first query ARCore often answers `UNKNOWN_CHECKING` while it asks its remote service
 * (typically when "Google Play Services for AR" is not installed yet). Google recommends asking
 * again shortly after, so this polls until the answer is definite or [timeoutMs] has passed.
 *
 * `SUPPORTED_NOT_INSTALLED` and `SUPPORTED_APK_TOO_OLD` count as supported: SceneView asks the
 * user to install or update ARCore when the session starts. An answer that stays unknown (for
 * example offline) is reported as [ArSupport.UNKNOWN] and must not be treated as unsupported.
 */
suspend fun awaitArSupport(
    pollIntervalMs: Long = 200,
    timeoutMs: Long = 5_000,
    checkAvailability: () -> ArCoreApk.Availability,
): ArSupport {
    var availability = runCatching(checkAvailability).getOrNull()
    var waited = 0L
    while (availability?.isTransient == true && waited < timeoutMs) {
        delay(pollIntervalMs)
        waited += pollIntervalMs
        availability = runCatching(checkAvailability).getOrNull()
    }
    return when {
        availability == null -> ArSupport.UNKNOWN
        availability.isSupported -> ArSupport.SUPPORTED
        availability.isUnsupported -> ArSupport.UNSUPPORTED
        else -> ArSupport.UNKNOWN
    }
}
