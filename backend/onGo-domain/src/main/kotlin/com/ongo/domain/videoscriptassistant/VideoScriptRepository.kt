package com.ongo.domain.videoscriptassistant

interface VideoScriptRepository {
    fun findById(id: Long): VideoScript?
    fun findByUserId(userId: Long): List<VideoScript>
    fun save(script: VideoScript): VideoScript
    fun updateStatus(id: Long, status: String)
    fun deleteById(id: Long)
}
