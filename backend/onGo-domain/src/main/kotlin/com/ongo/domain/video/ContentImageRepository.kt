package com.ongo.domain.video

interface ContentImageRepository {
    fun save(image: ContentImage): ContentImage
    fun saveAll(images: List<ContentImage>): List<ContentImage>
    fun findByVideoId(videoId: Long): List<ContentImage>
    fun deleteByVideoId(videoId: Long)
    fun updateOrder(videoId: Long, imageIds: List<Long>)
}
