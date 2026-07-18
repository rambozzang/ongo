package com.ongo.application.ugc

import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 초대 토큰 생성·해싱. 원문 토큰은 발급 시 1회만 노출하고 DB에는 SHA-256 해시(hex)만 저장한다.
 */
@Service
class InviteTokenService {

    private val random = SecureRandom()

    /** 256비트 URL-safe 랜덤 토큰 원문(64자 hex)을 생성한다. */
    fun generateToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.toHex()
    }

    /** 토큰 원문의 SHA-256 해시(64자 hex)를 반환한다. 저장·조회 키로 사용한다. */
    fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.UTF_8))
            .toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
