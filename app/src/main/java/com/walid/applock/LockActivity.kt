package com.walid.applock

import android.content.Context
import android.content.Intent
import android.hardware.fingerprint.FingerprintManager
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.walid.applock.auth.BiometricSupport
import com.walid.applock.auth.Method
import com.walid.applock.auth.UnlockChain
import com.walid.applock.data.CredentialStore
import com.walid.applock.data.CredentialType
import com.walid.applock.data.LockRepository
import com.walid.applock.session.UnlockSession
import com.walid.applock.ui.AppLockTheme
import com.walid.applock.ui.PatternView
import com.walid.applock.ui.PinPad
import kotlinx.coroutines.delay

/**
 * The lock screen. Runs the unlock chain in the order chosen in settings:
 * phone face unlock (system prompt) → fingerprint only → app PIN / pattern.
 */
class LockActivity : FragmentActivity() {

    private lateinit var repo: LockRepository
    private lateinit var creds: CredentialStore
    private lateinit var prompt: BiometricPrompt
    private val handler = Handler(Looper.getMainLooper())

    private var targetPkg = ""
    private var chain: UnlockChain? = null

    /** Bumped whenever a step starts or is cancelled, so stale callbacks are ignored. */
    private var generation = 0
    private var promptGeneration = -1
    private var fingerprintCancel: CancellationSignal? = null
    private var fingerprintFails = 0
    private var resumed = false
    private var stepPending = false

    private var step by mutableStateOf(Method.CREDENTIAL)
    private var message by mutableStateOf("")
    private var appLabel by mutableStateOf("")
    private var hasBiometricSteps by mutableStateOf(false)
    private var lockoutUntil by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        repo = LockRepository(this)
        creds = CredentialStore(this)

        prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (promptGeneration == generation) succeed()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (promptGeneration == generation) advance()
                }
            },
        )
        onBackPressedDispatcher.addCallback(this) { goHome() }

        setContent {
            AppLockTheme {
                LockScreen(
                    appLabel = appLabel,
                    step = step,
                    message = message,
                    credentialType = creds.type,
                    lockoutUntil = lockoutUntil,
                    canRetryBiometrics = hasBiometricSteps,
                    onSecret = ::submitSecret,
                    onSkip = ::advance,
                    onRetryBiometrics = ::restartChain,
                )
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (!creds.isSet) { finish(); return }
        targetPkg = intent.getStringExtra(EXTRA_PACKAGE) ?: packageName
        if (isStarted) visibleFor = targetPkg
        appLabel = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(targetPkg, 0)).toString()
        } catch (e: Exception) {
            targetPkg
        }
        restartChain()
    }

    private var isStarted = false

    override fun onStart() {
        super.onStart()
        isStarted = true
        visibleFor = targetPkg
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (stepPending) startStep()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onStop() {
        isStarted = false
        if (visibleFor == targetPkg) visibleFor = null
        if (!isChangingConfigurations) {
            // Do not keep the sensor busy in the background; resume the same step later.
            cancelRunningStep()
            stepPending = true
        }
        super.onStop()
    }

    private fun restartChain() {
        cancelRunningStep()
        message = ""
        lockoutUntil = repo.lockoutUntil
        val c = UnlockChain(repo.enabledOrder()) { BiometricSupport.isAvailable(this, it, repo.forceFace) }
        chain = c
        hasBiometricSteps = c.steps.size > 1
        scheduleStep()
    }

    /** Current step failed, was cancelled or skipped: go to the next one. */
    private fun advance() {
        cancelRunningStep()
        chain?.fail()
        scheduleStep()
    }

    private fun scheduleStep() {
        stepPending = true
        step = chain?.current ?: Method.CREDENTIAL
        // Small delay lets a previous system prompt finish closing first.
        handler.postDelayed({ if (resumed && stepPending) startStep() }, 250)
    }

    private fun cancelRunningStep() {
        generation++
        handler.removeCallbacksAndMessages(null)
        fingerprintCancel?.cancel()
        fingerprintCancel = null
        if (::prompt.isInitialized) prompt.cancelAuthentication()
    }

    private fun startStep() {
        val c = chain ?: return
        stepPending = false
        val gen = ++generation
        step = c.current
        when (c.current) {
            Method.FACE -> showSystemPrompt(gen, BIOMETRIC_WEAK, getString(R.string.prompt_face_title))
            Method.FINGERPRINT -> {
                fingerprintFails = 0
                message = ""
                if (!startFingerprintOnly(gen)) {
                    showSystemPrompt(gen, BIOMETRIC_STRONG, getString(R.string.prompt_fingerprint_title))
                }
            }
            Method.CREDENTIAL -> Unit
        }
    }

    private fun showSystemPrompt(gen: Int, authenticators: Int, title: String) {
        promptGeneration = gen
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(appLabel)
            .setNegativeButtonText(getString(R.string.next_method))
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(false)
            .build()
        try {
            prompt.authenticate(info)
        } catch (e: Exception) {
            advance()
        }
    }

    /** Fingerprint sensor directly, so the phone cannot answer this step with face. */
    @Suppress("DEPRECATION")
    private fun startFingerprintOnly(gen: Int): Boolean {
        return try {
            val fm = getSystemService(FingerprintManager::class.java) ?: return false
            if (!fm.isHardwareDetected || !fm.hasEnrolledFingerprints()) return false
            val signal = CancellationSignal()
            fm.authenticate(
                null, signal, 0,
                object : FingerprintManager.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult?) {
                        if (gen == generation) succeed()
                    }

                    override fun onAuthenticationFailed() {
                        if (gen != generation) return
                        if (++fingerprintFails >= MAX_FINGERPRINT_FAILS) advance()
                        else message = getString(R.string.fingerprint_not_recognized)
                    }

                    override fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence?) {
                        if (gen == generation && !helpString.isNullOrBlank()) message = helpString.toString()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        if (gen == generation) advance()
                    }
                },
                null,
            )
            fingerprintCancel = signal
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun submitSecret(secret: String) {
        val now = System.currentTimeMillis()
        if (now < repo.lockoutUntil) return
        if (secret.isEmpty()) { message = getString(R.string.pattern_too_short); return }
        if (creds.verify(secret)) {
            repo.failedAttempts = 0
            repo.lockoutUntil = 0L
            succeed()
        } else {
            val fails = repo.failedAttempts + 1
            repo.failedAttempts = fails
            val wait = LockRepository.lockoutMillis(fails)
            if (wait > 0) repo.lockoutUntil = now + wait
            lockoutUntil = repo.lockoutUntil
            message = getString(
                if (creds.type == CredentialType.PIN) R.string.wrong_pin else R.string.wrong_pattern,
            )
        }
    }

    private fun succeed() {
        cancelRunningStep()
        UnlockSession.unlock(targetPkg)
        finish()
    }

    private fun goHome() {
        cancelRunningStep()
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        fingerprintCancel?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val MAX_FINGERPRINT_FAILS = 3

        /** Package the lock screen is currently covering, if it is on screen. */
        @Volatile var visibleFor: String? = null
            private set

        fun intent(context: Context, pkg: String): Intent =
            Intent(context, LockActivity::class.java)
                .putExtra(EXTRA_PACKAGE, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}

@Composable
private fun LockScreen(
    appLabel: String,
    step: Method,
    message: String,
    credentialType: CredentialType,
    lockoutUntil: Long,
    canRetryBiometrics: Boolean,
    onSecret: (String) -> Unit,
    onSkip: () -> Unit,
    onRetryBiometrics: () -> Unit,
) {
    var remaining by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lockoutUntil) {
        while (true) {
            remaining = ((lockoutUntil - System.currentTimeMillis() + 999) / 1000).coerceAtLeast(0)
            if (remaining == 0L) break
            delay(500)
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                if (step == Method.FACE) Icons.Default.Face else Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.locked_title, appLabel), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    when (step) {
                        Method.FACE -> R.string.step_face
                        Method.FINGERPRINT -> R.string.step_fingerprint
                        Method.CREDENTIAL ->
                            if (credentialType == CredentialType.PIN) R.string.enter_pin else R.string.draw_pattern
                    },
                ),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (step == Method.CREDENTIAL && remaining > 0) stringResource(R.string.locked_out, remaining) else message,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.height(48.dp),
            )
            when (step) {
                Method.CREDENTIAL -> {
                    if (credentialType == CredentialType.PIN) {
                        PinPad(enabled = remaining == 0L, onSubmit = onSecret)
                    } else {
                        PatternView(enabled = remaining == 0L, onPattern = onSecret)
                    }
                    if (canRetryBiometrics) {
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = onRetryBiometrics) { Text(stringResource(R.string.retry_biometrics)) }
                    }
                }
                else -> TextButton(onClick = onSkip) { Text(stringResource(R.string.next_method)) }
            }
        }
    }
}
