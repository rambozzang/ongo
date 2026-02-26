package com.ongo.application.thumbnailabtest

import com.ongo.application.thumbnailabtest.dto.*
import com.ongo.domain.thumbnailabtest.*
import org.springframework.stereotype.Service

@Service
class ThumbnailAbTestUseCase(
    private val repository: ThumbnailAbTestRepository,
) {

    fun getTests(workspaceId: Long, status: String?): List<ThumbnailTestResponse> {
        val tests = if (status != null) {
            repository.findByWorkspaceIdAndStatus(workspaceId, status)
        } else {
            repository.findByWorkspaceId(workspaceId)
        }
        return tests.map { toResponse(it) }
    }

    fun createTest(workspaceId: Long, request: CreateThumbnailTestRequest): ThumbnailTestResponse {
        val test = ThumbnailAbTest(
            workspaceId = workspaceId,
            videoTitle = "Video #${request.videoId}",
            platform = request.platform,
        )
        return toResponse(repository.save(test))
    }

    fun endTest(id: Long): ThumbnailTestResponse? {
        val test = repository.findById(id) ?: return null
        val winner = if (test.variantACtr > test.variantBCtr) "A" else "B"
        repository.updateStatus(id, "COMPLETED", winner)
        return toResponse(test.copy(status = "COMPLETED", winner = winner))
    }

    fun getSummary(workspaceId: Long): ThumbnailAbTestSummaryResponse {
        val tests = repository.findByWorkspaceId(workspaceId)
        val active = tests.count { it.status == "ACTIVE" }
        val completed = tests.filter { it.status == "COMPLETED" }
        val avgImprovement = if (completed.isNotEmpty()) {
            completed.map { maxOf(it.variantACtr, it.variantBCtr).toDouble() - minOf(it.variantACtr, it.variantBCtr).toDouble() }.average()
        } else 0.0
        val topPlatform = tests.groupBy { it.platform }.maxByOrNull { it.value.size }?.key ?: "-"
        return ThumbnailAbTestSummaryResponse(tests.size, active, avgImprovement, 0.0, topPlatform)
    }

    private fun toResponse(t: ThumbnailAbTest) = ThumbnailTestResponse(
        id = t.id, videoTitle = t.videoTitle, platform = t.platform, status = t.status,
        variantA = ThumbnailVariantResponse(t.variantAImageUrl, t.variantACtr.toDouble(), t.variantAImpressions, t.variantAClicks),
        variantB = ThumbnailVariantResponse(t.variantBImageUrl, t.variantBCtr.toDouble(), t.variantBImpressions, t.variantBClicks),
        winner = t.winner, startedAt = t.startedAt.toString(), endedAt = t.endedAt?.toString(),
    )
}
