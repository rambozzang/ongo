package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.videoseo.*
import org.springframework.stereotype.Repository

@Repository
class SeoAnalysisStubRepository : SeoAnalysisRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<SeoAnalysis> = emptyList()
    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<SeoAnalysis> = emptyList()
    override fun save(analysis: SeoAnalysis): SeoAnalysis = analysis.copy(id = 1)
}

@Repository
class SeoKeywordStubRepository : SeoKeywordRepository {
    override fun findByAnalysisId(analysisId: Long): List<SeoKeyword> = emptyList()
}
