package com.walid.applock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.walid.applock.data.CredentialStore
import com.walid.applock.data.LockRepository
import com.walid.applock.service.AppLockAccessibilityService
import com.walid.applock.session.UnlockSession
import com.walid.applock.ui.AppLockTheme
import com.walid.applock.ui.SettingsScreen
import com.walid.applock.ui.SetupScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Screen { APPS, SETTINGS, CHANGE_CREDENTIAL }

class MainActivity : ComponentActivity() {

    private lateinit var repo: LockRepository
    private lateinit var creds: CredentialStore

    private var hasCredential by mutableStateOf(false)
    private var unlocked by mutableStateOf(false)
    private var serviceEnabled by mutableStateOf(false)
    private var lockLaunched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repo = LockRepository(this)
        creds = CredentialStore(this)
        hasCredential = creds.isSet

        setContent {
            AppLockTheme {
                when {
                    !hasCredential -> Surface(Modifier.fillMaxSize()) {
                        SetupScreen(
                            onDone = { type, secret ->
                                creds.save(type, secret)
                                UnlockSession.unlock(packageName)
                                unlocked = true
                                hasCredential = true
                            },
                            onCancel = null,
                            modifier = Modifier.padding(top = 48.dp),
                        )
                    }
                    !unlocked -> Surface(Modifier.fillMaxSize()) {}
                    else -> MainContent(repo, creds, serviceEnabled)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled = AppLockAccessibilityService.isEnabled(this)
        if (!creds.isSet) return
        // The app's own screens are protected by the same lock screen.
        if (UnlockSession.isUnlocked(packageName)) {
            unlocked = true
            lockLaunched = false
        } else {
            unlocked = false
            lockLaunched = true
            startActivity(LockActivity.intent(this, packageName))
        }
    }

    override fun onStop() {
        if (!lockLaunched && !isChangingConfigurations) {
            UnlockSession.lock(packageName)
            unlocked = false
        }
        super.onStop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(repo: LockRepository, creds: CredentialStore, serviceEnabled: Boolean) {
    var screen by remember { mutableStateOf(Screen.APPS) }
    BackHandler(enabled = screen != Screen.APPS) {
        screen = if (screen == Screen.CHANGE_CREDENTIAL) Screen.SETTINGS else Screen.APPS
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (screen == Screen.APPS) R.string.app_name else R.string.settings))
                },
                navigationIcon = {
                    if (screen != Screen.APPS) {
                        IconButton(onClick = {
                            screen = if (screen == Screen.CHANGE_CREDENTIAL) Screen.SETTINGS else Screen.APPS
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    }
                },
                actions = {
                    if (screen == Screen.APPS) {
                        IconButton(onClick = { screen = Screen.SETTINGS }) {
                            Icon(Icons.Default.Settings, stringResource(R.string.settings))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (screen) {
            Screen.APPS -> AppListScreen(repo, serviceEnabled, modifier)
            Screen.SETTINGS -> SettingsScreen(repo, creds.type, { screen = Screen.CHANGE_CREDENTIAL }, modifier)
            Screen.CHANGE_CREDENTIAL -> SetupScreen(
                onDone = { type, secret ->
                    creds.save(type, secret)
                    screen = Screen.SETTINGS
                },
                onCancel = { screen = Screen.SETTINGS },
                modifier = modifier,
            )
        }
    }
}

private data class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap?)

private suspend fun loadApps(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(launcher, 0)
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != context.packageName }
        .map { info ->
            AppEntry(
                pkg = info.activityInfo.packageName,
                label = info.loadLabel(pm).toString(),
                icon = try {
                    info.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
                } catch (e: Exception) {
                    null
                },
            )
        }
        .sortedBy { it.label.lowercase() }
}

@Composable
private fun AppListScreen(repo: LockRepository, serviceEnabled: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val apps by produceState<List<AppEntry>?>(null) { value = loadApps(context) }
    var locked by remember { mutableStateOf(repo.lockedPackages) }
    var query by remember { mutableStateOf("") }

    Column(modifier.fillMaxSize()) {
        if (!serviceEnabled) {
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.service_off_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.service_off_body), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text(stringResource(R.string.open_accessibility)) }
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val shown = list
            .filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
            .sortedByDescending { it.pkg in locked }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.pkg }) { app ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (app.icon != null) {
                        Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                    } else {
                        Spacer(Modifier.size(40.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(app.label, Modifier.weight(1f))
                    Switch(
                        checked = app.pkg in locked,
                        onCheckedChange = {
                            repo.setLocked(app.pkg, it)
                            locked = repo.lockedPackages
                        },
                    )
                }
            }
        }
    }
}
