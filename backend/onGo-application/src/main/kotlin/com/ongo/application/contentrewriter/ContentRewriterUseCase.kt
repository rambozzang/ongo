package com.ongo.application.contentrewriter

import com.ongo.application.contentrewriter.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.contentrewriter.ContentRewriteResult
import com.ongo.domain.contentrewriter.ContentRewriteResultRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContentRewriterUseCase(
    private val contentRewriteResultRepository: ContentRewriteResultRepository,
) {

    fun history(userId: Long): List<RewriteHistoryResponse> {
        return contentRewriteResultRepository.findByUserId(userId).map { it.toHistoryResponse() }
    }

    @Transactional
    fun rewrite(userId: Long, request: RewriteRequest): RewriteResponse {
        val result = ContentRewriteResult(
            userId = userId,
            sourceText = request.sourceText,
            sourceType = request.sourceType,
            targetFormats = request.targetFormats.joinToString(","),
            results = "[]",
        )
        val saved = contentRewriteResultRepository.save(result)
        return saved.toRewriteResponse()
    }

    @Transactional
    fun deleteResult(userId: Long, resultId: Long) {
        val result = contentRewriteResultRepository.findById(resultId)
            ?: throw NotFoundException("리라이트 결과", resultId)
        if (result.userId != userId) throw ForbiddenException("해당 리라이트 결과에 대한 권한이 없습니다")
        contentRewriteResultRepository.delete(resultId)
    }

    private fun ContentRewriteResult.toRewriteResponse() = RewriteResponse(
        id = id!!,
        sourceText = sourceText,
        sourceType = sourceType,
        targetFormats = targetFormats,
        results = results,
        createdAt = createdAt,
    )

    private fun ContentRewriteResult.toHistoryResponse() = RewriteHistoryResponse(
        id = id!!,
        sourceText = sourceText,
        sourceType = sourceType,
        targetFormats = targetFormats,
        results = results,
        createdAt = createdAt,
    )
}
