package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.team.TeamMember
import com.ongo.domain.team.TeamMemberRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.INVITED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.JOINED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.MEMBER_EMAIL
import com.ongo.infrastructure.persistence.jooq.Fields.MEMBER_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.PERMISSIONS
import com.ongo.infrastructure.persistence.jooq.Fields.ROLE
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.TEAM_MEMBERS
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class TeamMemberJooqRepository(
    private val dsl: DSLContext,
) : TeamMemberRepository {

    override fun findByUserId(userId: Long): List<TeamMember> =
        dsl.select()
            .from(TEAM_MEMBERS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toTeamMember() }

    override fun findById(id: Long): TeamMember? =
        dsl.select()
            .from(TEAM_MEMBERS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toTeamMember()

    override fun findByUserIdAndEmail(userId: Long, email: String): TeamMember? =
        dsl.select()
            .from(TEAM_MEMBERS)
            .where(USER_ID.eq(userId))
            .and(MEMBER_EMAIL.eq(email))
            .fetchOne()
            ?.toTeamMember()

    override fun save(member: TeamMember): TeamMember {
        val permissionsField = DSL.field("permissions", JSONB::class.java)
        val id = dsl.insertInto(TEAM_MEMBERS)
            .set(USER_ID, member.userId)
            .set(MEMBER_EMAIL, member.memberEmail)
            .set(MEMBER_NAME, member.memberName)
            .set(ROLE, member.role)
            .set(STATUS, member.status)
            .set(permissionsField, JSONB.jsonb(member.permissions ?: "{}"))
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(member: TeamMember): TeamMember {
        val permissionsField = DSL.field("permissions", JSONB::class.java)
        dsl.update(TEAM_MEMBERS)
            .set(MEMBER_NAME, member.memberName)
            .set(ROLE, member.role)
            .set(STATUS, member.status)
            .set(permissionsField, JSONB.jsonb(member.permissions ?: "{}"))
            .set(JOINED_AT, member.joinedAt)
            .where(ID.eq(member.id))
            .execute()

        return findById(member.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TEAM_MEMBERS)
            .where(ID.eq(id))
            .execute()
    }

    override fun findByMemberEmailAndUserId(memberEmail: String, teamOwnerId: Long): TeamMember? =
        dsl.select()
            .from(TEAM_MEMBERS)
            .where(MEMBER_EMAIL.eq(memberEmail))
            .and(USER_ID.eq(teamOwnerId))
            .fetchOne()
            ?.toTeamMember()

    override fun countByUserId(userId: Long): Int =
        dsl.selectCount()
            .from(TEAM_MEMBERS)
            .where(USER_ID.eq(userId))
            .fetchOne(0, Int::class.java) ?: 0

    override fun findTeamsForMember(memberEmail: String): List<TeamMember> =
        dsl.select()
            .from(TEAM_MEMBERS)
            .where(MEMBER_EMAIL.eq(memberEmail))
            .and(STATUS.eq("JOINED"))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toTeamMember() }

    private fun Record.toTeamMember(): TeamMember {
        val permissionsRaw = get("permissions")
        val permissions: String = when (permissionsRaw) {
            is JSONB -> permissionsRaw.data()
            is String -> permissionsRaw
            else -> "{}"
        }
        return TeamMember(
            id = get(ID),
            userId = get(USER_ID),
            memberEmail = get(MEMBER_EMAIL),
            memberName = get(MEMBER_NAME),
            role = get(ROLE),
            status = get(STATUS),
            permissions = permissions,
            invitedAt = localDateTime(INVITED_AT),
            joinedAt = localDateTime(JOINED_AT),
            createdAt = localDateTime(CREATED_AT),
        )
    }
}
