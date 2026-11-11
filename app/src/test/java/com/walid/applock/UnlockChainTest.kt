package com.walid.applock

import com.walid.applock.auth.Method
import com.walid.applock.auth.Method.CREDENTIAL
import com.walid.applock.auth.Method.FACE
import com.walid.applock.auth.Method.FINGERPRINT
import com.walid.applock.auth.UnlockChain
import com.walid.applock.data.LockRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class UnlockChainTest {
    private val all: (Method) -> Boolean = { true }

    @Test fun defaultOrderIsFaceThenFingerprintThenCredential() {
        val chain = UnlockChain(LockRepository.DEFAULT_ORDER, all)
        assertEquals(listOf(FACE, FINGERPRINT, CREDENTIAL), chain.steps)
        assertEquals(FACE, chain.current)
        assertEquals(FINGERPRINT, chain.fail())
        assertEquals(CREDENTIAL, chain.fail())
        assertEquals(CREDENTIAL, chain.fail()) // stays on the last step
    }

    @Test fun userCanPutFingerprintFirst() {
        assertEquals(listOf(FINGERPRINT, FACE, CREDENTIAL), UnlockChain(listOf(FINGERPRINT, FACE), all).steps)
    }

    @Test fun unavailableMethodsAreSkipped() {
        val chain = UnlockChain(listOf(FACE, FINGERPRINT)) { it != FACE }
        assertEquals(listOf(FINGERPRINT, CREDENTIAL), chain.steps)
    }

    @Test fun credentialIsAlwaysLastAndAlwaysPresent() {
        assertEquals(listOf(CREDENTIAL), UnlockChain(emptyList(), all).steps)
        assertEquals(listOf(CREDENTIAL), UnlockChain(listOf(FACE, FINGERPRINT)) { false }.steps)
        assertEquals(listOf(FACE, CREDENTIAL), UnlockChain(listOf(CREDENTIAL, FACE, FACE), all).steps)
    }

    @Test fun skipToCredential() {
        val chain = UnlockChain(listOf(FACE, FINGERPRINT), all)
        assertEquals(CREDENTIAL, chain.skipToCredential())
    }

    @Test fun parseOrderFillsMissingAndIgnoresJunk() {
        assertEquals(listOf(FACE, FINGERPRINT), LockRepository.parseOrder(null))
        assertEquals(listOf(FINGERPRINT, FACE), LockRepository.parseOrder("FINGERPRINT,FACE"))
        assertEquals(listOf(FINGERPRINT, FACE), LockRepository.parseOrder("FINGERPRINT,bogus,CREDENTIAL"))
    }

    @Test fun lockoutGrowsAfterFiveFailures() {
        assertEquals(0L, LockRepository.lockoutMillis(4))
        assertEquals(30_000L, LockRepository.lockoutMillis(5))
        assertEquals(60_000L, LockRepository.lockoutMillis(6))
        assertEquals(30_000L * 64, LockRepository.lockoutMillis(100))
    }
}
