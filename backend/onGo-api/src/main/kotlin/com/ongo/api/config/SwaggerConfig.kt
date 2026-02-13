package com.ongo.api.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {

    @Bean
    fun openAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("onGo API")
                .description("크리에이터 멀티 플랫폼 관리 SaaS - onGo 백엔드 API 문서")
                .version("1.0.0")
                .contact(
                    Contact()
                        .name("onGo 개발팀")
                        .email("dev@ongo.kr")
                )
        )
        .addSecurityItem(SecurityRequirement().addList("Bearer 인증"))
        .components(
            Components()
                .addSecuritySchemes(
                    "Bearer 인증",
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT Access Token을 입력하세요")
                )
        )
        .tags(
            listOf(
                Tag().name("인증").description("소셜 로그인 (Google/Kakao), 토큰 갱신, 로그아웃"),
                Tag().name("채널 관리").description("플랫폼 채널 연동/해제/갱신 (YouTube, TikTok, Instagram, Naver Clip)"),
                Tag().name("영상 관리").description("영상 업로드 초기화, 멀티플랫폼 게시, 목록/상세 조회, 수정, 삭제"),
                Tag().name("예약 관리").description("영상 예약 게시 등록/수정/취소, 캘린더 조회"),
                Tag().name("분석").description("대시보드 KPI, 트렌드, 영상별 분석, 히트맵, 인기 영상, 플랫폼 비교"),
                Tag().name("AI 도구").description("AI 기반 메타데이터 생성, 해시태그, STT, 대본 분석, 댓글 답변, 일정 추천, 아이디어 생성, 리포트"),
                Tag().name("크레딧").description("AI 크레딧 잔액 조회, 사용 내역, 충전, 패키지 목록"),
                Tag().name("구독").description("구독 현황 조회, 플랜 변경, 구독 취소, 플랜 비교"),
                Tag().name("결제").description("결제 내역 조회, PG사 웹훅 처리 (Toss Payments)"),
                Tag().name("파일 업로드").description("Tus 프로토콜 기반 대용량 파일 이어받기 업로드"),
            )
        )
}
