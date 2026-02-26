package com.ongo.domain.contentrights

interface ContentRightRepository {
    fun findById(id: Long): ContentRight?
    fun findByUserId(userId: Long): List<ContentRight>
    fun save(right: ContentRight): ContentRight
    fun update(right: ContentRight): ContentRight
    fun delete(id: Long)
}
