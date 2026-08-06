package com.ongo.infrastructure.persistence.jooq

import org.jooq.conf.RenderNameCase
import org.jooq.conf.RenderQuotedNames
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * jOOQ 렌더 설정만 얹는다. 나머지는 스프링 부트 `JooqAutoConfiguration` 에 맡긴다.
 *
 * 예전에는 여기서 `DefaultConfiguration` 빈을 직접 만들어 자동설정 빈을 대체했다.
 * 그 결과 자동설정이 함께 구성하는 것들이 통째로 빠졌고, 특히
 * `TransactionAwareDataSourceProxy` 가 없어 **jOOQ 가 스프링 트랜잭션에 참여하지 못했다.**
 * `@Transactional` 이 무력화되고 `SELECT ... FOR UPDATE` 잠금이 즉시 풀리는 결함이었다.
 *
 * 자동설정에 맡기면 아래가 모두 따라온다.
 * - `DataSourceConnectionProvider(TransactionAwareDataSourceProxy(dataSource))`
 * - `SpringTransactionProvider` — `dsl.transaction { }` 도 스프링 트랜잭션에 참여한다
 * - `ExceptionTranslatorExecuteListener` — jOOQ 예외를 스프링 `DataAccessException` 계층으로 번역
 *
 * SQL dialect 는 `application.yml` 의 `spring.jooq.sql-dialect: POSTGRES` 가 결정한다.
 *
 * `SpringTransactionParticipationIT` 가 트랜잭션 참여를,
 * `JooqExceptionContractIT` 가 예외 타입 계약을 검증한다.
 */
@Configuration
class JooqConfig {

    @Bean
    fun jooqRenderSettingsCustomizer() = DefaultConfigurationCustomizer { configuration ->
        configuration.set(
            configuration.settings()
                .withRenderNameCase(RenderNameCase.LOWER)
                .withRenderQuotedNames(RenderQuotedNames.NEVER)
        )
    }
}
