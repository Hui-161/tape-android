package com.tape.measure.ui.screens.measure

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.ar.core.ArCoreApk
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.tape.measure.R
import com.tape.measure.data.ar.ArFrameReader
import com.tape.measure.data.ar.ArSupport
import com.tape.measure.data.ar.awaitArSupport
import com.tape.measure.data.ar.toArError
import com.tape.measure.domain.measure.AccuracyLevel
import com.tape.measure.domain.measure.ArError
import com.tape.measure.domain.measure.ScreenPoint
import com.tape.measure.domain.measure.ScreenSegment
import com.tape.measure.domain.model.UnitSystem
import com.tape.measure.ui.theme.AmberBottom
import com.tape.measure.ui.theme.AmberTop
import com.tape.measure.ui.theme.ConfidenceHigh
import com.tape.measure.ui.theme.ConfidenceLow
import com.tape.measure.ui.theme.ConfidenceMedium
import com.tape.measure.ui.theme.InkBackground
import com.tape.measure.ui.theme.TapeMeasurement
import io.github.sceneview.ar.ARScene
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Core AR measurement screen: a live tape measure with a crosshair in the centre.
 *
 * Layout (all fullscreen, stacked in a Box):
 *   1. [ARScene]   — camera feed + detected planes (SceneView)
 *   2. [MeasureHud] — crosshair, measuring line, distance label, accuracy badge, controls
 *
 * Points are added with the + button at the crosshair, not by tapping the camera view: the
 * finger does not cover the target, and SceneView consumes touches on its view anyway.
 */
@Composable
fun MeasureScreen(
    onNavigateToSaved: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onDeviceUnsupported: () -> Unit,
    viewModel: MeasureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val view = LocalView.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val overlayState = viewModel.overlay.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.measure_saved_snackbar)

    // On a fresh install ARCore may still be asking its remote service. Starting the session
    // before the answer is definite makes SceneView report the device as incompatible.
    var arReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val support = awaitArSupport { ArCoreApk.getInstance().checkAvailability(context) }
        if (support == ArSupport.UNSUPPORTED) onDeviceUnsupported() else arReady = true
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                MeasureEvent.POINT_ADDED -> view.confirmHaptic()
                MeasureEvent.NO_SURFACE -> view.rejectHaptic()
                // showSnackbar suspends until dismissed; don't hold back haptics meanwhile.
                MeasureEvent.SAVED -> launch { snackbarHostState.showSnackbar(savedMessage) }
            }
        }
    }

    // Measuring takes a while; don't let the display time out.
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val frameReader = remember { ArFrameReader() }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewportSize = it },
    ) {
        if (arReady) {
            key(uiState.sessionAttempt) {
                ARScene(
                    modifier = Modifier.fillMaxSize(),
                    planeRenderer = true,
                    sessionConfiguration = { session, config -> ArFrameReader.configure(session, config) },
                    onSessionCreated = { session ->
                        frameReader.onSessionCreated(session)
                        viewModel.onSessionCreated()
                    },
                    onSessionUpdated = { session, frame ->
                        val size = viewportSize
                        if (size.width > 0 && size.height > 0) {
                            viewModel.onFrame(frameReader.read(session, frame, size.width, size.height))
                        }
                    },
                    onSessionFailed = { exception ->
                        if (exception is UnavailableDeviceNotCompatibleException) {
                            onDeviceUnsupported()
                        } else {
                            viewModel.onArError(exception.toArError())
                        }
                    },
                )
                DisposableEffect(Unit) {
                    onDispose { viewModel.onArSceneDisposed() }
                }
            }
        }

        MeasureHud(
            uiState = uiState,
            overlay = { overlayState.value },
            snackbarHostState = snackbarHostState,
            onUnitTap = viewModel::cycleUnit,
            onNavigateToSaved = onNavigateToSaved,
            onNavigateToSettings = onNavigateToSettings,
            onRetry = viewModel::retrySession,
            onUndo = viewModel::undo,
            onAddPoint = viewModel::addPoint,
            onSave = viewModel::saveMeasurement,
        )
    }
}

/**
 * Everything drawn on top of the camera feed. Kept apart from [ARScene] so it can be
 * previewed and tested without ARCore.
 */
