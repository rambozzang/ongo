package com.ongo.infrastructure.external.googledrive

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Google Drive 영상 임포트 기능 설정.
 *
 * - `client-id`, `client-secret`, `redirect-uri`는 OAuth 2.0 인증에 사용됨.
 * - `oauth-state-secret`는 OAuth state 파라미터 HMAC 서명에 사용 (CSRF 방지).
 *   현재는 Google Drive 전용이지만, 추후 다른 콘텐츠 소스 제공자가 추가되면
 *   `ongo.content-source.oauth-state-secret`로 상위 이동 고려.
 * - 프로덕션 배포 전 모든 값을 환경 변수로 주입 필수.
 * - 개발 환경에서는 env 변수 없이도 앱이 기동되도록 빈 문자열 기본값을 허용.
 */
@ConfigurationProperties(prefix = "ongo.content-source.google-drive")
data class GoogleDriveProperties(
    val clientId: String = "",
    val clientSecret: String = "",
    val redirectUri: String = "",
    val scopes: List<String> = listOf("https://www.googleapis.com/auth/drive.readonly"),
    val oauthStateSecret: String = "change-me-32chars-minimum-local-dev-only",
    val import: Import = Import(),
) {
    data class Import(
        val downloadBufferSize: Int = 8 * 1024 * 1024,
        val progressUpdateIntervalMs: Long = 2000,
        val maxConcurrentPerUser: Int = 2,
        val timeoutMinutes: Long = 60,
    )
}
