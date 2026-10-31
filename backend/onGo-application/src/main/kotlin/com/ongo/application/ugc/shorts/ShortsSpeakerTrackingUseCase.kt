package com.ongo.application.ugc.shorts

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ai.ChatClientRegistry
import com.ongo.application.ai.economics.AiUnitEconomics
import com.ongo.application.credit.CreditService
import com.ongo.application.ugc.shorts.dto.ClipEditRequest
import com.ongo.application.ugc.shorts.dto.ClipEditResponse
import com.ongo.application.video.StorageService
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.AiProvider
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.FrameSampler
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.video.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Service
import org.springframework.util.MimeType

/**
 * 클립 하나에 **화자 추적 세로 변환**을 적용한다.
 *
 * 예전 세로 변환은 영상을 보지 않은 AI 가 제목·전사문만 보고 고른 상자 하나를 실행 전체에 썼다.
 * 여기서는 클립 구간의 실제 장면을 보고, 말하는 사람을 따라 움직이는 크롭을 만든다.
 *
 * - 크레딧은 결과를 적용했을 때만 가져간다. 장면 추출 실패·모델 오류·인물 부족이면 전액 환불된다
 * - 저장은 편집기와 같은 길([ShortsClipEditUseCase.edit])로 한다 — 권한·편집 가능 여부·렌더 지시서 재생성·
 *   이전 렌더 폐기가 한 곳에서 일어난다
 * - 비전 모델은 사용자 설정과 무관하게 Qwen 을 쓴다(이미지를 읽는 제공 모델이 그것뿐이다)
 */
@Service
class ShortsSpeakerTrackingUseCase(
    private val clipEditUseCase: ShortsClipEditUseCase,
    private val runRepository: PipelineRunRepository,
    private val videoRepository: VideoRepository,
    private val storageService: StorageService,
    private val frameSampler: FrameSampler,
    private val chatClientRegistry: ChatClientRegistry,
    private val creditService: CreditService,
    private val unitEconomics: AiUnitEconomics,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = jacksonObjectMapper()

    data class TrackingResponse(
        val edit: ClipEditResponse,
        val foundTiles: Int,
        val totalTiles: Int,
        val creditsCharged: Int,
    )

    fun creditCost(): Int = maxOf(AiFeature.SHORTS_SPEAKER_TRACK.creditCost, unitEconomics.creditsForVisionCall(images = 1))

    fun track(userId: Long, workspaceId: Long, runId: Long, clipId: Long): TrackingResponse {
        // 권한·소속·편집 가능 여부를 크레딧을 받기 **전에** 확인한다.
        val editor = clipEditUseCase.getEditor(userId, workspaceId, runId, clipId)
        if (!editor.editable) throw BusinessException("SHORTS_CLIP_NOT_EDITABLE", editor.notEditableReason ?: "편집할 수 없는 클립입니다")
        if (!chatClientRegistry.isProviderAvailable(AiProvider.QWEN)) {
            throw BusinessException("SHORTS_TRACK_UNAVAILABLE", "화자 추적을 지금 사용할 수 없습니다. 관리자에게 문의해 주세요.")
        }
        val run = runRepository.findById(runId) ?: throw BusinessException("SHORTS_TRACK_UNAVAILABLE", "실행을 찾을 수 없습니다")
        val video = videoRepository.findById(run.sourceVideoId)
            ?: throw BusinessException("SHORTS_TRACK_SOURCE_MISSING", "원본 영상을 찾을 수 없습니다")
        val sourceUrl = video.fileUrl?.let { storageService.getFileUrl(run.sourceVideoId, it) }?.takeIf { it.isNotBlank() }
            ?: throw BusinessException("SHORTS_TRACK_SOURCE_MISSING", "원본 영상 위치가 없어 화자 추적을 할 수 없습니다")

        val credits = creditCost()
        return creditService.withCredits(userId, credits, AiFeature.SHORTS_SPEAKER_TRACK.name) {
            val sheet = try {
                frameSampler.contactSheet(sourceUrl, editor.startMs, editor.endMs)
            } catch (e: IllegalStateException) {
                throw BusinessException("SHORTS_TRACK_FRAMES_FAILED", e.message ?: "원본 영상에서 장면을 추출하지 못했습니다")
            }
            val result = chatClientRegistry.getClient(AiProvider.QWEN).prompt()
                .user { u -> u.text(ShortsSpeakerTracking.prompt(sheet)).media(MimeType.valueOf(sheet.mimeType), ByteArrayResource(sheet.image)) }
                .call()
                .entity(SpeakerTrackResult::class.java)
                ?: throw BusinessException("AI_PARSE_ERROR", "화자 추적 응답을 읽을 수 없습니다")

            val track = ShortsSpeakerTracking.toTrack(sheet, result)
                ?: throw BusinessException(
                    "SHORTS_TRACK_NOT_FOUND",
                    "장면에서 인물을 충분히 찾지 못해 화자 추적을 적용하지 않았습니다. 크레딧은 돌려드렸습니다.",
                )
            log.info("shorts_track runId={} clipId={} found={}/{}", runId, clipId, track.foundTiles, track.totalTiles)

            val crop: Map<String, Any?> = mapper.readValue(ShortsCropPolicy.toJson(track.crop), Map::class.java)
                .entries.associate { (k, v) -> k.toString() to v }
            val edit = clipEditUseCase.edit(
                userId, workspaceId, runId, clipId,
                ClipEditRequest(crop = crop, sourceWidth = sheet.sourceWidth, sourceHeight = sheet.sourceHeight),
            )
            TrackingResponse(edit, track.foundTiles, track.totalTiles, credits)
        }
    }
}
