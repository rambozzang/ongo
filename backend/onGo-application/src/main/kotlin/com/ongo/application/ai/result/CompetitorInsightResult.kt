package com.ongo.application.ai.result

data class CompetitorInsightResult(
    val summary: String,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val opportunities: List<String>,
    val recommendations: List<String>,
)
