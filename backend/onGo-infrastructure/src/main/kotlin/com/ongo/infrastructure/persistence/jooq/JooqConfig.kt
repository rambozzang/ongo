package com.ongo.infrastructure.persistence.jooq

import org.jooq.SQLDialect
import org.jooq.conf.RenderNameCase
import org.jooq.conf.RenderQuotedNames
import org.jooq.conf.Settings
import org.jooq.impl.DefaultConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class JooqConfig {

    @Bean
    fun jooqConfiguration(dataSource: DataSource): DefaultConfiguration {
        val settings = Settings()
            .withRenderNameCase(RenderNameCase.LOWER)
            .withRenderQuotedNames(RenderQuotedNames.NEVER)

        return DefaultConfiguration().apply {
            set(dataSource)
            set(SQLDialect.POSTGRES)
            set(settings)
        }
    }
}
