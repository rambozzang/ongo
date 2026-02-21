package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.EMAIL
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.OWNER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_LOGO_URL
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_SLUG
import com.ongo.infrastructure.persistence.jooq.Tables.WORKSPACES
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class WorkspaceJooqRepository(
    private val dsl: DSLContext,
) : WorkspaceRepository {

    override fun findById(id: Long): Workspace? =
        dsl.select().from(WORKSPACES).where(ID.eq(id)).fetchOne()?.toWorkspace()

    override fun findBySlug(slug: String): Workspace? =
        dsl.select().from(WORKSPACES).where(WORKSPACE_SLUG.eq(slug)).fetchOne()?.toWorkspace()

    override fun findByOwnerId(ownerId: Long): List<Workspace> =
        dsl.select().from(WORKSPACES).where(OWNER_ID.eq(ownerId))
            .orderBy(CREATED_AT.asc()).fetch().map { it.toWorkspace() }

    override fun findAccessibleByUserId(userId: Long): List<Workspace> {
        val userEmail = dsl.select(EMAIL).from(Tables.USERS).where(ID.eq(userId))
            .fetchOne()?.get(EMAIL) ?: return emptyList()

        return dsl.selectDistinct(
            DSL.field("w.id", Long::class.java),
            DSL.field("w.owner_id", Long::class.java),
            DSL.field("w.name", String::class.java),
            DSL.field("w.slug", String::class.java),
            DSL.field("w.description", String::class.java),
            DSL.field("w.logo_url", String::class.java),
            DSL.field("w.created_at", LocalDateTime::class.java),
            DSL.field("w.updated_at", LocalDateTime::class.java),
        )
            .from(DSL.table("workspaces").`as`("w"))
            .leftJoin(DSL.table("team_members").`as`("tm"))
            .on(DSL.field("tm.workspace_id", Long::class.java).eq(DSL.field("w.id", Long::class.java)))
            .where(
                DSL.field("w.owner_id", Long::class.java).eq(userId)
                    .or(
                        DSL.field("tm.member_email", String::class.java).eq(userEmail)
                            .and(DSL.field("tm.status", String::class.java).eq("JOINED"))
                    )
            )
            .orderBy(DSL.field("w.created_at").asc())
            .fetch()
            .map { record ->
                Workspace(
                    id = record.get("id", Long::class.java),
                    ownerId = record.get("owner_id", Long::class.java),
                    name = record.get("name", String::class.java),
                    slug = record.get("slug", String::class.java),
                    description = record.get("description", String::class.java),
                    logoUrl = record.get("logo_url", String::class.java),
                    createdAt = record.get("created_at", LocalDateTime::class.java),
                    updatedAt = record.get("updated_at", LocalDateTime::class.java),
                )
            }
    }

    override fun save(workspace: Workspace): Workspace {
        val id = dsl.insertInto(WORKSPACES)
            .set(OWNER_ID, workspace.ownerId)
            .set(WORKSPACE_NAME, workspace.name)
            .set(WORKSPACE_SLUG, workspace.slug)
            .set(WORKSPACE_DESCRIPTION, workspace.description)
            .set(WORKSPACE_LOGO_URL, workspace.logoUrl)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return findById(id)!!
    }

    override fun update(workspace: Workspace): Workspace {
        dsl.update(WORKSPACES)
            .set(WORKSPACE_NAME, workspace.name)
            .set(WORKSPACE_SLUG, workspace.slug)
            .set(WORKSPACE_DESCRIPTION, workspace.description)
            .set(WORKSPACE_LOGO_URL, workspace.logoUrl)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(workspace.id))
            .execute()
        return findById(workspace.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(WORKSPACES).where(ID.eq(id)).execute()
    }

    override fun countByOwnerId(ownerId: Long): Int =
        dsl.selectCount().from(WORKSPACES).where(OWNER_ID.eq(ownerId)).fetchOne(0, Int::class.java)!!

    private fun Record.toWorkspace(): Workspace = Workspace(
        id = get(ID),
        ownerId = get(OWNER_ID),
        name = get(WORKSPACE_NAME),
        slug = get(WORKSPACE_SLUG),
        description = get(WORKSPACE_DESCRIPTION),
        logoUrl = get(WORKSPACE_LOGO_URL),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
