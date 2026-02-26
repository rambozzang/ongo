package com.ongo.domain.creatoracademy

interface AcademyEnrollmentRepository {
    fun findByUserId(userId: Long): List<AcademyEnrollment>
    fun findByUserIdAndCourseId(userId: Long, courseId: Long): AcademyEnrollment?
    fun save(enrollment: AcademyEnrollment): AcademyEnrollment
    fun update(enrollment: AcademyEnrollment): AcademyEnrollment
}
