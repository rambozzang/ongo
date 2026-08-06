package com.ongo.infrastructure.persistence.jooq

import org.jooq.SQLDialect
import org.jooq.conf.RenderNameCase
import org.jooq.conf.RenderQuotedNames
import org.jooq.conf.Settings
import org.jooq.impl.DefaultConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import javax.sql.DataSource

@Configuration
class JooqConfig {

    /**
     * jOOQ 설정. **DataSource 를 반드시 [TransactionAwareDataSourceProxy] 로 감싸야 한다.**
     *
     * 원본 DataSource 를 그대로 넘기면 jOOQ 가 커넥션 풀에서 새 커넥션을 직접 꺼내
     * **스프링 트랜잭션에 참여하지 않는다.** 그 결과:
     * - `@Transactional` 이 붙어도 각 쿼리가 개별 auto-commit 된다. 롤백이 동작하지 않는다
     * - `SELECT ... FOR UPDATE` 의 행 잠금이 쿼리 직후 풀린다. 비관적 락이 무효가 된다
     *   (`CreditJooqRepository` 의 크레딧 차감, `PaymentJooqRepository` 의 결제 잠금 등)
     *
     * 스프링 부트의 `JooqAutoConfiguration` 은 기본으로 이 프록시를 씌우는데,
     * 이 설정이 그 빈을 대체하므로 여기서 직접 씌워야 한다.
     *
     * `SpringTransactionParticipationIT` 가 이 전제를 검증한다.
     */
    @Bean
    fun jooqConfiguration(dataSource: DataSource): DefaultConfiguration {
        val settings = Settings()
            .withRenderNameCase(RenderNameCase.LOWER)
            .withRenderQuotedNames(RenderQuotedNames.NEVER)

        return DefaultConfiguration().apply {
            set(TransactionAwareDataSourceProxy(dataSource))
            set(SQLDialect.POSTGRES)
            set(settings)
        }
    }
}
