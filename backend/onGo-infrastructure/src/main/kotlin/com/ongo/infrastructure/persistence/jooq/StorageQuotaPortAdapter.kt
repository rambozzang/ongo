package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.storage.StorageQuotaPort
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_SIZE_BYTES
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.ASSETS
import com.ongo.infrastructure.persistence.jooq.Tables.VIDEOS
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class StorageQuotaPortAdapter(
    private val dsl: DSLContext,
) : StorageQuotaPort {

    override fun calculateUserStorageBytes(userId: Long): Long {
        val videoSize = dsl.select(DSL.coalesce(DSL.sum(FILE_SIZE_BYTES), 0L))
            .from(VIDEOS)
            .where(USER_ID.eq(userId))
            .fetchOne(0, Long::class.java) ?: 0L

        val assetSize = dsl.select(DSL.coalesce(DSL.sum(FILE_SIZE_BYTES), 0L))
            .from(ASSETS)
            .where(USER_ID.eq(userId))
            .fetchOne(0, Long::class.java) ?: 0L

        return videoSize + assetSize
    }
}
