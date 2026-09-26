package com.tape.measure.ui.screens.measure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.tape.measure.data.db.MeasurementDao
import com.tape.measure.data.db.MeasurementEntity
import com.tape.measure.data.prefs.UserPreferencesRepository
import com.tape.measure.data.repository.MeasurementRepository
import com.tape.measure.domain.measure.AccuracyEstimator
import com.tape.measure.domain.measure.ArError
import com.tape.measure.domain.measure.FarTargeting
import com.tape.measure.domain.measure.FrameSample
import com.tape.measure.domain.measure.HitKind
import com.tape.measure.domain.measure.SurfaceHit
import com.tape.measure.domain.measure.TrackedPoint
import com.tape.measure.domain.measure.TrackingProblem
import com.tape.measure.domain.measure.Vec3
import com.tape.measure.domain.measure.identityMatrix
import com.tape.measure.domain.measure.lookAtMatrix
import com.tape.measure.domain.measure.perspectiveMatrix
import com.tape.measure.domain.measure.translationMatrix
import com.tape.measure.domain.model.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sqrt

@OptIn(ExperimentalCoroutinesApi::class)
class MeasureViewModelTest {

    private class FakePoint(var position: Vec3?) : TrackedPoint {
        var released = false
        var lost = false
        override fun currentPosition() = position
        override val isLost get() = lost
        override fun release() {
            released = true
        }
    }

    private class FakeDao : MeasurementDao {
        val items = MutableStateFlow<List<MeasurementEntity>>(emptyList())
        override fun getAll(): Flow<List<MeasurementEntity>> = items
        override suspend fun insert(m: MeasurementEntity) {
            items.value = listOf(m) + items.value
        }
        override suspend fun delete(m: MeasurementEntity) {
            items.value = items.value - m
        }
        override suspend fun update(m: MeasurementEntity) {
            items.value = items.value.map { if (it.id == m.id) m else it }
        }
    }

