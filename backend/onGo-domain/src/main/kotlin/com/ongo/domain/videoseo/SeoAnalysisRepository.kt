package com.ongo.domain.videoseo

interface SeoAnalysisRepository {
    fun findByWorkspaceId(workspaceId: Long): List<SeoAnalysis>
    fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<SeoAnalysis>
    fun save(analysis: SeoAnalysis): SeoAnalysis
}
