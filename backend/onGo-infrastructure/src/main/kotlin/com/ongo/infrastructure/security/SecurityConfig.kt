package com.ongo.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.common.ResData
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
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
    private val objectMapper: ObjectMapper,
    private val environment: Environment
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
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/dev-login", // 관리자 초기 계정 생성용 (모든 프로필 허용)
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/api-docs/**",
                    "/actuator/health",
                    "/ws/**",
                    "/api/v1/ai/demo/**",
                    "/api/v1/paddle/webhooks",
                )
                auth
                    .requestMatchers(*publicPaths.toTypedArray()).permitAll()
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

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOriginPatterns = listOf("*") // allowedOrigins 대신 패턴 사용 (allowCredentials 지원)
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")
        configuration.allowedHeaders = listOf(
            "Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin",
            "Tus-Resumable", "Upload-Length", "Upload-Offset", "Upload-Metadata",
        )
        configuration.exposedHeaders = listOf(
            "Location", "Upload-Offset", "Upload-Length", "Tus-Resumable",
            "Tus-Version", "Tus-Extension", "Tus-Max-Size",
        )
        configuration.allowCredentials = true
        configuration.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
