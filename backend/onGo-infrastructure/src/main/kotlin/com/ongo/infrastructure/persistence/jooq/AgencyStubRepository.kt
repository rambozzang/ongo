package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.agency.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class AgencyJooqRepository(
    private val dsl: DSLContext,
) : AgencyRepository {

    companion object {
        // agency_workspaces
        private val WS_TABLE = DSL.table("agency_workspaces")
        private val OWNER_USER_ID = DSL.field("owner_user_id", Long::class.java)
        private val NAME = DSL.field("name", String::class.java)
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val LOGO_URL = DSL.field("logo_url", String::class.java)

        // agency_creators
        private val CREATOR_TABLE = DSL.table("agency_creators")
        private val ROLE = DSL.field("role", String::class.java)
        private val JOINED_AT = DSL.field("joined_at", LocalDateTime::class.java)

        // client_portals
        private val PORTAL_TABLE = DSL.table("client_portals")
        private val CLIENT_NAME = DSL.field("client_name", String::class.java)
        private val ACCESS_TOKEN = DSL.field("access_token", String::class.java)
        private val PERMISSIONS = DSL.field("permissions", String::class.java)
        private val EXPIRES_AT = DSL.field("expires_at", LocalDateTime::class.java)
    }

    // ==================== AgencyWorkspace ====================

    override fun findWorkspaceById(id: Long): AgencyWorkspace? =
        dsl.select().from(WS_TABLE)
            .where(ID.eq(id))
            .fetchOne()?.toWorkspace()

    override fun findWorkspacesByOwner(ownerUserId: Long): List<AgencyWorkspace> =
        dsl.select().from(WS_TABLE)
            .where(OWNER_USER_ID.eq(ownerUserId))
            .fetch { it.toWorkspace() }

    override fun saveWorkspace(workspace: AgencyWorkspace): AgencyWorkspace {
        val id = dsl.insertInto(WS_TABLE)
            .set(OWNER_USER_ID, workspace.ownerUserId)
            .set(NAME, workspace.name)
            .set(DESCRIPTION, workspace.description)
            .set(LOGO_URL, workspace.logoUrl)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findWorkspaceById(id)!!
    }

    override fun updateWorkspace(workspace: AgencyWorkspace): AgencyWorkspace {
        dsl.update(WS_TABLE)
            .set(NAME, workspace.name)
            .set(DESCRIPTION, workspace.description)
            .set(LOGO_URL, workspace.logoUrl)
            .where(ID.eq(workspace.id))
            .execute()

        return findWorkspaceById(workspace.id!!)!!
    }

    override fun deleteWorkspace(id: Long) {
        dsl.deleteFrom(WS_TABLE).where(ID.eq(id)).execute()
    }

    // ==================== AgencyCreator ====================

    override fun findCreatorsByWorkspaceId(workspaceId: Long): List<AgencyCreator> =
        dsl.select().from(CREATOR_TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .fetch { it.toCreator() }

    override fun saveCreator(creator: AgencyCreator): AgencyCreator {
        val id = dsl.insertInto(CREATOR_TABLE)
            .set(WORKSPACE_ID, creator.workspaceId)
            .set(USER_ID, creator.userId)
            .set(ROLE, creator.role)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return dsl.select().from(CREATOR_TABLE).where(ID.eq(id)).fetchOne()!!.toCreator()
    }

    override fun deleteCreator(id: Long) {
        dsl.deleteFrom(CREATOR_TABLE).where(ID.eq(id)).execute()
    }

    // ==================== ClientPortal ====================

    override fun findPortalById(id: Long): ClientPortal? =
        dsl.select().from(PORTAL_TABLE)
            .where(ID.eq(id))
            .fetchOne()?.toPortal()

    override fun findPortalsByWorkspaceId(workspaceId: Long): List<ClientPortal> =
        dsl.select().from(PORTAL_TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .fetch { it.toPortal() }

    override fun findPortalByAccessToken(token: String): ClientPortal? =
        dsl.select().from(PORTAL_TABLE)
            .where(ACCESS_TOKEN.eq(token))
            .fetchOne()?.toPortal()

    override fun savePortal(portal: ClientPortal): ClientPortal {
        val id = dsl.insertInto(PORTAL_TABLE)
            .set(WORKSPACE_ID, portal.workspaceId)
            .set(CLIENT_NAME, portal.clientName)
            .set(ACCESS_TOKEN, portal.accessToken)
            .set(PERMISSIONS, DSL.field("?::jsonb", String::class.java, portal.permissions))
            .set(EXPIRES_AT, portal.expiresAt)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findPortalById(id)!!
    }

    override fun deletePortal(id: Long) {
        dsl.deleteFrom(PORTAL_TABLE).where(ID.eq(id)).execute()
    }

    // ==================== Record mappers ====================

    private fun Record.toWorkspace(): AgencyWorkspace =
        AgencyWorkspace(
            id = get(ID),
            ownerUserId = get(OWNER_USER_ID),
            name = get(NAME),
            description = get(DESCRIPTION),
            logoUrl = get(LOGO_URL),
            createdAt = localDateTime(CREATED_AT),
        )

    private fun Record.toCreator(): AgencyCreator =
        AgencyCreator(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            userId = get(USER_ID),
            role = get(ROLE) ?: "CREATOR",
            joinedAt = localDateTime(JOINED_AT),
        )

    private fun Record.toPortal(): ClientPortal {
        val permissionsRaw = get("permissions")
        return ClientPortal(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            clientName = get(CLIENT_NAME),
            accessToken = get(ACCESS_TOKEN),
            permissions = when (permissionsRaw) {
                is String -> permissionsRaw
                else -> permissionsRaw?.toString() ?: "{}"
            },
            expiresAt = localDateTime(EXPIRES_AT),
            createdAt = localDateTime(CREATED_AT),
        )
    }
}
