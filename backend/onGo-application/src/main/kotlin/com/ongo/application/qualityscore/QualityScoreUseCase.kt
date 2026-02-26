package com.ongo.application.qualityscore

import com.ongo.application.qualityscore.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.qualityscore.QualityReport
import com.ongo.domain.qualityscore.QualityReportRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class QualityScoreUseCase(
    private val qualityReportRepository: QualityReportRepository,
) {

    @Transactional
    fun check(userId: Long, request: QualityCheckRequest): QualityCheckResponse {
        val report = QualityReport(
            userId = userId,
            videoId = request.videoId,
            videoTitle = request.videoTitle,
            overallScore = 0,
            overallGrade = "PENDING",
        )
        val saved = qualityReportRepository.save(report)
        return saved.toCheckResponse()
    }

    fun history(userId: Long): List<QualityReportResponse> {
        return qualityReportRepository.findByUserId(userId).map { it.toReportResponse() }
    }

    fun getReport(userId: Long, reportId: Long): QualityReportResponse {
        val report = qualityReportRepository.findById(reportId)
            ?: throw NotFoundException("품질 리포트", reportId)
        if (report.userId != userId) throw ForbiddenException("해당 품질 리포트에 대한 권한이 없습니다")
        return report.toReportResponse()
    }

    private fun QualityReport.toCheckResponse() = QualityCheckResponse(
        id = id!!,
        videoId = videoId,
        videoTitle = videoTitle,
        overallScore = overallScore,
        overallGrade = overallGrade,
        metrics = metrics,
        improvements = improvements,
        competitorAvg = competitorAvg,
        checkedAt = checkedAt,
    )

    private fun QualityReport.toReportResponse() = QualityReportResponse(
        id = id!!,
        videoId = videoId,
        videoTitle = videoTitle,
        overallScore = overallScore,
        overallGrade = overallGrade,
        metrics = metrics,
        improvements = improvements,
        competitorAvg = competitorAvg,
        checkedAt = checkedAt,
    )
}