    private class FakePreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private lateinit var dao: FakeDao
    private lateinit var prefs: UserPreferencesRepository
    private lateinit var viewModel: MeasureViewModel
    private val createdPoints = mutableListOf<FakePoint>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dao = FakeDao()
        prefs = UserPreferencesRepository(FakePreferencesStore())
        viewModel = MeasureViewModel(MeasurementRepository(dao), prefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun hitAt(
        x: Float,
        y: Float = 0f,
        z: Float = -1f,
        cameraDistance: Float = 1f,
        kind: HitKind = HitKind.PLANE_HORIZONTAL,
    ) = SurfaceHit(Vec3(x, y, z), kind, cameraDistance) {
        FakePoint(Vec3(x, y, z)).also { createdPoints += it }
    }

    private fun frame(
        hit: SurfaceHit?,
        tracking: Boolean = true,
        problem: TrackingProblem = TrackingProblem.NONE,
        surfacesDetected: Boolean = true,
        tooFarMeters: Float? = null,
        view: FloatArray = identityMatrix(),
        groundHeight: Float? = null,
    ) = FrameSample(
        isTracking = tracking,
        problem = problem,
        view = view,
        projection = perspectiveMatrix(),
        width = 1000,
        height = 1000,
        surfacesDetected = surfacesDetected,
        crosshairHit = if (tracking) hit else null,
        crosshairTooFarMeters = tooFarMeters,
        groundHeight = groundHeight,
        createPointAt = { position -> FakePoint(position).also { createdPoints += it } },
    )

    /** Phone held 1.5 m above the ground at y = 0, the crosshair on [target]. */
    private fun aimingAt(target: Vec3) = lookAtMatrix(Vec3(0f, 1.5f, 0f), target)

    /**
     * A frame with the crosshair aimed at [target], beyond ARCore's range. If [surfaceSeen], ARCore
     * sees a surface there, at the right distance but too imprecise to measure on.
     */
    private fun farFrame(target: Vec3, groundHeight: Float? = 0f, surfaceSeen: Boolean = true) = frame(
        hit = null,
        tooFarMeters = if (surfaceSeen) target.distanceTo(Vec3(0f, 1.5f, 0f)) else null,
        view = aimingAt(target),
        groundHeight = groundHeight,
    )

    private fun rayAt(target: Vec3) = FarTargeting.centerRay(aimingAt(target), perspectiveMatrix())

    private fun assertVec(expected: Vec3, actual: Vec3?, tolerance: Float = 1e-3f) {
        assertNotNull(actual)
        assertEquals("x", expected.x, actual!!.x, tolerance)
        assertEquals("y", expected.y, actual.y, tolerance)
        assertEquals("z", expected.z, actual.z, tolerance)
    }

    /** Aims the crosshair at [hit] and presses +. */
    private fun placeAt(hit: SurfaceHit) {
        viewModel.onFrame(frame(hit))
        viewModel.addPoint()
    }

    private val ui get() = viewModel.uiState.value
    private val overlay get() = viewModel.overlay.value

    @Test
    fun initially_idleAndNothingToPlace() {
        assertEquals(MeasurePhase.IDLE, ui.phase)
        assertEquals(Guidance.STARTING, ui.guidance)
        assertFalse(ui.canAddPoint)
        assertEquals(CrosshairState.HIDDEN, overlay.crosshair)
    }

    @Test
    fun crosshairOnSurface_allowsPlacingTheStartPoint() {
        viewModel.onFrame(frame(hitAt(0f)))
        assertTrue(ui.canAddPoint)
        assertEquals(Guidance.PLACE_START, ui.guidance)
        assertEquals(CrosshairState.ON_SURFACE, overlay.crosshair)
    }

    @Test
    fun noSurface_guidesTheUser() {
        viewModel.onFrame(frame(hit = null, surfacesDetected = false))
        assertEquals(Guidance.FIND_SURFACE, ui.guidance)
        assertEquals(CrosshairState.SEARCHING, overlay.crosshair)

        viewModel.onFrame(frame(hit = null, surfacesDetected = true))
        assertEquals(Guidance.AIM_AT_SURFACE, ui.guidance)
        assertFalse(ui.canAddPoint)
    }

    @Test
    fun farTarget_canBePlacedButSuggestsMovingCloser() {
        viewModel.onFrame(frame(hitAt(0f, cameraDistance = 4f)))

        assertEquals(Guidance.CLOSER_IS_MORE_ACCURATE, ui.guidance)
        assertTrue(ui.canAddPoint)
    }

    @Test
    fun impreciseResult_suggestsMeasuringAgainFromCloser() {
        // Both points placed from 4.5 m for a 0.5 m distance: about ±9.5 cm, i.e. 19 %.
        placeAt(hitAt(0f, cameraDistance = 4.5f))
        placeAt(hitAt(0.5f, cameraDistance = 4.5f))
        viewModel.onFrame(frame(hitAt(0f)))

        assertEquals(Guidance.MEASURED_IMPRECISE, ui.guidance)
    }

    @Test
    fun surfaceTooFarWithoutKnownGround_cannotBeMeasuredAndSaysSo() {
        viewModel.onFrame(farFrame(Vec3(0f, 0f, -10f), groundHeight = null))

        assertEquals(Guidance.NEED_GROUND, ui.guidance)
        assertEquals(CrosshairState.TOO_FAR, overlay.crosshair)
        assertFalse(ui.canAddPoint)

        viewModel.addPoint()
        assertEquals(MeasurePhase.IDLE, ui.phase)
    }

    @Test
    fun crosshairMovingOutOfRange_dropsTheLiveLine() {
        placeAt(hitAt(0f, y = 0f, z = -3f, cameraDistance = 3.4f))
        viewModel.onFrame(farFrame(Vec3(0f, 0f, -10f), groundHeight = null))

        assertEquals(Guidance.NEED_GROUND, ui.guidance)
        assertNull(overlay.segment)
        assertNull(overlay.distanceMeters)
        assertNotNull("the start point stays visible", overlay.pointA)
    }

    // ── Beyond ARCore's range ────────────────────────────────────────────────

    @Test
    fun distantGround_isEstimatedAndCanBePlaced() {
        viewModel.onFrame(farFrame(Vec3(0f, 0f, -10f)))

        assertEquals(Guidance.FAR_GROUND, ui.guidance)
        assertEquals(CrosshairState.FAR_ESTIMATE, overlay.crosshair)
        assertTrue(ui.canAddPoint)

        viewModel.addPoint()
        assertEquals(MeasurePhase.POINT_A, ui.phase)
        assertVec(Vec3(0f, 0f, -10f), createdPoints.single().position)
    }

    @Test
    fun distanceToADistantGroundPoint_includesItsEstimatedError() {
        val start = hitAt(0f, y = 0f, z = -1f, cameraDistance = 1.8f)
        viewModel.onFrame(frame(start, view = aimingAt(Vec3(0f, 0f, -1f)), groundHeight = 0f))
        viewModel.addPoint()

        viewModel.onFrame(farFrame(Vec3(0f, 0f, -10f)))

        assertTrue(overlay.isLive)
        assertEquals(9f, overlay.distanceMeters!!, 1e-3f)
        val end = FarTargeting.groundTarget(rayAt(Vec3(0f, 0f, -10f)), 0f)!!.uncertaintyMeters
        val expected = sqrt(start.uncertaintyMeters * start.uncertaintyMeters + end * end)
        assertEquals(expected, overlay.accuracy!!.uncertaintyMeters, 1e-5f)

        viewModel.addPoint()
        viewModel.onFrame(farFrame(Vec3(0f, 0f, -20f)))
        assertEquals(MeasurePhase.BOTH, ui.phase)
        assertEquals(9f, overlay.distanceMeters!!, 1e-3f)
        assertEquals(expected, overlay.accuracy!!.uncertaintyMeters, 1e-5f)
    }

    @Test
    fun heightAboveADistantStartPoint_isEstimatedOnAVerticalPlane() {
        viewModel.onFrame(farFrame(Vec3(0f, 0f, -15f)))
        viewModel.addPoint()

        viewModel.onFrame(farFrame(Vec3(0f, 2f, -15f)))

        assertEquals(Guidance.FAR_VERTICAL, ui.guidance)
        assertEquals(CrosshairState.FAR_ESTIMATE, overlay.crosshair)
        assertEquals(2f, overlay.distanceMeters!!, 1e-3f)
        // The start point's own error (±69 cm along the view) is part of the height's, not added.
        val base = FarTargeting.groundTarget(rayAt(Vec3(0f, 0f, -15f)), 0f)!!
        val top = FarTargeting.verticalTarget(rayAt(Vec3(0f, 2f, -15f)), base.position, base.uncertaintyMeters)!!
        assertEquals(top.uncertaintyMeters, overlay.accuracy!!.uncertaintyMeters, 1e-5f)
        assertTrue(overlay.accuracy!!.uncertaintyMeters < 0.1f)
    }

    @Test
    fun distantTargetAboveTheHorizon_asksForItsBaseFirst() {
        viewModel.onFrame(farFrame(Vec3(0f, 5f, -15f)))

        assertEquals(Guidance.AIM_AT_BASE, ui.guidance)
        assertEquals(CrosshairState.TOO_FAR, overlay.crosshair)
        assertFalse(ui.canAddPoint)
    }

    @Test
    fun wallInFrontOfTheGroundPoint_asksToAimAtItsBase() {
        // Aiming at a wall 10 m away at knee height: the ground there would be 15 m away.
        viewModel.onFrame(farFrame(Vec3(0f, 0.5f, -10f)))

        assertEquals(Guidance.AIM_AT_BASE, ui.guidance)
        assertEquals(CrosshairState.TOO_FAR, overlay.crosshair)
        assertFalse(ui.canAddPoint)

        // Where the wall meets the ground, ARCore and the estimate agree.
        viewModel.onFrame(farFrame(Vec3(0f, 0f, -10f)))
        assertEquals(Guidance.FAR_GROUND, ui.guidance)
    }

    @Test
    fun groundBeyondTheMaximumDistance_isTooFar() {
        viewModel.onFrame(farFrame(Vec3(0f, 0f, -40f), surfaceSeen = false))

        assertEquals(Guidance.TOO_FAR, ui.guidance)
        assertEquals(CrosshairState.TOO_FAR, overlay.crosshair)
        assertFalse(ui.canAddPoint)
    }

    @Test
    fun surfaceInRange_isPreferredToAnEstimate() {
        val hit = hitAt(0.2f, y = 0f, z = -2f, cameraDistance = 2.5f)
        viewModel.onFrame(frame(hit, view = aimingAt(Vec3(0f, 0f, -2f)), groundHeight = 0f))

        assertEquals(CrosshairState.ON_SURFACE, overlay.crosshair)
        assertEquals(Guidance.PLACE_START, ui.guidance)
        viewModel.addPoint()
        assertEquals(Vec3(0.2f, 0f, -2f), createdPoints.single().position)
    }

    @Test
    fun walkingBetweenThePoints_addsTrackingDriftButHandTremorDoesNot() {
        placeAt(hitAt(0f))
        // Walk 10 m in 0.5 m steps, the phone swaying by 2 cm in between.
        for (step in 1..20) {
            viewModel.onFrame(frame(hit = null, view = translationMatrix(0f, 0f, 0.5f * step)))
            viewModel.onFrame(frame(hit = null, view = translationMatrix(0.02f, 0f, 0.5f * step)))
        }
        viewModel.onFrame(frame(hitAt(0f, z = -11f), view = translationMatrix(0f, 0f, 10f)))
        viewModel.addPoint()
        viewModel.onFrame(frame(hit = null, view = translationMatrix(0f, 0f, 10f)))

        val point = AccuracyEstimator.pointUncertainty(HitKind.PLANE_HORIZONTAL, 1f)
        val end = AccuracyEstimator.withDrift(point, walkedMeters = 10f)
        assertEquals(10f, overlay.distanceMeters!!, 1e-4f)
        assertEquals(sqrt(point * point + end * end), overlay.accuracy!!.uncertaintyMeters, 1e-4f)
    }

    @Test
    fun startPoint_drawsLiveLineToCrosshair() {
        placeAt(hitAt(0f))
        viewModel.onFrame(frame(hitAt(0.3f)))

        assertEquals(MeasurePhase.POINT_A, ui.phase)
        assertEquals(Guidance.PLACE_END, ui.guidance)
        assertTrue(overlay.isLive)
        assertEquals(0.3f, overlay.distanceMeters!!, 1e-5f)
        assertNotNull(overlay.pointA)
        assertNull(overlay.pointB)
        assertNotNull(overlay.segment)
    }

    @Test
    fun endPoint_fixesTheDistanceWhateverTheCrosshairDoes() {
        placeAt(hitAt(0f))
        placeAt(hitAt(0.5f))
        viewModel.onFrame(frame(hitAt(2f)))

        assertEquals(MeasurePhase.BOTH, ui.phase)
        assertEquals(Guidance.MEASURED, ui.guidance)
        assertFalse(overlay.isLive)
        assertEquals(0.5f, overlay.distanceMeters!!, 1e-5f)
        assertNotNull(overlay.pointB)
    }

    @Test
    fun accuracy_usesThePlacementDistanceOfEachPoint() {
        placeAt(hitAt(0f, cameraDistance = 1f))
        placeAt(hitAt(1f, cameraDistance = 2f))
        viewModel.onFrame(frame(hitAt(0f)))

        val expected = sqrt(
            AccuracyEstimator.pointUncertainty(HitKind.PLANE_HORIZONTAL, 1f).let { it * it } +
                AccuracyEstimator.pointUncertainty(HitKind.PLANE_HORIZONTAL, 2f).let { it * it },
        )
        assertEquals(expected, overlay.accuracy!!.uncertaintyMeters, 1e-6f)
    }

    @Test
    fun plusAfterCompleteMeasurement_startsANewOne() {
        placeAt(hitAt(0f))
        placeAt(hitAt(0.5f))
        placeAt(hitAt(1f))

        assertEquals(MeasurePhase.POINT_A, ui.phase)
        assertTrue(createdPoints[0].released)
        assertTrue(createdPoints[1].released)
        assertFalse(createdPoints[2].released)
    }

    @Test
    fun undo_removesAndReleasesTheLastPoint() {
        placeAt(hitAt(0f))
        placeAt(hitAt(0.5f))

        viewModel.undo()
        assertEquals(MeasurePhase.POINT_A, ui.phase)
        assertTrue(createdPoints[1].released)
        assertFalse(createdPoints[0].released)

        viewModel.undo()
        assertEquals(MeasurePhase.IDLE, ui.phase)
        assertTrue(createdPoints[0].released)
    }

    @Test
    fun events_reportPlacementAndMissingSurface() = runTest {
        val events = mutableListOf<MeasureEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }

        viewModel.addPoint() // no surface under the crosshair yet
        placeAt(hitAt(0f))

        assertEquals(listOf(MeasureEvent.NO_SURFACE, MeasureEvent.POINT_ADDED), events)
        assertEquals(MeasurePhase.POINT_A, ui.phase)
        job.cancel()
    }

