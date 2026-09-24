package com.fluidreader.app.ui.camera

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.fluidreader.app.measurement.ManualLineType
import com.fluidreader.app.measurement.MeasurementUiState
import com.fluidreader.app.ui.theme.BadRed
import com.fluidreader.app.ui.theme.GoodGreen
import com.fluidreader.app.ui.theme.ManualBase
import com.fluidreader.app.ui.theme.ManualLiquid
import com.fluidreader.app.ui.theme.ManualRim
import com.fluidreader.app.ui.theme.WarnAmber
import com.fluidreader.app.vision.model.CupBoundary
import com.fluidreader.core.measurement.ConfidenceLevel
import kotlin.math.abs

/**
 * Draws the cup outline + liquid-level line over the camera preview, following the detected
 * (or manually-adjusted) geometry as the camera moves. In manual mode, the three horizontal
 * lines (rim / bottom / liquid) become drag handles.
 *
 * The auto-detected outline is drawn as a straight-sided trapezoid (top-left/top-right/
 * bottom-right/bottom-left, from [CupBoundary]'s row-averaged corner positions) rather than a
 * per-row polyline traced through every individually-detected wall pixel - a real cup's sides
 * are straight, so this both looks more like an actual cup and is far less visually noisy than
 * plotting every row's small left/right jitter. On top of that, the four corners (and the
 * liquid line) are smoothly animated toward each new frame's detection instead of snapping
 * straight to it, which damps single-frame detection noise into a steady, non-jittery outline.
 */
