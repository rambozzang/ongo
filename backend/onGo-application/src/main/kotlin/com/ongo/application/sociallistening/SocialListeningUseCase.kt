package com.ongo.application.sociallistening

import com.ongo.application.sociallistening.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.sociallistening.BrandMention
import com.ongo.domain.sociallistening.BrandMentionRepository
import com.ongo.domain.sociallistening.KeywordAlert
import com.ongo.domain.sociallistening.KeywordAlertRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SocialListeningUseCase(
    private val brandMentionRepository: BrandMentionRepository,
    private val keywordAlertRepository: KeywordAlertRepository,
) {

    fun getReport(userId: Long, period: String): ListeningReportResponse {
        val days = parsePeriodToDays(period)
        val mentions = brandMentionRepository.findByUserIdAndPeriod(userId, days)

        val positive = mentions.count { it.sentiment == "POSITIVE" }
        val neutral = mentions.count { it.sentiment == "NEUTRAL" }
        val negative = mentions.count { it.sentiment == "NEGATIVE" }

        return ListeningReportResponse(
            period = period,
            totalMentions = mentions.size,
            sentimentBreakdown = SentimentBreakdown(
                positive = positive,
                neutral = neutral,
                negative = negative,
            ),
            mentions = mentions.map { it.toResponse() },
        )
    }

    @Transactional
    fun createAlert(userId: Long, request: CreateKeywordAlertRequest): KeywordAlertResponse {
        val alert = KeywordAlert(
            userId = userId,
            keyword = request.keyword,
            platforms = request.platforms.joinToString(","),
            notifyEmail = request.notifyEmail,
            notifyPush = request.notifyPush,
        )
        return keywordAlertRepository.save(alert).toResponse()
    }

    @Transactional
    fun toggleAlert(userId: Long, alertId: Long): KeywordAlertResponse {
        val alert = keywordAlertRepository.findById(alertId)
            ?: throw NotFoundException("키워드 알림", alertId)
        if (alert.userId != userId) throw ForbiddenException("해당 키워드 알림에 대한 권한이 없습니다")
        val updated = alert.copy(enabled = !alert.enabled)
        return keywordAlertRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteAlert(userId: Long, alertId: Long) {
        val alert = keywordAlertRepository.findById(alertId)
            ?: throw NotFoundException("키워드 알림", alertId)
        if (alert.userId != userId) throw ForbiddenException("해당 키워드 알림에 대한 권한이 없습니다")
        keywordAlertRepository.delete(alertId)
    }

    private fun parsePeriodToDays(period: String): Int {
        return when {
            period.endsWith("d") -> period.removeSuffix("d").toIntOrNull() ?: 14
            period.endsWith("w") -> (period.removeSuffix("w").toIntOrNull() ?: 2) * 7
            period.endsWith("m") -> (period.removeSuffix("m").toIntOrNull() ?: 1) * 30
            else -> 14
        }
    }

    private fun BrandMention.toResponse() = BrandMentionResponse(
        id = id!!,
        platform = platform,
        mentionText = mentionText,
        authorName = authorName,
        authorUrl = authorUrl,
        sentiment = sentiment,
        reach = reach,
        sourceUrl = sourceUrl,
        mentionedAt = mentionedAt,
    )

    private fun KeywordAlert.toResponse() = KeywordAlertResponse(
        id = id!!,
        keyword = keyword,
        platforms = platforms,
        enabled = enabled,
        notifyEmail = notifyEmail,
        notifyPush = notifyPush,
        lastTriggeredAt = lastTriggeredAt,
        createdAt = createdAt,
    )
}