    @Test
    fun trackingLost_hidesTheOverlayAndSaysWhy() {
        placeAt(hitAt(0f))
        viewModel.onFrame(frame(hit = null, tracking = false, problem = TrackingProblem.INSUFFICIENT_LIGHT))

        assertEquals(OverlayState(), overlay)
        assertEquals(Guidance.TOO_DARK, ui.guidance)
        assertFalse(ui.canAddPoint)
        assertEquals("the placed point is kept", MeasurePhase.POINT_A, ui.phase)
    }

    @Test
    fun lostPoint_resetsTheMeasurement() {
        placeAt(hitAt(0f))
        createdPoints[0].lost = true
        viewModel.onFrame(frame(hitAt(0.2f)))

        assertEquals(MeasurePhase.IDLE, ui.phase)
        assertNull(overlay.segment)
    }

    @Test
    fun disposedScene_dropsPointsWithoutTouchingThem() {
        placeAt(hitAt(0f))
        placeAt(hitAt(0.5f))

        viewModel.onArSceneDisposed()

        assertEquals(MeasurePhase.IDLE, ui.phase)
        assertEquals(OverlayState(), overlay)
        assertTrue("anchors of a closed session must not be detached", createdPoints.none { it.released })
    }

    @Test
    fun save_storesOnceAndConfirms() = runTest {
        val events = mutableListOf<MeasureEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        placeAt(hitAt(0f))
        placeAt(hitAt(0.5f))
        viewModel.onFrame(frame(hitAt(0f)))

        viewModel.saveMeasurement()
        viewModel.saveMeasurement()

        assertEquals(1, dao.items.value.size)
        assertEquals(0.5f, dao.items.value.single().distanceMeters, 1e-5f)
        assertTrue(ui.isSaved)
        assertTrue(MeasureEvent.SAVED in events)
        job.cancel()
    }

