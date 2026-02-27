package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.creatoracademy.*
import org.springframework.stereotype.Repository

@Repository
class AcademyCourseStubRepository : AcademyCourseRepository {
    override fun findById(id: Long): AcademyCourse? = null
    override fun findAll(): List<AcademyCourse> = emptyList()
    override fun findByCategory(category: String): List<AcademyCourse> = emptyList()
    override fun save(course: AcademyCourse): AcademyCourse = course.copy(id = 1)
    override fun update(course: AcademyCourse): AcademyCourse = course
    override fun delete(id: Long) {}
}

@Repository
class AcademyEnrollmentStubRepository : AcademyEnrollmentRepository {
    override fun findByUserId(userId: Long): List<AcademyEnrollment> = emptyList()
    override fun findByUserIdAndCourseId(userId: Long, courseId: Long): AcademyEnrollment? = null
    override fun save(enrollment: AcademyEnrollment): AcademyEnrollment = enrollment.copy(id = 1)
    override fun update(enrollment: AcademyEnrollment): AcademyEnrollment = enrollment
}
