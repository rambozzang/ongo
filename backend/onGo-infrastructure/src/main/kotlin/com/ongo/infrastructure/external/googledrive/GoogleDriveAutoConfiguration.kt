package com.ongo.infrastructure.external.googledrive

import io.netty.channel.ChannelOption
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
@EnableConfigurationProperties(GoogleDriveProperties::class)
class GoogleDriveAutoConfiguration {

    /**
     * Google Drive 관련 HTTP 호출용 WebClient.
     * 애플리케이션에 이미 WebClient 빈이 존재하면 재사용한다.
     */
    @Bean
    @ConditionalOnMissingBean(WebClient::class)
    fun googleDriveWebClient(): WebClient {
        val httpClient = HttpClient.create()
            .responseTimeout(Duration.ofSeconds(30))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }

    /**
     * OAuth state 발급/검증 매니저.
     * secret은 [GoogleDriveProperties.oauthStateSecret]에서 주입.
     *
     * 이 secret 은 소셜 로그인 state(`verifyAnonymous`)와 Drive 연동 state(`verify`)를
     * 모두 서명한다. 기본값은 저장소에 공개되어 있으므로, 그대로 운영에 나가면 누구나
     * state 를 위조해 CSRF 방어를 우회하고 `verify()` 가 돌려주는 userId 까지 임의로
     * 지정할 수 있다. 그래서 prod 에서는 조용히 기동시키지 않고 즉시 실패시킨다.
     */
    @Bean
    fun oauthStateManager(
        props: GoogleDriveProperties,
        environment: Environment,
        redisConnectionFactory: ObjectProvider<RedisConnectionFactory>,
    ): OAuthStateManager {
        val isProd = environment.activeProfiles.contains("prod")
        require(!(isProd && props.oauthStateSecret == GoogleDriveProperties.DEV_ONLY_STATE_SECRET)) {
            "OAUTH_STATE_SECRET 환경변수가 설정되지 않았습니다. " +
                "운영 환경에서는 기본 secret 을 사용할 수 없습니다(32자 이상의 임의 값을 주입하세요)."
        }
        val stateStore = redisConnectionFactory.ifAvailable?.let(::RedisOAuthStateStore)
            ?: InMemoryOAuthStateStore()
        return OAuthStateManager(secret = props.oauthStateSecret, ttlSeconds = 300, stateStore = stateStore)
    }
}
