package com.ongo.domain.ugc.shorts

import java.time.Instant

/**
 * UGC 쇼츠 파이프라인 실행. 롱폼 영상 하나에 대응한다.
 */
data class PipelineRun(
    val id: Long = 0,
    val workspaceId: Long,
    val userId: Long,
    val sourceVideoId: Long,
    val templateId: Long? = null,
    /** Compose의 원클릭 쇼츠 작업인지. 수동 UGC 실행은 false다. */
    val autoSchedule: Boolean = false,
    val autoScheduleStartAt: Instant? = null,
    val autoScheduleIntervalHours: Int? = null,
    /** PLATFORM 또는 PLATFORM#channelId 형식의 게시 대상. */
    val autoSchedulePlatforms: List<String> = emptyList(),
    /** 요청 재전송 시 동일한 파이프라인 실행으로 접기 위한 클라이언트 키. */
    val idempotencyKey: String? = null,
    /** 키 재사용으로 다른 요청을 덮어쓰지 않도록 비교하는 요청 지문. */
    val requestHash: String? = null,
    /**
     * 실행 생성 시 서버가 **측정해 수락한** 원본 길이(ms).
     *
     * 전사 크레딧 산정의 근거다. 전사 원가는 길이에 정비례하는데 정액으로 매기면 긴 원본이
     * 그대로 손실이 된다.
     *
     * 생성 후 바뀌지 않는다. 재실행도 이 값을 다시 써야 첫 견적과 같은 금액이 나온다.
     * 다시 프로브하면 같은 실행이 두 번 다른 금액을 낼 수 있고, 그건 인용한 적 없는
     * 청구다.
     *
     * `null` 은 이 필드 도입 이전에 만들어진 실행이다. 소급 측정하지 않으며, 그 경우
     * 전사 크레딧은 종전대로 정액이다.
     *
     * 클라이언트가 보낸 값을 넣지 않는다 — 청구 근거를 청구받는 쪽이 정할 수는 없다.
     */
    val sourceDurationMs: Long? = null,
    val status: PipelineRunStatus = PipelineRunStatus.PENDING,
    val currentStage: PipelineStage? = null,
    val transcriptText: String? = null,
    /** REFRAME 산출 크롭 좌표. 게이트 재개 후 TEMPLATE 이 클립에 복사한다. */
    val cropJson: String? = null,
    val clipCount: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    /**
     * 파이프라인이 **처음** 실행된 시각. 리드타임의 시작점이다.
     *
     * `createdAt` 과 다르다 — 실행 생성과 첫 단계 시작 사이에는 큐 대기가 있고, 파일럿에서
     * 궁금한 것은 "우리가 언제부터 일을 시작했나"다. 재실행·재개가 덮어쓰면 리드타임이
     * 실제보다 짧게 보이므로 최초 값만 유지한다.
     */
    val startedAt: Instant? = null,
    /**
     * 첫 클립 렌더가 완료된 시각. 리드타임의 종점이자 납품 성공의 증거다.
     *
     * 별도 성공 이벤트를 두지 않는 이유가 이것이다 — 값이 있으면 납품된 것이고,
     * 없으면 아직 아니다.
     */
    val deliveredAt: Instant? = null,
    val version: Long = 0,
)
