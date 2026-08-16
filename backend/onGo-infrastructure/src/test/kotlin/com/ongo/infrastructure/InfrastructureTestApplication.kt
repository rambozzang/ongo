package com.ongo.infrastructure

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import com.ongo.infrastructure.security.TokenBlacklistConfig

/**
 * Minimal Spring Boot application class used **ONLY** by @SpringBootTest in this module.
 *
 * Production entry point (`@SpringBootApplication`) lives in onGo-api — this class exists
 * purely so @SpringBootTest has a context-providing `@SpringBootConfiguration` to bootstrap.
 *
 * 의도적으로 scope를 persistence 레이어와 domain으로 한정한다:
 * - persistence.jooq: @Repository 빈 (테스트 대상)
 * - domain: repository 인터페이스/엔티티
 *
 * ai/external/payment/security 등 무거운 설정은 @Value 프로퍼티(JWT_SECRET, API keys 등)를
 * 요구하므로, 통합 테스트 부트스트랩을 복잡하게 만든다. 좁은 스캔으로 이를 회피한다.
 */
@SpringBootApplication
@Import(TokenBlacklistConfig::class)
@ComponentScan(
    basePackages = [
        "com.ongo.infrastructure.persistence",
        "com.ongo.domain",
    ],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = [".*\\.external\\..*", ".*\\.ai\\..*", ".*\\.payment\\..*", ".*\\.security\\..*"],
        ),
    ],
)
class InfrastructureTestApplication
