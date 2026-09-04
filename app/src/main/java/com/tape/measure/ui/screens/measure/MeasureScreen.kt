package com.tape.measure.ui.screens.measure

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.ar.core.ArCoreApk
import com.google.ar.core.TrackingState
import com.tape.measure.ui.theme.AmberBottom
import com.tape.measure.ui.theme.AmberTop
import com.tape.measure.ui.theme.ConfidenceHigh
import com.tape.measure.ui.theme.ConfidenceLow
import com.tape.measure.ui.theme.ConfidenceMedium
import com.tape.measure.ui.theme.InkBackground
import com.tape.measure.ui.theme.TapeMeasurement
import io.github.sceneview.ar.ARScene
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext

/**
 * Core AR measurement screen.
 *
 * Layout (all fullscreen, stacked in a Box):
 *   1. [ARScene]          — camera feed + plane-detection grid  (SceneView)
 *   2. [MeasurementCanvas]— amber dots + connecting line + distance pill
 *   3. [TopHud]           — confidence badge (left) + unit chip + nav icons (right)
 *   4. [BottomControls]   — phase-aware hint / Reset / Save buttons
 *
 * Threading:
 *   ARScene calls [MeasureViewModel.onFrame] on the GL render thread.
 *   All other callbacks land on the main thread.
 *   See MeasureViewModel KDoc for the full threading contract.
 */