    @Test
    fun save_isIgnoredUntilBothPointsArePlaced() {
        placeAt(hitAt(0f))
        viewModel.onFrame(frame(hitAt(0.4f)))
        viewModel.saveMeasurement()
        assertTrue(dao.items.value.isEmpty())
    }

    @Test
    fun newMeasurementAfterSave_canBeSavedAgain() {
        placeAt(hitAt(0f))
        placeAt(hitAt(0.5f))
        viewModel.onFrame(frame(hitAt(0f)))
        viewModel.saveMeasurement()

        placeAt(hitAt(1f))
        assertFalse(ui.isSaved)
    }

    @Test
    fun cycleUnit_isStoredAppWide() = runTest {
        prefs.setUnit(UnitSystem.CM)
        assertEquals(UnitSystem.CM, ui.unitSystem)

        viewModel.cycleUnit()

        assertEquals(UnitSystem.M, ui.unitSystem)
        assertEquals(UnitSystem.M, prefs.unitFlow.first())
    }

    @Test
    fun arError_isShownUntilRetry() {
        viewModel.onArError(ArError.CAMERA_UNAVAILABLE)
        assertEquals(ArError.CAMERA_UNAVAILABLE, ui.arError)

        viewModel.retrySession()
        assertNull(ui.arError)
        assertEquals(1, ui.sessionAttempt)
    }
}
