package com.ongo.domain.ugc.shorts

interface ClipHookRepository {
    fun saveAll(hooks: List<ClipHook>): List<ClipHook>
    fun findByClipIds(clipIds: List<Long>): List<ClipHook>
    fun clearSelection(clipId: Long)
    fun markSelected(clipId: Long, variant: HookVariant, text: String): ClipHook
    fun deleteByClipIds(clipIds: List<Long>): Int
}
