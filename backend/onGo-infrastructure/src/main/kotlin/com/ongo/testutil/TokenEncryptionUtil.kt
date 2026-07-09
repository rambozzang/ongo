package com.ongo.testutil

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * 테스트용 토큰 암호화 유틸리티.
 * Channel.access_token 에 넣기 전에 실행해서 암호문을 얻습니다.
 *
 * 사용법:
 *   ./gradlew :onGo-infrastructure:run -Dexec.mainClass="com.ongo.testutil.TokenEncryptionUtilKt" \
 *     -Dexec.args="<base64-32byte-key> <plain-token>"
 */
private const val ALGORITHM = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH = 128
private const val IV_LENGTH = 12

fun encryptToken(encryptionKey: String, plainText: String): String {
    val keyBytes = Base64.getDecoder().decode(encryptionKey)
    require(keyBytes.size == 32) { "AES-256 키는 32바이트여야 합니다. 현재: ${keyBytes.size}" }
    val keySpec = SecretKeySpec(keyBytes, "AES")

    val cipher = Cipher.getInstance(ALGORITHM)
    val iv = ByteArray(IV_LENGTH)
    SecureRandom().nextBytes(iv)
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_LENGTH, iv))
    val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    val combined = iv + encrypted
    return Base64.getEncoder().encodeToString(combined)
}

fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Usage: TokenEncryptionUtilKt <base64-32byte-key> <plain-token>")
        return
    }
    val key = args[0]
    val token = args[1]
    println(encryptToken(key, token))
}
