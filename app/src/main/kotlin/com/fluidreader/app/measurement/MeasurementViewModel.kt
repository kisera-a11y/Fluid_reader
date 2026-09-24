package com.fluidreader.app.measurement

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fluidreader.app.calibration.CupProfileRepository
import com.fluidreader.app.settings.AppSettings
import com.fluidreader.app.settings.SettingsStore
import com.fluidreader.app.vision.VisionPipeline
import com.fluidreader.app.vision.model.PipelineResult
import com.fluidreader.app.vision.model.PipelineState
import com.fluidreader.core.cup.CupProfile
import com.fluidreader.core.cup.CupProfileRegistry
import com.fluidreader.core.measurement.ConfidenceInputs
import com.fluidreader.core.measurement.ConfidenceModel
import com.fluidreader.core.measurement.TemporalSmoother
import com.fluidreader.core.measurement.VolumeCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Owns the live camera-facing measurement loop: receives [PipelineResult]s from
 * [com.fluidreader.app.camera.FrameAnalyzer], turns the winning frame's normalized liquid
 * height into an ounce estimate via [VolumeCalculator], smooths it over time, scores
 * confidence, and folds all of that (plus manual-override state) into one [MeasurementUiState]
 * for the Compose UI to render.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val cupProfileRepository = CupProfileRepository(application)

    /** Shared across the whole camera session so ML Kit's detector is created only once. */
    val visionPipeline = VisionPipeline()

    private val smoother = TemporalSmoother()
    private val recentRawVolumes = ArrayDeque<Double>()
    private var consecutiveGoodFrames = 0
    private var consecutiveMissedFrames = 0
    private var hasEverMeasured = false
    private var lastDisplayedNumberAtMs = 0L

    @Volatile private var currentSettings: AppSettings = AppSettings()
    @Volatile private var currentCupProfile: CupProfile = CupProfileRegistry.SOLO_16OZ

    private val _uiState = MutableStateFlow(MeasurementUiState())
    val uiState: StateFlow<MeasurementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                currentSettings = settings
                _uiState.update { it.copy(units = settings.units, showOverlay = settings.showOverlay) }
            }
        }
        viewModelScope.launch {
            settingsStore.settings
                .map { it.cupProfileId }
                .distinctUntilChanged()
                .flatMapLatest { id -> cupProfileRepository.observeEffectiveProfile(id) }
                .collect { profile ->
                    currentCupProfile = profile
                    _uiState.update { it.copy(cupProfile = profile) }
                }
        }
    }

    fun currentCupProfileForAnalysis(): CupProfile = currentCupProfile

    /** Called from [com.fluidreader.app.camera.FrameAnalyzer] for every processed frame. */
    fun onPipelineResult(result: PipelineResult) {
        if (_uiState.value.manualOverrideEnabled) {
            _uiState.update { it.copy(frameWidth = result.frameWidth, frameHeight = result.frameHeight) }
            return
        }

        if (result.state != PipelineState.MEASURED || result.heightFraction == null || result.boundary == null) {
            consecutiveMissedFrames++
            // A single flaky frame (glare, a momentary miss on the liquid line, a shaky hand)
            // shouldn't blow away an already-good reading: that's what was making the whole
            // display - including the Log drink button, which hides once we're not READY -
            // flicker in and out and re-stabilize on every brief dropout. Only treat this as a
            // real loss of tracking once misses persist past a short grace window.
            if (hasEverMeasured && consecutiveMissedFrames <= MAX_MISS_STREAK) {
                _uiState.update { it.copy(frameWidth = result.frameWidth, frameHeight = result.frameHeight) }
                return
            }
            consecutiveGoodFrames = 0
            hasEverMeasured = false
            lastDisplayedNumberAtMs = 0L
            smoother.reset()
            recentRawVolumes.clear()
            _uiState.update {
                it.copy(
                    displayState = mapNonMeasuredState(result.state),
                    boundary = result.boundary,
                    liquidY = null,
                    frameWidth = result.frameWidth,
                    frameHeight = result.frameHeight,
                    debugLog = result.debugLog,
                )
            }
            return
        }

        consecutiveMissedFrames = 0
        val isFirstMeasurement = !hasEverMeasured
        hasEverMeasured = true
        consecutiveGoodFrames++
        val rawVolume = VolumeCalculator.volumeOzAtFraction(currentCupProfile, result.heightFraction)
        recentRawVolumes.addLast(rawVolume)
        while (recentRawVolumes.size > STABILITY_WINDOW) recentRawVolumes.removeFirst()
        val smoothedVolume = smoother.addSample(rawVolume)

        val confidenceInputs = ConfidenceInputs(
            cupDetectionScore = result.candidate?.detectorScore ?: 0.0,
            boundaryQuality = ((result.boundary.edgeQuality + result.boundary.shapeMatchScore) / 2.0),
            angleQuality = result.perspective?.angleQuality ?: 0.0,
            liquidLineVisibility = result.liquid?.visibilityScore ?: 0.0,
            temporalStability = temporalStabilityScore(),
        )
        val confidenceResult = ConfidenceModel.evaluate(confidenceInputs, smoothedVolume)
        val range = ConfidenceModel.rangeOz(confidenceResult, smoothedVolume)

        val displayState = when {
            confidenceResult.score < currentSettings.confidenceThreshold -> DisplayState.BELOW_CONFIDENCE_THRESHOLD
            consecutiveGoodFrames < STABILIZE_FRAMES -> DisplayState.STABILIZING
            else -> DisplayState.READY
        }

        // The underlying smoother/confidence math still runs every frame above so it's never
        // stale, but how often that settles into a *new displayed reading* is throttled: without
        // this, the on-screen number could change every ~125ms as fresh frames come in, which
        // didn't leave enough time to react and tap "Log drink" before it moved on. displayState
        // is held along with the number - not just volumeOz - so a transient low-confidence frame
        // can't flip the button-hiding BELOW_CONFIDENCE_THRESHOLD state on and off faster than the
        // number itself changes; otherwise the Log button could still flicker even with a frozen
        // number showing. The very first reading after acquiring a cup is shown immediately rather
        // than held, so there's no artificial delay before a number appears at all.
        val now = SystemClock.elapsedRealtime()
        val publishNewNumber = isFirstMeasurement || now - lastDisplayedNumberAtMs >= DISPLAY_HOLD_MS
        if (publishNewNumber) lastDisplayedNumberAtMs = now

        _uiState.update { current ->
            current.copy(
                displayState = if (publishNewNumber) displayState else current.displayState,
                volumeOz = if (publishNewNumber) smoothedVolume else current.volumeOz,
                rangeLowOz = if (publishNewNumber) range.start else current.rangeLowOz,
                rangeHighOz = if (publishNewNumber) range.endInclusive else current.rangeHighOz,
                confidenceLevel = if (publishNewNumber) confidenceResult.level else current.confidenceLevel,
                confidenceScore = if (publishNewNumber) confidenceResult.score else current.confidenceScore,
                boundary = result.boundary,
                liquidY = result.liquid?.surfaceY,
                frameWidth = result.frameWidth,
                frameHeight = result.frameHeight,
                debugLog = result.debugLog,
            )
        }
    }

    private fun mapNonMeasuredState(state: PipelineState): DisplayState = when (state) {
        PipelineState.NO_CUP -> DisplayState.NO_CUP
        PipelineState.NEEDS_CENTERING -> DisplayState.NEEDS_CENTERING
        PipelineState.NEEDS_SIDE_VIEW -> DisplayState.NEEDS_SIDE_VIEW
        PipelineState.ANGLE_TOO_EXTREME -> DisplayState.ANGLE_TOO_EXTREME
        PipelineState.LIQUID_NOT_VISIBLE -> DisplayState.LIQUID_NOT_VISIBLE
        PipelineState.MEASURED -> DisplayState.READY // unreachable here, kept exhaustive
    }

    /** 0..1: how little the last few raw (pre-smoothing) readings have varied. */
    private fun temporalStabilityScore(): Double {
        if (recentRawVolumes.size < 2) return 0.3
        val mean = recentRawVolumes.average()
        val variance = recentRawVolumes.sumOf { (it - mean) * (it - mean) } / recentRawVolumes.size
        val stdDev = sqrt(variance)
        return (1.0 - (stdDev / STABILITY_SCALE_OZ)).coerceIn(0.0, 1.0)
    }

    // --- Manual override -------------------------------------------------------------------

    fun enterManualOverride() {
        val state = _uiState.value
        val boundary = state.boundary
        val rim = boundary?.rimY ?: (state.frameHeight * 0.25).toInt()
        val bottom = boundary?.bottomY ?: (state.frameHeight * 0.75).toInt()
        val liquid = state.liquidY ?: ((rim + bottom) / 2)
        _uiState.update {
            it.copy(
                manualOverrideEnabled = true,
                manualRimY = rim,
                manualBottomY = bottom,
                manualLiquidY = liquid,
                displayState = DisplayState.MANUAL,
            )
        }
        recalculateManual()
    }

    fun exitManualOverride() {
        consecutiveGoodFrames = 0
        consecutiveMissedFrames = 0
        hasEverMeasured = false
        lastDisplayedNumberAtMs = 0L
        smoother.reset()
        recentRawVolumes.clear()
        _uiState.update { it.copy(manualOverrideEnabled = false, displayState = DisplayState.STABILIZING) }
    }

    fun setManualLine(type: ManualLineType, frameY: Int) {
        _uiState.update { state ->
            when (type) {
                ManualLineType.RIM -> state.copy(manualRimY = frameY)
                ManualLineType.BOTTOM -> state.copy(manualBottomY = frameY)
                ManualLineType.LIQUID -> state.copy(manualLiquidY = frameY)
            }
        }
        recalculateManual()
    }

    private fun recalculateManual() {
        val state = _uiState.value
        val rim = state.manualRimY ?: return
        val bottom = state.manualBottomY ?: return
        val liquid = state.manualLiquidY ?: return
        if (bottom <= rim) return

        val fraction = ((bottom - liquid).toDouble() / (bottom - rim).toDouble()).coerceIn(0.0, 1.0)
        val volume = VolumeCalculator.volumeOzAtFraction(currentCupProfile, fraction)
        // Manual placement is a direct human judgment call, so we show a tight, fixed range
        // around it rather than running it back through the automatic confidence model.
        val halfWidth = 0.3
        _uiState.update {
            it.copy(
                volumeOz = volume,
                rangeLowOz = (volume - halfWidth).coerceAtLeast(0.0),
                rangeHighOz = volume + halfWidth,
                liquidY = liquid,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        visionPipeline.close()
    }

    companion object {
        private const val STABILIZE_FRAMES = 4
        private const val STABILITY_WINDOW = 6
        private const val STABILITY_SCALE_OZ = 1.2
        // How many consecutive non-measured frames to ride out (holding the last good reading
        // steady) before treating tracking as actually lost. At the analyzer's ~8fps cap this is
        // roughly a one-second grace period.
        private const val MAX_MISS_STREAK = 8
        // Minimum time the displayed reading (number, range, confidence) is held before it's
        // allowed to move to a new value, so there's a real window to react and tap "Log drink".
        private const val DISPLAY_HOLD_MS = 2500L
    }
}
