package com.ongo.infrastructure.external.googledrive

import io.netty.channel.ChannelOption
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
@EnableConfigurationProperties(GoogleDriveProperties::class)
class GoogleDriveAutoConfiguration {

    /**
     * Google Drive 관련 HTTP 호출용 WebClient.
     * 애플리케이션에 이미 WebClient 빈이 존재하면 재사용한다.
     */
    @Bean
    @ConditionalOnMissingBean(WebClient::class)
    fun googleDriveWebClient(): WebClient {
        val httpClient = HttpClient.create()
            .responseTimeout(Duration.ofSeconds(30))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }

    /**
     * OAuth state 발급/검증 매니저.
     * secret은 [GoogleDriveProperties.oauthStateSecret]에서 주입.
     */
    @Bean
    fun oauthStateManager(props: GoogleDriveProperties): OAuthStateManager =
        OAuthStateManager(secret = props.oauthStateSecret, ttlSeconds = 300)
}
