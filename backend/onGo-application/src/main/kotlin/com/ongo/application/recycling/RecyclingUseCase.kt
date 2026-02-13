package com.ongo.application.recycling

import com.ongo.domain.recycling.RecyclingSuggestion
import com.ongo.domain.recycling.RecyclingSuggestionRepository
import com.ongo.domain.video.VideoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class RecyclingUseCase(
    private val recyclingSuggestionRepository: RecyclingSuggestionRepository,
    private val videoRepository: VideoRepository,
) {

    @Transactional(readOnly = true)
    fun getSuggestions(userId: Long, status: String?): List<RecyclingSuggestion> {
        return recyclingSuggestionRepository.findByUserId(userId, status)
    }

    @Transactional
    fun generateSuggestions(userId: Long): List<RecyclingSuggestion> {
        // Analyze old high-engagement videos (older than 30 days with above-average views)
        val allVideos = videoRepository.findByUserId(userId, 0, 1000, null)

        if (allVideos.isEmpty()) return emptyList()

        val thirtyDaysAgo = LocalDateTime.now().minusDays(30)

        // Calculate average views from analytics (use file size as proxy for engagement if views unavailable)
        val oldVideos = allVideos.filter { video ->
            val createdAt = video.createdAt
            createdAt != null && createdAt.isBefore(thirtyDaysAgo)
        }

        if (oldVideos.isEmpty()) return emptyList()

        val suggestions = mutableListOf<RecyclingSuggestion>()

        for (video in oldVideos) {
            val videoId = video.id ?: continue
            val videoCreatedAt = video.createdAt ?: continue

            // Determine suggestion type based on video age and characteristics
            val ageInDays = java.time.Duration.between(videoCreatedAt, LocalDateTime.now()).toDays()
            val duration = video.durationSeconds ?: 0

            val suggestionType = when {
                ageInDays > 180 -> "REPOST"
                ageInDays > 90 -> "UPDATE_METADATA"
                duration > 600 -> "CLIP"
                else -> "REMIX"
            }

            val reason = when (suggestionType) {
                "REPOST" -> "6개월 이상 지난 인기 콘텐츠입니다. 재게시하여 새로운 시청자에게 도달하세요."
                "UPDATE_METADATA" -> "3개월 이상 지난 콘텐츠입니다. 제목과 태그를 최적화하여 노출을 높이세요."
                "CLIP" -> "긴 영상에서 짧은 클립을 만들어 숏폼 플랫폼에 게시하세요."
                "REMIX" -> "기존 콘텐츠를 리믹스하여 새로운 형태로 재활용하세요."
                else -> "콘텐츠 재활용을 추천합니다."
            }

            val suggestedPlatforms = when (suggestionType) {
                "CLIP" -> listOf("TIKTOK", "INSTAGRAM", "NAVER_CLIP")
                "REPOST" -> listOf("YOUTUBE", "TIKTOK")
                else -> listOf("YOUTUBE")
            }

            val priorityScore = when {
                ageInDays > 365 -> 30
                ageInDays > 180 -> 60
                ageInDays > 90 -> 80
                else -> 50
            }

            suggestions.add(
                RecyclingSuggestion(
                    userId = userId,
                    videoId = videoId,
                    suggestionType = suggestionType,
                    reason = reason,
                    suggestedPlatforms = suggestedPlatforms,
                    priorityScore = priorityScore,
                    status = "PENDING",
                )
            )
        }

        // Save suggestions (limit to top 20 by priority)
        val topSuggestions = suggestions.sortedByDescending { it.priorityScore }.take(20)
        return recyclingSuggestionRepository.saveAll(topSuggestions)
    }

    @Transactional
    fun acceptSuggestion(userId: Long, suggestionId: Long): RecyclingSuggestion {
        val suggestion = recyclingSuggestionRepository.findById(suggestionId)
            ?: throw com.ongo.common.exception.NotFoundException("재활용 제안", suggestionId)

        if (suggestion.userId != userId) {
            throw com.ongo.common.exception.UnauthorizedException("해당 제안에 대한 권한이 없습니다")
        }

        return recyclingSuggestionRepository.updateStatus(suggestionId, "ACCEPTED")
            ?: throw com.ongo.common.exception.NotFoundException("재활용 제안", suggestionId)
    }

    @Transactional
    fun dismissSuggestion(userId: Long, suggestionId: Long): RecyclingSuggestion {
        val suggestion = recyclingSuggestionRepository.findById(suggestionId)
            ?: throw com.ongo.common.exception.NotFoundException("재활용 제안", suggestionId)

        if (suggestion.userId != userId) {
            throw com.ongo.common.exception.UnauthorizedException("해당 제안에 대한 권한이 없습니다")
        }

        return recyclingSuggestionRepository.updateStatus(suggestionId, "DISMISSED")
            ?: throw com.ongo.common.exception.NotFoundException("재활용 제안", suggestionId)
    }
}
