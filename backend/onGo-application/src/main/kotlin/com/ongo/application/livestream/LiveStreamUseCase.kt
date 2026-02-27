package com.ongo.application.livestream

import com.ongo.application.livestream.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.livestream.LiveStream
import com.ongo.domain.livestream.LiveStreamRepository
import com.ongo.domain.livestream.StreamChat
import com.ongo.domain.livestream.StreamChatRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LiveStreamUseCase(
    private val streamRepository: LiveStreamRepository,
    private val chatRepository: StreamChatRepository,
) {

    fun getStream(userId: Long, streamId: Long): LiveStreamResponse {
        val stream = streamRepository.findById(streamId)
            ?: throw NotFoundException("라이브 스트림", streamId)
        if (stream.userId != userId) throw ForbiddenException("해당 스트림에 대한 권한이 없습니다")
        return stream.toResponse()
    }

    fun getStreams(userId: Long, status: String?): List<LiveStreamResponse> {
        val streams = if (status != null) {
            streamRepository.findByStatus(userId, status)
        } else {
            streamRepository.findByUserId(userId)
        }
        return streams.map { it.toResponse() }
    }

    fun getChats(streamId: Long): List<StreamChatResponse> {
        return chatRepository.findByStreamId(streamId).map { it.toResponse() }
    }

    @Transactional
    fun create(userId: Long, request: CreateStreamRequest): LiveStreamResponse {
        val stream = LiveStream(
            userId = userId,
            title = request.title,
            description = request.description,
            platform = request.platform,
            scheduledAt = request.scheduledAt,
        )
        return streamRepository.save(stream).toResponse()
    }

    @Transactional
    fun endStream(userId: Long, streamId: Long): LiveStreamResponse {
        val stream = streamRepository.findById(streamId)
            ?: throw NotFoundException("라이브 스트림", streamId)
        streamRepository.updateStatus(streamId, "ENDED")
        return stream.copy(status = "ENDED").toResponse()
    }

    fun getSummary(userId: Long): LiveStreamSummaryResponse {
        val streams = streamRepository.findByUserId(userId)
        val live = streams.count { it.status == "LIVE" }
        val scheduled = streams.filter { it.status == "SCHEDULED" }.minByOrNull { it.scheduledAt }
        return LiveStreamSummaryResponse(
            totalStreams = streams.size,
            liveNow = live,
            avgViewers = if (streams.isNotEmpty()) streams.sumOf { it.peakViewers } / streams.size else 0,
            totalChatMessages = streams.sumOf { it.chatMessages.toLong() },
            nextScheduled = scheduled?.scheduledAt,
        )
    }

    private fun LiveStream.toResponse() = LiveStreamResponse(
        id = id!!,
        title = title,
        description = description,
        platform = platform,
        status = status,
        scheduledAt = scheduledAt,
        startedAt = startedAt,
        endedAt = endedAt,
        viewerCount = viewerCount,
        peakViewers = peakViewers,
        chatMessages = chatMessages,
        streamUrl = streamUrl,
        thumbnailUrl = thumbnailUrl,
    )

    private fun StreamChat.toResponse() = StreamChatResponse(
        id = id!!,
        streamId = streamId,
        username = username,
        message = message,
        timestamp = timestamp,
        isHighlighted = isHighlighted,
        isModerator = isModerator,
    )
}
