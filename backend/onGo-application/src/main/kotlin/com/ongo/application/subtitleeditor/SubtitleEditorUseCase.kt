package com.ongo.application.subtitleeditor

import com.ongo.application.subtitleeditor.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.subtitleeditor.SubtitleEditorRepository
import com.ongo.domain.subtitleeditor.SubtitleTrack
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SubtitleEditorUseCase(
    private val subtitleEditorRepository: SubtitleEditorRepository,
) {

    fun listSubtitleTracks(userId: Long): List<SubtitleTrackResponse> {
        return subtitleEditorRepository.findByUserId(userId).map { it.toResponse() }
    }

    fun listSubtitleTracksByVideo(videoId: Long): List<SubtitleTrackResponse> {
        return subtitleEditorRepository.findByVideoId(videoId).map { it.toResponse() }
    }

    @Transactional
    fun createSubtitleTrack(userId: Long, request: CreateSubtitleTrackRequest): SubtitleTrackResponse {
        val track = SubtitleTrack(
            userId = userId,
            videoId = request.videoId,
            videoTitle = request.videoTitle,
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
        val updated = track.copy(
            language = request.language ?: track.language,
            status = request.status ?: track.status,
            cues = request.cues ?: track.cues,
            totalDuration = request.totalDuration ?: track.totalDuration,
            wordCount = request.wordCount ?: track.wordCount,
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
}
