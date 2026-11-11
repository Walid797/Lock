package com.walid.applock.auth

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK

enum class MethodStatus {
    AVAILABLE,
    NOT_ENROLLED,
    NOT_SUPPORTED,

    /** Phone does not announce face hardware, but its system prompt may still offer face. */
    UNCONFIRMED,
}

object BiometricSupport {

    fun status(context: Context, method: Method): MethodStatus = when (method) {
        Method.FACE -> faceStatus(context)
        Method.FINGERPRINT -> fingerprintStatus(context)
        Method.CREDENTIAL -> MethodStatus.AVAILABLE
    }

    /** [forceFace]: the user chose to try the system prompt even though face is unconfirmed. */
    fun isAvailable(context: Context, method: Method, forceFace: Boolean = false): Boolean {
        val status = status(context, method)
        return status == MethodStatus.AVAILABLE ||
            (method == Method.FACE && forceFace && status == MethodStatus.UNCONFIRMED)
    }

    /**
     * The phone's own face unlock is only reachable through the system biometric
     * prompt. Phones that declare face hardware are certain; some (e.g. Huawei)
     * do not declare it yet still show face in the prompt, so those are UNCONFIRMED.
     */
    private fun faceStatus(context: Context): MethodStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return MethodStatus.NOT_SUPPORTED
        val declared = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)
        return when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS ->
                if (declared) MethodStatus.AVAILABLE else MethodStatus.UNCONFIRMED
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                if (declared) MethodStatus.NOT_ENROLLED else MethodStatus.NOT_SUPPORTED
            else -> MethodStatus.NOT_SUPPORTED
        }
    }

    private fun fingerprintStatus(context: Context): MethodStatus {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
            return MethodStatus.NOT_SUPPORTED
        }
        legacyFingerprintStatus(context)?.let { return it }
        return when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> MethodStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> MethodStatus.NOT_ENROLLED
            else -> MethodStatus.NOT_SUPPORTED
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyFingerprintStatus(context: Context): MethodStatus? = try {
        val fm = context.getSystemService(FingerprintManager::class.java)
        when {
            fm == null || !fm.isHardwareDetected -> null
            fm.hasEnrolledFingerprints() -> MethodStatus.AVAILABLE
            else -> MethodStatus.NOT_ENROLLED
        }
    } catch (e: Exception) {
        null
    }
}
