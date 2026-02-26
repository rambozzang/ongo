package com.ongo.domain.portfoliobuilder

data class PortfolioSection(
    val id: Long? = null,
    val portfolioId: Long,
    val sectionType: String,
    val title: String,
    val content: String = "",
    val sortOrder: Int = 0,
    val isVisible: Boolean = true,
)
