package com.ongo.application.competitoranalysis

import com.ongo.application.competitoranalysis.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.competitoranalysis.CompetitorAnalysisRepository
import com.ongo.domain.competitoranalysis.CompetitorProfile
import com.ongo.domain.competitoranalysis.CompetitorReport
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CompetitorAnalysisUseCase(
    private val competitorAnalysisRepository: CompetitorAnalysisRepository,
) {

    fun listProfiles(userId: Long): List<CompetitorProfileResponse> {
        return competitorAnalysisRepository.findProfilesByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun createProfile(userId: Long, request: CreateCompetitorProfileRequest): CompetitorProfileResponse {
        val profile = CompetitorProfile(
            userId = userId,
            name = request.name,
            avatarUrl = request.avatarUrl,
            platforms = request.platforms,
            subscriberCount = request.subscriberCount,
            avgViews = request.avgViews,
            avgEngagement = request.avgEngagement,
        )
        return competitorAnalysisRepository.saveProfile(profile).toResponse()
    }

    @Transactional
    fun deleteProfile(userId: Long, profileId: Long) {
        val profile = competitorAnalysisRepository.findProfileById(profileId) ?: throw NotFoundException("경쟁자 프로필", profileId)
        if (profile.userId != userId) throw ForbiddenException("해당 경쟁자 프로필에 대한 권한이 없습니다")
        competitorAnalysisRepository.deleteProfile(profileId)
    }

    fun listReports(userId: Long): List<CompetitorReportResponse> {
        return competitorAnalysisRepository.findReportsByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun createReport(userId: Long, request: CreateCompetitorReportRequest): CompetitorReportResponse {
        val report = CompetitorReport(
            userId = userId,
            period = request.period,
            comparison = request.comparison,
            contentGaps = request.contentGaps,
            trendingTopics = request.trendingTopics,
        )
        return competitorAnalysisRepository.saveReport(report).toResponse()
    }

    @Transactional
    fun deleteReport(userId: Long, reportId: Long) {
        val report = competitorAnalysisRepository.findReportById(reportId) ?: throw NotFoundException("경쟁자 리포트", reportId)
        if (report.userId != userId) throw ForbiddenException("해당 경쟁자 리포트에 대한 권한이 없습니다")
        competitorAnalysisRepository.deleteReport(reportId)
    }

    private fun CompetitorProfile.toResponse() = CompetitorProfileResponse(
        id = id!!,
        name = name,
        avatarUrl = avatarUrl,
        platforms = platforms,
        subscriberCount = subscriberCount,
        avgViews = avgViews,
        avgEngagement = avgEngagement,
        addedAt = addedAt,
    )

    private fun CompetitorReport.toResponse() = CompetitorReportResponse(
        id = id!!,
        period = period,
        comparison = comparison,
        contentGaps = contentGaps,
        trendingTopics = trendingTopics,
        generatedAt = generatedAt,
    )
}
