package com.walid.applock.auth

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK

enum class MethodStatus { AVAILABLE, NOT_ENROLLED, NOT_SUPPORTED }

object BiometricSupport {

    fun status(context: Context, method: Method): MethodStatus = when (method) {
        Method.FACE -> faceStatus(context)
        Method.FINGERPRINT -> fingerprintStatus(context)
        Method.CREDENTIAL -> MethodStatus.AVAILABLE
    }

    fun isAvailable(context: Context, method: Method) =
        status(context, method) == MethodStatus.AVAILABLE

    /**
     * The phone's own face unlock is only reachable through the system biometric
     * prompt, and only on phones that declare face hardware to apps (Android 10+).
     */
    private fun faceStatus(context: Context): MethodStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return MethodStatus.NOT_SUPPORTED
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)) {
            return MethodStatus.NOT_SUPPORTED
        }
        return when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> MethodStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> MethodStatus.NOT_ENROLLED
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