@Composable
internal fun MeasureHud(
    uiState: MeasureUiState,
    overlay: () -> OverlayState,
    snackbarHostState: SnackbarHostState,
    onUnitTap: () -> Unit,
    onNavigateToSaved: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onRetry: () -> Unit,
    onUndo: () -> Unit,
    onAddPoint: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Recompose the badge when the accuracy level changes, not on every frame.
    val accuracyLevel by remember(overlay) { derivedStateOf { overlay().accuracy?.level } }

    Box(modifier = modifier.fillMaxSize()) {
        // Keeps the status bar and the HUD readable over bright camera images.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(140.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))),
        )

        MeasurementOverlay(overlay = overlay, modifier = Modifier.fillMaxSize())

        DistanceLabel(overlay = overlay, unitSystem = uiState.unitSystem)

        TopHud(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            badge = badgeFor(uiState.isTracking, accuracyLevel),
            unitSystem = uiState.unitSystem,
            onUnitTap = onUnitTap,
            onNavigateToSaved = onNavigateToSaved,
            onNavigateToSettings = onNavigateToSettings,
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp),
        )

        uiState.arError?.let { error ->
            ArErrorCard(
                error = error,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (uiState.arError == null) {
            BottomControls(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                guidance = uiState.guidance,
                phase = uiState.phase,
                canAddPoint = uiState.canAddPoint,
                isSaving = uiState.isSaving,
                isSaved = uiState.isSaved,
                onUndo = onUndo,
                onAddPoint = onAddPoint,
                onSave = onSave,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas overlay: measuring line, points, crosshair
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MeasurementOverlay(
    overlay: () -> OverlayState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dash = remember(density) {
        with(density) { PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())) }
    }
    Canvas(modifier = modifier) {
        // Reading the state here only invalidates drawing, not composition.
        val state = overlay()

        state.segment?.let { segment ->
            val start = segment.start.toOffset()
            val end = segment.end.toOffset()
            // Dark halo keeps the line visible on bright surfaces.
            drawLine(
                color = Color.Black.copy(alpha = 0.35f),
                start = start,
                end = end,
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = start,
                end = end,
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (state.isLive) dash else null,
            )
        }

        state.pointA?.let { drawMeasurePoint(it.toOffset()) }
        state.pointB?.let { drawMeasurePoint(it.toOffset()) }
        drawCrosshair(state.crosshair, dash)
    }
}

private val PointRadius = 9.dp

private fun DrawScope.drawMeasurePoint(position: Offset) {
    drawCircle(color = Color.White.copy(alpha = 0.7f), radius = PointRadius.toPx(), center = position)
    drawCircle(color = AmberBottom, radius = PointRadius.toPx() - 2.dp.toPx(), center = position)
}

private val CrosshairRadius = 20.dp

private fun DrawScope.drawCrosshair(state: CrosshairState, dash: PathEffect) {
    if (state == CrosshairState.HIDDEN) return
    val onSurface = state == CrosshairState.ON_SURFACE
    val color = when (state) {
        CrosshairState.ON_SURFACE -> AmberTop
        CrosshairState.TOO_FAR -> ConfidenceLow
        else -> Color.White.copy(alpha = 0.6f)
    }
    val radius = CrosshairRadius.toPx()
    drawCircle(
        color = Color.Black.copy(alpha = 0.3f),
        radius = radius,
        center = center,
        style = Stroke(width = 4.dp.toPx()),
    )
    drawCircle(
        color = color,
        radius = radius,
        center = center,
        style = Stroke(width = 2.dp.toPx(), pathEffect = if (onSurface) null else dash),
    )
    drawCircle(color = color, radius = 2.5.dp.toPx(), center = center)
}

private fun ScreenPoint.toOffset() = Offset(x, y)

// ─────────────────────────────────────────────────────────────────────────────
// Distance label at the midpoint of the line
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DistanceLabel(
    overlay: () -> OverlayState,
    unitSystem: UnitSystem,
    modifier: Modifier = Modifier,
) {
    // Small leaf that recomposes at frame rate while a line is shown.
    val state = overlay()
    val segment = state.segment ?: return
    val distance = state.distanceMeters ?: return
    val density = LocalDensity.current
    val gapPx = with(density) { 14.dp.roundToPx() }
    val crosshairKeepOutPx = with(density) { (CrosshairRadius + 12.dp).roundToPx() }
    val pointKeepOutPx = with(density) { PointRadius.roundToPx() }

    Surface(
        modifier = modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(placeable.width, placeable.height) {
                placeable.place(
                    distanceLabelPosition(
                        segment = segment,
                        labelSize = IntSize(placeable.width, placeable.height),
                        screenSize = IntSize(constraints.maxWidth, constraints.maxHeight),
                        gap = gapPx,
                        crosshairKeepOut = crosshairKeepOutPx,
                        pointKeepOut = pointKeepOutPx,
                    ),
                )
            }
        },
        shape = RoundedCornerShape(8.dp),
        color = InkBackground.copy(alpha = 0.82f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = unitSystem.format(distance),
                style = TapeMeasurement.Medium,
                color = AmberTop,
            )
            state.accuracy?.let { accuracy ->
                Text(
                    text = stringResource(
                        R.string.measure_uncertainty_estimated,
                        unitSystem.formatUncertainty(accuracy.uncertaintyMeters),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/**
 * Where to put the distance label: beside the measuring line, like a dimension on a technical
 * drawing. It tries the midpoint on the side above the line (right of a near-vertical line),
 * then the other side, then a quarter along the line, and takes the first position that leaves
 * the crosshair and both end points visible. The label always stays fully on screen.
 */
internal fun distanceLabelPosition(
    segment: ScreenSegment,
    labelSize: IntSize,
    screenSize: IntSize,
    gap: Int,
    crosshairKeepOut: Int,
    pointKeepOut: Int,
): IntOffset {
    val start = segment.start
    val end = segment.end
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy)
    // Unit normal of the line; a line of zero length gets its label above.
    var normalX = if (length < 1f) 0f else -dy / length
    var normalY = if (length < 1f) -1f else dx / length
    val preferOtherSide = if (abs(normalY) > 0.3f) normalY > 0f else normalX < 0f
    if (preferOtherSide) {
        normalX = -normalX
        normalY = -normalY
    }

    val centre = IntOffset(screenSize.width / 2, screenSize.height / 2)
    val keepOut = listOf(
        keepOutBox(centre.x.toFloat(), centre.y.toFloat(), crosshairKeepOut),
        keepOutBox(start.x, start.y, pointKeepOut),
        keepOutBox(end.x, end.y, pointKeepOut),
    )
    fun onScreen(x: Float, y: Float) = IntOffset(
        x.roundToInt().coerceIn(0, (screenSize.width - labelSize.width).coerceAtLeast(0)),
        y.roundToInt().coerceIn(0, (screenSize.height - labelSize.height).coerceAtLeast(0)),
    )
    fun IntOffset.isClear() = keepOut.none { IntRect(this, labelSize).overlaps(it) }

    val halfWidth = labelSize.width / 2f
    val halfHeight = labelSize.height / 2f
    for (along in floatArrayOf(0.5f, 0.25f, 0.75f)) {
        val anchorX = start.x + dx * along
        val anchorY = start.y + dy * along
        for (side in floatArrayOf(1f, -1f)) {
            val nx = normalX * side
            val ny = normalY * side
            // Keeps the whole label `gap` away from the line, whatever the line's angle.
            val reach = gap + abs(nx) * halfWidth + abs(ny) * halfHeight
            val candidate = onScreen(anchorX + nx * reach - halfWidth, anchorY + ny * reach - halfHeight)
            if (candidate.isClear()) return candidate
        }
    }
    // A very short line right at the crosshair: below the crosshair.
    return onScreen(centre.x - halfWidth, centre.y + crosshairKeepOut + gap.toFloat())
}

private fun keepOutBox(x: Float, y: Float, radius: Int): IntRect {
    val cx = x.roundToInt()
    val cy = y.roundToInt()
    return IntRect(cx - radius, cy - radius, cx + radius, cy + radius)
}

// ─────────────────────────────────────────────────────────────────────────────
// Top HUD: accuracy badge + unit chip + navigation
// ─────────────────────────────────────────────────────────────────────────────

private enum class Badge(@StringRes val label: Int, val color: Color) {
    NO_TRACKING(R.string.measure_accuracy_no_tracking, ConfidenceLow),
    TRACKING(R.string.measure_accuracy_tracking, ConfidenceHigh),
    GOOD(R.string.measure_accuracy_good, ConfidenceHigh),
    FAIR(R.string.measure_accuracy_fair, ConfidenceMedium),
    POOR(R.string.measure_accuracy_poor, ConfidenceLow),
}

private fun badgeFor(isTracking: Boolean, level: AccuracyLevel?) = when {
    !isTracking -> Badge.NO_TRACKING
    level == null -> Badge.TRACKING
    level == AccuracyLevel.GOOD -> Badge.GOOD
    level == AccuracyLevel.FAIR -> Badge.FAIR
    else -> Badge.POOR
}

@Composable
private fun TopHud(
    badge: Badge,
    unitSystem: UnitSystem,
    onUnitTap: () -> Unit,
    onNavigateToSaved: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccuracyBadge(badge = badge)

        Row(verticalAlignment = Alignment.CenterVertically) {
            UnitChip(unitSystem = unitSystem, onClick = onUnitTap)
            Spacer(Modifier.width(4.dp))
            // Same dark backing as the badge and chip: bare white icons vanish on bright scenes.
            val hudIconColors = IconButtonDefaults.iconButtonColors(
                containerColor = InkBackground.copy(alpha = 0.72f),
                contentColor = Color.White,
            )
            IconButton(onClick = onNavigateToSaved, colors = hudIconColors) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.List,
                    contentDescription = stringResource(R.string.measure_open_saved),
                )
            }
            IconButton(onClick = onNavigateToSettings, colors = hudIconColors) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.measure_open_settings),
                )
            }
        }
    }
}

