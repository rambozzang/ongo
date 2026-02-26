package com.ongo.domain.performancereport

data class ReportSection(
    val id: Long? = null,
    val reportId: Long,
    val sectionType: String,
    val title: String,
    val content: String,
    val chartData: String? = null,
    val sortOrder: Int = 0,
)
