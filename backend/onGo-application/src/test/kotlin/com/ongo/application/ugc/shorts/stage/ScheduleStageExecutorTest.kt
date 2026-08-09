package com.ongo.application.ugc.shorts.stage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ugc.shorts.ShortsPublishAdapter
import com.ongo.application.ugc.shorts.ShortsPublishRequest
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.publishing.PlatformPublishOutcome
import com.ongo.domain.ugc.shorts.ClipPublication
import com.ongo.domain.ugc.shorts.ClipPublicationRepository
import com.ongo.domain.ugc.shorts.ClipPublicationStatus
import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.ShortsClip
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ScheduleStageExecutor — 예약 시각 계산과 기존 게시 흐름 위임 검증.
 *
 * 플랫폼을 비워 보내면 게시 없이 예약 시각만 확정하고,
 * 플랫폼을 지정하면 렌더 영상이 연결된 클립만 실제 게시로 넘어간다.
 */
class ScheduleStageExecutorTest {

    private val publishAdapter = mockk<ShortsPublishAdapter>()
    private val publicationRepository = mockk<ClipPublicationRepository>(relaxed = true)
    private val executor = ScheduleStageExecutor(publishAdapter, publicationRepository)
    private val mapper = jacksonObjectMapper()

    private val startAt: Instant = Instant.parse("2026-03-01T09:00:00Z")

    private fun clip(
        id: Long,
        seq: Int,
        status: ClipStatus = ClipStatus.RENDER_READY,
        renderedVideoId: Long? = null,
    ) = ShortsClip(
        id = id,
        runId = 1,
        seq = seq,
        startMs = (seq - 1) * 15000L,
        endMs = seq * 15000L,
        status = status,
        renderedVideoId = renderedVideoId,
    )

    private fun noPriorPublication() {
        every { publicationRepository.findByClipIdAndPlatform(any(), any()) } returns null
    }

    // ---- 예약 시각 계산 규칙 ----

    @Test
    fun `예약 파라미터가 없으면 SHORTS_RUN_INVALID_STATE`() {
        val ex = assertFailsWithBusiness {
            executor.execute(stageContext(clips = listOf(clip(11, 1)), schedule = null))
        }
        assertEquals("SHORTS_RUN_INVALID_STATE", ex.code)
    }

    @Test
    fun `예약 대상 클립이 없으면 SHORTS_RUN_INVALID_STATE`() {
        val ex = assertFailsWithBusiness {
            executor.execute(
                stageContext(
                    clips = listOf(clip(11, 1, ClipStatus.DISCARDED)),
                    schedule = ScheduleParams(startAt, 6, listOf("YOUTUBE")),
                ),
            )
        }
        assertEquals("SHORTS_RUN_INVALID_STATE", ex.code)
    }

    @Test
    fun `플랫폼을 비우면 게시 없이 예약 시각만 확정한다`() {
        val clips = listOf(clip(11, 1), clip(12, 2), clip(13, 3))

        val output = executor.execute(
            stageContext(clips = clips, schedule = ScheduleParams(startAt, 6, emptyList())),
        )

        assertEquals(
            mapOf(
                11L to startAt,
                12L to startAt.plusSeconds(6 * 3600L),
                13L to startAt.plusSeconds(12 * 3600L),
            ),
            output.scheduledAts,
        )
        verify(exactly = 0) { publishAdapter.publishAll(any(), any(), any()) }
    }

    @Test
    fun `DISCARDED 클립은 예약에서 빠지고 나머지가 seq 순으로 번호를 받는다`() {
        // 가운데 클립이 폐기되면 마지막 클립이 두 번째 슬롯을 받는다
        val clips = listOf(clip(11, 1), clip(12, 2, ClipStatus.DISCARDED), clip(13, 3))

        val output = executor.execute(
            stageContext(clips = clips, schedule = ScheduleParams(startAt, 6, emptyList())),
        )

        assertEquals(
            mapOf(11L to startAt, 13L to startAt.plusSeconds(6 * 3600L)),
            output.scheduledAts,
        )
    }

    // ---- 게시 위임 ----

