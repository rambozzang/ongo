package com.ongo.application.copyrightcheck

import com.ongo.application.copyrightcheck.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.copyrightcheck.CopyrightCheckRepository
import com.ongo.domain.copyrightcheck.CopyrightCheckResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CopyrightCheckUseCase(
    private val copyrightCheckRepository: CopyrightCheckRepository,
) {

    fun listResults(userId: Long): List<CopyrightCheckResultResponse> {
        return copyrightCheckRepository.findByUserId(userId).map { it.toResponse() }
    }

    fun getResult(userId: Long, resultId: Long): CopyrightCheckResultResponse {
        val result = copyrightCheckRepository.findById(resultId) ?: throw NotFoundException("저작권 검사 결과", resultId)
        if (result.userId != userId) throw ForbiddenException("해당 저작권 검사 결과에 대한 권한이 없습니다")
        return result.toResponse()
    }

    @Transactional
    fun runCheck(userId: Long, request: RunCheckRequest): RunCheckResponse {
        val checkResult = CopyrightCheckResult(
            userId = userId,
            videoId = request.videoId,
            videoTitle = request.videoTitle,
            status = "CHECKING",
        )
        val saved = copyrightCheckRepository.save(checkResult)
        return saved.toRunCheckResponse()
    }

    private fun CopyrightCheckResult.toResponse() = CopyrightCheckResultResponse(
        id = id!!,
        videoId = videoId,
        videoTitle = videoTitle,
        status = status,
        issues = issues,
        musicDetected = musicDetected,
        monetizationEligible = monetizationEligible,
        platformChecks = platformChecks,
        checkedAt = checkedAt,
    )

    private fun CopyrightCheckResult.toRunCheckResponse() = RunCheckResponse(
        id = id!!,
        videoId = videoId,
        videoTitle = videoTitle,
        status = status,
        issues = issues,
        musicDetected = musicDetected,
        monetizationEligible = monetizationEligible,
        platformChecks = platformChecks,
        checkedAt = checkedAt,
    )
}
