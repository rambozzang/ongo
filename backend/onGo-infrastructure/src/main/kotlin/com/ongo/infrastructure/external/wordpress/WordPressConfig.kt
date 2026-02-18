package com.ongo.infrastructure.external.wordpress

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.time.Duration

@Configuration
class WordPressConfig {

    @Value("\${platform.wordpress.api-base-url:https://public-api.wordpress.com}")
    private lateinit var apiBaseUrl: String

    @Value("\${platform.wordpress.client-id:}")
    private lateinit var clientId: String

    @Value("\${platform.wordpress.client-secret:}")
    private lateinit var clientSecret: String

    @Bean
    fun wordPressApi(): WordPressApi {
        val restClient = RestClient.builder()
            .baseUrl(apiBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(WordPressApi::class.java)
    }

    @Bean
    fun wordPressOAuthApi(): WordPressOAuthApi {
        val restClient = RestClient.builder()
            .baseUrl(apiBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(WordPressOAuthApi::class.java)
    }

    fun getClientId(): String = clientId
    fun getClientSecret(): String = clientSecret

    private fun createRequestFactory(): SimpleClientHttpRequestFactory {
        return SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(Duration.ofSeconds(30))
        }
    }
}
