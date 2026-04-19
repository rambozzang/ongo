package com.ongo.application.videoseo.dto

data class VideoSeoScoreResponse(
    val videoId: Long,
    val overallScore: Int,
    val titleScore: Int,
    val descriptionScore: Int,
    val tagsScore: Int,
    val generalScore: Int,
    val titleSuggestions: List<String>,
    val descriptionSuggestions: List<String>,
    val tagsSuggestions: List<String>,
    val generalSuggestions: List<String>,
    val competitorKeywords: List<String>,
)
