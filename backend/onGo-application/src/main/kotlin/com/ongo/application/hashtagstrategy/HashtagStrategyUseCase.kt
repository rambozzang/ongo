package com.ongo.application.hashtagstrategy

import com.ongo.application.hashtagstrategy.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.hashtagstrategy.HashtagSet
import com.ongo.domain.hashtagstrategy.HashtagStrategyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class HashtagStrategyUseCase(
    private val hashtagStrategyRepository: HashtagStrategyRepository,
) {

    fun listHashtagSets(userId: Long): List<HashtagSetResponse> {
        return hashtagStrategyRepository.findByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun createHashtagSet(userId: Long, request: CreateHashtagSetRequest): HashtagSetResponse {
        val hashtagSet = HashtagSet(
            userId = userId,
            name = request.name,
            hashtags = request.hashtags,
            totalReachEstimate = request.totalReachEstimate,
            overallDifficulty = request.overallDifficulty,
            score = request.score,
            platform = request.platform,
        )
        return hashtagStrategyRepository.save(hashtagSet).toResponse()
    }

    @Transactional
    fun deleteHashtagSet(userId: Long, setId: Long) {
        val hashtagSet = hashtagStrategyRepository.findById(setId) ?: throw NotFoundException("해시태그 세트", setId)
        if (hashtagSet.userId != userId) throw ForbiddenException("해당 해시태그 세트에 대한 권한이 없습니다")
        hashtagStrategyRepository.delete(setId)
    }

    private fun HashtagSet.toResponse() = HashtagSetResponse(
        id = id!!,
        name = name,
        hashtags = hashtags,
        totalReachEstimate = totalReachEstimate,
        overallDifficulty = overallDifficulty,
        score = score,
        platform = platform,
        createdAt = createdAt,
    )
}
