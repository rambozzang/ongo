package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.UploadStatus
import com.ongo.domain.storage.StorageQuotaPort
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_SIZE_BYTES
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_URL
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.ASSETS
import com.ongo.infrastructure.persistence.jooq.Tables.USERS
import com.ongo.infrastructure.persistence.jooq.Tables.VIDEOS
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class StorageQuotaPortAdapter(
    private val dsl: DSLContext,
) : StorageQuotaPort {

    /**
     * 사용자 행을 잠가 사용량 판정 ~ 예약 저장 구간을 직렬화한다.
     *
     * 같은 사용자에 대한 동시 요청만 줄을 세우므로 다른 사용자에게는 영향이 없다.
     * 호출한 트랜잭션이 끝나면 잠금도 함께 풀린다.
     */
    override fun lockUserForQuota(userId: Long) {
        dsl.select(ID)
            .from(USERS)
            .where(ID.eq(userId))
            .forUpdate()
            .fetchOne()
    }

    override fun calculateUserStorageBytes(userId: Long, excludeVideoId: Long?): Long {
        // 보관 중인 파일 + 진행 중인 업로드 예약.
        //
        // 예약을 세지 않으면 동시성 우회가 열린다. init 은 fileUrl 이 없는 행을 만드는데,
        // 그 행이 사용량에 잡히지 않으면 같은 사용자가 동시에 여러 건을 시작해 전부 한도를
        // 통과하고 2GB 짜리 서명 URL 을 원하는 만큼 받아갈 수 있다. UPLOADING 구간에는
        // 실제로 오브젝트가 올라가는 중이므로 그 크기를 미리 잡아두는 것이 맞다.
        //
        // 방치된 UPLOADING 이 예약을 영구히 물고 있지 않도록 StaleUploadCleanupUseCase 가
        // 만료된 행을 걷어간다.
        val reservation = DSL.and(
            STATUS_TEXT.eq(UploadStatus.UPLOADING.name),
            FILE_URL.isNull,
            if (excludeVideoId != null) ID.ne(excludeVideoId) else DSL.noCondition(),
        )

        val inFlightSize = dsl.select(DSL.coalesce(DSL.sum(FILE_SIZE_BYTES), 0L))
            .from(VIDEOS)
            .where(USER_ID.eq(userId).and(reservation))
            .fetchOne(0, Long::class.java) ?: 0L

        // 확정된 보관분. 스트리밍 게시는 임시 파일로 플랫폼에 흘려보낸 뒤
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

        return videoSize + assetSize + inFlightSize
    }
}
