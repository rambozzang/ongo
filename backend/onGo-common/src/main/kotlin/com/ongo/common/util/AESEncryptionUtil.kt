package com.ongo.common.util

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM 암복호화 유틸리티.
 * 플랫폼 OAuth 토큰 등 민감 데이터 암호화에 사용.
 */
object AESEncryptionUtil {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    fun encrypt(plainText: String, secretKey: String): String {
        val key = toSecretKey(secretKey)
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        }
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(cipherText: String, secretKey: String): String {
        val key = toSecretKey(secretKey)
        val decoded = Base64.getDecoder().decode(cipherText)
        val iv = decoded.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = decoded.copyOfRange(GCM_IV_LENGTH, decoded.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        }
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun toSecretKey(key: String): SecretKey {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        require(keyBytes.size == 32) { "AES-256 키는 32바이트여야 합니다. 현재: ${keyBytes.size}바이트" }
        return SecretKeySpec(keyBytes, ALGORITHM)
    }
}