    @Test
    fun `렌더 영상이 연결된 클립은 예약 시각과 함께 게시로 넘어간다`() {
        noPriorPublication()
        val requests = slot<List<ShortsPublishRequest>>()
        every { publishAdapter.publishAll(any(), any(), capture(requests)) } returns listOf(
            PlatformPublishOutcome("YOUTUBE", videoUploadId = 700L, status = "SCHEDULED", errorMessage = null),
        )

        val output = executor.execute(
            stageContext(
                clips = listOf(clip(11, 1, renderedVideoId = 500L)),
                schedule = ScheduleParams(startAt, 6, listOf("YOUTUBE")),
            ),
        )

        verify { publishAdapter.publishAll(userId = 1, videoId = 500L, requests = any()) }
        assertEquals("YOUTUBE", requests.captured.single().platformName)
        assertEquals(startAt, requests.captured.single().scheduledAt)
        assertEquals(mapOf(11L to startAt), output.scheduledAts)
    }

    @Test
    fun `같은 플랫폼의 두 계정은 쇼츠 게시 대상과 중복 방지를 계정별로 분리한다`() {
        noPriorPublication()
        val requests = slot<List<ShortsPublishRequest>>()
        every { publishAdapter.publishAll(any(), any(), capture(requests)) } returns listOf(
            PlatformPublishOutcome("YOUTUBE#101", videoUploadId = 701L, status = "SCHEDULED"),
            PlatformPublishOutcome("YOUTUBE#102", videoUploadId = 702L, status = "SCHEDULED"),
        )

        val output = executor.execute(
            stageContext(
                clips = listOf(clip(11, 1, renderedVideoId = 500L)),
                schedule = ScheduleParams(startAt, 6, listOf("YOUTUBE#101", "YOUTUBE#102")),
            ),
        )

        assertEquals(listOf(101L, 102L), requests.captured.map { it.channelId })
        assertEquals(ScheduleOutcome.SUCCESS, output.scheduleOutcome)
    }

    @Test
    fun `렌더 영상이 없으면 게시하지 않고 SKIPPED 로 남긴다`() {
        noPriorPublication()
        val saved = slot<ClipPublication>()
        every { publicationRepository.save(capture(saved)) } answers { firstArg() }

        val output = executor.execute(
            stageContext(
                clips = listOf(clip(11, 1, renderedVideoId = null)),
                schedule = ScheduleParams(startAt, 6, listOf("YOUTUBE")),
            ),
        )

        verify(exactly = 0) { publishAdapter.publishAll(any(), any(), any()) }
        assertEquals(ClipPublicationStatus.SKIPPED, saved.captured.status)
        assertTrue(output.scheduledAts.isNullOrEmpty())

        val snapshot = mapper.readTree(output.outputSnapshot)
        assertTrue(snapshot.path("clips")[0].path("skipped").asBoolean())
        assertEquals("렌더 영상 미연결", snapshot.path("clips")[0].path("skipReason").asText())
    }

    @Test
    fun `게시 실패는 FAILED 로 기록하고 오류 메시지를 남긴다`() {
        noPriorPublication()
        val saved = slot<ClipPublication>()
        every { publicationRepository.save(capture(saved)) } answers { firstArg() }
        every { publishAdapter.publishAll(any(), any(), any()) } throws IllegalStateException("업로드 토큰 만료")

        val output = executor.execute(
            stageContext(
                clips = listOf(clip(11, 1, renderedVideoId = 500L)),
                schedule = ScheduleParams(startAt, 6, listOf("YOUTUBE")),
            ),
        )

        assertEquals(ClipPublicationStatus.FAILED, saved.captured.status)
        assertEquals("업로드 토큰 만료", saved.captured.errorMessage)
        assertEquals(ScheduleOutcome.FAILED, output.scheduleOutcome)
    }

