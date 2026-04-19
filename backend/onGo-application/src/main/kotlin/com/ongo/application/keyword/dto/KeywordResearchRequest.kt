package com.ongo.application.keyword.dto

data class KeywordResearchRequest(
    val keyword: String,
    val platforms: List<String> = listOf("YOUTUBE", "TIKTOK", "INSTAGRAM", "NAVER_CLIP"),
    val category: String = "일반",
)
