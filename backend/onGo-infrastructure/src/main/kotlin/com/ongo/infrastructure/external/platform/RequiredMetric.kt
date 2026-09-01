package com.ongo.infrastructure.external.platform

import com.ongo.common.exception.PlatformApiException

/**
 * **플랫폼이 지원한다고 선언한 지표**의 응답값을 읽는 공통 검증.
 *
 * ## 무엇이 거짓이었나
 *
 * 각 클라이언트가 `viewCount ?: 0` 처럼 응답 필드가 없을 때 `0` 을 넣었다. 그 값은
 * `AnalyticsSyncScheduler` 가 `analytics_daily` 에 그대로 저장하는데, 그 컬럼들은 전부
 * `NOT NULL DEFAULT 0` 이라 **"응답에 없었다" 와 "실제로 0 이었다" 가 같은 행이 된다.**
 * 한 번 0 으로 굳으면 이후 어떤 계층도 두 사실을 되살릴 수 없다.
 *
 * 저장 계층이 `null` 을 담을 수 없으므로, 정직해질 수 있는 유일한 지점은 **저장 전에
 * 멈추는 것**이다. 여기서 [PlatformApiException] 을 던지면 스케줄러가 그 날짜를 쓰지
 * 않고, 해당 날짜는 `existingDates` 에 남지 않아 **다음 실행에서 다시 시도된다**
 * (`AnalyticsSyncWindow.datesToSync`).
 *
 * ## 무엇에 쓰면 안 되나
 *
 * **플랫폼이 그 지표를 아예 주지 않는 자리에는 쓰지 않는다.** 예를 들어 대부분의
 * 플랫폼은 시청 시간·구독 증가를 제공하지 않아 클라이언트가 `0` 을 하드코딩하는데,
 * 그것은 이미 [com.ongo.application.analytics.PlatformMetricAvailability] 가 미수집으로
 * 선언해 소비 단계에서 숨긴다. 그 자리에 예외를 던지면 **정상 응답이 매번 실패한다.**
 *
 * 요약하면 이 검증의 대상은 "지원한다고 선언했는데 이번 응답에 값이 없는" 경우뿐이다.
 *
 * ## 실측 0 은 반드시 통과한다
 *
 * 판정 기준은 **값의 존재 여부**이지 크기가 아니다. 응답이 `0` 을 명시하면 그것은 관측이므로
 * 그대로 통과한다.
 */

/**
 * 응답에 값이 있어야 하는 지표를 읽는다.
 *
 * @param metric 진단용 지표 이름. 스케줄러 경고 로그에 그대로 남아 어떤 지표가 빠졌는지
 *   운영에서 확인할 수 있어야 한다.
 * @throws PlatformApiException 값이 `null`(응답 필드 누락) 일 때.
 */
fun <T : Any> T?.requireMetric(platform: String, metric: String): T =
    this ?: throw PlatformApiException(
        platform,
        "분석 응답에 '$metric' 값이 없습니다. 잘못된 0 을 저장하지 않도록 이 날짜를 건너뜁니다.",
    )

/**
 * 문자열로 오는 지표를 `Long` 으로 읽는다. **숫자로 파싱되지 않으면 누락과 같이 취급한다.**
 *
 * YouTube Analytics 는 행(`rows`)을 문자열 배열로 준다. `toLongOrNull() ?: 0` 은 빈 칸·
 * 예상치 못한 형식을 전부 `0` 으로 만들었다.
 */
fun String?.requireLongMetric(platform: String, metric: String): Long =
    this?.toLongOrNull().requireMetric(platform, metric)

/** [requireLongMetric] 의 `Int` 판. */
fun String?.requireIntMetric(platform: String, metric: String): Int =
    this?.toIntOrNull().requireMetric(platform, metric)