@Composable
private fun AccuracyBadge(badge: Badge, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = InkBackground.copy(alpha = 0.72f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(badge.color, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(badge.label),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun UnitChip(
    unitSystem: UnitSystem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.measure_change_unit, unitSystem.label())
    Surface(
        modifier = modifier.semantics { contentDescription = description },
        shape = RoundedCornerShape(20.dp),
        color = InkBackground.copy(alpha = 0.72f),
        onClick = onClick,
    ) {
        Text(
            text = unitSystem.label(),
            style = MaterialTheme.typography.labelLarge,
            color = AmberTop,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom controls: hint, Undo / Add point / Save
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BottomControls(
    guidance: Guidance,
    phase: MeasurePhase,
    canAddPoint: Boolean,
    isSaving: Boolean,
    isSaved: Boolean,
    onUndo: () -> Unit,
    onAddPoint: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(guidance.textRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(InkBackground.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(
                icon = Icons.AutoMirrored.Outlined.Undo,
                contentDescription = stringResource(R.string.measure_undo),
                enabled = phase != MeasurePhase.IDLE,
                onClick = onUndo,
            )
            AddPointButton(enabled = canAddPoint, onClick = onAddPoint)
            RoundIconButton(
                icon = if (isSaved) Icons.Filled.BookmarkAdded else Icons.Outlined.BookmarkAdd,
                contentDescription = stringResource(
                    if (isSaved) R.string.measure_saved_state else R.string.measure_save,
                ),
                enabled = phase == MeasurePhase.BOTH && !isSaving && !isSaved,
                onClick = onSave,
            )
        }
    }
}

@Composable
private fun AddPointButton(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) AmberBottom else AmberBottom.copy(alpha = 0.35f),
        contentColor = InkBackground,
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.measure_add_point),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = InkBackground.copy(alpha = 0.72f),
        contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
        modifier = Modifier.size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}

@StringRes
private fun Guidance.textRes(): Int = when (this) {
    Guidance.STARTING -> R.string.measure_guidance_starting
    Guidance.TOO_DARK -> R.string.measure_guidance_too_dark
    Guidance.TOO_FAST -> R.string.measure_guidance_too_fast
    Guidance.LOW_TEXTURE -> R.string.measure_guidance_low_texture
    Guidance.CAMERA_UNAVAILABLE -> R.string.measure_guidance_camera_unavailable
    Guidance.AR_STOPPED -> R.string.measure_guidance_ar_stopped
    Guidance.FIND_SURFACE -> R.string.measure_guidance_find_surface
    Guidance.TOO_FAR -> R.string.measure_guidance_too_far
    Guidance.AIM_AT_SURFACE -> R.string.measure_guidance_aim_surface
    Guidance.PLACE_START -> R.string.measure_guidance_place_start
    Guidance.PLACE_END -> R.string.measure_guidance_place_end
    Guidance.MEASURED -> R.string.measure_guidance_measured
}

// ─────────────────────────────────────────────────────────────────────────────
// Session errors
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ArErrorCard(
    error: ArError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(24.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRetry,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberBottom,
                    contentColor = InkBackground,
                ),
            ) {
                Text(stringResource(R.string.ar_error_retry), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@StringRes
private fun ArError.messageRes(): Int = when (this) {
    ArError.INSTALL_DECLINED -> R.string.ar_error_install_declined
    ArError.ARCORE_UPDATE_REQUIRED -> R.string.ar_error_arcore_update_required
    ArError.APP_UPDATE_REQUIRED -> R.string.ar_error_app_update_required
    ArError.CAMERA_UNAVAILABLE -> R.string.ar_error_camera_unavailable
    ArError.CAMERA_PERMISSION -> R.string.ar_error_camera_permission
    ArError.OTHER -> R.string.ar_error_other
}

private fun View.confirmHaptic() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.VIRTUAL_KEY,
    )
}

private fun View.rejectHaptic() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS,
    )
}
