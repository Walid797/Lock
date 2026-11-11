package com.walid.applock.session

/**
 * Remembers which package the user has just unlocked. The unlock lasts only
 * while that app stays in the foreground and the screen stays on.
 */
object UnlockSession {
    private val unlocked = HashSet<String>()

    @Synchronized fun unlock(pkg: String) { unlocked.add(pkg) }

    @Synchronized fun lock(pkg: String) { unlocked.remove(pkg) }

    @Synchronized fun isUnlocked(pkg: String) = pkg in unlocked

    /** A (different) app came to the foreground: every other app locks again. */
    @Synchronized fun onForeground(pkg: String) { unlocked.retainAll(setOf(pkg)) }

    @Synchronized fun clear() { unlocked.clear() }
}
