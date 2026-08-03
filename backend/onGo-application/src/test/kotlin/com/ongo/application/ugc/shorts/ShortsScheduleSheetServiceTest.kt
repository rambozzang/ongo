package com.ongo.application.ugc.shorts

import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.ClipHook
import com.ongo.domain.ugc.shorts.ClipHookRepository
import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.HookVariant
import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.RunStage
import com.ongo.domain.ugc.shorts.RunStageRepository
import com.ongo.domain.ugc.shorts.RunStageStatus
import com.ongo.domain.ugc.shorts.ShortsClip
import com.ongo.domain.ugc.shorts.ShortsClipRepository
import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 예약표 엑셀 내보내기/가져오기 검증.
 * preview는 DB를 건드리지 않고 diff만 내고, apply는 그 diff를 실제로 반영해야 한다.
 */
@ExtendWith(MockKExtension::class)
class ShortsScheduleSheetServiceTest {

    @MockK
    lateinit var pipelineRunRepository: PipelineRunRepository

    @MockK
    lateinit var runStageRepository: RunStageRepository

    @MockK
    lateinit var shortsClipRepository: ShortsClipRepository

    @MockK
    lateinit var clipHookRepository: ClipHookRepository

    @MockK
    lateinit var workspaceRepository: WorkspaceRepository

    @InjectMockKs
    lateinit var service: ShortsScheduleSheetService

    private val userId = 1L
    private val workspaceId = 10L
    private val runId = 100L

    private val formatter = DataFormatter()
    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private fun grantAccess() {
        every { workspaceRepository.findAccessibleByUserId(userId) } returns
            listOf(Workspace(id = workspaceId, ownerId = userId, name = "WS", slug = "ws"))
    }

    private fun stubRun() {
        every { pipelineRunRepository.findById(runId) } returns PipelineRun(
            id = runId,
            workspaceId = workspaceId,
            userId = userId,
            sourceVideoId = 55L,
        )
    }

    private val scheduledAt: Instant = Instant.parse("2026-08-10T09:00:00Z")

    private fun clip(
        id: Long,
        seq: Int,
        title: String? = "제목$seq",
        caption: String? = "캡션$seq",
        scheduled: Instant? = scheduledAt,
        status: ClipStatus = ClipStatus.SCHEDULED,
    ) = ShortsClip(
        id = id,
        runId = runId,
        seq = seq,
        startMs = 0,
        endMs = 45_000,
        title = title,
        caption = caption,
        status = status,
        scheduledAt = scheduled,
    )

    private fun selectedHook(clipId: Long, text: String = "후킹$clipId") =
        ClipHook(id = clipId * 10, clipId = clipId, variant = HookVariant.A, text = text, selected = true)

    /** 서비스와 같은 규칙으로 예약시각을 시트 문자열로 변환한다 */
    private fun sheetDate(instant: Instant): String =
        dateFormat.format(instant.atZone(ZoneId.systemDefault()))

