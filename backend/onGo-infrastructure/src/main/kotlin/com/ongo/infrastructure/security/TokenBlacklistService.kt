package com.ongo.infrastructure.security

import com.ongo.domain.auth.TokenBlacklistPort
import java.util.concurrent.ConcurrentHashMap

/** 인메모리 TTL 토큰 블랙리스트. */
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
