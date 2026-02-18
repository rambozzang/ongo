package com.ongo.infrastructure.external.facebook

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.time.Duration

@Configuration
class FacebookConfig {

    @Value("\${platform.facebook.api-base-url:https://graph.facebook.com/v21.0}")
    private lateinit var apiBaseUrl: String

    @Value("\${platform.facebook.app-id:}")
    private lateinit var appId: String

    @Value("\${platform.facebook.app-secret:}")
    private lateinit var appSecret: String

    @Bean
    fun facebookApi(): FacebookApi {
        val restClient = RestClient.builder()
            .baseUrl(apiBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(FacebookApi::class.java)
    }

    @Bean
    fun facebookOAuthApi(): FacebookOAuthApi {
        val restClient = RestClient.builder()
            .baseUrl(apiBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(FacebookOAuthApi::class.java)
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