    /** 테스트용 시트를 만든다. 각 행은 [클립ID, 제목, 후킹문구, 캡션, 예약시각] 순이다. */
    private fun sheetBytes(vararg rows: Array<Any?>): ByteArray {
        val buffer = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("예약표")
            val header = sheet.createRow(0)
            listOf("순번", "클립ID", "파일명", "제목", "후킹문구", "캡션", "플랫폼", "예약시각", "상태")
                .forEachIndexed { i, h -> header.createCell(i).setCellValue(h) }
            rows.forEachIndexed { index, values ->
                val row = sheet.createRow(index + 1)
                row.createCell(0).setCellValue((index + 1).toDouble())
                row.createCell(1).setCellValue((values[0] as Long).toDouble())
                row.createCell(2).setCellValue("clip-${index + 1}.mp4")
                row.createCell(3).setCellValue(values[1] as? String ?: "")
                row.createCell(4).setCellValue(values[2] as? String ?: "")
                row.createCell(5).setCellValue(values[3] as? String ?: "")
                row.createCell(7).setCellValue(values[4] as? String ?: "")
                row.createCell(8).setCellValue("SCHEDULED")
            }
            workbook.write(buffer)
        }
        return buffer.toByteArray()
    }

    private fun cellText(row: org.apache.poi.ss.usermodel.Row, index: Int): String =
        formatter.formatCellValue(row.getCell(index))

    // ---- 내보내기 ----

    @Test
    fun `내보내기는 고정 컬럼 순서와 클립 데이터를 담는다`() {
        grantAccess()
        stubRun()
        every { shortsClipRepository.findByRunId(runId) } returns listOf(clip(1L, 1), clip(2L, 2))
        every { clipHookRepository.findByClipIds(listOf(1L, 2L)) } returns listOf(selectedHook(1L))
        every { runStageRepository.findByRunIdAndStage(runId, PipelineStage.SCHEDULE) } returns RunStage(
            runId = runId,
            stage = PipelineStage.SCHEDULE,
            status = RunStageStatus.COMPLETED,
            outputSnapshot = """{"platforms":["YOUTUBE","TIKTOK"]}""",
        )

        val bytes = service.exportSheet(userId, workspaceId, runId)

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val header = sheet.getRow(0)
            assertEquals("순번", cellText(header, 0))
            assertEquals("클립ID", cellText(header, 1))
            assertEquals("파일명", cellText(header, 2))
            assertEquals("제목", cellText(header, 3))
            assertEquals("후킹문구", cellText(header, 4))
            assertEquals("캡션", cellText(header, 5))
            assertEquals("플랫폼", cellText(header, 6))
            assertEquals("예약시각", cellText(header, 7))
            assertEquals("상태", cellText(header, 8))
            assertTrue(header.getCell(0).cellStyle.font.bold)

            val first = sheet.getRow(1)
            assertEquals("1", cellText(first, 1))
            assertEquals("clip-1.mp4", cellText(first, 2))
            assertEquals("제목1", cellText(first, 3))
            assertEquals("후킹1", cellText(first, 4))
            assertEquals("캡션1", cellText(first, 5))
            assertEquals("YOUTUBE,TIKTOK", cellText(first, 6))
            assertEquals(sheetDate(scheduledAt), cellText(first, 7))
            assertEquals("SCHEDULED", cellText(first, 8))

            // 선택된 후킹이 없는 클립은 후킹문구가 빈칸
            assertEquals("", cellText(sheet.getRow(2), 4))
        }
    }

    @Test
    fun `내보내기 대상 클립이 없으면 예외`() {
        grantAccess()
        stubRun()
        every { shortsClipRepository.findByRunId(runId) } returns listOf(clip(1L, 1, status = ClipStatus.DISCARDED))

        assertFailsWith<BusinessException> {
            service.exportSheet(userId, workspaceId, runId)
        }
    }

    // ---- 미리보기 ----

    @Test
    fun `미리보기는 변경분만 diff로 돌려주고 DB를 건드리지 않는다`() {
        grantAccess()
        stubRun()
        every { shortsClipRepository.findByRunId(runId) } returns listOf(clip(1L, 1), clip(2L, 2))
        every { clipHookRepository.findByClipIds(listOf(1L, 2L)) } returns listOf(selectedHook(1L))

        val newDate = sheetDate(scheduledAt.plusSeconds(3600))
        val sheet = sheetBytes(
            arrayOf(1L, "바뀐제목", "바뀐후킹", "캡션1", newDate), // 제목·후킹·예약시각 변경
            arrayOf(2L, "제목2", "", "캡션2", sheetDate(scheduledAt)), // 변경 없음
        )

        val result = service.previewSheet(userId, workspaceId, runId, ByteArrayInputStream(sheet))

        assertEquals(3, result.rows.size)
        assertEquals(
            listOf("title", "hookText", "scheduledAt"),
            result.rows.map { it.field },
        )
        val titleRow = result.rows.first { it.field == "title" }
        assertEquals(1L, titleRow.clipId)
        assertEquals("제목1", titleRow.before)
        assertEquals("바뀐제목", titleRow.after)
        assertTrue(result.unknownClipIds.isEmpty())
        assertTrue(result.invalidRows.isEmpty())

        verify(exactly = 0) { shortsClipRepository.update(any()) }
        verify(exactly = 0) { clipHookRepository.markSelected(any(), any(), any()) }
    }

    @Test
    fun `미리보기는 모르는 클립ID와 잘못된 날짜를 모아서 알려준다`() {
        grantAccess()
        stubRun()
        every { shortsClipRepository.findByRunId(runId) } returns listOf(clip(1L, 1))
        every { clipHookRepository.findByClipIds(listOf(1L)) } returns emptyList()

        val sheet = sheetBytes(
            arrayOf(1L, "제목1", "", "캡션1", "2026-13-99 99:99"), // 날짜 오류 → 이 필드만 제외
            arrayOf(999L, "아무개", "", "", ""), // 이 실행에 없는 클립
        )

        val result = service.previewSheet(userId, workspaceId, runId, ByteArrayInputStream(sheet))

        assertTrue(result.rows.none { it.field == "scheduledAt" })
        assertEquals(listOf(999L), result.unknownClipIds)
        assertEquals(1, result.invalidRows.size)
        assertTrue(result.invalidRows.first().contains("예약시각"))
    }

    @Test
    fun `미리보기에서 빈 후킹 칸은 지우기가 아니라 변경 없음이다`() {
        grantAccess()
        stubRun()
        every { shortsClipRepository.findByRunId(runId) } returns listOf(clip(1L, 1))
        every { clipHookRepository.findByClipIds(listOf(1L)) } returns listOf(selectedHook(1L))

        val sheet = sheetBytes(arrayOf(1L, "제목1", "", "캡션1", sheetDate(scheduledAt)))

        val result = service.previewSheet(userId, workspaceId, runId, ByteArrayInputStream(sheet))

        assertTrue(result.rows.isEmpty())
    }

    // ---- 반영 ----

    @Test
    fun `반영은 diff를 클립과 후킹에 실제로 저장한다`() {
        grantAccess()
        stubRun()
        val original = clip(1L, 1)
        every { shortsClipRepository.findByRunId(runId) } returns listOf(original)
        every { clipHookRepository.findByClipIds(listOf(1L)) } returns listOf(selectedHook(1L))

        val updatedSlot = slot<ShortsClip>()
        every { shortsClipRepository.update(capture(updatedSlot)) } answers { updatedSlot.captured }
        every { clipHookRepository.markSelected(1L, HookVariant.A, "바뀐후킹") } answers {
            selectedHook(1L, "바뀐후킹")
        }

        val newDateText = sheetDate(scheduledAt.plusSeconds(7200))
        val sheet = sheetBytes(arrayOf(1L, "바뀐제목", "바뀐후킹", "바뀐캡션", newDateText))

        val result = service.applySheet(userId, workspaceId, runId, ByteArrayInputStream(sheet))

        assertEquals(4, result.rows.size)
        val saved = updatedSlot.captured
        assertEquals("바뀐제목", saved.title)
        assertEquals("바뀐캡션", saved.caption)
        assertEquals(scheduledAt.plusSeconds(7200), saved.scheduledAt)
        verify(exactly = 1) { shortsClipRepository.update(any()) }
        verify(exactly = 1) { clipHookRepository.markSelected(1L, HookVariant.A, "바뀐후킹") }
    }

    @Test
    fun `반영할 변경이 없으면 저장을 호출하지 않는다`() {
        grantAccess()
        stubRun()
        every { shortsClipRepository.findByRunId(runId) } returns listOf(clip(1L, 1))
        every { clipHookRepository.findByClipIds(listOf(1L)) } returns listOf(selectedHook(1L))

        val sheet = sheetBytes(arrayOf(1L, "제목1", "후킹1", "캡션1", sheetDate(scheduledAt)))

        val result = service.applySheet(userId, workspaceId, runId, ByteArrayInputStream(sheet))

        assertTrue(result.rows.isEmpty())
        verify(exactly = 0) { shortsClipRepository.update(any()) }
        verify(exactly = 0) { clipHookRepository.markSelected(any(), any(), any()) }
    }

    @Test
    fun `다른 워크스페이스의 실행이면 ACCESS_DENIED`() {
        grantAccess()
        every { pipelineRunRepository.findById(runId) } returns PipelineRun(
            id = runId,
            workspaceId = 999L,
            userId = userId,
            sourceVideoId = 55L,
        )

        assertFailsWith<BusinessException> {
            service.previewSheet(userId, workspaceId, runId, ByteArrayInputStream(ByteArray(0)))
        }
    }
}
