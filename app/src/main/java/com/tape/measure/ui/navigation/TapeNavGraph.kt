package com.tape.measure.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tape.measure.ui.screens.measure.MeasureScreen
import com.tape.measure.ui.screens.permission.PermissionScreen
import com.tape.measure.ui.screens.saved.SavedListScreen
import com.tape.measure.ui.screens.settings.SettingsScreen
import com.tape.measure.ui.screens.unsupported.UnsupportedScreen
import com.tape.measure.ui.screens.welcome.WelcomeScreen

/**
 * Root navigation host.
 *
 * Flow:
 *  Welcome → Permission → Measure  (happy path)
 *                       ↘ Unsupported  (no ARCore / denied)
 *  Measure → Saved
 *  Measure / Saved → Settings
 *
 * All screen-to-screen transitions are pop-inclusive where appropriate so the
 * system back button doesn't resurface the permission gate after it's done.
 */
@Composable
fun TapeNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Route.Welcome,
        modifier = modifier,
    ) {

        composable<Route.Welcome> {
            WelcomeScreen(
                onContinue = { navController.navigate(Route.Permission) },
            )
        }

        composable<Route.Permission> {
            PermissionScreen(
                onPermissionGranted = {
                    navController.navigate(Route.Measure) {
                        // Remove the permission screen from back-stack so back
                        // from Measure goes to Welcome, not back to Permission.
                        popUpTo<Route.Permission> { inclusive = true }
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
                onBack = { navController.popBackStack() },
            )
        }

        composable<Route.Measure> {
            MeasureScreen(
                onNavigateToSaved = { navController.navigate(Route.Saved) },
                onNavigateToSettings = { navController.navigate(Route.Settings) },
                onDeviceUnsupported = {
                    // Session creation can fail even if ARCore reported "installed" —
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
                onNavigateToSettings = { navController.navigate(Route.Settings) },
            )
        }

        composable<Route.Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
