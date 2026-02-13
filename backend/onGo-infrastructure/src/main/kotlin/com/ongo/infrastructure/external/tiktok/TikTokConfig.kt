package com.ongo.infrastructure.external.tiktok

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.time.Duration

@Configuration
class TikTokConfig {

    @Value("\${platform.tiktok.api-base-url:https://open.tiktokapis.com}")
    private lateinit var apiBaseUrl: String

    @Value("\${platform.tiktok.client-key:}")
    private lateinit var clientKey: String

    @Value("\${platform.tiktok.client-secret:}")
    private lateinit var clientSecret: String

    @Bean
    fun tikTokApi(): TikTokApi {
        val restClient = RestClient.builder()
            .baseUrl(apiBaseUrl)
            .defaultHeader("Content-Type", "application/json")
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(TikTokApi::class.java)
    }

    @Bean
    fun tikTokOAuthApi(): TikTokOAuthApi {
        val restClient = RestClient.builder()
            .baseUrl(apiBaseUrl)
            .defaultHeader("Content-Type", "application/json")
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(TikTokOAuthApi::class.java)
    }

    fun getClientKey(): String = clientKey
    fun getClientSecret(): String = clientSecret

    private fun createRequestFactory(): SimpleClientHttpRequestFactory {
        return SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(Duration.ofSeconds(30))
        }
    }
}
