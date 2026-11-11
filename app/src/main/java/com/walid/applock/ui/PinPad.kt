package com.walid.applock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val PIN_MIN = 4
const val PIN_MAX = 8

/** Numeric keypad. [onSubmit] gets the digits when the user presses the confirm key. */
@Composable
fun PinPad(enabled: Boolean, onSubmit: (String) -> Unit, modifier: Modifier = Modifier) {
    var pin by remember { mutableStateOf("") }
    val primary = MaterialTheme.colorScheme.primary

    // Digits keep the same positions in right-to-left languages.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.height(24.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
                repeat(maxOf(pin.length, PIN_MIN)) { i ->
                    val dot = Modifier.size(14.dp)
                    Box(
                        if (i < pin.length) dot.background(primary, CircleShape)
                        else dot.border(1.5.dp, primary, CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            val rows = listOf("123", "456", "789", "<0>")
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { key ->
                            FilledTonalButton(
                                onClick = {
                                    when (key) {
                                        '<' -> pin = pin.dropLast(1)
                                        '>' -> { val entered = pin; pin = ""; onSubmit(entered) }
                                        else -> if (pin.length < PIN_MAX) pin += key
                                    }
                                },
                                enabled = enabled && when (key) {
                                    '<' -> pin.isNotEmpty()
                                    '>' -> pin.length >= PIN_MIN
                                    else -> true
                                },
                                shape = CircleShape,
                                modifier = Modifier.size(72.dp),
                            ) {
                                Text(
                                    when (key) { '<' -> "⌫"; '>' -> "✓"; else -> key.toString() },
                                    fontSize = 24.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
