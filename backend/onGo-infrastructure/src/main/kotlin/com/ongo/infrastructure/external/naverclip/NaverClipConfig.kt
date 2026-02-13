package com.ongo.infrastructure.external.naverclip

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.time.Duration

@Configuration
class NaverClipConfig {

    @Value("\${platform.naver-clip.api-base-url:https://openapi.naver.com}")
    private lateinit var apiBaseUrl: String

    @Value("\${platform.naver.oauth-base-url:https://nid.naver.com}")
    private lateinit var oauthBaseUrl: String

    @Value("\${platform.naver.client-id:}")
    private lateinit var clientId: String

    @Value("\${platform.naver.client-secret:}")
    private lateinit var clientSecret: String

    @Bean
    fun naverClipApi(): NaverClipApi {
        val restClient = RestClient.builder()
            .baseUrl(apiBaseUrl)
            .defaultHeader("Content-Type", "application/json")
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(NaverClipApi::class.java)
    }

    @Bean
    fun naverOAuthApi(): NaverOAuthApi {
        val restClient = RestClient.builder()
            .baseUrl(oauthBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(NaverOAuthApi::class.java)
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
