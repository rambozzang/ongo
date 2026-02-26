package com.ongo.domain.videoseo

interface SeoKeywordRepository {
    fun findByAnalysisId(analysisId: Long): List<SeoKeyword>
}
