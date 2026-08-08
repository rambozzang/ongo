package com.ongo.infrastructure.external.platform

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.client.RestClient

/**
 * Spring Boot 4의 기본 RestClient는 Jackson 3 변환기를 우선 등록한다.
 * 외부 플랫폼 DTO와 공통 ObjectMapper는 Jackson 2 + Kotlin 모듈을 사용하므로,
 * 플랫폼 API 경계에서는 이 설정을 강제해 응답이 운영에서만 깨지지 않게 한다.
 */
object PlatformRestClientSupport {
    fun builder(baseUrl: String): RestClient.Builder =
        RestClient.builder()
            .baseUrl(baseUrl)
            .messageConverters { converters ->
                converters.removeIf { it.javaClass.name.contains("Jackson") }
                converters.add(0, MappingJackson2HttpMessageConverter(jacksonObjectMapper()))
            }
}
