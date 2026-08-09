package com.ongo.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.common.ResData
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.core.env.Environment
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val accountFreezeFilter: AccountFreezeFilter,
    private val objectMapper: ObjectMapper,
    private val environment: Environment,
    @org.springframework.beans.factory.annotation.Value("\${cors.allowed-origins:*}")
    private val allowedOrigins: String,
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .headers { headers ->
                headers.contentSecurityPolicy { it.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self'") }
                headers.frameOptions { it.deny() }
            }
            .authorizeHttpRequests { auth ->
                val publicPaths = mutableListOf(
                    "/api/v1/auth/login/**",
                    // 소셜 로그인 CSRF용 state 발급 — 로그인 '전'에 호출되므로 인증을 요구하면 안 된다
                    "/api/v1/auth/*/state",
                    "/api/v1/auth/refresh",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/api-docs/**",
                    "/actuator/health",
                    "/ws/**",
                    "/api/v1/ai/demo/**",
                    "/api/v1/portone/webhook",
                    // Public API OAuth callback validates its own signed state.
                    "/api/v1/public/v1/social/callback",
                    "/public/v1/social/callback",
                )
                if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("dev", "local"))) {
                    publicPaths += "/api/v1/auth/dev-login"
                }
                auth
                    .requestMatchers(*publicPaths.toTypedArray()).permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/linkbio/public/*").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/linkbio/public/*/links/*/click").permitAll()
                    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            }
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint { _, response, _ ->
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.characterEncoding = "UTF-8"
                    val body = ResData<Nothing>(success = false, error = "인증이 필요합니다")
                    objectMapper.writeValue(response.outputStream, body)
                }
                exceptions.accessDeniedHandler { _, response, _ ->
                    response.status = HttpServletResponse.SC_FORBIDDEN
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.characterEncoding = "UTF-8"
                    val body = ResData<Nothing>(success = false, error = "접근 권한이 없습니다")
                    objectMapper.writeValue(response.outputStream, body)
                }
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            // 인증이 끝나야 대상 사용자를 알 수 있으므로 JWT 필터 뒤에 둔다.
            .addFilterAfter(accountFreezeFilter, JwtAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOriginPatterns = allowedOrigins.split(",").map { it.trim() }
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")
        configuration.allowedHeaders = listOf(
            "Authorization", "X-API-Key", "Content-Type", "Accept", "X-Requested-With", "Origin",
        )
        configuration.exposedHeaders = listOf("Location")
        configuration.allowCredentials = true
        configuration.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
