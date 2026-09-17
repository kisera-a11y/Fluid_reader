package com.fluidreader.app.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.fluidreader.app.measurement.ManualLineType
import com.fluidreader.app.measurement.MeasurementUiState
import com.fluidreader.app.ui.theme.BadRed
import com.fluidreader.app.ui.theme.CupBlue
import com.fluidreader.app.ui.theme.GoodGreen
import com.fluidreader.app.ui.theme.WarnAmber
import com.fluidreader.core.measurement.ConfidenceLevel
import kotlin.math.abs

/**
 * Draws the cup outline + liquid-level line over the camera preview, following the detected
 * (or manually-adjusted) geometry as the camera moves. In manual mode, the three horizontal
 * lines (rim / bottom / liquid) become drag handles.
 */
@Composable
fun MeasurementOverlay(
    state: MeasurementUiState,
    modifier: Modifier = Modifier,
    onManualDrag: (ManualLineType, Int) -> Unit = { _, _ -> },
) {
    var draggingLine by remember { mutableStateOf<ManualLineType?>(null) }

    Canvas(
        modifier = modifier
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
                    val frameY = mapper.screenYToFrameY(change.position.y)
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

            rim?.let { drawGuideLine(mapper, leftX, rightX, it, CupBlue, "RIM") }
            bottom?.let { drawGuideLine(mapper, leftX, rightX, it, CupBlue, "BASE") }
            liquid?.let { drawGuideLine(mapper, leftX, rightX, it, GoodGreen, "LIQUID") }
        } else if (boundary != null) {
            // Tapered outline.
            val path = androidx.compose.ui.graphics.Path().apply {
                val start = mapper.map(boundary.leftAt(boundary.rimY), boundary.rimY)
                moveTo(start.x, start.y)
                val rows = boundary.leftEdge.size
                var i = 0
                while (i < rows) {
                    val y = boundary.rimY + i
                    val p = mapper.map(boundary.leftAt(y), y)
                    lineTo(p.x, p.y)
                    i += maxOf(1, rows / 20)
                }
                val bp = mapper.map(boundary.leftAt(boundary.bottomY), boundary.bottomY)
                lineTo(bp.x, bp.y)
                val bpr = mapper.map(boundary.rightAt(boundary.bottomY), boundary.bottomY)
                lineTo(bpr.x, bpr.y)
                i = rows - 1
                while (i >= 0) {
                    val y = boundary.rimY + i
                    val p = mapper.map(boundary.rightAt(y), y)
                    lineTo(p.x, p.y)
                    i -= maxOf(1, rows / 20)
                }
                close()
            }
            drawPath(path, color = outlineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))

            state.liquidY?.let { liquidY ->
                val left = mapper.map(boundary.leftAt(liquidY), liquidY)
                val right = mapper.map(boundary.rightAt(liquidY), liquidY)
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGuideLine(
    mapper: CoordinateMapper,
    leftX: Int,
    rightX: Int,
    y: Int,
    color: Color,
    @Suppress("UNUSED_PARAMETER") label: String,
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
