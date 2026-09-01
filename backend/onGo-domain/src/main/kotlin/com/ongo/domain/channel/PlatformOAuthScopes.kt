package com.ongo.domain.channel

/**
 * 플랫폼 연동에 요청하는 OAuth scope.
 *
 * **한 곳에서만 정의한다.** 인가 URL 을 만드는 곳이 둘이라(인증된 UI 흐름과 공개 API
 * 흐름) 각자 문자열을 들고 있으면, 한쪽만 고쳤을 때 어떤 사용자는 수익 권한에 동의하고
 * 어떤 사용자는 못 하는 상태가 조용히 생긴다.
 */
object PlatformOAuthScopes {

    /**
     * YouTube 업로드·관리 + 수익 분석.
     *
     * `yt-analytics-monetary.readonly` 는 `estimatedRevenue` 를 읽기 위한 별도 권한이다.
     * 광범위한 `auth/youtube` 만으로는 금전 지표를 조회할 수 없다.
     *
     * **이 scope 를 더해도 기존에 연결된 채널은 소급 적용되지 않는다.** Google 은 이미
     * 발급된 refresh token 의 권한을 바꾸지 않는다. 그래서 기존 사용자는 채널을 다시
     * 연동해 동의해야 하고, 그 전까지 수익 조회는 401/403 이 된다. 그 상태를
     * `RevenueStatus.PERMISSION_REQUIRED` 로 저장해 일반 분석과 분리한다.
     */
    const val YOUTUBE =
        "https://www.googleapis.com/auth/youtube " +
            "https://www.googleapis.com/auth/yt-analytics-monetary.readonly"
}
