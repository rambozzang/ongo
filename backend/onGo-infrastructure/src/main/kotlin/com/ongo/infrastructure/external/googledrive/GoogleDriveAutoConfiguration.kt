package com.ongo.infrastructure.external.googledrive

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableConfigurationProperties(GoogleDriveProperties::class)
class GoogleDriveAutoConfiguration {

    /**
     * Google Drive 관련 HTTP 호출용 WebClient.
     * 애플리케이션에 이미 WebClient 빈이 존재하면 재사용한다.
     */
    @Bean
    @ConditionalOnMissingBean(WebClient::class)
    fun googleDriveWebClient(): WebClient = WebClient.builder().build()
}