    @Test
    fun `한 플랫폼 성공과 한 플랫폼 실패는 PARTIAL 결과로 남긴다`() {
        noPriorPublication()
        val saved = mutableListOf<ClipPublication>()
        every { publicationRepository.save(any()) } answers {
            val publication = firstArg<ClipPublication>()
            saved += publication
            publication
        }
        every { publishAdapter.publishAll(any(), any(), any()) } returns listOf(
            PlatformPublishOutcome("YOUTUBE", videoUploadId = 700L, status = "SCHEDULED", errorMessage = null),
        )

        val output = executor.execute(
            stageContext(
                clips = listOf(clip(11, 1, renderedVideoId = 500L)),
                schedule = ScheduleParams(startAt, 6, listOf("YOUTUBE", "TIKTOK")),
            ),
        )

        assertEquals(ScheduleOutcome.PARTIAL, output.scheduleOutcome)
        assertTrue(saved.any { it.platform == "TIKTOK" && it.status == ClipPublicationStatus.FAILED })
    }

    // ---- 중복 게시 방지 ----

    @Test
    fun `이미 예약된 클립과 플랫폼은 다시 게시하지 않는다`() {
        every { publicationRepository.findByClipIdAndPlatform(11L, "YOUTUBE") } returns
            ClipPublication(id = 1, clipId = 11, platform = "YOUTUBE", status = ClipPublicationStatus.SCHEDULED)

        val output = executor.execute(
            stageContext(
                clips = listOf(clip(11, 1, renderedVideoId = 500L)),
                schedule = ScheduleParams(startAt, 6, listOf("YOUTUBE")),
            ),
        )

        verify(exactly = 0) { publishAdapter.publishAll(any(), any(), any()) }
        // 이미 잡혀 있으므로 클립 자체는 예약된 것으로 본다
        assertEquals(mapOf(11L to startAt), output.scheduledAts)

        val publications = mapper.readTree(output.outputSnapshot).path("publications")
        assertTrue(publications[0].path("duplicate").asBoolean())
    }

    @Test
    fun `이미 게시된 플랫폼은 건너뛰고 남은 플랫폼만 게시한다`() {
        every { publicationRepository.findByClipIdAndPlatform(11L, "YOUTUBE") } returns
            ClipPublication(id = 1, clipId = 11, platform = "YOUTUBE", status = ClipPublicationStatus.PUBLISHED)
        every { publicationRepository.findByClipIdAndPlatform(11L, "TIKTOK") } returns null
        val requests = slot<List<ShortsPublishRequest>>()
        every { publishAdapter.publishAll(any(), any(), capture(requests)) } returns listOf(
            PlatformPublishOutcome("TIKTOK", videoUploadId = 701L, status = "SCHEDULED", errorMessage = null),
        )

        executor.execute(
            stageContext(
                clips = listOf(clip(11, 1, renderedVideoId = 500L)),
                schedule = ScheduleParams(startAt, 6, listOf("YOUTUBE", "TIKTOK")),
            ),
        )

        assertEquals(listOf("TIKTOK"), requests.captured.map { it.platformName })
    }

    // ---- 스냅샷 ----

    @Test
    fun `출력 스냅샷에 예약 파라미터와 클립별 시각이 기록된다`() {
        noPriorPublication()
        every { publishAdapter.publishAll(any(), any(), any()) } returns listOf(
            PlatformPublishOutcome("YOUTUBE", videoUploadId = 700L, status = "SCHEDULED", errorMessage = null),
            PlatformPublishOutcome("TIKTOK", videoUploadId = 701L, status = "SCHEDULED", errorMessage = null),
        )

        val output = executor.execute(
            stageContext(
                clips = listOf(clip(11, 1, renderedVideoId = 500L)),
                schedule = ScheduleParams(startAt, 6, listOf("YOUTUBE", "TIKTOK")),
            ),
        )

        val snapshot = mapper.readTree(output.outputSnapshot)
        assertEquals(startAt.toString(), snapshot.path("startAt").asText())
        assertEquals(6, snapshot.path("intervalHours").asInt())
        assertEquals(listOf("YOUTUBE", "TIKTOK"), snapshot.path("platforms").map { it.asText() })
        assertEquals(11, snapshot.path("clips")[0].path("clipId").asLong())
        assertEquals(startAt.toString(), snapshot.path("clips")[0].path("scheduledAt").asText())
    }

    private fun assertFailsWithBusiness(block: () -> Unit): BusinessException {
        var caught: BusinessException? = null
        try {
            block()
        } catch (e: BusinessException) {
            caught = e
        }
        return caught ?: error("BusinessException 이 발생하지 않았습니다")
    }
}
