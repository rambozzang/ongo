package com.ongo.domain.platformhealth

interface HealthIssueRepository {
    fun findByHealthScoreId(healthScoreId: Long): List<HealthIssue>
}
