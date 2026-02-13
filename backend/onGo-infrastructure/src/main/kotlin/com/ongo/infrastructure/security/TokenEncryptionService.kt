package com.ongo.infrastructure.security

import com.ongo.domain.channel.TokenEncryptionPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

@Service
class TokenEncryptionService(
    @Value("\${platform.token.encryption-key}") private val encryptionKey: String
) : TokenEncryptionPort {
    private val algorithm = "AES/GCM/NoPadding"
    private val keySpec: SecretKeySpec
    private val gcmTagLength = 128
    private val ivLength = 12

    init {
        val keyBytes = Base64.getDecoder().decode(encryptionKey)
        require(keyBytes.size == 32) { "AES-256 키는 32바이트여야 합니다" }
        keySpec = SecretKeySpec(keyBytes, "AES")
    }

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(algorithm)
        val iv = ByteArray(ivLength)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(gcmTagLength, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.getEncoder().encodeToString(combined)
    }

    override fun decrypt(cipherText: String): String {
        val combined = Base64.getDecoder().decode(cipherText)
        val iv = combined.copyOfRange(0, ivLength)
        val encrypted = combined.copyOfRange(ivLength, combined.size)
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(gcmTagLength, iv))
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, Charsets.UTF_8)
    }
}
