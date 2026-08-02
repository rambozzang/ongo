package com.ongo.domain.ugc.shorts

interface ShortsClipRepository {
    fun saveAll(clips: List<ShortsClip>): List<ShortsClip>
    fun update(clip: ShortsClip): ShortsClip
    fun findByRunId(runId: Long): List<ShortsClip>
    fun findById(id: Long): ShortsClip?
    fun deleteByRunId(runId: Long): Int
}
