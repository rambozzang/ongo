package com.ongo.infrastructure.security

import com.ongo.domain.auth.TokenBlacklistPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory

/**
 * 토큰 블랙리스트 빈 구성.
 *
 * `RedisConnectionFactory` 빈이 있으면 Redis 기반(분산), 없으면 인메모리(단일 인스턴스)를 사용한다.
 * 판단을 [ObjectProvider] 로 **빈 생성 시점(자동설정 이후)** 에 수행해,
 * `@Component` + `@ConditionalOnBean/@ConditionalOnMissingBean` 조합의 컴포넌트 스캔 순서 문제를 제거한다.
 * (이 순서 문제로 prod에서 `TokenBlacklistPort` 빈이 누락되어 `JwtAuthenticationFilter` 생성이 실패했었다.)
 */
@Configuration
class TokenBlacklistConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun tokenBlacklistPort(redisConnectionFactory: ObjectProvider<RedisConnectionFactory>): TokenBlacklistPort {
        val factory = redisConnectionFactory.ifAvailable
        return if (factory != null) {
            log.info("TokenBlacklist: Redis 기반 사용(분산 환경)")
            RedisTokenBlacklistService(factory)
        } else {
            log.info("TokenBlacklist: 인메모리 사용(단일 인스턴스/Redis 미구성)")
            TokenBlacklistService()
        }
    }
}
