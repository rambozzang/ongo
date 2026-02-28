package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentversioning.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
class ContentVersionGroupJooqRepository(
    private val dsl: DSLContext,
) : ContentVersionGroupRepository {

    companion object {
        private val TABLE = DSL.table("content_version_groups")
        private val CONTENT_ID = DSL.field("content_id", Long::class.java)
        private val CONTENT_TITLE = DSL.field("content_title", String::class.java)
        private val TOTAL_VERSIONS = DSL.field("total_versions", Int::class.java)
        private val LATEST_VERSION = DSL.field("latest_version", Int::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<ContentVersionGroup> =
        dsl.select().from(TABLE).where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(UPDATED_AT.desc())
            .fetch().map { it.toGroup() }

    override fun findById(id: Long): ContentVersionGroup? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toGroup()

    override fun save(group: ContentVersionGroup): ContentVersionGroup {
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, group.workspaceId)
            .set(CONTENT_ID, group.contentId)
            .set(CONTENT_TITLE, group.contentTitle)
            .set(PLATFORM, group.platform)
            .set(TOTAL_VERSIONS, group.totalVersions)
            .set(LATEST_VERSION, group.latestVersion)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun update(group: ContentVersionGroup): ContentVersionGroup {
        dsl.update(TABLE)
            .set(CONTENT_TITLE, group.contentTitle)
            .set(PLATFORM, group.platform)
            .set(TOTAL_VERSIONS, group.totalVersions)
            .set(LATEST_VERSION, group.latestVersion)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(group.id))
            .execute()

        return findById(group.id)!!
    }

    private fun Record.toGroup(): ContentVersionGroup =
        ContentVersionGroup(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            contentId = get(CONTENT_ID),
            contentTitle = get(CONTENT_TITLE),
            platform = get(PLATFORM),
            totalVersions = get(TOTAL_VERSIONS),
            latestVersion = get(LATEST_VERSION),
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
            updatedAt = localDateTime(UPDATED_AT) ?: LocalDateTime.now(),
        )
}

@Repository
class ContentVersionJooqRepository(
    private val dsl: DSLContext,
) : ContentVersionRepository {

    companion object {
        private val TABLE = DSL.table("content_versions")
        private val GROUP_ID = DSL.field("group_id", Long::class.java)
        private val VERSION_NUMBER = DSL.field("version_number", Int::class.java)
        private val CHANGE_TYPE = DSL.field("change_type", String::class.java)
        private val CHANGE_DESCRIPTION = DSL.field("change_description", String::class.java)
        private val PREVIOUS_VALUE = DSL.field("previous_value", String::class.java)
        private val NEW_VALUE = DSL.field("new_value", String::class.java)
        private val PERF_BEFORE_VIEWS = DSL.field("perf_before_views", Long::class.java)
        private val PERF_BEFORE_LIKES = DSL.field("perf_before_likes", Int::class.java)
        private val PERF_BEFORE_ENGAGEMENT = DSL.field("perf_before_engagement", BigDecimal::class.java)
        private val PERF_BEFORE_CTR = DSL.field("perf_before_ctr", BigDecimal::class.java)
        private val PERF_AFTER_VIEWS = DSL.field("perf_after_views", Long::class.java)
        private val PERF_AFTER_LIKES = DSL.field("perf_after_likes", Int::class.java)
        private val PERF_AFTER_ENGAGEMENT = DSL.field("perf_after_engagement", BigDecimal::class.java)
        private val PERF_AFTER_CTR = DSL.field("perf_after_ctr", BigDecimal::class.java)
        private val CREATED_BY = DSL.field("created_by", String::class.java)
    }

    override fun findByGroupId(groupId: Long): List<ContentVersion> =
        dsl.select().from(TABLE).where(GROUP_ID.eq(groupId))
            .orderBy(VERSION_NUMBER.asc())
            .fetch().map { it.toVersion() }

    override fun findById(id: Long): ContentVersion? =
        dsl.select().from(TABLE).where(Fields.ID.eq(id)).fetchOne()?.toVersion()

    override fun save(version: ContentVersion): ContentVersion {
        val id = dsl.insertInto(TABLE)
            .set(GROUP_ID, version.groupId)
            .set(VERSION_NUMBER, version.versionNumber)
            .set(CHANGE_TYPE, version.changeType)
            .set(CHANGE_DESCRIPTION, version.changeDescription)
            .set(PREVIOUS_VALUE, version.previousValue)
            .set(NEW_VALUE, version.newValue)
            .set(PERF_BEFORE_VIEWS, version.perfBeforeViews)
            .set(PERF_BEFORE_LIKES, version.perfBeforeLikes)
            .set(PERF_BEFORE_ENGAGEMENT, version.perfBeforeEngagement)
            .set(PERF_BEFORE_CTR, version.perfBeforeCtr)
            .set(PERF_AFTER_VIEWS, version.perfAfterViews)
            .set(PERF_AFTER_LIKES, version.perfAfterLikes)
            .set(PERF_AFTER_ENGAGEMENT, version.perfAfterEngagement)
            .set(PERF_AFTER_CTR, version.perfAfterCtr)
            .set(CREATED_BY, version.createdBy)
            .returningResult(Fields.ID)
            .fetchOne()!!.get(Fields.ID)

        return findById(id)!!
    }

    private fun Record.toVersion(): ContentVersion =
        ContentVersion(
            id = get(Fields.ID),
            groupId = get(GROUP_ID),
            versionNumber = get(VERSION_NUMBER),
            changeType = get(CHANGE_TYPE),
            changeDescription = get(CHANGE_DESCRIPTION),
            previousValue = get(PREVIOUS_VALUE),
            newValue = get(NEW_VALUE),
            perfBeforeViews = get("perf_before_views")?.let { (it as Number).toLong() },
            perfBeforeLikes = get("perf_before_likes")?.let { (it as Number).toInt() },
            perfBeforeEngagement = get("perf_before_engagement")?.let { it as BigDecimal },
            perfBeforeCtr = get("perf_before_ctr")?.let { it as BigDecimal },
            perfAfterViews = get("perf_after_views")?.let { (it as Number).toLong() },
            perfAfterLikes = get("perf_after_likes")?.let { (it as Number).toInt() },
            perfAfterEngagement = get("perf_after_engagement")?.let { it as BigDecimal },
            perfAfterCtr = get("perf_after_ctr")?.let { it as BigDecimal },
            createdBy = get(CREATED_BY),
            createdAt = localDateTime(Fields.CREATED_AT) ?: LocalDateTime.now(),
        )
}
