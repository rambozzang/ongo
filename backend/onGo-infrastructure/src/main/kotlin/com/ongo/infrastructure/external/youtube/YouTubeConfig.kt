package com.ongo.infrastructure.external.youtube

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.time.Duration

@Configuration
class YouTubeConfig {

    @Value("\${platform.youtube.api-base-url:https://www.googleapis.com}")
    private lateinit var apiBaseUrl: String

    @Value("\${platform.youtube.upload-base-url:https://www.googleapis.com}")
    private lateinit var uploadBaseUrl: String

    @Value("\${platform.youtube.analytics-base-url:https://youtubeanalytics.googleapis.com}")
    private lateinit var analyticsBaseUrl: String

    @Value("\${platform.google.oauth-token-url:https://oauth2.googleapis.com}")
    private lateinit var oauthBaseUrl: String

    @Value("\${platform.google.client-id:}")
    private lateinit var clientId: String

    @Value("\${platform.google.client-secret:}")
    private lateinit var clientSecret: String

    @Value("\${platform.google.api-key:}")
    private lateinit var apiKey: String

    @Bean
    fun youTubeApi(): YouTubeApi {
        val restClient = RestClient.builder()
            .baseUrl(apiBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(YouTubeApi::class.java)
    }

    @Bean
    fun youTubeAnalyticsApi(): YouTubeAnalyticsApi {
        val restClient = RestClient.builder()
            .baseUrl(analyticsBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(YouTubeAnalyticsApi::class.java)
    }

    @Bean
    fun googleOAuthApi(): GoogleOAuthApi {
        val restClient = RestClient.builder()
            .baseUrl(oauthBaseUrl)
            .requestFactory(createRequestFactory())
            .build()
        val factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
        return factory.createClient(GoogleOAuthApi::class.java)
    }

    fun getClientId(): String = clientId
    fun getClientSecret(): String = clientSecret
    fun getApiKey(): String = apiKey

    private fun createRequestFactory(): SimpleClientHttpRequestFactory {
        return SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(Duration.ofSeconds(30))
        }
    }
}
