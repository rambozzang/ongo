package com.ongo.domain.auth

interface TokenBlacklistPort {
    fun blacklist(tokenJti: String, ttlMillis: Long)
    fun isBlacklisted(tokenJti: String): Boolean
}
