package com.ongo.infrastructure.security

import com.ongo.domain.auth.TokenBlacklistPort
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Redis 기반 토큰 블랙리스트 — 분산 환경(다중 인스턴스)에서도 공유됨.
 * RedisConnectionFactory 빈이 있을 때만 활성화된다.
 */
@Component
@ConditionalOnBean(RedisConnectionFactory::class)
class RedisTokenBlacklistService(
    connectionFactory: RedisConnectionFactory,
) : TokenBlacklistPort {

    private val log = LoggerFactory.getLogger(javaClass)
    private val redisTemplate = StringRedisTemplate(connectionFactory)

    private val keyPrefix = "token:blacklist:"

    override fun blacklist(tokenJti: String, ttlMillis: Long) {
        val key = "$keyPrefix$tokenJti"
        val ttlSeconds = TimeUnit.MILLISECONDS.toSeconds(ttlMillis).coerceAtLeast(1)
        redisTemplate.opsForValue().set(key, "1", ttlSeconds, TimeUnit.SECONDS)
        log.debug("Token blacklisted in Redis: jti={}, ttl={}s", tokenJti, ttlSeconds)
    }

    override fun isBlacklisted(tokenJti: String): Boolean {
        val key = "$keyPrefix$tokenJti"
        return redisTemplate.hasKey(key) == true
    }
}
