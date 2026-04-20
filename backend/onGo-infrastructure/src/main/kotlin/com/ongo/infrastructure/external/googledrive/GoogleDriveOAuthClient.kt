package com.ongo.infrastructure.external.googledrive

import com.ongo.infrastructure.external.googledrive.dto.TokenResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Google OAuth 2.0 client — 인가 URL 생성, 코드 교환, 토큰 갱신, 토큰 revoke 담당.
 *
 * 엔드포인트는 기본값이 Google 공식 URL이지만, 테스트(MockWebServer)에서 주입 가능하도록
 * 생성자 파라미터로 노출한다.
 */
@Component
class GoogleDriveOAuthClient(
    private val webClient: WebClient,
    private val props: GoogleDriveProperties,
    private val tokenEndpoint: String = "https://oauth2.googleapis.com/token",
    private val revokeEndpoint: String = "https://oauth2.googleapis.com/revoke",
) {
    /**
     * OAuth 인가 엔드포인트 URL 생성.
     * `access_type=offline` + `prompt=consent`로 refresh_token 재발급을 강제한다.
     */
    fun authUrl(state: String): String {
        val scopes = props.scopes.joinToString(" ")
        val params = listOf(
            "client_id" to props.clientId,
            "redirect_uri" to props.redirectUri,
            "response_type" to "code",
            "scope" to scopes,
            "access_type" to "offline",
            "prompt" to "consent",
            "state" to state,
        ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, StandardCharsets.UTF_8)}" }
        return "https://accounts.google.com/o/oauth2/v2/auth?$params"
    }

    /**
     * 인가 코드를 access/refresh token으로 교환.
     */
    fun exchangeCode(code: String): TokenResponse = try {
        webClient.post().uri(tokenEndpoint)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue(
                LinkedMultiValueMap<String, String>().apply {
                    add("code", code)
                    add("client_id", props.clientId)
                    add("client_secret", props.clientSecret)
                    add("redirect_uri", props.redirectUri)
                    add("grant_type", "authorization_code")
                },
            )
            .retrieve()
            .bodyToMono(TokenResponse::class.java)
            .block() ?: throw OAuthTokenExchangeException("empty response from token endpoint")
    } catch (e: WebClientResponseException) {
        throw OAuthTokenExchangeException("exchangeCode failed: ${e.statusCode} ${e.responseBodyAsString}", e)
    }

    /**
     * refresh_token으로 새 access_token 발급.
     * 응답에 refresh_token이 포함되지 않는 경우가 일반적 (기존 refresh_token 유지).
     */
    fun refresh(refreshToken: String): TokenResponse = try {
        webClient.post().uri(tokenEndpoint)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue(
                LinkedMultiValueMap<String, String>().apply {
                    add("refresh_token", refreshToken)
                    add("client_id", props.clientId)
                    add("client_secret", props.clientSecret)
                    add("grant_type", "refresh_token")
                },
            )
            .retrieve()
            .bodyToMono(TokenResponse::class.java)
            .block() ?: throw OAuthTokenExchangeException("empty response from refresh endpoint")
    } catch (e: WebClientResponseException) {
        throw OAuthTokenExchangeException("refresh failed: ${e.statusCode} ${e.responseBodyAsString}", e)
    }

    /**
     * access_token 또는 refresh_token을 revoke.
     * 이미 revoke된 토큰에 대해서는 Google이 400을 반환하는데, 멱등 처리로 true 반환한다.
     */
    fun revoke(token: String): Boolean = try {
        webClient.post().uri("$revokeEndpoint?token=${URLEncoder.encode(token, StandardCharsets.UTF_8)}")
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
        true
    } catch (e: WebClientResponseException) {
        // 이미 revoke된 토큰은 400을 반환 — 멱등 처리. 그 외 상태는 예외로 재전파해
        // 호출자(DisconnectContentSourceUseCase 등)가 에러 원인을 인지하도록 한다.
        if (e.statusCode.value() == 400) true
        else throw OAuthTokenExchangeException("revoke failed: ${e.statusCode}", e)
    }
}

class OAuthTokenExchangeException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
