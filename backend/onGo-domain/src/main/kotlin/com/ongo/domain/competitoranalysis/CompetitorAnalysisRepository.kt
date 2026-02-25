package com.ongo.domain.competitoranalysis

interface CompetitorAnalysisRepository {
    fun findProfileById(id: Long): CompetitorProfile?
    fun findProfilesByUserId(userId: Long): List<CompetitorProfile>
    fun saveProfile(profile: CompetitorProfile): CompetitorProfile
    fun updateProfile(profile: CompetitorProfile): CompetitorProfile
    fun deleteProfile(id: Long)

    fun findReportById(id: Long): CompetitorReport?
    fun findReportsByUserId(userId: Long): List<CompetitorReport>
    fun saveReport(report: CompetitorReport): CompetitorReport
    fun deleteReport(id: Long)
}
