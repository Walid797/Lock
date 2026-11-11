package com.walid.applock.auth

enum class Method { FACE, FINGERPRINT, CREDENTIAL }

/**
 * Ordered list of unlock steps. Biometric methods come in the user's chosen
 * order (skipping any that are unavailable); the app PIN/pattern is always last.
 */
class UnlockChain(order: List<Method>, isAvailable: (Method) -> Boolean) {

    val steps: List<Method> =
        order.filter { it != Method.CREDENTIAL }.distinct().filter(isAvailable) + Method.CREDENTIAL

    private var index = 0

    val current: Method get() = steps[index]

    /** Current step failed or was skipped: move to the next one (stays on the last). */
    fun fail(): Method {
        if (index < steps.lastIndex) index++
        return current
    }

    fun skipToCredential(): Method {
        index = steps.lastIndex
        return current
    }
}
