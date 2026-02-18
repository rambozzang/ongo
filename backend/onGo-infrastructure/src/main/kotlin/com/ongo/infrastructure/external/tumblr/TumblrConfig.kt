package com.ongo.infrastructure.external.tumblr

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.time.Duration

@Configuration
class TumblrConfig {

    @Value("\${platform.tumblr.api-base-url:https://api.tumblr.com}")
    private lateinit var apiBaseUrl: String

    @Value("\${platform.tumblr.consumer-key:}")
    private lateinit var consumerKey: String

    @Value("\${platform.tumblr.consumer-secret:}")
    private lateinit var consumerSecret: String

    @Bean
    fun tumblrApi(): TumblrApi {
        val restClient = RestClient.builder()
            .baseUrl(apiBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(TumblrApi::class.java)
    }

    @Bean
    fun tumblrOAuthApi(): TumblrOAuthApi {
        val restClient = RestClient.builder()
            .baseUrl("https://www.tumblr.com")
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(TumblrOAuthApi::class.java)
    }

    fun getConsumerKey(): String = consumerKey
    fun getConsumerSecret(): String = consumerSecret

    private fun createRequestFactory(): SimpleClientHttpRequestFactory {
        return SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(Duration.ofSeconds(30))
        }
    }
}
