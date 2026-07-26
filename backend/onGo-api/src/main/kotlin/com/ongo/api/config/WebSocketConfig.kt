package com.ongo.api.config

import com.ongo.domain.auth.AuthTokenPort
import com.ongo.domain.auth.TokenBlacklistPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessageDeliveryException
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration
import java.security.Principal

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    @Value("\${cors.allowed-origins}") private val allowedOrigins: String,
    private val authTokenPort: AuthTokenPort,
    private val tokenBlacklist: TokenBlacklistPort,
) : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config.enableSimpleBroker("/topic", "/queue")
        config.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        val origins = allowedOrigins.split(",").map { it.trim() }.toTypedArray()
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(*origins)
            .withSockJS()
    }

    override fun configureWebSocketTransport(registration: WebSocketTransportRegistration) {
        registration.setMessageSizeLimit(128 * 1024)
    }

    /**
     * STOMP 레이어 인증.
     *
     * HTTP 필터 체인은 핸드셰이크를 위해 ws 엔드포인트를 permitAll 로 열어두므로 실제
     * 인증은 여기서 한다. 이 인터셉터가 없으면 SimpleBroker 는 목적지를 단순 문자열로만
     * 다루기 때문에 누구나 `/queue/user/{임의 id}` 를 구독해 타인의 알림을 읽을 수 있다.
     */
    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(AuthChannelInterceptor(authTokenPort, tokenBlacklist))
    }
}

/** STOMP 세션에 붙는 사용자 식별자. 이름은 userId 문자열이다. */
private class StompUserPrincipal(private val userId: String) : Principal {
    override fun getName(): String = userId
}

private class AuthChannelInterceptor(
    private val authTokenPort: AuthTokenPort,
    private val tokenBlacklist: TokenBlacklistPort,
) : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = StompHeaderAccessor.wrap(message)

        when (accessor.command) {
            StompCommand.CONNECT -> authenticate(accessor)
            StompCommand.SUBSCRIBE -> authorizeSubscription(accessor)
            // 서버에 @MessageMapping 핸들러가 하나도 없다. 클라이언트가 브로커로 직접
            // 메시지를 보낼 이유가 없으므로 전면 차단한다. 허용하면 SimpleBroker 가
            // 그대로 릴레이해 타인 화면에 가짜 알림을 주입할 수 있다.
            StompCommand.SEND -> throw MessageDeliveryException("클라이언트 메시지 전송은 허용되지 않습니다")
            else -> Unit
        }
        return message
    }

    private fun authenticate(accessor: StompHeaderAccessor) {
        val token = accessor.getFirstNativeHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer ") }
            ?.substring(7)
            ?.trim()
            ?: throw MessageDeliveryException("인증 토큰이 없습니다")

        if (!authTokenPort.validateToken(token)) {
            throw MessageDeliveryException("유효하지 않은 토큰입니다")
        }
        // SSE 전용 토큰이나 refresh 토큰으로 소켓을 열 수 없도록 타입을 고정한다.
        if (authTokenPort.getTokenType(token) != "access") {
            throw MessageDeliveryException("허용되지 않은 토큰 타입입니다")
        }
        val jti = authTokenPort.getTokenJti(token) ?: token.hashCode().toString()
        if (tokenBlacklist.isBlacklisted(jti)) {
            throw MessageDeliveryException("무효화된 토큰입니다")
        }

        accessor.user = StompUserPrincipal(authTokenPort.getUserIdFromToken(token).toString())
    }

    private fun authorizeSubscription(accessor: StompHeaderAccessor) {
        val userId = accessor.user?.name
            ?: throw MessageDeliveryException("인증되지 않은 세션입니다")
        val destination = accessor.destination
            ?: throw MessageDeliveryException("구독 대상이 없습니다")

        // 현재 서버가 발행하는 목적지는 /queue/user/{userId} 와 /topic/team/{teamId} 뿐이다.
        // 팀 브로드캐스트는 멤버십 검증 수단이 아직 없고 구독하는 클라이언트도 없으므로
        // 열어두지 않는다. 필요해지면 팀 멤버십 조회를 붙여 함께 허용할 것.
        if (destination != "/queue/user/$userId") {
            throw MessageDeliveryException("구독할 수 없는 대상입니다: $destination")
        }
    }
}
