package com.ongo.domain.contentfunnel

interface FunnelStageRepository {
    fun findByVideoId(videoId: Long): List<FunnelStage>
    fun findByUserId(userId: Long): List<FunnelStage>
    fun save(stage: FunnelStage): FunnelStage
}