@Composable
fun MeasureScreen(
    onNavigateToSaved: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onDeviceUnsupported: () -> Unit,
    viewModel: MeasureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Pixel dimensions for the 3-D→2-D projection in the ViewModel.
    var viewportWidth  by remember { mutableStateOf(1) }
    var viewportHeight by remember { mutableStateOf(1) }

    // Pre-compute pixel sizes used in both Canvas and positional offsets.
    val density = LocalDensity.current
    val dotRadiusPx    = with(density) { 10.dp.toPx() }
    val strokeWidthPx  = with(density) { 2.5.dp.toPx() }
    val dotOutlinePx   = with(density) { 2.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                val w = coords.size.width
                val h = coords.size.height
                if (w != viewportWidth || h != viewportHeight) {
                    viewportWidth  = w
                    viewportHeight = h
                    viewModel.setViewport(w, h)
                }
            },
    ) {

        // ── 1. ARScene (camera background + plane grid) ──────────────────────
        ARScene(
            modifier = Modifier.fillMaxSize(),
            planeRenderer = true,
            onSessionCreated = { _ ->
                // Session is ready — nothing to do here for now.
                // Repository injection (item 6) may want to know when the session starts.
            },
            onFrame = { arFrame ->
                // arFrame.camera is com.google.ar.core.Camera (SceneView exposes it directly)
                viewModel.onFrame(arFrame.camera)
            },
            onTap = { hitResult, _ ->
                viewModel.onTap(hitResult)
            },
            onARSessionFailed = { exception ->
                // Session creation failed (device issue, ARCore not installed, etc.).
                // Navigate away so the user sees a clear explanation.
                onDeviceUnsupported()
            },
        )

        // ── 2. Measurement overlay (Canvas) ──────────────────────────────────
        MeasurementCanvas(
            modifier       = Modifier.fillMaxSize(),
            screenPointA   = uiState.screenPointA,
            screenPointB   = uiState.screenPointB,
            dotRadiusPx    = dotRadiusPx,
            dotOutlinePx   = dotOutlinePx,
            strokeWidthPx  = strokeWidthPx,
        )

        // ── Distance pill at midpoint (uses Compose Text, not Canvas drawText) ──
        val pA = uiState.screenPointA
        val pB = uiState.screenPointB
        val dist = uiState.distanceMeters
        if (pA != null && pB != null && dist != null) {
            val midX = ((pA.x + pB.x) / 2f).roundToInt()
            val midY = ((pA.y + pB.y) / 2f).roundToInt() - with(density) { 28.dp.roundToPx() }
            DistancePill(
                text = uiState.unitSystem.format(dist),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(midX, midY) },
            )
        }

        // ── 3. Top HUD ────────────────────────────────────────────────────────
        TopHud(
            modifier             = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            confidence           = uiState.confidence,
            currentUnitLabel     = uiState.unitSystem.label(),
            onUnitTap            = { viewModel.cycleUnit() },
            onNavigateToSaved    = onNavigateToSaved,
            onNavigateToSettings = onNavigateToSettings,
        )

        // ── 4. Bottom controls ────────────────────────────────────────────────
        BottomControls(
            modifier    = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            phase       = uiState.phase,
            isSaving    = uiState.isSaving,
            onReset     = { viewModel.reset() },
            onSave      = { viewModel.saveMeasurement() },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MeasurementCanvas(
    screenPointA:  Offset?,
    screenPointB:  Offset?,
    dotRadiusPx:   Float,
    dotOutlinePx:  Float,
    strokeWidthPx: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (screenPointA == null && screenPointB == null) return@Canvas

        // Connecting line (drawn first so dots sit on top)
        if (screenPointA != null && screenPointB != null) {
            drawLine(
                color       = Color.White.copy(alpha = 0.75f),
                start       = screenPointA,
                end         = screenPointB,
                strokeWidth = strokeWidthPx,
                cap         = StrokeCap.Round,
            )
        }

        // Dot A
        if (screenPointA != null) {
            // Outer ring
            drawCircle(
                color  = Color.White.copy(alpha = 0.6f),
                radius = dotRadiusPx + dotOutlinePx,
                center = screenPointA,
            )
            // Filled core
            drawCircle(
                color  = AmberBottom,
                radius = dotRadiusPx,
                center = screenPointA,
            )
        }

        // Dot B (same style as A — both endpoints look identical)
        if (screenPointB != null) {
            drawCircle(
                color  = Color.White.copy(alpha = 0.6f),
                radius = dotRadiusPx + dotOutlinePx,
                center = screenPointB,
            )
            drawCircle(
                color  = AmberBottom,
                radius = dotRadiusPx,
                center = screenPointB,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Distance pill
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DistancePill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier  = modifier,
        shape     = RoundedCornerShape(8.dp),
        color     = InkBackground.copy(alpha = 0.82f),
        tonalElevation = 0.dp,
    ) {
        Text(
            text      = text,
            style     = TapeMeasurement.Medium,
            color     = AmberTop,
            modifier  = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top HUD: confidence badge + unit chip + nav icons
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopHud(
    confidence:           TrackingConfidence,
    currentUnitLabel:     String,
    onUnitTap:            () -> Unit,
    onNavigateToSaved:    () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        // Confidence badge (left)
        ConfidenceBadge(confidence = confidence)

        // Unit chip + nav icons (right)
        Row(verticalAlignment = Alignment.CenterVertically) {
            UnitChip(label = currentUnitLabel, onClick = onUnitTap)
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onNavigateToSaved) {
                Icon(
                    imageVector        = Icons.Outlined.List,
                    contentDescription = "Saved measurements",
                    tint               = Color.White.copy(alpha = 0.8f),
                )
            }
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector        = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint               = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(
    confidence: TrackingConfidence,
    modifier: Modifier = Modifier,
) {
    val dotColor = when (confidence) {
        TrackingConfidence.HIGH         -> ConfidenceHigh
        TrackingConfidence.LOW          -> ConfidenceMedium
        TrackingConfidence.NOT_TRACKING -> ConfidenceLow
    }

    Surface(
        modifier       = modifier,
        shape          = RoundedCornerShape(20.dp),
        color          = InkBackground.copy(alpha = 0.72f),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Text(
                    text  = confidence.label(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                )
                Text(
                    text  = confidence.accuracyHint(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.85f,
                    ),
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun UnitChip(
    label:   String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier  = modifier,
        shape     = RoundedCornerShape(20.dp),
        color     = InkBackground.copy(alpha = 0.72f),
        onClick   = onClick,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelLarge,
            color    = AmberTop,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom controls: hint text, Reset, Save
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BottomControls(
    phase:    MeasurePhase,
    isSaving: Boolean,
    onReset:  () -> Unit,
    onSave:   () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier              = modifier,
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(12.dp),
    ) {
        // Hint text changes per phase
        val hint = when (phase) {
            MeasurePhase.IDLE    -> "Point at a flat surface, then tap to place the first point."
            MeasurePhase.POINT_A -> "First point set. Tap to place the second point."
            MeasurePhase.BOTH    -> "Tap anywhere to start a new measurement."
        }
        Text(
            text      = hint,
            style     = MaterialTheme.typography.bodySmall,
            color     = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            modifier  = Modifier
                .background(InkBackground.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Reset + Save only appear once both points are placed
        AnimatedVisibility(
            visible = phase == MeasurePhase.BOTH,
            enter   = fadeIn(),
            exit    = fadeOut(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier              = Modifier.fillMaxWidth(),
            ) {
                // Reset button
                Button(
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                    shape    = MaterialTheme.shapes.medium,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                        contentColor   = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier           = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Reset", style = MaterialTheme.typography.labelLarge)
                }

                // Save button (wires to Room in item 6)
                Button(
                    onClick  = onSave,
                    enabled  = !isSaving,
                    modifier = Modifier.weight(1f),
                    shape    = MaterialTheme.shapes.medium,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = AmberBottom,
                        contentColor   = InkBackground,
                    ),
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.Bookmark,
                        contentDescription = null,
                        modifier           = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text  = if (isSaving) "Saving…" else "Save",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
