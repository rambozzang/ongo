package com.ongo.infrastructure.security

import com.ongo.domain.auth.TokenBlacklistPort
import org.springframework.jdbc.core.JdbcTemplate
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 토큰 블랙리스트 빈 구성.
 *
 * 운영 인프라에는 외부 캐시를 두지 않는다. JWT access token은 짧은 수명을 사용하고,
 * 로그아웃한 JTI의 만료시각만 PostgreSQL에 보관한다.
 */
@Configuration
class TokenBlacklistConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun tokenBlacklistPort(jdbcTemplate: JdbcTemplate): TokenBlacklistPort {
        log.info("TokenBlacklist: PostgreSQL TTL 저장소 사용 (외부 캐시 없음)")
        return TokenBlacklistService(jdbcTemplate)
    }
}
