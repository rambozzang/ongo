package com.ongo.domain.accountdeletion

/**
 * 동결을 우회할 수 있는 시스템 경로 목록.
 *
 * [WriteOrigin.SYSTEM_RECONCILIATION] 은 강력하다. 아무 데서나 쓰면 동결이 무의미해진다.
 * 그래서 **여기에 근거와 함께 등록된 경로만** 쓸 수 있게 한다.
 * `UserFkPolicyRegistry` 와 같은 방식이다 — 우회는 기본값이 아니라 명시적 결정이어야 한다.
 *
 * 등록 기준은 하나다. **그 작업을 멈추면 다른 정합성이 깨지는가.**
 * "편해서"나 "동결을 신경 쓰기 귀찮아서"는 근거가 아니다.
 */
object SystemWritePathRegistry {

    data class Entry(
        /** 경로 식별자. 보통 클래스명이다. */
        val path: String,
        /** 왜 동결 중에도 진행해야 하는가. */
        val rationale: String,
    )

    val entries: List<Entry> = listOf(
        Entry(
            "WebhookRetryScheduler",
            "결제 웹훅 재처리. 멈추면 결제 상태·환불·크레딧 원장이 어긋난다. " +
                "동결은 사용자 쓰기를 막는 장치이지 결제 정합성을 멈추는 장치가 아니다",
        ),
        Entry(
            "PortOnePaymentService",
            "결제 웹훅 수신 처리. 외부 PG 가 보내는 사실을 기록하는 것이라 " +
                "동결 여부와 무관하게 반영해야 한다",
        ),
        Entry(
            "PaddleWebhookService",
            "레거시 결제 웹훅 처리. 위와 같은 이유",
        ),
        Entry(
            "BillingScheduler",
            "구독 상태 DB 정합화. 외부 게이트웨이 호출이 없다. " +
                "다만 새 청구를 만들지 않는지 확인이 필요하다",
        ),
        Entry(
            "CreditScheduler",
            "무료 크레딧 리셋. 일괄로 건너뛰면 동결이 길어졌다 풀렸을 때 권리가 누락된다. " +
                "제품 정책 확정 전 기본값으로 계속 처리한다",
        ),
        Entry(
            "PartitionMaintenanceScheduler",
            "파티션 생성. 사용자 데이터를 쓰지 않는 스키마 유지보수다",
        ),
        Entry(
            "RefreshTokenCleanupScheduler",
            "만료 토큰 정리. 사용자 콘텐츠가 아니라 세션 위생이다",
        ),
    )

    private val byPath = entries.associateBy { it.path }

    fun isRegistered(path: String): Boolean = byPath.containsKey(path)

    fun rationaleFor(path: String): String? = byPath[path]?.rationale
}
