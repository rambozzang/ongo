package com.ongo.application.portfoliobuilder.dto

import java.time.LocalDateTime

data class PortfolioResponse(
    val id: Long,
    val title: String,
    val description: String,
    val template: String,
    val theme: String,
    val isPublished: Boolean,
    val publicUrl: String?,
    val viewCount: Int,
    val sections: List<PortfolioSectionResponse>,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class PortfolioSectionResponse(
    val id: Long,
    val sectionType: String,
    val title: String,
    val content: String,
    val order: Int,
    val isVisible: Boolean,
)

data class PortfolioBuilderSummaryResponse(
    val totalPortfolios: Int,
    val publishedCount: Int,
    val totalViews: Int,
    val avgSections: Int,
    val topTemplate: String,
)

data class CreatePortfolioRequest(
    val title: String,
    val template: String,
)
