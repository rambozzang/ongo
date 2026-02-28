package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.collaborationboard.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class BoardTaskJooqRepository(
    private val dsl: DSLContext,
) : BoardTaskRepository {

    companion object {
        private val TABLE = DSL.table("board_tasks")
        private val TITLE = DSL.field("title", String::class.java)
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val COLUMN_TYPE = DSL.field("column_type", String::class.java)
        private val PRIORITY = DSL.field("priority", String::class.java)
        private val ASSIGNEE_ID = DSL.field("assignee_id", Long::class.java)
        private val DUE_DATE = DSL.field("due_date", java.time.LocalDate::class.java)
        private val TAGS = DSL.field("tags", Array<String>::class.java)
        private val ATTACHMENTS = DSL.field("attachments", Int::class.java)
        private val VIDEO_ID = DSL.field("video_id", String::class.java)
        private val ORDER_INDEX = DSL.field("order_index", Int::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<BoardTask> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(ORDER_INDEX)
            .fetch { it.toBoardTask() }

    override fun findByWorkspaceIdAndColumn(workspaceId: Long, columnType: String): List<BoardTask> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(COLUMN_TYPE.eq(columnType))
            .orderBy(ORDER_INDEX)
            .fetch { it.toBoardTask() }

    override fun findById(id: Long): BoardTask? =
        dsl.select().from(TABLE)
            .where(ID.eq(id))
            .fetchOne()?.toBoardTask()

    override fun save(task: BoardTask): BoardTask {
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, task.workspaceId)
            .set(TITLE, task.title)
            .set(DESCRIPTION, task.description)
            .set(COLUMN_TYPE, task.columnType)
            .set(PRIORITY, task.priority)
            .set(STATUS, task.status)
            .set(ASSIGNEE_ID, task.assigneeId)
            .set(DUE_DATE, task.dueDate)
            .set(TAGS, task.tags.toTypedArray())
            .set(ATTACHMENTS, task.attachments)
            .set(VIDEO_ID, task.videoId)
            .set(ORDER_INDEX, task.orderIndex)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun update(task: BoardTask): BoardTask {
        dsl.update(TABLE)
            .set(TITLE, task.title)
            .set(DESCRIPTION, task.description)
            .set(COLUMN_TYPE, task.columnType)
            .set(PRIORITY, task.priority)
            .set(STATUS, task.status)
            .set(ASSIGNEE_ID, task.assigneeId)
            .set(DUE_DATE, task.dueDate)
            .set(TAGS, task.tags.toTypedArray())
            .set(ATTACHMENTS, task.attachments)
            .set(VIDEO_ID, task.videoId)
            .set(ORDER_INDEX, task.orderIndex)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(task.id))
            .execute()

        return findById(task.id)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    override fun countByWorkspaceId(workspaceId: Long): Int =
        dsl.selectCount().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .fetchOne(0, Int::class.java) ?: 0

    private fun Record.toBoardTask(): BoardTask {
        val tagsRaw = get("tags")
        val tagsList: List<String> = when (tagsRaw) {
            is Array<*> -> tagsRaw.filterIsInstance<String>()
            else -> emptyList()
        }
        return BoardTask(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            title = get(TITLE),
            description = get(DESCRIPTION),
            columnType = get(COLUMN_TYPE) ?: "IDEA",
            priority = get(PRIORITY) ?: "MEDIUM",
            status = get(STATUS) ?: "TODO",
            assigneeId = get("assignee_id")?.let { (it as Number).toLong() },
            dueDate = localDate(DUE_DATE),
            tags = tagsList,
            attachments = get(ATTACHMENTS) ?: 0,
            videoId = get(VIDEO_ID),
            orderIndex = get(ORDER_INDEX) ?: 0,
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
            updatedAt = localDateTime(UPDATED_AT) ?: LocalDateTime.now(),
        )
    }
}

@Repository
class BoardActivityJooqRepository(
    private val dsl: DSLContext,
) : BoardActivityRepository {

    companion object {
        private val TABLE = DSL.table("board_activities")
        private val ACTION = DSL.field("action", String::class.java)
        private val TASK_ID = DSL.field("task_id", Long::class.java)
        private val TASK_TITLE = DSL.field("task_title", String::class.java)
        private val FROM_COLUMN = DSL.field("from_column", String::class.java)
        private val TO_COLUMN = DSL.field("to_column", String::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long, limit: Int): List<BoardActivity> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(CREATED_AT.desc())
            .limit(limit)
            .fetch { it.toBoardActivity() }

    override fun save(activity: BoardActivity): BoardActivity {
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, activity.workspaceId)
            .set(USER_ID, activity.userId)
            .set(ACTION, activity.action)
            .set(TASK_ID, activity.taskId)
            .set(TASK_TITLE, activity.taskTitle)
            .set(FROM_COLUMN, activity.fromColumn)
            .set(TO_COLUMN, activity.toColumn)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()!!.toBoardActivity()
    }

    private fun Record.toBoardActivity(): BoardActivity =
        BoardActivity(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            userId = get(USER_ID),
            action = get(ACTION),
            taskId = get("task_id")?.let { (it as Number).toLong() },
            taskTitle = get(TASK_TITLE),
            fromColumn = get(FROM_COLUMN),
            toColumn = get(TO_COLUMN),
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
        )
}
