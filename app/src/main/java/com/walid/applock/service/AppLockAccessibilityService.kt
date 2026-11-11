package com.walid.applock.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.walid.applock.LockActivity
import com.walid.applock.data.LockRepository
import com.walid.applock.session.UnlockSession

/** Watches which app comes to the foreground and puts the lock screen over locked ones. */
class AppLockAccessibilityService : AccessibilityService() {

    private lateinit var repo: LockRepository
    private val relevantCache = HashMap<String, Boolean>()
    private var receiverRegistered = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = UnlockSession.clear()
    }

    override fun onServiceConnected() {
        repo = LockRepository(this)
        ContextCompat.registerReceiver(
            this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == packageName) {
            // Our own lock screen is not "another app"; our main screen is.
            if (event.className?.toString() != LockActivity::class.java.name) UnlockSession.onForeground(pkg)
            return
        }
        val locked = pkg in repo.lockedPackages
        if (!locked && !isRealApp(pkg)) return // keyboards, system UI, permission dialogs…

        UnlockSession.onForeground(pkg)
        if (locked && !UnlockSession.isUnlocked(pkg) && LockActivity.visibleFor != pkg) {
            startActivity(LockActivity.intent(this, pkg))
        }
    }

    /** Only launcher-visible apps and home screens count as the user "leaving" an app. */
    private fun isRealApp(pkg: String): Boolean = relevantCache.getOrPut(pkg) {
        packageManager.getLaunchIntentForPackage(pkg) != null ||
            packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0,
            ).any { it.activityInfo.packageName == pkg }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(screenOffReceiver)
        super.onDestroy()
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, AppLockAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
