package com.tape.measure.ui.screens.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.google.ar.core.ArCoreApk
import com.tape.measure.data.ar.ArSupport
import com.tape.measure.data.ar.awaitArSupport
import com.tape.measure.ui.theme.AmberBottom
import com.tape.measure.ui.theme.AmberTop
import com.tape.measure.ui.theme.InkBackground
import com.tape.measure.ui.theme.InkTextSecondary

/**
 * Gate screen that:
 *  1. Checks ARCore availability — routes to UnsupportedScreen if the device cannot run AR
 *     (no need to ask for camera on an incompatible device).
 *  2. Explains why camera access is needed.
 *  3. Requests the CAMERA permission.
 *  4. On denial shows a rationale + "Open Settings" deep-link, and continues automatically
 *     once the permission was granted there.
 *
 * The screen itself never holds state beyond this flow; everything is a one-shot side-effect.
 */
@Composable
fun PermissionScreen(
    onPermissionGranted: () -> Unit,
    onDeviceUnsupported: () -> Unit,
) {
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }

    // The ARCore check and the permission flow can both leave this screen; only leave once.
    var done by remember { mutableStateOf(false) }
    fun leave(navigate: () -> Unit) {
        if (!done) {
            done = true
            navigate()
        }
    }

    // "Unknown" (e.g. offline) is not "unsupported": the measure screen checks again before it
    // starts the AR session.
    LaunchedEffect(Unit) {
        val support = awaitArSupport { ArCoreApk.getInstance().checkAvailability(context) }
        if (support == ArSupport.UNSUPPORTED) leave(onDeviceUnsupported)
    }

    // Also covers coming back from the system settings with the permission granted.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (context.hasCameraPermission()) leave(onPermissionGranted)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) leave(onPermissionGranted) else permissionDenied = true
    }

    val amberGradient = Brush.horizontalGradient(listOf(AmberTop, AmberBottom))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {

            // Brand mark (reused pattern from WelcomeScreen)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "TAPE",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "│││││",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkTextSecondary,
                )
            }

            // Icon + headline + body copy
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    text = if (permissionDenied) "Camera access required." else "We need your camera.",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (permissionDenied)
                        "Tape can't measure without seeing what your camera sees. Open Settings and enable the Camera permission to continue."
                    else
                        "Tape measures by analyzing depth from your camera feed. Frames are processed entirely on-device — nothing is uploaded.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // CTA button — either "Allow camera" or "Open Settings"
            Column {
                Button(
                    onClick = {
                        if (permissionDenied) {
                            // Permission was permanently denied — open app settings
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberBottom,
                        contentColor = InkBackground,
                    ),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(amberGradient),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (permissionDenied) "Open Settings" else "Allow camera access",
                            style = MaterialTheme.typography.labelLarge,
                            color = InkBackground,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (!permissionDenied) {
                    Text(
                        text = "You can revoke this at any time in Settings → Apps → Tape.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}

internal fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
