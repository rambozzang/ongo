package com.ongo.application.ugc.shorts

import com.ongo.application.ugc.shorts.dto.ShortsPilotCandidatePage
import com.ongo.application.ugc.shorts.dto.ShortsPilotCandidateRow
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.ShortsPilotEventRepository
import com.ongo.domain.video.VideoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 파일럿 코호트에 넣을 **후보** 실행 목록.
 *
 * ## 왜 필요한가
 *
 * 등록 API 는 runId 를 받는데, 운영자가 그 값을 알아낼 곳이 화면에 없었다. DB 를 직접
 * 열거나 고객에게 물어야 했고, 그 과정에서 엉뚱한 실행을 코호트에 넣으면 파일럿 지표가
 * 조용히 오염된다. 되돌릴 방법도 없다 — 등록 이벤트는 append-only 다.
 *
 * ## 조회 횟수
 *
 * 등록 집합 1회 + 총수 1회 + 목록 1회 + 제목 일괄 1회, **총 4회로 고정**이다.
 * 페이지 크기나 실행 수에 비례해 늘지 않는다.
 *
 * ## 제외를 어디서 하는가
 *
 * SQL 에서 한다. 페이지만큼 읽어 온 뒤 애플리케이션에서 거르면 페이지마다 남는 개수가
 * 달라지고, 총수와도 어긋나 페이지 이동이 깨진다. 등록 집합은 **한 번만** 읽어
 * 목록·총수 두 질의에 같은 값을 넘긴다 — 사이에 다시 읽으면 두 질의의 기준이 갈라진다.
 */
@Service
class ShortsPilotCandidateUseCase(
    private val pipelineRunRepository: PipelineRunRepository,
    private val pilotEventRepository: ShortsPilotEventRepository,
    /** 제목 일괄 조회 전용. 영상 본문·URL 은 읽지도 내보내지도 않는다. */
    private val videoRepository: VideoRepository,
) {

    @Transactional(readOnly = true)
    fun candidates(page: Int, size: Int): ShortsPilotCandidatePage {
        val safePage = maxOf(page, 0)
        val safeSize = size.coerceIn(MIN_SIZE, MAX_SIZE)

        /*
         * 등록 집합을 여기서 한 번만 읽어 아래 두 질의에 그대로 넘긴다. 각 질의가 따로
         * 읽으면 그 사이 등록된 실행 때문에 총수와 목록의 기준이 달라진다.
         */
        val enrolledRunIds = pilotEventRepository.findEnrolledRunIds()

        val total = pipelineRunRepository.countRecentExcluding(enrolledRunIds)
        val runs = pipelineRunRepository.findRecentExcluding(
            excludedIds = enrolledRunIds,
            offset = safePage * safeSize,
            limit = safeSize,
        )

        /*
         * 제목은 실행마다 묻지 않는다. 실행 하나에 한 번씩 findById 를 돌리면 페이지
         * 크기만큼 조회가 나가고, 그 패턴은 파일럿이 끝난 뒤에도 남는다.
         */
        val titlesByVideoId = if (runs.isEmpty()) {
            emptyMap()
        } else {
            videoRepository.findByIds(runs.map { it.sourceVideoId })
                .mapNotNull { video -> video.id?.let { it to video.title } }
                .toMap()
        }

        return ShortsPilotCandidatePage(
            candidates = runs.map { run ->
                ShortsPilotCandidateRow(
                    runId = run.id,
                    status = run.status.name,
                    createdAt = run.createdAt,
                    // 영상이 지워졌거나 제목이 비어 있으면 null 이다. 빈 문자열로 바꾸면
                    // 화면이 "제목 없음"과 "제목이 공백"을 구분하지 못한다.
                    sourceVideoTitle = titlesByVideoId[run.sourceVideoId]?.takeIf { it.isNotBlank() },
                )
            },
            total = total,
            page = safePage,
            size = safeSize,
        )
    }

    private companion object {
        const val MIN_SIZE = 1

        /** 한 번에 200건을 넘기지 않는다. 운영자가 훑을 수 있는 양이 아니고 응답만 커진다. */
        const val MAX_SIZE = 200
    }
}
