package com.ongo.infrastructure.external.pinterest

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.time.Duration

@Configuration
class PinterestConfig {

    @Value("\${platform.pinterest.api-base-url:https://api.pinterest.com}")
    private lateinit var apiBaseUrl: String

    @Value("\${platform.pinterest.app-id:}")
    private lateinit var appId: String

    @Value("\${platform.pinterest.app-secret:}")
    private lateinit var appSecret: String

    @Bean
    fun pinterestApi(): PinterestApi {
        val restClient = RestClient.builder()
            .baseUrl(apiBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(PinterestApi::class.java)
    }

    @Bean
    fun pinterestOAuthApi(): PinterestOAuthApi {
        val restClient = RestClient.builder()
            .baseUrl(apiBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(PinterestOAuthApi::class.java)
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
