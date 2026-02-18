package com.ongo.infrastructure.external.twitter

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.time.Duration

@Configuration
class TwitterConfig {

    @Value("\${platform.twitter.api-base-url:https://api.twitter.com}")
    private lateinit var apiBaseUrl: String

    @Value("\${platform.twitter.upload-base-url:https://upload.twitter.com}")
    private lateinit var uploadBaseUrl: String

    @Value("\${platform.twitter.client-id:}")
    private lateinit var clientId: String

    @Value("\${platform.twitter.client-secret:}")
    private lateinit var clientSecret: String

    @Bean
    fun twitterApi(): TwitterApi {
        val restClient = RestClient.builder()
            .baseUrl(apiBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(TwitterApi::class.java)
    }

    @Bean
    fun twitterMediaApi(): TwitterMediaApi {
        val restClient = RestClient.builder()
            .baseUrl(uploadBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(TwitterMediaApi::class.java)
    }

    @Bean
    fun twitterOAuthApi(): TwitterOAuthApi {
        val restClient = RestClient.builder()
            .baseUrl(apiBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(TwitterOAuthApi::class.java)
    }

    fun getClientId(): String = clientId
    fun getClientSecret(): String = clientSecret

    private fun createRequestFactory(): SimpleClientHttpRequestFactory {
        return SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(Duration.ofSeconds(60))
        }
    }
}
