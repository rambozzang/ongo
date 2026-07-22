package com.ongo.infrastructure.security

import com.ongo.domain.auth.TokenBlacklistPort
import java.util.concurrent.ConcurrentHashMap

/**
 * 인메모리 토큰 블랙리스트 — 단일 인스턴스/Redis 미구성 환경용.
 * 빈 등록은 [TokenBlacklistConfig] 가 담당한다(RedisConnectionFactory 없을 때 사용).
 */
class TokenBlacklistService : TokenBlacklistPort {

    private val blacklist = ConcurrentHashMap<String, Long>()

    override fun blacklist(tokenJti: String, ttlMillis: Long) {
        val expiry = System.currentTimeMillis() + ttlMillis
        blacklist[tokenJti] = expiry
    }

    override fun isBlacklisted(tokenJti: String): Boolean {
        val expiry = blacklist[tokenJti] ?: return false
        if (System.currentTimeMillis() > expiry) {
            blacklist.remove(tokenJti)
            return false
        }
        return true
    }
}
