package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.creatoracademy.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class AcademyCourseJooqRepository(
    private val dsl: DSLContext,
) : AcademyCourseRepository {

    companion object {
        private val TABLE = DSL.table("academy_courses")
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val CATEGORY = DSL.field("category", String::class.java)
        private val LEVEL = DSL.field("level", String::class.java)
        private val INSTRUCTOR_NAME = DSL.field("instructor_name", String::class.java)
        private val INSTRUCTOR_AVATAR = DSL.field("instructor_avatar", String::class.java)
        private val THUMBNAIL_URL = DSL.field("thumbnail_url", String::class.java)
        private val TOTAL_LESSONS = DSL.field("total_lessons", Int::class.java)
        private val DURATION = DSL.field("duration", Int::class.java)
        private val RATING = DSL.field("rating", java.math.BigDecimal::class.java)
        private val ENROLLED_COUNT = DSL.field("enrolled_count", Int::class.java)
        private val CREDIT_REWARD = DSL.field("credit_reward", Int::class.java)
        private val TAGS = DSL.field("tags", String::class.java)
    }

    override fun findById(id: Long): AcademyCourse? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toAcademyCourse()

    override fun findAll(): List<AcademyCourse> =
        dsl.select().from(TABLE).fetch().map { it.toAcademyCourse() }

    override fun findByCategory(category: String): List<AcademyCourse> =
        dsl.select().from(TABLE).where(CATEGORY.eq(category)).fetch().map { it.toAcademyCourse() }

    override fun save(course: AcademyCourse): AcademyCourse {
        val newId = dsl.insertInto(TABLE)
            .set(TITLE, course.title)
            .set(DESCRIPTION, course.description)
            .set(CATEGORY, course.category)
            .set(LEVEL, course.level)
            .set(INSTRUCTOR_NAME, course.instructorName)
            .set(INSTRUCTOR_AVATAR, course.instructorAvatar)
            .set(THUMBNAIL_URL, course.thumbnailUrl)
            .set(TOTAL_LESSONS, course.totalLessons)
            .set(DURATION, course.duration)
            .set(RATING, java.math.BigDecimal.valueOf(course.rating))
            .set(ENROLLED_COUNT, course.enrolledCount)
            .set(CREDIT_REWARD, course.creditReward)
            .set(TAGS, course.tags)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(newId)!!
    }

    override fun update(course: AcademyCourse): AcademyCourse {
        dsl.update(TABLE)
            .set(TITLE, course.title)
            .set(DESCRIPTION, course.description)
            .set(CATEGORY, course.category)
            .set(LEVEL, course.level)
            .set(INSTRUCTOR_NAME, course.instructorName)
            .set(INSTRUCTOR_AVATAR, course.instructorAvatar)
            .set(THUMBNAIL_URL, course.thumbnailUrl)
            .set(TOTAL_LESSONS, course.totalLessons)
            .set(DURATION, course.duration)
            .set(RATING, java.math.BigDecimal.valueOf(course.rating))
            .set(ENROLLED_COUNT, course.enrolledCount)
            .set(CREDIT_REWARD, course.creditReward)
            .set(TAGS, course.tags)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(course.id))
            .execute()
        return findById(course.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toAcademyCourse(): AcademyCourse {
        val ratingVal = get(RATING)
        return AcademyCourse(
            id = get(ID),
            title = get(TITLE),
            description = get(DESCRIPTION),
            category = get(CATEGORY) ?: "GROWTH",
            level = get(LEVEL) ?: "BEGINNER",
            instructorName = get(INSTRUCTOR_NAME),
            instructorAvatar = get(INSTRUCTOR_AVATAR),
            thumbnailUrl = get(THUMBNAIL_URL),
            totalLessons = get(TOTAL_LESSONS) ?: 0,
            duration = get(DURATION) ?: 0,
            rating = ratingVal?.toDouble() ?: 0.0,
            enrolledCount = get(ENROLLED_COUNT) ?: 0,
            creditReward = get(CREDIT_REWARD) ?: 0,
            tags = get(TAGS) ?: "[]",
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}

@Repository
class AcademyEnrollmentJooqRepository(
    private val dsl: DSLContext,
) : AcademyEnrollmentRepository {

    companion object {
        private val TABLE = DSL.table("academy_enrollments")
        private val COURSE_ID = DSL.field("course_id", Long::class.java)
        private val COMPLETED_LESSONS = DSL.field("completed_lessons", Int::class.java)
        private val LAST_LESSON_ID = DSL.field("last_lesson_id", Long::class.java)
        private val ENROLLED_AT = DSL.field("enrolled_at", LocalDateTime::class.java)
        private val COMPLETED_AT = DSL.field("completed_at", LocalDateTime::class.java)
    }

    override fun findByUserId(userId: Long): List<AcademyEnrollment> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId)).fetch().map { it.toAcademyEnrollment() }

    override fun findByUserIdAndCourseId(userId: Long, courseId: Long): AcademyEnrollment? =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .and(COURSE_ID.eq(courseId))
            .fetchOne()?.toAcademyEnrollment()

    override fun save(enrollment: AcademyEnrollment): AcademyEnrollment {
        val newId = dsl.insertInto(TABLE)
            .set(USER_ID, enrollment.userId)
            .set(COURSE_ID, enrollment.courseId)
            .set(COMPLETED_LESSONS, enrollment.completedLessons)
            .set(LAST_LESSON_ID, enrollment.lastLessonId)
            .set(COMPLETED_AT, enrollment.completedAt)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return dsl.select().from(TABLE).where(ID.eq(newId)).fetchOne()!!.toAcademyEnrollment()
    }

    override fun update(enrollment: AcademyEnrollment): AcademyEnrollment {
        dsl.update(TABLE)
            .set(COMPLETED_LESSONS, enrollment.completedLessons)
            .set(LAST_LESSON_ID, enrollment.lastLessonId)
            .set(COMPLETED_AT, enrollment.completedAt)
            .where(ID.eq(enrollment.id))
            .execute()
        return dsl.select().from(TABLE).where(ID.eq(enrollment.id)).fetchOne()!!.toAcademyEnrollment()
    }

    private fun Record.toAcademyEnrollment(): AcademyEnrollment =
        AcademyEnrollment(
            id = get(ID),
            userId = get(USER_ID),
            courseId = get(COURSE_ID),
            completedLessons = get(COMPLETED_LESSONS) ?: 0,
            lastLessonId = get("last_lesson_id")?.let { (it as Number).toLong() },
            enrolledAt = localDateTime(ENROLLED_AT),
            completedAt = localDateTime(COMPLETED_AT),
        )
}
