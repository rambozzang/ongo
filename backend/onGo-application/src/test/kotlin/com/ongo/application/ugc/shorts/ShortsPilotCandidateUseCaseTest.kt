package com.ongo.application.ugc.shorts

import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.PipelineRunStatus
import com.ongo.domain.ugc.shorts.ShortsPilotEventRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 파일럿 후보 목록.
 *
 * 등록 API 는 runId 를 받는데 운영자가 그 값을 알아낼 곳이 화면에 없었다. 이 유스케이스가
 * 그 공백을 메운다. 여기서 고정하는 것은 세 가지다.
 *
 *  1. **이미 등록된 실행은 나오지 않는다** — 코호트에 두 번 넣으려다 시간을 쓰지 않게
 *  2. **조회 횟수가 페이지 크기에 비례하지 않는다** — 목록 화면의 N+1 은 조용히 자란다
 *  3. **고객 정보를 싣지 않는다** — 등록 화면은 콘텐츠 열람 화면이 아니다
 */
class ShortsPilotCandidateUseCaseTest {

    private val pipelineRunRepository = mockk<PipelineRunRepository>()
    private val pilotEventRepository = mockk<ShortsPilotEventRepository>()
    private val videoRepository = mockk<VideoRepository>()

    private val useCase = ShortsPilotCandidateUseCase(
        pipelineRunRepository = pipelineRunRepository,
        pilotEventRepository = pilotEventRepository,
        videoRepository = videoRepository,
    )

    private fun run(id: Long, videoId: Long = id * 10) = PipelineRun(
        id = id,
        workspaceId = 1L,
        userId = 2L,
        sourceVideoId = videoId,
        status = PipelineRunStatus.COMPLETED,
        createdAt = Instant.parse("2026-08-20T01:00:00Z"),
    )

    // Video.title 은 non-null 이다. "영상이 없다"는 목록에서 빼서 표현한다.
    private fun video(id: Long, title: String) = mockk<Video>(relaxed = true).also {
        every { it.id } returns id
        every { it.title } returns title
    }

    private fun stub(
        enrolled: List<Long> = emptyList(),
        runs: List<PipelineRun> = emptyList(),
        total: Long = runs.size.toLong(),
        videos: List<Video> = emptyList(),
    ) {
        every { pilotEventRepository.findEnrolledRunIds() } returns enrolled
        every { pipelineRunRepository.countRecentExcluding(any()) } returns total
        every { pipelineRunRepository.findRecentExcluding(any(), any(), any()) } returns runs
        every { videoRepository.findByIds(any()) } returns videos
    }

    /* ---- 제외 ---- */

    /**
     * 제외 목록을 저장소에 **그대로 넘기는지** 본다. 유스케이스가 받은 뒤 애플리케이션에서
     * 거르면 페이지마다 남는 개수가 달라져 페이지 이동이 깨진다.
     */
    @Test
    fun `이미 등록된 실행 ID를 제외 조건으로 넘긴다`() {
        stub(enrolled = listOf(7L, 9L), runs = listOf(run(11L)), videos = listOf(video(110L, "제목")))

        useCase.candidates(page = 0, size = 20)

        verify(exactly = 1) { pipelineRunRepository.findRecentExcluding(listOf(7L, 9L), 0, 20) }
    }

    /**
     * 총수와 목록이 **같은 제외 집합**을 봐야 한다. 기준이 갈라지면 마지막 페이지가 비어
     * 보이고 운영자는 "왜 안 나오지"를 확인하느라 시간을 쓴다.
     */
    @Test
    fun `총수와 목록이 같은 제외 집합을 쓴다`() {
        stub(enrolled = listOf(7L), runs = listOf(run(11L)), total = 42, videos = listOf(video(110L, "제목")))

        val result = useCase.candidates(page = 0, size = 20)

        verify(exactly = 1) { pipelineRunRepository.countRecentExcluding(listOf(7L)) }
        verify(exactly = 1) { pipelineRunRepository.findRecentExcluding(listOf(7L), 0, 20) }
        assertEquals(42, result.total)
    }

    /* ---- N+1 ---- */

