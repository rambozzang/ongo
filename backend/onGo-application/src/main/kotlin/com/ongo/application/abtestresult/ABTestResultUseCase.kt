package com.ongo.application.abtestresult

import com.ongo.application.abtestresult.dto.*
import com.ongo.domain.abtestresult.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ABTestResultUseCase(
    private val resultRepository: ABTestResultRepository,
    private val variantRepository: TestVariantRepository,
) {

    fun getResults(workspaceId: Long, status: String? = null): List<ABTestResultResponse> {
        val list = if (status != null) resultRepository.findByWorkspaceIdAndStatus(workspaceId, status)
        else resultRepository.findByWorkspaceId(workspaceId)
        return list.map { toResponse(it) }
    }

    fun getResult(workspaceId: Long, id: Long): ABTestResultResponse {
        val result = resultRepository.findById(id)
            ?: throw IllegalArgumentException("A/B 테스트 결과를 찾을 수 없습니다")
        return toResponse(result)
    }

    @Transactional
    fun pauseTest(workspaceId: Long, id: Long): ABTestResultResponse {
        val result = resultRepository.findById(id)
            ?: throw IllegalArgumentException("A/B 테스트 결과를 찾을 수 없습니다")
        val updated = resultRepository.update(result.copy(status = "PAUSED", updatedAt = LocalDateTime.now()))
        return toResponse(updated)
    }

    @Transactional
    fun resumeTest(workspaceId: Long, id: Long): ABTestResultResponse {
        val result = resultRepository.findById(id)
            ?: throw IllegalArgumentException("A/B 테스트 결과를 찾을 수 없습니다")
        val updated = resultRepository.update(result.copy(status = "RUNNING", updatedAt = LocalDateTime.now()))
        return toResponse(updated)
    }

    @Transactional
    fun stopTest(workspaceId: Long, id: Long): ABTestResultResponse {
        val result = resultRepository.findById(id)
            ?: throw IllegalArgumentException("A/B 테스트 결과를 찾을 수 없습니다")
        val variants = variantRepository.findByResultId(id)
        val winner = variants.maxByOrNull { it.ctr }
        val updated = resultRepository.update(
            result.copy(
                status = "COMPLETED",
                endDate = LocalDateTime.now(),
                winner = winner?.variantId,
                updatedAt = LocalDateTime.now(),
            )
        )
        if (winner != null) {
            variantRepository.update(winner.copy(isWinner = true))
        }
        return toResponse(updated)
    }

    fun getSummary(workspaceId: Long): ABTestResultSummaryResponse {
        val all = resultRepository.findByWorkspaceId(workspaceId)
        val running = all.count { it.status == "RUNNING" }
        val completed = all.count { it.status == "COMPLETED" }
        val avgConf = if (all.isNotEmpty()) all.map { it.confidence.toDouble() }.average() else 0.0
        val avgImprove = if (completed > 0) {
            all.filter { it.status == "COMPLETED" }.map { result ->
                val variants = variantRepository.findByResultId(result.id)
                val control = variants.find { it.isControl }
                val winnerVariant = variants.find { it.isWinner }
                if (control != null && winnerVariant != null && control.ctr.toDouble() > 0) {
                    ((winnerVariant.ctr.toDouble() - control.ctr.toDouble()) / control.ctr.toDouble()) * 100
                } else 0.0
            }.average()
        } else 0.0
        return ABTestResultSummaryResponse(all.size, running, completed, avgConf, avgImprove)
    }

    private fun toResponse(r: ABTestResult): ABTestResultResponse {
        val variants = variantRepository.findByResultId(r.id)
        return ABTestResultResponse(
            id = r.id, testId = r.testId, testName = r.testName, status = r.status,
            startDate = r.startDate.toString(), endDate = r.endDate?.toString(),
            variants = variants.map { toVariantResponse(it) },
            winner = r.winner, confidence = r.confidence.toDouble(),
            metric = r.metric, platform = r.platform, createdAt = r.createdAt.toString(),
        )
    }

    private fun toVariantResponse(v: TestVariant) = TestVariantResponse(
        id = v.variantId, name = v.name, description = v.description, thumbnailUrl = v.thumbnailUrl,
        views = v.views, clicks = v.clicks, ctr = v.ctr.toDouble(), avgWatchTime = v.avgWatchTime,
        engagement = v.engagement.toDouble(), conversions = v.conversions,
        isControl = v.isControl, isWinner = v.isWinner,
    )
}
