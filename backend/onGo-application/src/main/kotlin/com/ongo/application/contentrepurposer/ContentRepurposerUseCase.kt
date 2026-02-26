package com.ongo.application.contentrepurposer

import com.ongo.application.contentrepurposer.dto.*
import com.ongo.domain.contentrepurposer.*
import org.springframework.stereotype.Service

@Service
class ContentRepurposerUseCase(
    private val jobRepository: RepurposeJobRepository,
    private val templateRepository: RepurposeTemplateRepository,
) {

    fun getJobs(workspaceId: Long, status: String?): List<RepurposeJobResponse> {
        val jobs = if (status != null) {
            jobRepository.findByWorkspaceIdAndStatus(workspaceId, status)
        } else {
            jobRepository.findByWorkspaceId(workspaceId)
        }
        return jobs.map { toJobResponse(it) }
    }

    fun createJob(workspaceId: Long, request: RepurposeRequest): RepurposeJobResponse {
        val job = RepurposeJob(
            workspaceId = workspaceId,
            originalTitle = "Video #${request.videoId}",
            originalPlatform = "YOUTUBE",
            targetPlatform = request.targetPlatform,
            targetFormat = request.targetFormat,
        )
        return toJobResponse(jobRepository.save(job))
    }

    fun getTemplates(workspaceId: Long): List<RepurposeTemplateResponse> {
        return templateRepository.findByWorkspaceIdOrDefault(workspaceId).map { toTemplateResponse(it) }
    }

    fun getSummary(workspaceId: Long): ContentRepurposerSummaryResponse {
        val jobs = jobRepository.findByWorkspaceId(workspaceId)
        val completed = jobs.count { it.status == "COMPLETED" }
        val rate = if (jobs.isNotEmpty()) completed.toDouble() / jobs.size * 100 else 0.0
        val topPlatform = jobs.groupBy { it.targetPlatform }.maxByOrNull { it.value.size }?.key ?: "-"
        return ContentRepurposerSummaryResponse(jobs.size, completed, 35.0, topPlatform, rate)
    }

    private fun toJobResponse(j: RepurposeJob) = RepurposeJobResponse(
        id = j.id, originalTitle = j.originalTitle, originalPlatform = j.originalPlatform,
        targetPlatform = j.targetPlatform, targetFormat = j.targetFormat,
        status = j.status, progress = j.progress, outputUrl = j.outputUrl,
        createdAt = j.createdAt.toString(),
    )

    private fun toTemplateResponse(t: RepurposeTemplate) = RepurposeTemplateResponse(
        id = t.id, name = t.name, sourcePlatform = t.sourcePlatform,
        targetPlatform = t.targetPlatform, targetFormat = t.targetFormat,
        description = t.description, isDefault = t.isDefault,
    )
}
