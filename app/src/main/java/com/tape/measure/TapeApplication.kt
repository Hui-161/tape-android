package com.tape.measure

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt's application entry point.
 * `@HiltAndroidApp` triggers Hilt's code generation so the dependency
 * graph exists by the time MainActivity is created.
 *
 * No custom init logic yet — ARCore availability check, locale
 * detection, and DB setup will land here in v1.0+.
 */
@HiltAndroidApp
class TapeApplication : Application()