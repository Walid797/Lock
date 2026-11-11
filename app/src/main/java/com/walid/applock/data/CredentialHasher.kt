package com.walid.applock.data

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Salted PBKDF2 hashing for the PIN / pattern. The secret itself is never stored. */
object CredentialHasher {
    const val ITERATIONS = 20_000
    private const val KEY_BITS = 256
    private const val SHA256 = "PBKDF2WithHmacSHA256"
    private const val SHA1 = "PBKDF2WithHmacSHA1" // Android 6–7 have no SHA256 variant

    data class Hashed(val algorithm: String, val iterations: Int, val saltHex: String, val hashHex: String)

    fun hash(secret: String): Hashed {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val algorithm = if (supports(SHA256)) SHA256 else SHA1
        return Hashed(algorithm, ITERATIONS, salt.toHex(), derive(secret, salt, algorithm, ITERATIONS).toHex())
    }

    fun verify(secret: String, stored: Hashed): Boolean = try {
        val actual = derive(secret, stored.saltHex.fromHex(), stored.algorithm, stored.iterations)
        MessageDigest.isEqual(actual, stored.hashHex.fromHex())
    } catch (e: Exception) {
        false
    }

    private fun supports(algorithm: String) = try {
        SecretKeyFactory.getInstance(algorithm); true
    } catch (e: NoSuchAlgorithmException) {
        false
    }

    private fun derive(secret: String, salt: ByteArray, algorithm: String, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray {
        require(length % 2 == 0)
        return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
