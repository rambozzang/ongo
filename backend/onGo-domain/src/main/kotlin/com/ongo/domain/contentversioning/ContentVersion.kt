package com.ongo.domain.contentversioning

import java.math.BigDecimal
import java.time.LocalDateTime

data class ContentVersion(
    val id: Long = 0,
    val groupId: Long,
    val versionNumber: Int,
    val changeType: String,
    val changeDescription: String,
    val previousValue: String? = null,
    val newValue: String? = null,
    val perfBeforeViews: Long? = null,
    val perfBeforeLikes: Int? = null,
    val perfBeforeEngagement: BigDecimal? = null,
    val perfBeforeCtr: BigDecimal? = null,
    val perfAfterViews: Long? = null,
    val perfAfterLikes: Int? = null,
    val perfAfterEngagement: BigDecimal? = null,
    val perfAfterCtr: BigDecimal? = null,
    val createdBy: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