@Composable
fun MeasurementOverlay(
    state: MeasurementUiState,
    modifier: Modifier = Modifier,
    // Screen-space Y (same pixel space as this Canvas) that manual-adjust lines must stay above -
    // typically the top edge of the bottom panel, minus a small margin. Framed close to the cup,
    // a line's position can otherwise land entirely behind that opaque panel with no way to touch
    // it to drag it back into view. Float.MAX_VALUE means "not yet known / no limit".
    manualDragCeilingPx: Float = Float.MAX_VALUE,
    onManualDrag: (ManualLineType, Int) -> Unit = { _, _ -> },
) {
    var draggingLine by remember { mutableStateOf<ManualLineType?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Whenever manual mode is (re-)entered, or the safe ceiling/canvas size becomes known, pull
    // any line that's at or past it back up to the highest reachable point - this is what
    // actually keeps a close-framed default position from starting out already unreachable.
    LaunchedEffect(state.manualOverrideEnabled, manualDragCeilingPx, canvasSize, state.frameWidth, state.frameHeight) {
        if (!state.manualOverrideEnabled) return@LaunchedEffect
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return@LaunchedEffect
        if (state.frameWidth <= 0 || state.frameHeight <= 0) return@LaunchedEffect
        if (manualDragCeilingPx == Float.MAX_VALUE) return@LaunchedEffect
        val mapper = CoordinateMapper(state.frameWidth, state.frameHeight, canvasSize.width.toFloat(), canvasSize.height.toFloat())
        val maxFrameY = mapper.screenYToFrameY(manualDragCeilingPx)
        state.manualRimY?.let { if (it > maxFrameY) onManualDrag(ManualLineType.RIM, maxFrameY) }
        state.manualBottomY?.let { if (it > maxFrameY) onManualDrag(ManualLineType.BOTTOM, maxFrameY) }
        state.manualLiquidY?.let { if (it > maxFrameY) onManualDrag(ManualLineType.LIQUID, maxFrameY) }
    }

    // Holds the last real (non-null) boundary/liquid-row so the animation target freezes
    // (rather than retargeting toward zero) during the brief frames where detection drops out,
    // and so re-detection resumes smoothly from nearby instead of snapping in from the origin.
    var lastBoundary by remember { mutableStateOf<CupBoundary?>(null) }
    if (state.boundary != null) lastBoundary = state.boundary
    var lastLiquidY by remember { mutableStateOf<Int?>(null) }
    if (state.liquidY != null) lastLiquidY = state.liquidY
    val effectiveBoundary = lastBoundary

    val smoothingSpec = tween<Float>(220)
    val rimY by animateFloatAsState(effectiveBoundary?.rimY?.toFloat() ?: 0f, smoothingSpec, label = "rimY")
    val bottomY by animateFloatAsState(effectiveBoundary?.bottomY?.toFloat() ?: 0f, smoothingSpec, label = "bottomY")
    val topLeftX by animateFloatAsState(effectiveBoundary?.topLeftX?.toFloat() ?: 0f, smoothingSpec, label = "topLeftX")
    val topRightX by animateFloatAsState(effectiveBoundary?.topRightX?.toFloat() ?: 0f, smoothingSpec, label = "topRightX")
    val bottomLeftX by animateFloatAsState(effectiveBoundary?.bottomLeftX?.toFloat() ?: 0f, smoothingSpec, label = "bottomLeftX")
    val bottomRightX by animateFloatAsState(effectiveBoundary?.bottomRightX?.toFloat() ?: 0f, smoothingSpec, label = "bottomRightX")
    val liquidY by animateFloatAsState(lastLiquidY?.toFloat() ?: 0f, smoothingSpec, label = "liquidY")

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(state.manualOverrideEnabled, state.frameWidth, state.frameHeight) {
                if (!state.manualOverrideEnabled) return@pointerInput
                val mapper = CoordinateMapper(state.frameWidth, state.frameHeight, size.width.toFloat(), size.height.toFloat())
                detectDragGestures(
                    onDragStart = { start ->
                        draggingLine = nearestManualLine(state, mapper, start)
                    },
                    onDragEnd = { draggingLine = null },
                    onDragCancel = { draggingLine = null },
                ) { change, _ ->
                    change.consume()
                    val line = draggingLine ?: return@detectDragGestures
                    // Clamped so a drag can't push a line back down past the safe ceiling either -
                    // otherwise it would just re-create the same "hidden behind the panel, can't
                    // grab it" problem on the next attempt to adjust it.
                    val clampedY = change.position.y.coerceAtMost(manualDragCeilingPx)
                    val frameY = mapper.screenYToFrameY(clampedY)
                    onManualDrag(line, frameY)
                }
            },
    ) {
        if (state.frameWidth <= 0 || state.frameHeight <= 0) return@Canvas
        val mapper = CoordinateMapper(state.frameWidth, state.frameHeight, size.width, size.height)

        val boundary = state.boundary
        val outlineColor = when (state.confidenceLevel) {
            ConfidenceLevel.HIGH -> GoodGreen
            ConfidenceLevel.MEDIUM -> WarnAmber
            ConfidenceLevel.LOW -> BadRed
        }

        if (state.manualOverrideEnabled) {
            val rim = state.manualRimY
            val bottom = state.manualBottomY
            val liquid = state.manualLiquidY
            val leftX = boundary?.let { (it.leftAt(rim ?: it.rimY) + it.leftAt(bottom ?: it.bottomY)) / 2 }
                ?: (state.frameWidth * 0.3).toInt()
            val rightX = boundary?.let { (it.rightAt(rim ?: it.rimY) + it.rightAt(bottom ?: it.bottomY)) / 2 }
                ?: (state.frameWidth * 0.7).toInt()

            rim?.let { drawGuideLine(mapper, leftX, rightX, it, ManualRim, "TOP") }
            bottom?.let { drawGuideLine(mapper, leftX, rightX, it, ManualBase, "BOTTOM") }
            liquid?.let { drawGuideLine(mapper, leftX, rightX, it, ManualLiquid, "FILL") }
        } else if (boundary != null) {
            val topLeft = mapper.map(topLeftX, rimY)
            val topRight = mapper.map(topRightX, rimY)
            val bottomRight = mapper.map(bottomRightX, bottomY)
            val bottomLeft = mapper.map(bottomLeftX, bottomY)

            val path = Path().apply {
                moveTo(topLeft.x, topLeft.y)
                lineTo(topRight.x, topRight.y)
                lineTo(bottomRight.x, bottomRight.y)
                lineTo(bottomLeft.x, bottomLeft.y)
                close()
            }
            drawPath(path, color = outlineColor, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))

            if (state.liquidY != null) {
                val heightSpan = (bottomY - rimY).coerceAtLeast(1f)
                val liquidFraction = ((liquidY - rimY) / heightSpan).coerceIn(0f, 1f)
                val liquidLeftX = topLeftX + (bottomLeftX - topLeftX) * liquidFraction
                val liquidRightX = topRightX + (bottomRightX - topRightX) * liquidFraction
                val left = mapper.map(liquidLeftX, liquidY)
                val right = mapper.map(liquidRightX, liquidY)
                drawLine(
                    color = GoodGreen,
                    start = Offset(left.x, left.y),
                    end = Offset(right.x, right.y),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun DrawScope.drawGuideLine(
    mapper: CoordinateMapper,
    leftX: Int,
    rightX: Int,
    y: Int,
    color: Color,
    label: String,
) {
    val left = mapper.map(leftX, y)
    val right = mapper.map(rightX, y)
    drawLine(
        color = color,
        start = Offset(left.x, left.y),
        end = Offset(right.x, right.y),
        strokeWidth = 8f,
        cap = StrokeCap.Round,
    )
    // Drag-handle dots at both ends make the interactive line more discoverable.
    drawCircle(color = color, radius = 14f, center = Offset(left.x, left.y))
    drawCircle(color = color, radius = 14f, center = Offset(right.x, right.y))

    // Name the line right where it ends, so which of the three (top/bottom/fill) you're
    // looking at - and which one you're about to drag - never has to be guessed from color
    // alone. A dark outline behind the text keeps it legible over any background.
    val paint = labelPaint(color.toArgb())
    val textWidth = paint.measureText(label)
    val labelX = (right.x + 20f).coerceAtMost(size.width - textWidth - 8f)
    val labelY = right.y + paint.textSize / 3f
    val outlinePaint = labelPaint(android.graphics.Color.BLACK).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    drawContext.canvas.nativeCanvas.apply {
        drawText(label, labelX, labelY, outlinePaint)
        drawText(label, labelX, labelY, paint)
    }
}

private fun labelPaint(argb: Int) = Paint().apply {
    color = argb
    textSize = 40f
    isAntiAlias = true
    typeface = Typeface.DEFAULT_BOLD
}

private fun nearestManualLine(state: MeasurementUiState, mapper: CoordinateMapper, screenPoint: Offset): ManualLineType? {
    val candidates = listOfNotNull(
        state.manualRimY?.let { ManualLineType.RIM to it },
        state.manualBottomY?.let { ManualLineType.BOTTOM to it },
        state.manualLiquidY?.let { ManualLineType.LIQUID to it },
    )
    if (candidates.isEmpty()) return null
    val touchFrameY = mapper.screenYToFrameY(screenPoint.y)
    return candidates.minByOrNull { abs(it.second - touchFrameY) }?.first
}
