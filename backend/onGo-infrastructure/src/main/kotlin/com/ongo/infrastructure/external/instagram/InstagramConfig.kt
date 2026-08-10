package com.ongo.infrastructure.external.instagram

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import com.ongo.infrastructure.external.platform.PlatformRestClientSupport
import java.time.Duration

@Configuration
class InstagramConfig {

    @Value("\${platform.instagram.graph-api-base-url:https://graph.instagram.com/v21.0}")
    private lateinit var graphApiBaseUrl: String

    // The authorization-code exchange lives on api.instagram.com while the
    // long-lived-token and Graph operations live on graph.instagram.com.
    @Value("\${platform.instagram.oauth-base-url:https://api.instagram.com}")
    private lateinit var oauthBaseUrl: String

    @Value("\${platform.instagram.app-id:}")
    private lateinit var appId: String

    @Value("\${platform.instagram.app-secret:}")
    private lateinit var appSecret: String

    @Bean
    fun instagramApi(): InstagramApi {
        val restClient = PlatformRestClientSupport.builder(graphApiBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(InstagramApi::class.java)
    }

    @Bean
    fun instagramOAuthApi(): InstagramOAuthApi {
        val restClient = PlatformRestClientSupport.builder(oauthBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(InstagramOAuthApi::class.java)
    }

    @Bean
    fun instagramGraphOAuthApi(): InstagramGraphOAuthApi {
        val restClient = PlatformRestClientSupport.builder(graphApiBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(InstagramGraphOAuthApi::class.java)
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
