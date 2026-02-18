package com.ongo.infrastructure.external.dailymotion

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.time.Duration

@Configuration
class DailymotionConfig {

    @Value("\${platform.dailymotion.api-base-url:https://api.dailymotion.com}")
    private lateinit var apiBaseUrl: String

    @Value("\${platform.dailymotion.api-key:}")
    private lateinit var apiKey: String

    @Value("\${platform.dailymotion.api-secret:}")
    private lateinit var apiSecret: String

    @Bean
    fun dailymotionApi(): DailymotionApi {
        val restClient = RestClient.builder()
            .baseUrl(apiBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(DailymotionApi::class.java)
    }

    @Bean
    fun dailymotionOAuthApi(): DailymotionOAuthApi {
        val restClient = RestClient.builder()
            .baseUrl(apiBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(DailymotionOAuthApi::class.java)
    }

    fun getApiKey(): String = apiKey
    fun getApiSecret(): String = apiSecret

    private fun createRequestFactory(): SimpleClientHttpRequestFactory {
        return SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(Duration.ofSeconds(30))
        }
    }
}
