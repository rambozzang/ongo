package com.ongo.domain.creatoracademy

interface AcademyCourseRepository {
    fun findById(id: Long): AcademyCourse?
    fun findAll(): List<AcademyCourse>
    fun findByCategory(category: String): List<AcademyCourse>
    fun save(course: AcademyCourse): AcademyCourse
    fun update(course: AcademyCourse): AcademyCourse
    fun delete(id: Long)
}
