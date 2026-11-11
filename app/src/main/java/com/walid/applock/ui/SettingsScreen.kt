package com.walid.applock.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.walid.applock.R
import com.walid.applock.auth.BiometricSupport
import com.walid.applock.auth.Method
import com.walid.applock.auth.MethodStatus
import com.walid.applock.data.CredentialType
import com.walid.applock.data.LockRepository

@Composable
fun SettingsScreen(
    repo: LockRepository,
    credentialType: CredentialType,
    onChangeCredential: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var order by remember { mutableStateOf(repo.methodOrder) }
    var enabled by remember { mutableStateOf(order.associateWith(repo::isEnabled)) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.priority_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.priority_body), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))

        order.forEachIndexed { index, method ->
            val status = BiometricSupport.status(context, method)
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Icon(if (method == Method.FACE) Icons.Default.Face else Icons.Default.Lock, null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(if (method == Method.FACE) R.string.method_face else R.string.method_fingerprint))
                        Text(
                            stringResource(
                                when (status) {
                                    MethodStatus.AVAILABLE ->
                                        if (method == Method.FACE) R.string.status_face_ok else R.string.status_ok
                                    MethodStatus.NOT_ENROLLED -> R.string.status_not_enrolled
                                    MethodStatus.NOT_SUPPORTED ->
                                        if (method == Method.FACE) R.string.status_face_unsupported else R.string.status_unsupported
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status == MethodStatus.AVAILABLE) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                    IconButton(
                        onClick = {
                            order = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                            repo.methodOrder = order
                        },
                        enabled = index > 0,
                    ) { Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.move_up)) }
                    IconButton(
                        onClick = {
                            order = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                            repo.methodOrder = order
                        },
                        enabled = index < order.lastIndex,
                    ) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.move_down)) }
                    Switch(
                        checked = enabled[method] == true,
                        onCheckedChange = {
                            repo.setEnabled(method, it)
                            enabled = enabled + (method to it)
                        },
                    )
                }
            }
        }
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${order.size + 1}", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (credentialType == CredentialType.PIN) R.string.method_pin else R.string.method_pattern))
                    Text(stringResource(R.string.always_last), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { openBiometricSettings(context) }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.open_biometric_settings))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onChangeCredential, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.change_credential))
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.face_note), style = MaterialTheme.typography.bodySmall)
    }
}

private fun openBiometricSettings(context: Context) {
    val candidates = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(Intent(Settings.ACTION_BIOMETRIC_ENROLL))
        add(Intent(Settings.ACTION_SECURITY_SETTINGS))
        add(Intent(Settings.ACTION_SETTINGS))
    }
    for (intent in candidates) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
            // try the next, more general screen
        }
    }
}
