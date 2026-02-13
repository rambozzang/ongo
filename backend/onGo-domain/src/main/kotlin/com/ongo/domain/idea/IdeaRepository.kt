package com.ongo.domain.idea

interface IdeaRepository {
    fun findById(id: Long): Idea?
    fun findByUserId(userId: Long, status: String?, category: String?, priority: String?): List<Idea>
    fun save(idea: Idea): Idea
    fun update(idea: Idea): Idea
    fun delete(id: Long)
}