    /**
     * 등록 집합을 두 번 읽으면 그 사이 등록된 실행 때문에 총수와 목록의 기준이 달라진다.
     * 횟수 자체가 계약이다.
     */
    @Test
    fun `등록 집합은 한 번만 읽는다`() {
        stub(runs = listOf(run(1L), run(2L), run(3L)), videos = listOf(video(10L, "가"), video(20L, "나"), video(30L, "다")))

        useCase.candidates(page = 0, size = 20)

        verify(exactly = 1) { pilotEventRepository.findEnrolledRunIds() }
    }

    /** 제목을 실행마다 물으면 페이지 크기만큼 조회가 나간다. */
    @Test
    fun `제목은 실행 수와 무관하게 한 번에 조회한다`() {
        stub(
            runs = listOf(run(1L), run(2L), run(3L)),
            videos = listOf(video(10L, "가"), video(20L, "나"), video(30L, "다")),
        )

        useCase.candidates(page = 0, size = 20)

        verify(exactly = 1) { videoRepository.findByIds(listOf(10L, 20L, 30L)) }
        verify(exactly = 0) { videoRepository.findById(any()) }
    }

    /** 후보가 없으면 제목을 물을 대상도 없다. 빈 IN 질의를 던지지 않는다. */
    @Test
    fun `후보가 없으면 제목을 조회하지 않는다`() {
        stub(runs = emptyList(), total = 0)

        val result = useCase.candidates(page = 0, size = 20)

        assertTrue(result.candidates.isEmpty())
        assertEquals(0, result.total)
        verify(exactly = 0) { videoRepository.findByIds(any()) }
    }

    /* ---- 응답 내용 ---- */

    @Test
    fun `runId 상태 생성시각 제목만 담는다`() {
        stub(runs = listOf(run(11L)), videos = listOf(video(110L, "여름 브이로그")))

        val row = useCase.candidates(page = 0, size = 20).candidates.single()

        assertEquals(11L, row.runId)
        assertEquals("COMPLETED", row.status)
        assertEquals(Instant.parse("2026-08-20T01:00:00Z"), row.createdAt)
        assertEquals("여름 브이로그", row.sourceVideoTitle)
    }

    /** 영상이 지워졌으면 제목을 지어내지 않는다. */
    @Test
    fun `원본 영상이 없으면 제목은 null이다`() {
        stub(runs = listOf(run(11L)), videos = emptyList())

        assertNull(useCase.candidates(page = 0, size = 20).candidates.single().sourceVideoTitle)
    }

    /**
     * 공백뿐인 제목은 null 로 내린다. 빈 문자열을 그대로 내리면 화면이 "제목 없음"과
     * "제목이 공백"을 구분하려 들고, 그 구분은 운영자에게 아무 의미가 없다.
     */
    @Test
    fun `공백뿐인 제목은 null로 내린다`() {
        stub(runs = listOf(run(11L)), videos = listOf(video(110L, "   ")))

        assertNull(useCase.candidates(page = 0, size = 20).candidates.single().sourceVideoTitle)
    }

    /* ---- 페이지 경계 ---- */

    @Test
    fun `음수 페이지는 첫 페이지로 본다`() {
        stub(runs = emptyList(), total = 0)

        val result = useCase.candidates(page = -3, size = 20)

        assertEquals(0, result.page)
        verify { pipelineRunRepository.findRecentExcluding(any(), 0, 20) }
    }

    @Test
    fun `크기 0은 최소값으로 올린다`() {
        stub(runs = emptyList(), total = 0)

        val result = useCase.candidates(page = 0, size = 0)

        assertEquals(1, result.size)
    }

    /** 상한이 없으면 한 번에 수천 건을 끌어와 응답만 커진다. */
    @Test
    fun `크기 상한을 넘기면 200으로 자른다`() {
        stub(runs = emptyList(), total = 0)

        val result = useCase.candidates(page = 0, size = 5_000)

        assertEquals(200, result.size)
        verify { pipelineRunRepository.findRecentExcluding(any(), 0, 200) }
    }

    /** offset 은 잘라낸 크기로 계산해야 한다. 요청값으로 계산하면 페이지가 건너뛴다. */
    @Test
    fun `offset은 보정된 크기로 계산한다`() {
        stub(runs = emptyList(), total = 0)

        useCase.candidates(page = 2, size = 5_000)

        verify { pipelineRunRepository.findRecentExcluding(any(), 400, 200) }
    }
}
