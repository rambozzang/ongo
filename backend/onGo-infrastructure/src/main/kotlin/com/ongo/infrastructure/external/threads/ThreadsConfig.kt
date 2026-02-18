package com.ongo.infrastructure.external.threads

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.time.Duration

@Configuration
class ThreadsConfig {

    @Value("\${platform.threads.api-base-url:https://graph.threads.net/v1.0}")
    private lateinit var apiBaseUrl: String

    @Value("\${platform.threads.app-id:}")
    private lateinit var appId: String

    @Value("\${platform.threads.app-secret:}")
    private lateinit var appSecret: String

    @Bean
    fun threadsApi(): ThreadsApi {
        val restClient = RestClient.builder()
            .baseUrl(apiBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(ThreadsApi::class.java)
    }

    @Bean
    fun threadsOAuthApi(): ThreadsOAuthApi {
        val restClient = RestClient.builder()
            .baseUrl(apiBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(ThreadsOAuthApi::class.java)
    }

    fun getAppId(): String = appId
    fun getAppSecret(): String = appSecret

    private fun createRequestFactory(): SimpleClientHttpRequestFactory {
        return SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(Duration.ofSeconds(30))
        }
    }
}
