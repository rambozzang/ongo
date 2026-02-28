package com.ongo.infrastructure.persistence.jooq

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.domain.moodboard.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class MoodBoardJooqRepository(
    private val dsl: DSLContext,
) : MoodBoardRepository {

    companion object {
        private val TABLE = DSL.table("mood_boards")
        private val NAME = DSL.field("name", String::class.java)
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val CATEGORY = DSL.field("category", String::class.java)
        private val ITEM_COUNT = DSL.field("item_count", Int::class.java)
        private val COVER_IMAGE = DSL.field("cover_image", String::class.java)
        private val TAGS = DSL.field("tags", String::class.java)
        private val IS_PUBLIC = DSL.field("is_public", Boolean::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<MoodBoard> =
        dsl.select().from(TABLE).where(WORKSPACE_ID.eq(workspaceId)).fetch().map { it.toMoodBoard() }

    override fun findById(id: Long): MoodBoard? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toMoodBoard()

    private val objectMapper = jacksonObjectMapper()

    override fun save(board: MoodBoard): MoodBoard {
        val tagsJson = objectMapper.writeValueAsString(board.tags)
        val newId = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, board.workspaceId)
            .set(NAME, board.name)
            .set(DESCRIPTION, board.description)
            .set(CATEGORY, board.category)
            .set(ITEM_COUNT, board.itemCount)
            .set(COVER_IMAGE, board.coverImage)
            .set(TAGS, DSL.field("?::jsonb", String::class.java, tagsJson))
            .set(IS_PUBLIC, board.isPublic)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(newId)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toMoodBoard(): MoodBoard {
        val tagsRaw = get("tags")
        val tagsList: List<String> = try {
            val raw = when (tagsRaw) {
                is String -> tagsRaw
                else -> tagsRaw?.toString()
            }
            if (raw != null) {
                objectMapper.readValue(raw, object : TypeReference<List<String>>() {})
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
        return MoodBoard(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            name = get(NAME),
            description = get(DESCRIPTION),
            category = get(CATEGORY),
            itemCount = get(ITEM_COUNT) ?: 0,
            coverImage = get(COVER_IMAGE),
            tags = tagsList,
            isPublic = get(IS_PUBLIC) ?: false,
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
        )
    }
}

@Repository
class MoodBoardItemJooqRepository(
    private val dsl: DSLContext,
) : MoodBoardItemRepository {

    companion object {
        private val TABLE = DSL.table("mood_board_items")
        private val BOARD_ID = DSL.field("board_id", Long::class.java)
        private val TYPE = DSL.field("type", String::class.java)
        private val TITLE = DSL.field("title", String::class.java)
        private val IMAGE_URL = DSL.field("image_url", String::class.java)
        private val SOURCE_URL = DSL.field("source_url", String::class.java)
        private val NOTE = DSL.field("note", String::class.java)
        private val COLOR = DSL.field("color", String::class.java)
        private val POSITION = DSL.field("position", Int::class.java)
    }

    override fun findByBoardId(boardId: Long): List<MoodBoardItem> =
        dsl.select().from(TABLE)
            .where(BOARD_ID.eq(boardId))
            .orderBy(POSITION.asc())
            .fetch().map { it.toMoodBoardItem() }

    private fun Record.toMoodBoardItem(): MoodBoardItem =
        MoodBoardItem(
            id = get(ID),
            boardId = get(BOARD_ID),
            type = get(TYPE),
            title = get(TITLE),
            imageUrl = get(IMAGE_URL),
            sourceUrl = get(SOURCE_URL),
            note = get(NOTE),
            color = get(COLOR),
            position = get(POSITION) ?: 0,
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
        )
}
