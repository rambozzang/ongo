package com.ongo.domain.calendarinsights

interface UploadPatternRepository {
    fun findByWorkspaceId(workspaceId: Long): List<UploadPattern>
    fun save(pattern: UploadPattern): UploadPattern
}
