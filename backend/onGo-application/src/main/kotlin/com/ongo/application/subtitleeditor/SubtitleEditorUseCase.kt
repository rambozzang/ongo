package com.ongo.application.subtitleeditor

import com.ongo.application.subtitleeditor.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.domain.subtitleeditor.SubtitleEditorRepository
import com.ongo.domain.subtitleeditor.SubtitleTrack
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SubtitleEditorUseCase(
    private val subtitleEditorRepository: SubtitleEditorRepository,
    private val videoRepository: VideoRepository,
    private val objectMapper: ObjectMapper,
) {

    fun listSubtitleTracks(userId: Long): List<SubtitleTrackResponse> {
        return subtitleEditorRepository.findByUserId(userId).map { it.toResponse() }
    }

    fun listSubtitleTracksByVideo(userId: Long, videoId: Long): List<SubtitleTrackResponse> {
        requireOwnedVideo(userId, videoId)
        return subtitleEditorRepository.findByVideoId(videoId).map { it.toResponse() }
    }

    @Transactional
    fun createSubtitleTrack(userId: Long, request: CreateSubtitleTrackRequest): SubtitleTrackResponse {
        val video = requireOwnedVideo(userId, request.videoId)
        validateTrack(request.language, request.cues, request.totalDuration, request.wordCount)
        val track = SubtitleTrack(
            userId = userId,
            videoId = request.videoId,
            videoTitle = video.title,
            language = request.language,
            cues = request.cues,
            totalDuration = request.totalDuration,
            wordCount = request.wordCount,
        )
        return subtitleEditorRepository.save(track).toResponse()
    }

    @Transactional
    fun updateSubtitleTrack(userId: Long, trackId: Long, request: UpdateSubtitleTrackRequest): SubtitleTrackResponse {
        val track = subtitleEditorRepository.findById(trackId) ?: throw NotFoundException("자막 트랙", trackId)
        if (track.userId != userId) throw ForbiddenException("해당 자막 트랙에 대한 권한이 없습니다")
        val language = request.language ?: track.language
        val cues = request.cues ?: track.cues
        val totalDuration = request.totalDuration ?: track.totalDuration
        val wordCount = request.wordCount ?: track.wordCount
        validateTrack(language, cues, totalDuration, wordCount)
        val status = request.status ?: track.status
        require(status in ALLOWED_STATUSES) { "지원하지 않는 자막 트랙 상태입니다: $status" }
        val updated = track.copy(
            language = language,
            status = status,
            cues = cues,
            totalDuration = totalDuration,
            wordCount = wordCount,
        )
        return subtitleEditorRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteSubtitleTrack(userId: Long, trackId: Long) {
        val track = subtitleEditorRepository.findById(trackId) ?: throw NotFoundException("자막 트랙", trackId)
        if (track.userId != userId) throw ForbiddenException("해당 자막 트랙에 대한 권한이 없습니다")
        subtitleEditorRepository.delete(trackId)
    }

    private fun SubtitleTrack.toResponse() = SubtitleTrackResponse(
        id = id!!,
        videoId = videoId,
        videoTitle = videoTitle,
        language = language,
        status = status,
        cues = cues,
        totalDuration = totalDuration,
        wordCount = wordCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun requireOwnedVideo(userId: Long, videoId: Long): Video {
        val video = videoRepository.findById(videoId) ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) throw ForbiddenException("해당 영상에 대한 권한이 없습니다")
        return video
    }

    private fun validateTrack(
        language: String,
        cues: String,
        totalDuration: java.math.BigDecimal,
        wordCount: Int,
    ) {
        require(language.trim().length in 2..10) { "언어 코드는 2~10자여야 합니다" }
        require(totalDuration >= java.math.BigDecimal.ZERO) { "자막 길이는 음수일 수 없습니다" }
        require(wordCount >= 0) { "단어 수는 음수일 수 없습니다" }
        val node = try { objectMapper.readTree(cues) } catch (_: Exception) {
            throw IllegalArgumentException("자막 큐 형식이 올바르지 않습니다")
        }
        require(node.isArray) { "자막 큐는 배열이어야 합니다" }
        node.forEach(::validateCue)
    }

    private fun validateCue(cue: JsonNode) {
        require(cue.isObject) { "자막 큐 항목이 올바르지 않습니다" }
        val startNode = cue.get("start") ?: throw IllegalArgumentException("자막 시작 시간이 없습니다")
        val endNode = cue.get("end") ?: throw IllegalArgumentException("자막 종료 시간이 없습니다")
        require(startNode.isNumber && endNode.isNumber) { "자막 시간은 숫자여야 합니다" }
        val start = startNode.asDouble()
        val end = endNode.asDouble()
        require(start.isFinite() && end.isFinite()) { "자막 시간은 유한한 숫자여야 합니다" }
        require(start >= 0 && end >= start) { "자막 시간 범위가 올바르지 않습니다" }
        require(cue.get("text")?.isTextual == true) { "자막 텍스트가 없습니다" }
    }

    private companion object {
        val ALLOWED_STATUSES = setOf("DRAFT", "READY", "EXPORTED")
    }
}
