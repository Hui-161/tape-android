package com.tape.measure.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tape.measure.ui.screens.measure.MeasureScreen
import com.tape.measure.ui.screens.permission.PermissionScreen
import com.tape.measure.ui.screens.permission.hasCameraPermission
import com.tape.measure.ui.screens.saved.SavedListScreen
import com.tape.measure.ui.screens.settings.SettingsScreen
import com.tape.measure.ui.screens.unsupported.UnsupportedScreen
import com.tape.measure.ui.screens.welcome.WelcomeScreen

/**
 * Root navigation host.
 *
 * Flow:
 *  Welcome → Permission → Measure  (first launch)
 *                       ↘ Unsupported  (device can't run ARCore)
 *  Measure                          (every later launch: permission already granted)
 *  Measure → Saved
 *  Measure / Saved → Settings
 *
 * Onboarding is removed from the back stack once it is done, so the system back button
 * leaves the app from Measure instead of resurfacing Welcome or Permission.
 */
@Composable
fun TapeNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val startDestination: Route = remember {
        if (context.hasCameraPermission()) Route.Measure else Route.Welcome
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {

        composable<Route.Welcome> {
            WelcomeScreen(
                onContinue = { navController.navigate(Route.Permission) { launchSingleTop = true } },
            )
        }

        composable<Route.Permission> {
            PermissionScreen(
                onPermissionGranted = {
                    navController.navigate(Route.Measure) {
                        popUpTo<Route.Welcome> { inclusive = true }
                    }
                },
                onDeviceUnsupported = {
                    navController.navigate(Route.Unsupported) {
                        popUpTo<Route.Permission> { inclusive = true }
                    }
                },
            )
        }

        composable<Route.Unsupported> {
            UnsupportedScreen(
                // Unsupported can be the only entry when Measure was the start destination.
                onBack = { if (!navController.popBackStack()) context.findActivity()?.finish() },
            )
        }

        composable<Route.Measure> {
            MeasureScreen(
                onNavigateToSaved = { navController.navigate(Route.Saved) { launchSingleTop = true } },
                onNavigateToSettings = { navController.navigate(Route.Settings) { launchSingleTop = true } },
                onDeviceUnsupported = {
                    // Session creation can fail even if ARCore reported "supported" —
                    // navigate away so the user sees a clear explanation.
                    navController.navigate(Route.Unsupported) {
                        popUpTo<Route.Measure> { inclusive = true }
                    }
                },
            )
        }

        composable<Route.Saved> {
            SavedListScreen(
                onBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Route.Settings) { launchSingleTop = true } },
            )
        }

        composable<Route.Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
