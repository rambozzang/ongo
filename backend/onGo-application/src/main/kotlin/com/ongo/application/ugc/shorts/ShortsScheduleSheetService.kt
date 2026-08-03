package com.ongo.application.ugc.shorts

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ugc.shorts.dto.SheetDiffRow
import com.ongo.application.ugc.shorts.dto.SheetPreviewResponse
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.shorts.ClipHookRepository
import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.HookVariant
import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.RunStageRepository
import com.ongo.domain.ugc.shorts.ShortsClip
import com.ongo.domain.ugc.shorts.ShortsClipRepository
import com.ongo.domain.workspace.WorkspaceRepository
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 쇼츠 업로드 예약표 엑셀(.xlsx) 내보내기/가져오기.
 * 가져오기는 사고 방지를 위해 반드시 2단계다.
 * - preview: 파일을 파싱해 변경 diff만 계산한다. DB는 건드리지 않는다.
 * - apply: 같은 파싱 결과를 실제로 클립/후킹에 반영한다.
 *
 * 컬럼 순서 고정: 순번 | 클립ID | 파일명 | 제목 | 후킹문구 | 캡션 | 플랫폼 | 예약시각 | 상태
 * 이 중 가져오기에서 반영하는 것은 제목·후킹문구·캡션·예약시각 4개뿐이다.
 */
