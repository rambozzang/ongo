package com.ongo.infrastructure.security

import com.ongo.domain.auth.TokenBlacklistPort
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 인메모리 토큰 블랙리스트 — 단일 인스턴스 환경용.
 * RedisTokenBlacklistService가 활성화되면 자동으로 대첸다.
 */
@Component
@ConditionalOnMissingBean(TokenBlacklistPort::class)
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
