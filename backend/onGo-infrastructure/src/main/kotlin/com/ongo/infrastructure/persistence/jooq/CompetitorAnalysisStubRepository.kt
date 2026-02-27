package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.competitoranalysis.*
import org.springframework.stereotype.Repository

@Repository
class CompetitorAnalysisStubRepository : CompetitorAnalysisRepository {
    override fun findProfileById(id: Long): CompetitorProfile? = null
    override fun findProfilesByUserId(userId: Long): List<CompetitorProfile> = emptyList()
    override fun saveProfile(profile: CompetitorProfile): CompetitorProfile = profile.copy(id = 1)
    override fun updateProfile(profile: CompetitorProfile): CompetitorProfile = profile
    override fun deleteProfile(id: Long) {}

    override fun findReportById(id: Long): CompetitorReport? = null
    override fun findReportsByUserId(userId: Long): List<CompetitorReport> = emptyList()
    override fun saveReport(report: CompetitorReport): CompetitorReport = report.copy(id = 1)
    override fun deleteReport(id: Long) {}
}
