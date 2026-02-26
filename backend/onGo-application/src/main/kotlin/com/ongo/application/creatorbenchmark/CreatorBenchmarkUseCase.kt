package com.ongo.application.creatorbenchmark

import com.ongo.application.creatorbenchmark.dto.*
import com.ongo.domain.creatorbenchmark.*
import org.springframework.stereotype.Service

@Service
class CreatorBenchmarkUseCase(
    private val resultRepository: BenchmarkResultRepository,
    private val peerRepository: BenchmarkPeerRepository,
) {

    fun getResults(workspaceId: Long, platform: String? = null): List<BenchmarkResultResponse> {
        val list = if (platform != null) resultRepository.findByWorkspaceIdAndPlatform(workspaceId, platform)
        else resultRepository.findByWorkspaceId(workspaceId)
        return list.map { toResultResponse(it) }
    }

    fun getPeers(workspaceId: Long, platform: String? = null): List<BenchmarkPeerResponse> {
        val list = if (platform != null) peerRepository.findByWorkspaceIdAndPlatform(workspaceId, platform)
        else peerRepository.findByWorkspaceId(workspaceId)
        return list.map { toPeerResponse(it) }
    }

    fun getSummary(workspaceId: Long): CreatorBenchmarkSummaryResponse {
        val all = resultRepository.findByWorkspaceId(workspaceId)
        val aboveAvg = all.count { it.myValue > it.avgValue }
        val topPercentile = all.maxByOrNull { it.percentile }?.percentile ?: 0
        val strongest = all.maxByOrNull { it.percentile }?.metric ?: "-"
        val weakest = all.minByOrNull { it.percentile }?.metric ?: "-"
        return CreatorBenchmarkSummaryResponse(all.size, aboveAvg, topPercentile, strongest, weakest)
    }

    private fun toResultResponse(r: BenchmarkResult) = BenchmarkResultResponse(
        id = r.id, platform = r.platform, category = r.category,
        myValue = r.myValue.toDouble(), avgValue = r.avgValue.toDouble(),
        topValue = r.topValue.toDouble(), percentile = r.percentile,
        metric = r.metric, trend = r.trend, updatedAt = r.updatedAt.toString(),
    )

    private fun toPeerResponse(p: BenchmarkPeer) = BenchmarkPeerResponse(
        id = p.id, name = p.name, platform = p.platform,
        subscribers = p.subscribers, avgViews = p.avgViews,
        engagementRate = p.engagementRate.toDouble(), category = p.category,
    )
}
