package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.UploadStatus
import com.ongo.domain.storage.StorageQuotaPort
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_SIZE_BYTES
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_URL
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.ASSETS
import com.ongo.infrastructure.persistence.jooq.Tables.CONTENT_IMAGES
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

        /*
         * 게시 이미지. **이것이 빠져 있어서 이미지 게시가 요금제 한도를 통째로 우회했다.**
         *
         * `content_images` 에는 소유자가 없으므로 `videos` 로 이어 붙여 찾는다. 두 테이블
         * 모두 `id`·`file_size_bytes`·`created_at` 을 가지고 있어 컬럼을 전부 한정한다 —
         * 한정을 빼면 어느 쪽 크기를 더하는지 SQL 이 정하고, 합계가 조용히 달라진다.
         *
         * `excludeVideoId` 는 적용하지 않는다. 그 인자는 **확정 중인 자기 예약분**을 두 번
         * 세지 않기 위한 것이고, 예약(`UPLOADING` + `file_url IS NULL`)인 영상에는 이미지가
         * 달릴 수 없다. 여기서 빼면 실제로 차지하고 있는 용량을 놓친다.
         */
        val contentImageSize = dsl.select(DSL.coalesce(DSL.sum(CONTENT_IMAGE_FILE_SIZE), 0L))
            .from(CONTENT_IMAGES)
            .join(VIDEOS).on(CONTENT_IMAGE_VIDEO_ID.eq(VIDEO_ROW_ID))
            .where(VIDEO_OWNER_ID.eq(userId))
            .fetchOne(0, Long::class.java) ?: 0L

        return videoSize + assetSize + inFlightSize + contentImageSize
    }

    private companion object {
        /** 두 테이블이 같은 컬럼명을 가지므로 반드시 한정한다. */
        val CONTENT_IMAGE_FILE_SIZE = DSL.field(DSL.name("content_images", "file_size_bytes"), Long::class.java)
        val CONTENT_IMAGE_VIDEO_ID = DSL.field(DSL.name("content_images", "video_id"), Long::class.java)
        val VIDEO_ROW_ID = DSL.field(DSL.name("videos", "id"), Long::class.java)
        val VIDEO_OWNER_ID = DSL.field(DSL.name("videos", "user_id"), Long::class.java)
    }
}
