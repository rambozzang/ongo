package com.ongo.api.ai.dto

data class AiDemoRequest(
    val category: String,
)

data class AiDemoResponse(
    val titles: List<String>,
    val tags: List<String>,
)
