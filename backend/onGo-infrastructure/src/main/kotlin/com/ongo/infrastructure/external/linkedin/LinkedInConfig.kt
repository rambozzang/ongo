package com.ongo.infrastructure.external.linkedin

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.time.Duration

@Configuration
class LinkedInConfig {

    @Value("\${platform.linkedin.api-base-url:https://api.linkedin.com}")
    private lateinit var apiBaseUrl: String

    @Value("\${platform.linkedin.oauth-base-url:https://www.linkedin.com}")
    private lateinit var oauthBaseUrl: String

    @Value("\${platform.linkedin.client-id:}")
    private lateinit var clientId: String

    @Value("\${platform.linkedin.client-secret:}")
    private lateinit var clientSecret: String

    @Value("\${platform.linkedin.api-version:202607}")
    private lateinit var apiVersion: String

    @Value("\${platform.linkedin.video-poll-attempts:10}")
    private var videoPollAttempts: Int = 10

    @Value("\${platform.linkedin.video-poll-interval-ms:1000}")
    private var videoPollIntervalMillis: Long = 1000

    @Bean
    fun linkedInApi(): LinkedInApi {
        val restClient = RestClient.builder()
            .baseUrl(apiBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(LinkedInApi::class.java)
    }

    @Bean
    fun linkedInOAuthApi(): LinkedInOAuthApi {
        val restClient = RestClient.builder()
            .baseUrl(oauthBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(LinkedInOAuthApi::class.java)
    }

    @Bean
    fun linkedInVideosApi(): LinkedInVideosApi {
        val restClient = RestClient.builder()
            .baseUrl(apiBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(LinkedInVideosApi::class.java)
    }

    fun getClientId(): String = clientId
    fun getClientSecret(): String = clientSecret
    fun getApiVersion(): String = apiVersion
    fun getVideoPollAttempts(): Int = videoPollAttempts
    fun getVideoPollIntervalMillis(): Long = videoPollIntervalMillis

    private fun createRequestFactory(): SimpleClientHttpRequestFactory {
        return SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(Duration.ofSeconds(30))
        }
    }
}
