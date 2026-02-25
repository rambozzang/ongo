package com.ongo.domain.videoresizer

interface VideoResizerRepository {
    fun findById(id: Long): ResizeJob?
    fun findByUserId(userId: Long): List<ResizeJob>
    fun save(resizeJob: ResizeJob): ResizeJob
    fun update(resizeJob: ResizeJob): ResizeJob
    fun delete(id: Long)
}
