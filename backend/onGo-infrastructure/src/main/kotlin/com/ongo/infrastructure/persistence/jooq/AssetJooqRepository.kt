package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.asset.Asset
import com.ongo.domain.asset.AssetQuery
import com.ongo.domain.asset.AssetRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DURATION_SECONDS
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_SIZE_BYTES
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_URL
import com.ongo.infrastructure.persistence.jooq.Fields.STORAGE_OBJECT_KEY
import com.ongo.infrastructure.persistence.jooq.Fields.FILENAME
import com.ongo.infrastructure.persistence.jooq.Fields.FOLDER
import com.ongo.infrastructure.persistence.jooq.Fields.HEIGHT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.MIME_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.ORIGINAL_FILENAME
import com.ongo.infrastructure.persistence.jooq.Fields.TAGS
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.WIDTH
import com.ongo.infrastructure.persistence.jooq.Tables.ASSETS
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class AssetJooqRepository(
    private val dsl: DSLContext,
) : AssetRepository {

    override fun findById(id: Long): Asset? =
        dsl.select()
            .from(ASSETS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toAsset()

    override fun findByUserId(userId: Long, query: AssetQuery, page: Int, size: Int): List<Asset> =
        dsl.select()
            .from(ASSETS)
            .where(conditions(userId, query))
            .orderBy(CREATED_AT.desc(), ID.desc())
            .limit(size)
            .offset(page.toLong() * size)
            .fetch()
            .map { it.toAsset() }

    override fun count(userId: Long, query: AssetQuery): Int =
        dsl.selectCount()
            .from(ASSETS)
            .where(conditions(userId, query))
            .fetchOne(0, Int::class.java) ?: 0

    /**
     * 목록과 총계가 **같은 조건**을 쓰도록 한 곳에서 만든다.
     *
     * 조건을 두 질의에 따로 적으면 하나만 고쳤을 때 총계가 조용히 어긋나고, 화면은
     * 존재하지 않는 페이지로 넘어간다. 그런 어긋남은 눈으로 보고 알기 어렵다.
     */
    private fun conditions(userId: Long, query: AssetQuery): Condition {
        var condition: Condition = USER_ID.eq(userId)
        query.fileType?.takeIf { it.isNotBlank() }?.let { condition = condition.and(FILE_TYPE.eq(it)) }
        query.folder?.takeIf { it.isNotBlank() }?.let { condition = condition.and(FOLDER.eq(it)) }
        // 태그는 배열 컬럼이다. 포함 여부를 SQL 로 물어야 다른 페이지의 태그도 걸린다.
        query.tag?.takeIf { it.isNotBlank() }?.let {
            condition = condition.and(DSL.condition("{0} = ANY({1})", DSL.`val`(it), TAGS))
        }
        /*
         * 검색은 **저장 파일명·원본 파일명·태그**를 함께 본다. 화면이 이전에 클라이언트에서
         * 하던 것과 같은 범위이되, 이제 전체 라이브러리를 대상으로 한다 — 한 페이지만
         * 뒤지는 검색은 "없다"와 "이 페이지에 없다"를 구분하지 못한다.
         *
         * `%`·`_` 는 이스케이프한다. 이스케이프하지 않으면 사용자가 친 `%` 하나가 전체 조회가 된다.
         */
        query.search?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
            val pattern = "%" + raw.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
            condition = condition.and(
                FILENAME.likeIgnoreCase(pattern, '!')
                    .or(ORIGINAL_FILENAME.likeIgnoreCase(pattern, '!'))
                    .or(DSL.condition("EXISTS (SELECT 1 FROM unnest({0}) AS t(tag) WHERE t.tag ILIKE {1} ESCAPE '!')", TAGS, DSL.`val`(pattern))),
            )
        }
        return condition
    }

    override fun save(asset: Asset): Asset {
        val id = dsl.insertInto(ASSETS)
            .set(USER_ID, asset.userId)
            .set(FILENAME, asset.filename)
            .set(ORIGINAL_FILENAME, asset.originalFilename)
            .set(FILE_URL, asset.fileUrl)
            .set(STORAGE_OBJECT_KEY, asset.storageObjectKey)
            .set(FILE_TYPE, asset.fileType)
            .set(FILE_SIZE_BYTES, asset.fileSizeBytes)
            .set(MIME_TYPE, asset.mimeType)
            .set(TAGS, asset.tags.toTypedArray())
            .set(FOLDER, asset.folder)
            .set(WIDTH, asset.width)
            .set(HEIGHT, asset.height)
            .set(DURATION_SECONDS, asset.durationSeconds)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(asset: Asset): Asset {
        dsl.update(ASSETS)
            .set(TAGS, asset.tags.toTypedArray())
            .set(FOLDER, asset.folder)
            .where(ID.eq(asset.id))
            .execute()

        return findById(asset.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(ASSETS)
            .where(ID.eq(id))
            .execute()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Record.toAsset(): Asset {
        val tagsRaw = get("tags")
        val tags: List<String> = when (tagsRaw) {
            is Array<*> -> (tagsRaw as Array<String>).toList()
            is java.sql.Array -> (tagsRaw.array as Array<String>).toList()
            else -> emptyList()
        }

        return Asset(
            id = get(ID),
            userId = get(USER_ID),
            filename = get(FILENAME),
            originalFilename = get(ORIGINAL_FILENAME),
            fileUrl = get(FILE_URL),
            storageObjectKey = get(STORAGE_OBJECT_KEY),
            fileType = get(FILE_TYPE),
            fileSizeBytes = get(FILE_SIZE_BYTES),
            mimeType = get(MIME_TYPE),
            tags = tags,
            folder = get(FOLDER) ?: "default",
            width = get(WIDTH),
            height = get(HEIGHT),
            durationSeconds = get(DURATION_SECONDS),
            createdAt = localDateTime(CREATED_AT),
        )
    }
}
