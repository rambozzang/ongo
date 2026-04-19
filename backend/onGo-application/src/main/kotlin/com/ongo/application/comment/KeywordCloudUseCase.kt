package com.ongo.application.comment

import com.ongo.domain.comment.CommentRepository
import org.springframework.stereotype.Service

@Service
class KeywordCloudUseCase(
    private val commentRepository: CommentRepository,
) {

    private val stopWords = setOf(
        "이", "가", "을", "를", "은", "는", "의", "에", "와", "과", "도", "로", "으로",
        "에서", "그", "이런", "저런", "좀", "더", "또", "다", "너무", "진짜", "정말",
        "아", "어", "오", "야", "여", "거", "뭐", "왜", "어떻게", "그냥", "하지",
        "it", "is", "the", "a", "an", "and", "or", "but", "in", "on", "at",
    )

    fun getKeywordCloud(userId: Long, videoId: Long?, days: Int): Map<String, Int> {
        val comments = commentRepository.findByUserIdWithContent(
            userId = userId,
            videoId = videoId,
            days = days,
            limit = 500,
        )

        return comments
            .flatMap { comment ->
                comment.content
                    .replace(Regex("[^가-힣a-zA-Z0-9\\s]"), " ")
                    .split("\\s+".toRegex())
                    .filter { it.length >= 2 && it.lowercase() !in stopWords }
            }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(50)
            .associate { it.key to it.value }
    }
}