@Service
class ShortsScheduleSheetService(
    private val pipelineRunRepository: PipelineRunRepository,
    private val runStageRepository: RunStageRepository,
    private val shortsClipRepository: ShortsClipRepository,
    private val clipHookRepository: ClipHookRepository,
    private val workspaceRepository: WorkspaceRepository,
) {

    private val mapper = jacksonObjectMapper()
    private val cellFormatter = DataFormatter()

    companion object {
        private val HEADERS = listOf("순번", "클립ID", "파일명", "제목", "후킹문구", "캡션", "플랫폼", "예약시각", "상태")
        private const val COL_SEQ = 0
        private const val COL_CLIP_ID = 1
        private const val COL_TITLE = 3
        private const val COL_HOOK = 4
        private const val COL_CAPTION = 5
        private const val COL_SCHEDULED_AT = 7

        private const val FIELD_TITLE = "title"
        private const val FIELD_HOOK = "hookText"
        private const val FIELD_CAPTION = "caption"
        private const val FIELD_SCHEDULED_AT = "scheduledAt"

        // 시트의 예약시각은 이 형식 문자열로만 오간다. 날짜만 있는 값은 오류로 처리한다.
        private val SHEET_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }

    /** 예약표 .xlsx 바이트를 만든다. 헤더는 굵게, 열 너비는 내용에 맞춘다. */
    fun exportSheet(userId: Long, workspaceId: Long, runId: Long): ByteArray {
        assertWorkspaceAccess(userId, workspaceId)
        val run = loadRunInWorkspace(workspaceId, runId)
        val clips = targetClips(run.id)
        if (clips.isEmpty()) {
            throw BusinessException("SHORTS_CLIP_NOT_FOUND", "예약표에 담을 클립이 없습니다")
        }

        val hooksByClip = clipHookRepository.findByClipIds(clips.map { it.id })
            .filter { it.selected }
            .associateBy { it.clipId }
        val platforms = resolvePlatforms(run.id)

        val buffer = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("예약표")

            val headerStyle = workbook.createCellStyle().apply {
                setFont(workbook.createFont().apply { bold = true })
            }
            val headerRow = sheet.createRow(0)
            HEADERS.forEachIndexed { index, title ->
                headerRow.createCell(index).apply {
                    setCellValue(title)
                    cellStyle = headerStyle
                }
            }

            clips.forEachIndexed { index, clip ->
                val row = sheet.createRow(index + 1)
                row.createCell(COL_SEQ).setCellValue(clip.seq.toDouble())
                row.createCell(COL_CLIP_ID).setCellValue(clip.id.toDouble())
                row.createCell(2).setCellValue("clip-${clip.seq}.mp4")
                row.createCell(COL_TITLE).setCellValue(clip.title ?: "")
                row.createCell(COL_HOOK).setCellValue(hooksByClip[clip.id]?.text ?: "")
                row.createCell(COL_CAPTION).setCellValue(clip.caption ?: "")
                row.createCell(6).setCellValue(platforms)
                row.createCell(COL_SCHEDULED_AT).setCellValue(formatInstant(clip.scheduledAt) ?: "")
                row.createCell(8).setCellValue(clip.status.name)
            }

            HEADERS.indices.forEach { sheet.autoSizeColumn(it) }
            workbook.write(buffer)
        }
        return buffer.toByteArray()
    }

    /** 가져오기 1단계: 파일을 파싱해 변경 diff만 돌려준다. 저장은 하지 않는다. */
    fun previewSheet(userId: Long, workspaceId: Long, runId: Long, input: InputStream): SheetPreviewResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val run = loadRunInWorkspace(workspaceId, runId)
        return parseDiff(run.id, input)
    }

    /** 가져오기 2단계: 확인된 변경을 실제로 반영하고, 반영된 diff를 돌려준다. */
    @Transactional
    fun applySheet(userId: Long, workspaceId: Long, runId: Long, input: InputStream): SheetPreviewResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val run = loadRunInWorkspace(workspaceId, runId)
        val result = parseDiff(run.id, input)
        if (result.rows.isEmpty()) return result

        val clipsById = shortsClipRepository.findByRunId(run.id).associateBy { it.id }
        val hooksByClip = clipHookRepository.findByClipIds(clipsById.keys.toList())
            .filter { it.selected }
            .associateBy { it.clipId }

        // 클립별로 필드 변경을 모아 클립당 1번만 저장한다
        result.rows.groupBy { it.clipId }.forEach { (clipId, diffs) ->
            val clip = clipsById[clipId] ?: return@forEach
            var updated = clip
            diffs.forEach { diff ->
                when (diff.field) {
                    FIELD_TITLE -> updated = updated.copy(title = diff.after)
                    FIELD_CAPTION -> updated = updated.copy(caption = diff.after)
                    FIELD_SCHEDULED_AT -> updated = updated.copy(scheduledAt = diff.after?.let { parseSheetDate(it) })
                    FIELD_HOOK -> {
                        // 빈 칸은 diff에 올라오지 않으므로 여기서 after는 항상 값이 있다
                        val variant = hooksByClip[clipId]?.variant ?: HookVariant.CUSTOM
                        clipHookRepository.markSelected(clipId, variant, diff.after!!)
                    }
                }
            }
            if (updated != clip) {
                shortsClipRepository.update(updated)
            }
        }
        return result
    }

    // ---- 가져오기 파싱 ----

    /**
     * 시트를 읽어 현재 클립 상태와 비교한 diff를 만든다.
     * - 클립ID가 이 실행에 없으면 unknownClipIds로 모은다.
     * - 예약시각이 파싱되지 않으면 그 행의 예약시각 변경만 버리고 invalidRows에 사유를 남긴다(나머지 필드는 진행).
     * - 순번·클립ID·상태 열은 읽기 전용이라 비교하지 않는다.
     */
    private fun parseDiff(runId: Long, input: InputStream): SheetPreviewResponse {
        val clips = shortsClipRepository.findByRunId(runId).associateBy { it.id }
        val hooksByClip = clipHookRepository.findByClipIds(clips.keys.toList())
            .filter { it.selected }
            .associateBy { it.clipId }

        val rows = mutableListOf<SheetDiffRow>()
        val unknownClipIds = mutableListOf<Long>()
        val invalidRows = mutableListOf<String>()

        // .xlsx가 아니거나 깨진 파일이면 전체를 실패시키지 않고 명확한 오류로 바꿔 던진다
        val workbook = runCatching { XSSFWorkbook(input) }.getOrElse {
            throw BusinessException("SHORTS_SHEET_INVALID", "엑셀(.xlsx) 파일을 읽을 수 없습니다")
        }
        workbook.use { wb ->
            val sheet = wb.getSheetAt(0)
            // 0행은 헤더, 1행부터 데이터
            for (rowIndex in 1..sheet.lastRowNum) {
                val row: Row = sheet.getRow(rowIndex) ?: continue
                val displayRow = rowIndex + 1 // 사용자에게 보여 줄 엑셀 행 번호

                val clipId = readClipId(row)
                if (clipId == null) {
                    invalidRows.add("${displayRow}행: 클립ID를 읽을 수 없습니다")
                    continue
                }
                val clip = clips[clipId]
                if (clip == null) {
                    unknownClipIds.add(clipId)
                    continue
                }

                val title = readCell(row, COL_TITLE)
                if (title != clip.title) {
                    rows.add(SheetDiffRow(clipId, clip.seq, FIELD_TITLE, clip.title, title))
                }

                // 후킹문구는 빈 칸이면 "지우기"가 아니라 "변경 없음"으로 본다
                val hook = readCell(row, COL_HOOK)
                val currentHook = hooksByClip[clipId]?.text
                if (hook != null && hook != currentHook) {
                    rows.add(SheetDiffRow(clipId, clip.seq, FIELD_HOOK, currentHook, hook))
                }

                val caption = readCell(row, COL_CAPTION)
                if (caption != clip.caption) {
                    rows.add(SheetDiffRow(clipId, clip.seq, FIELD_CAPTION, clip.caption, caption))
                }

                // 예약시각도 빈 칸이면 변경 없음. 형식 오류는 이 필드만 버리고 진행한다
                val scheduledText = readCell(row, COL_SCHEDULED_AT)
                if (scheduledText != null) {
                    val parsed = runCatching { parseSheetDate(scheduledText) }.getOrNull()
                    if (parsed == null) {
                        invalidRows.add("${displayRow}행: 예약시각 형식이 올바르지 않습니다 ($scheduledText)")
                    } else if (parsed != clip.scheduledAt) {
                        rows.add(
                            SheetDiffRow(clipId, clip.seq, FIELD_SCHEDULED_AT, formatInstant(clip.scheduledAt), scheduledText),
                        )
                    }
                }
            }
        }

        return SheetPreviewResponse(
            rows = rows,
            unknownClipIds = unknownClipIds.distinct(),
            invalidRows = invalidRows,
        )
    }

    /** 클립ID 셀은 숫자/문자 어떤 형태로 저장돼 있어도 Long으로 읽는다. */
    private fun readClipId(row: Row): Long? {
        val cell = row.getCell(COL_CLIP_ID) ?: return null
        if (cell.cellType == CellType.NUMERIC) return cell.numericCellValue.toLong()
        return readCell(row, COL_CLIP_ID)?.toLongOrNull()
    }

    /** 셀을 표시 형식 그대로 문자열로 읽고, 공백만 있으면 null로 본다. */
    private fun readCell(row: Row, index: Int): String? =
        cellFormatter.formatCellValue(row.getCell(index))?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseSheetDate(text: String): Instant =
        LocalDateTime.parse(text, SHEET_DATE_FORMAT).atZone(ZoneId.systemDefault()).toInstant()

    private fun formatInstant(instant: Instant?): String? =
        instant?.let { SHEET_DATE_FORMAT.format(it.atZone(ZoneId.systemDefault())) }

    // ---- 날부 헬퍼 ----

    /** 예약표 대상 클립: 제외(DISCARDED)되지 않은 클립을 순번 오름차순으로. */
    private fun targetClips(runId: Long): List<ShortsClip> =
        shortsClipRepository.findByRunId(runId)
            .filter { it.status != ClipStatus.DISCARDED }
            .sortedBy { it.seq }

    /** SCHEDULE 단계 스냅샷의 platforms를 쉼표로 이은 문자열. 아직 예약 전이면 빈 문자열. */
    private fun resolvePlatforms(runId: Long): String {
        val snapshot = runStageRepository.findByRunIdAndStage(runId, PipelineStage.SCHEDULE)
            ?.outputSnapshot ?: return ""
        return runCatching {
            mapper.readTree(snapshot).path("platforms").map { it.asText() }.joinToString(",")
        }.getOrDefault("")
    }

    private fun assertWorkspaceAccess(userId: Long, workspaceId: Long) {
        val accessible = workspaceRepository.findAccessibleByUserId(userId).any { it.id == workspaceId }
        if (!accessible) throw NotFoundException("워크스페이스", workspaceId)
    }

    private fun loadRunInWorkspace(workspaceId: Long, runId: Long): PipelineRun {
        val run = pipelineRunRepository.findById(runId)
            ?: throw BusinessException("SHORTS_RUN_NOT_FOUND", "실행을 찾을 수 없습니다: $runId")
        if (run.workspaceId != workspaceId) {
            throw BusinessException("ACCESS_DENIED", "다른 워크스페이스의 실행입니다")
        }
        return run
    }
}
