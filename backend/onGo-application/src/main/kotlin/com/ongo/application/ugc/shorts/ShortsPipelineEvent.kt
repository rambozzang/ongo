package com.ongo.application.ugc.shorts

import com.ongo.domain.ugc.shorts.PipelineStage
import java.time.Instant

/**
 * 쇼츠 파이프라인 실행 이벤트. 커밋 후 @Async 리스너가 오케스트레이터를 돌린다.
 */
data class ShortsPipelineEvent(
    val runId: Long,
    val fromStage: PipelineStage,
    val scheduleStartAt: Instant? = null,
    val scheduleIntervalHours: Int? = null,
    val platforms: List<String> = emptyList(),
)

/**
 * 클립에 **접근 가능한 완성 영상이 연결됐다**.
 *
 * ## 무엇을 뜻하고 무엇을 뜻하지 않는가
 *
 * 뜻하는 것: 서버가 `videos` 레코드를 만들고 클립을 `RENDERED` 로 연결해, 고객이 이제
 * 그 결과물을 받아갈 수 있는 상태가 됐다.
 *
 * **뜻하지 않는 것: 고객이 그것을 열람하거나 내려받았다.** 열람·다운로드 계측은 아직
 * 없다. 이 이벤트를 "고객이 결과를 봤다"로 읽으면 안 된다.
 *
 * ## 왜 "서버가 렌더했다"가 아닌가
 *
 * 완성 영상이 붙는 경로가 둘이다. 서버 렌더([RenderedClipPersister])와, 서버 렌더를
 * 쓸 수 없는 환경에서 외부 완성본을 붙이는 보완 경로
 * ([ShortsPipelineUseCase.attachRenderedVideo]). 서버 렌더만 세면 후자로 결과를 받은
 * 고객이 통째로 빠져 지표가 과소집계된다. 그래서 사건의 정의를 **연결 성립**에 둔다.
 *
 * ## 담지 않는 것
 *
 * 이메일·URL·스토리지 키·파일명·렌더 spec 같은 값은 담지 않는다. 소비자는 활동 로그이고,
 * 그건 운영자가 SQL 로 훑는 테이블이다. 필요한 상세는 `ugc_shorts_clips` 를 조인해
 * 읽으면 되고 그쪽이 단일 진실이다.
 */
data class ShortsClipAvailableEvent(
    val userId: Long,
    val runId: Long,
    val clipId: Long,
)
