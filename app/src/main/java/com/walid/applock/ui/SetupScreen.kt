package com.walid.applock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.walid.applock.R
import com.walid.applock.data.CredentialType

/** Choose PIN or pattern, enter it twice, then [onDone]. */
@Composable
fun SetupScreen(onDone: (CredentialType, String) -> Unit, onCancel: (() -> Unit)?, modifier: Modifier = Modifier) {
    var type by remember { mutableStateOf<CredentialType?>(null) }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val chosen = type
        if (chosen == null) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.setup_body), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { type = CredentialType.PIN }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.use_pin))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { type = CredentialType.PATTERN }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.use_pattern))
            }
            if (onCancel != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
            return@Column
        }

        val isPin = chosen == CredentialType.PIN
        Text(
            stringResource(
                when {
                    first != null -> if (isPin) R.string.confirm_pin else R.string.confirm_pattern
                    isPin -> R.string.new_pin
                    else -> R.string.new_pattern
                },
            ),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            error?.let { stringResource(it) } ?: "",
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp).height(48.dp),
        )
        val onEntered: (String) -> Unit = { entered ->
            val previous = first
            when {
                entered.isEmpty() -> error = R.string.pattern_too_short
                previous == null -> { first = entered; error = null }
                previous == entered -> onDone(chosen, entered)
                else -> { first = null; error = R.string.mismatch }
            }
        }
        if (isPin) PinPad(enabled = true, onSubmit = onEntered) else PatternView(enabled = true, onPattern = onEntered)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { type = null; first = null; error = null }) { Text(stringResource(R.string.back)) }
    }
}
