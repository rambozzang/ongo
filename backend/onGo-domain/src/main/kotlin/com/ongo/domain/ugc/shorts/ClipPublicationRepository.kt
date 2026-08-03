package com.ongo.domain.ugc.shorts

interface ClipPublicationRepository {
    fun findByClipIdAndPlatform(clipId: Long, platform: String): ClipPublication?
    fun save(publication: ClipPublication): ClipPublication
    fun update(publication: ClipPublication): ClipPublication
}
