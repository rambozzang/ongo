package com.ongo.domain.contentrepurposer

interface RepurposeTemplateRepository {
    fun findByWorkspaceIdOrDefault(workspaceId: Long): List<RepurposeTemplate>
}
