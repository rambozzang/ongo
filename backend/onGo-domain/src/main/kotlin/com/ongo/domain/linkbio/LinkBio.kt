package com.ongo.domain.linkbio

import java.time.LocalDateTime

data class LinkBioPage(
    val id: Long? = null,
    val userId: Long,
    val slug: String,
    val title: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val theme: String = "default",
    val backgroundColor: String = "#ffffff",
    val textColor: String = "#000000",
    val isPublished: Boolean = false,
    val viewCount: Long = 0,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)

data class LinkBioLink(
    val id: Long? = null,
    val pageId: Long,
    val title: String,
    val url: String,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val clickCount: Long = 0,
    val isActive: Boolean = true,
    val createdAt: LocalDateTime? = null,
)
