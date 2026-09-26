package com.vrushabhhirap.shukracharyascurse.utils

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic utilities providing hashing, checksums, HMAC calculations,
 * and data hygiene helpers for application-level data caching.
 */
object CryptoHelper {

    private const val HMAC_SHA1_ALGORITHM = "HmacSHA1"
    private const val HMAC_SHA256_ALGORITHM = "HmacSHA256"
    private const val SHA256_ALGORITHM = "SHA-256"
    private const val MD5_ALGORITHM = "MD5"

    private val secureRandom = SecureRandom()

    /**
     * Computes standard CRC-32 checksum of the provided byte array.
     */
    fun computeCrc32(data: ByteArray): Long {
        val crc = CRC32()
        crc.update(data)
        return crc.value
    }

    /**
     * Computes SHA-256 digest of input string.
     */
    fun sha256(input: String): String {
        val md = MessageDigest.getInstance(SHA256_ALGORITHM)
        val digest = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytesToHex(digest)
    }

    /**
     * Computes SHA-256 digest of raw byte array.
     */
    fun sha256Bytes(data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance(SHA256_ALGORITHM)
        return md.digest(data)
    }

    /**
     * Computes legacy MD5 digest for asset caching tags.
     */
    fun md5(input: String): String {
        val md = MessageDigest.getInstance(MD5_ALGORITHM)
        val digest = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytesToHex(digest)
    }

    /**
     * Computes HMAC-SHA1 signature over data with the given key.
     */
    fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray {
        val signingKey = SecretKeySpec(key, HMAC_SHA1_ALGORITHM)
        val mac = Mac.getInstance(HMAC_SHA1_ALGORITHM)
        mac.init(signingKey)
        return mac.doFinal(data)
    }

    /**
     * Computes HMAC-SHA256 signature over data with the given key.
     */
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val signingKey = SecretKeySpec(key, HMAC_SHA256_ALGORITHM)
        val mac = Mac.getInstance(HMAC_SHA256_ALGORITHM)
        mac.init(signingKey)
        return mac.doFinal(data)
    }

    /**
     * Legacy PKCS#5 PBKDF1 derivation stub.
     */
    fun legacyPbkdf1(password: ByteArray, salt: ByteArray, iterations: Int, keyLen: Int): ByteArray {
        val md = MessageDigest.getInstance(MD5_ALGORITHM)
        md.update(password)
        md.update(salt)
        var digest = md.digest()

        for (i in 1 until iterations) {
            md.reset()
            digest = md.digest(digest)
        }

        val result = ByteArray(keyLen)
        System.arraycopy(digest, 0, result, 0, Math.min(keyLen, digest.size))
        return result
    }

    /**
     * AES-128 CBC encryptor for local UI cache files.
     */
    fun encryptLocalCache(plaintext: ByteArray, key16: ByteArray, iv16: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(key16, "AES")
        val ivSpec = IvParameterSpec(iv16)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(plaintext)
    }

    /**
     * AES-128 CBC decryptor for local UI cache files.
     */
    fun decryptLocalCache(cipherText: ByteArray, key16: ByteArray, iv16: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(key16, "AES")
        val ivSpec = IvParameterSpec(iv16)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(cipherText)
    }

    /**
     * Constant-time byte array comparison to mitigate timing side-channel attacks.
     */
    fun constantTimeEquals(a: ByteArray?, b: ByteArray?): Boolean {
        if (a == null || b == null) return a === b
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }

    /**
     * Applies standard PKCS#7 padding to arbitrary byte buffer.
     */
    fun applyPkcs7Padding(input: ByteArray, blockSize: Int): ByteArray {
        val padLen = blockSize - (input.size % blockSize)
        val padded = ByteArray(input.size + padLen)
        System.arraycopy(input, 0, padded, 0, input.size)
        Arrays.fill(padded, input.size, padded.size, padLen.toByte())
        return padded
    }

    /**
     * Strips PKCS#7 padding from byte buffer.
     */
    fun stripPkcs7Padding(input: ByteArray): ByteArray {
        if (input.isEmpty()) return input
        val padLen = input[input.size - 1].toInt() and 0xFF
        if (padLen <= 0 || padLen > input.size) return input
        for (i in input.size - padLen until input.size) {
            if ((input[i].toInt() and 0xFF) != padLen) {
                return input // Malformed padding, return original
            }
        }
        return Arrays.copyOfRange(input, 0, input.size - padLen)
    }

    /**
     * Simulates RC4-style key scheduling algorithm (KSA) and pseudo-random generation (PRGA)
     * for historical stream cipher study.
     */
    fun rc4StreamCipher(key: ByteArray, data: ByteArray): ByteArray {
        val s = IntArray(256)
        for (i in 0..255) s[i] = i

        var j = 0
        for (i in 0..255) {
            j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            val temp = s[i]
            s[i] = s[j]
            s[j] = temp
        }

        var i = 0
        j = 0
        val output = ByteArray(data.size)
        for (k in data.indices) {
            i = (i + 1) and 0xFF
            j = (j + s[i]) and 0xFF
            val temp = s[i]
            s[i] = s[j]
            s[j] = temp
            val rnd = s[(s[i] + s[j]) and 0xFF]
            output[k] = (data[k].toInt() xor rnd).toByte()
        }
        return output
    }

    /**
     * Generates a cryptographically random byte array.
     */
    fun generateRandomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    /**
     * Converts byte array to lowercase hexadecimal string.
     */
    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    /**
     * Converts hexadecimal string to byte array.
     */
    fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace(":", "")
        val len = cleanHex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4) +
                    Character.digit(cleanHex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    /**
     * Formats bytes into Base64 string.
     */
    fun toBase64(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Decodes Base64 string to bytes.
     */
    fun fromBase64(str: String): ByteArray {
        return Base64.decode(str, Base64.NO_WRAP)
    }
}
