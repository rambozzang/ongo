package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.storage.StorageQuotaPort
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_SIZE_BYTES
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_URL
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
        // 실제로 보관 중인 파일만 센다. 스트리밍 게시는 임시 파일로 플랫폼에 흘려보낸 뒤
        // 곧바로 지우기 때문에(fileUrl = null) onGo 가 차지하는 저장 공간이 0 이다.
        // 이 조건이 없으면 보관하지도 않는 용량으로 쿼터가 차서, 영상을 호스팅하지 않는
        // 제품인데도 "저장 공간이 가득 찼다"며 업로드가 막힌다.
        val videoSize = dsl.select(DSL.coalesce(DSL.sum(FILE_SIZE_BYTES), 0L))
            .from(VIDEOS)
            .where(USER_ID.eq(userId).and(FILE_URL.isNotNull))
            .fetchOne(0, Long::class.java) ?: 0L

        val assetSize = dsl.select(DSL.coalesce(DSL.sum(FILE_SIZE_BYTES), 0L))
            .from(ASSETS)
            .where(USER_ID.eq(userId))
            .fetchOne(0, Long::class.java) ?: 0L

        return videoSize + assetSize
    }
}
