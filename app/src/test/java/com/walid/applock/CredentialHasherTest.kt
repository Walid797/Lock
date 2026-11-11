package com.walid.applock

import com.walid.applock.data.CredentialHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialHasherTest {
    @Test fun correctSecretVerifies() {
        val stored = CredentialHasher.hash("1234")
        assertTrue(CredentialHasher.verify("1234", stored))
    }

    @Test fun wrongSecretIsRejected() {
        val stored = CredentialHasher.hash("0124678")
        assertFalse(CredentialHasher.verify("0124687", stored))
        assertFalse(CredentialHasher.verify("", stored))
    }

    @Test fun secretIsNotStoredAndSaltDiffers() {
        val a = CredentialHasher.hash("1234")
        val b = CredentialHasher.hash("1234")
        assertFalse(a.hashHex.contains("1234") && a.hashHex.length < 16)
        assertNotEquals(a.saltHex, b.saltHex)
        assertNotEquals(a.hashHex, b.hashHex)
    }

    @Test fun corruptedRecordDoesNotVerify() {
        val stored = CredentialHasher.hash("1234")
        assertFalse(CredentialHasher.verify("1234", stored.copy(hashHex = "zz")))
        assertFalse(CredentialHasher.verify("1234", stored.copy(algorithm = "nope")))
    }
}
