package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.PlanType
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CATEGORY
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.EMAIL
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.NICKNAME
import com.ongo.infrastructure.persistence.jooq.Fields.ONBOARDING_COMPLETED
import com.ongo.infrastructure.persistence.jooq.Fields.PLAN_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.PROFILE_IMAGE_URL
import com.ongo.infrastructure.persistence.jooq.Fields.PROVIDER
import com.ongo.infrastructure.persistence.jooq.Fields.PROVIDER_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.PROVIDER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.ROLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Tables.USERS
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class UserJooqRepository(
    private val dsl: DSLContext,
) : UserRepository {

    override fun findById(id: Long): User? =
        dsl.select()
            .from(USERS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toUser()

    override fun findByEmail(email: String): User? =
        dsl.select()
            .from(USERS)
            .where(EMAIL.eq(email))
            .fetchOne()
            ?.toUser()

    override fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User? =
        dsl.select()
            .from(USERS)
            .where(PROVIDER_TEXT.eq(provider.name))
            .and(PROVIDER_ID.eq(providerId))
            .fetchOne()
            ?.toUser()

    override fun findAll(offset: Int, limit: Int, searchQuery: String?): List<User> =
        dsl.select()
            .from(USERS)
            .where(buildSearchCondition(searchQuery))
            .orderBy(CREATED_AT.desc())
            .offset(offset)
            .limit(limit)
            .fetch()
            .map { it.toUser() }

    override fun countAll(searchQuery: String?): Long =
        dsl.selectCount()
            .from(USERS)
            .where(buildSearchCondition(searchQuery))
            .fetchOne(0, Long::class.java) ?: 0L

    private fun buildSearchCondition(searchQuery: String?): Condition {
        if (searchQuery.isNullOrBlank()) return DSL.trueCondition()
        val pattern = "%${searchQuery.trim().lowercase()}%"
        return DSL.lower(NAME).like(pattern).or(DSL.lower(EMAIL).like(pattern))
    }

    override fun save(user: User): User {
        val id = dsl.insertInto(USERS)
            .set(EMAIL, user.email)
            .set(NAME, user.name)
            .set(NICKNAME, user.nickname)
            .set(PROFILE_IMAGE_URL, user.profileImageUrl)
            .set(PROVIDER, user.provider.name)
            .set(PROVIDER_ID, user.providerId)
            .set(PLAN_TYPE, user.planType.name)
            .set(CATEGORY, user.category)
            .set(ONBOARDING_COMPLETED, user.onboardingCompleted)
            .set(ROLE, user.role)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(user: User): User {
        dsl.update(USERS)
            .set(EMAIL, user.email)
            .set(NAME, user.name)
            .set(NICKNAME, user.nickname)
            .set(PROFILE_IMAGE_URL, user.profileImageUrl)
            .set(PLAN_TYPE, user.planType.name)
            .set(CATEGORY, user.category)
            .set(ONBOARDING_COMPLETED, user.onboardingCompleted)
            .set(ROLE, user.role)
            .where(ID.eq(user.id))
            .execute()

        return findById(user.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(USERS)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toUser(): User = User(
        id = get(ID),
        email = get(EMAIL),
        name = get(NAME),
        nickname = get(NICKNAME),
        profileImageUrl = get(PROFILE_IMAGE_URL),
        provider = AuthProvider.valueOf(get(PROVIDER)),
        providerId = get(PROVIDER_ID),
        planType = PlanType.valueOf(get(PLAN_TYPE)),
        category = get(CATEGORY),
        onboardingCompleted = get(ONBOARDING_COMPLETED),
        role = get(ROLE),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
