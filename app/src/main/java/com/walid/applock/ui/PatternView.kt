package com.walid.applock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

const val PATTERN_MIN = 4

/** Index (0..8, row-major) of the dot under [o] in a 3×3 grid of side [side], or null. */
fun patternHit(o: Offset, side: Float): Int? {
    val cell = side / 3f
    if (o.x < 0f || o.y < 0f || o.x >= side || o.y >= side) return null
    val col = (o.x / cell).toInt()
    val row = (o.y / cell).toInt()
    val center = Offset(cell * col + cell / 2, cell * row + cell / 2)
    return if ((o - center).getDistance() < cell * 0.38f) row * 3 + col else null
}

/**
 * 3×3 pattern grid. [onPattern] gets the visited dots as a digit string, or an
 * empty string if fewer than [PATTERN_MIN] dots were joined.
 */
@Composable
fun PatternView(enabled: Boolean, onPattern: (String) -> Unit, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf(listOf<Int>()) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    val callback by rememberUpdatedState(onPattern)
    val active = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.outline

    Canvas(
        modifier
            .size(280.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val side = size.width.toFloat()
                    fun visit(o: Offset) {
                        finger = o
                        patternHit(o, side)?.let { if (it !in selected) selected = selected + it }
                    }
                    val down = awaitFirstDown()
                    selected = emptyList()
                    visit(down.position)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        visit(change.position)
                        change.consume()
                    }
                    val result = selected
                    selected = emptyList()
                    finger = null
                    if (result.isNotEmpty()) {
                        callback(if (result.size >= PATTERN_MIN) result.joinToString("") else "")
                    }
                }
            },
    ) {
        val cell = size.width / 3f
        fun center(i: Int) = Offset(cell * (i % 3) + cell / 2, cell * (i / 3) + cell / 2)
        val stroke = cell * 0.06f
        selected.zipWithNext { a, b -> drawLine(active, center(a), center(b), stroke, StrokeCap.Round) }
        val last = selected.lastOrNull()
        val f = finger
        if (last != null && f != null) drawLine(active, center(last), f, stroke, StrokeCap.Round)
        for (i in 0 until 9) {
            val on = i in selected
            drawCircle(if (on) active else idle, if (on) cell * 0.13f else cell * 0.08f, center(i))
        }
    }
}
