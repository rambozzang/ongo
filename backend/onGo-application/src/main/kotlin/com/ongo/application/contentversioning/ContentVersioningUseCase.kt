package com.ongo.application.contentversioning

import com.ongo.application.contentversioning.dto.*
import com.ongo.domain.contentversioning.*
import org.springframework.stereotype.Service

@Service
class ContentVersioningUseCase(
    private val groupRepository: ContentVersionGroupRepository,
    private val versionRepository: ContentVersionRepository,
) {

    fun getGroups(workspaceId: Long): List<ContentVersionGroupResponse> {
        return groupRepository.findByWorkspaceId(workspaceId).map { toGroupResponse(it) }
    }

    fun getVersions(contentId: Long): List<ContentVersionResponse> {
        val group = groupRepository.findById(contentId) ?: return emptyList()
        return versionRepository.findByGroupId(group.id).map { toVersionResponse(it) }
    }

    fun getSummary(workspaceId: Long): ContentVersioningSummaryResponse {
        val groups = groupRepository.findByWorkspaceId(workspaceId)
        val totalVersions = groups.sumOf { it.totalVersions }
        val avg = if (groups.isNotEmpty()) totalVersions.toDouble() / groups.size else 0.0
        val mostEdited = groups.maxByOrNull { it.totalVersions }?.contentTitle ?: "-"
        return ContentVersioningSummaryResponse(groups.size, totalVersions, avg, mostEdited, "썸네일 변경")
    }

    private fun toGroupResponse(g: ContentVersionGroup): ContentVersionGroupResponse {
        val versions = versionRepository.findByGroupId(g.id)
        return ContentVersionGroupResponse(
            contentId = g.contentId, contentTitle = g.contentTitle, platform = g.platform,
            totalVersions = g.totalVersions, latestVersion = g.latestVersion,
            versions = versions.map { toVersionResponse(it) }, createdAt = g.createdAt.toString(),
        )
    }

    private fun toVersionResponse(v: ContentVersion): ContentVersionResponse {
        val beforeViews = v.perfBeforeViews
        val perfBefore = if (beforeViews != null) VersionPerformanceResponse(
            beforeViews, v.perfBeforeLikes ?: 0, v.perfBeforeEngagement?.toDouble() ?: 0.0, v.perfBeforeCtr?.toDouble() ?: 0.0
        ) else null
        val afterViews = v.perfAfterViews
        val perfAfter = if (afterViews != null) VersionPerformanceResponse(
            afterViews, v.perfAfterLikes ?: 0, v.perfAfterEngagement?.toDouble() ?: 0.0, v.perfAfterCtr?.toDouble() ?: 0.0
        ) else null
        return ContentVersionResponse(
            id = v.id, versionNumber = v.versionNumber, changeType = v.changeType,
            changeDescription = v.changeDescription, previousValue = v.previousValue,
            newValue = v.newValue, performanceBefore = perfBefore, performanceAfter = perfAfter,
            createdBy = v.createdBy, createdAt = v.createdAt.toString(),
        )
    }
}
