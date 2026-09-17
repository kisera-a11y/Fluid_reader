package com.fluidreader.app.measurement

import android.app.Application
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
            consecutiveGoodFrames = 0
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

        _uiState.update {
            it.copy(
                displayState = displayState,
                volumeOz = smoothedVolume,
                rangeLowOz = range.start,
                rangeHighOz = range.endInclusive,
                confidenceLevel = confidenceResult.level,
                confidenceScore = confidenceResult.score,
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
    }
}
