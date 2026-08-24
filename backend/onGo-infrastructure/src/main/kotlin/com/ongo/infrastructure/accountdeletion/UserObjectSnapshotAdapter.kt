package com.ongo.infrastructure.accountdeletion

import com.ongo.domain.accountdeletion.UserObjectSnapshot
import com.ongo.domain.accountdeletion.UserObjectSnapshotPort
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_URL
import com.ongo.infrastructure.persistence.jooq.Fields.STORAGE_OBJECT_KEY
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.ASSETS
import com.ongo.infrastructure.persistence.jooq.Tables.VIDEOS
import org.jooq.DSLContext
import org.springframework.stereotype.Component

/**
 * 탈퇴 대상이 소유한 우리 버킷 객체 키를 모은다.
 *
 * `videos` 는 직접 업로드·URL 임포트·쇼츠 렌더 산출물을 모두 담는 하나의 테이블이라
 * 여기서 한 번에 덮인다. `assets` 는 별도 테이블이라 따로 읽는다.
 */
@Component
class UserObjectSnapshotAdapter(
    private val dsl: DSLContext,
) : UserObjectSnapshotPort {

    override fun snapshot(userId: Long): UserObjectSnapshot {
        val ownedKeys = (readKeys(userId, videos = true) + readKeys(userId, videos = false)).distinct()

        /*
         * 파일은 있는데 키가 없는 행.
         *
         * V96 이전에 만들어진 행이 여기 해당한다. URL 에서 키를 되짚어 지우고 싶겠지만
         * 하지 않는다 — file_url 은 서명이 붙은 presigned URL 이고 경로 형식도 어댑터마다
         * 달라서, 추측이 빗나가면 남의 파일을 지운다. 되돌릴 수 없는 작업이라 추측을 섞지 않는다.
         *
         * 대신 세어서 올려보낸다. 0 이 아니면 job 을 자동 완료시키지 않고 사람이 본다.
         */
        val unresolved = countUnresolved(userId, videos = true) + countUnresolved(userId, videos = false)

        /*
         * 다른 살아있는 사용자가 같은 키를 가리키면 제외한다.
         *
         * 반복 예약(RecurringScheduleExecutor)이 객체를 복제해 쓰기 때문에 같은 키를 두 행이
         * 가리킬 수 있다. 탈퇴자 쪽만 보고 지우면 남은 사용자의 영상이 사라진다.
         */
        val shared = if (ownedKeys.isEmpty()) emptySet() else sharedWithOtherUsers(userId, ownedKeys)

        return UserObjectSnapshot(
            deletableKeys = ownedKeys.filterNot { it in shared },
            unresolvedRowCount = unresolved,
            sharedKeyCount = shared.size,
        )
    }

    private fun readKeys(userId: Long, videos: Boolean): List<String> =
        dsl.select(STORAGE_OBJECT_KEY)
            .from(if (videos) VIDEOS else ASSETS)
            .where(USER_ID.eq(userId))
            .and(STORAGE_OBJECT_KEY.isNotNull)
            .fetch(STORAGE_OBJECT_KEY)
            .filterNotNull()
            .filter { it.isNotBlank() }

    /** 파일 URL 은 있는데 키가 없어 우리 버킷 객체라는 증거가 없는 행. */
    private fun countUnresolved(userId: Long, videos: Boolean): Int =
        dsl.selectCount()
            .from(if (videos) VIDEOS else ASSETS)
            .where(USER_ID.eq(userId))
            .and(FILE_URL.isNotNull)
            .and(STORAGE_OBJECT_KEY.isNull)
            .fetchOne(0, Int::class.java) ?: 0

    private fun sharedWithOtherUsers(userId: Long, keys: List<String>): Set<String> {
        val fromVideos = dsl.select(STORAGE_OBJECT_KEY)
            .from(VIDEOS)
            .where(USER_ID.ne(userId))
            .and(STORAGE_OBJECT_KEY.`in`(keys))
            .fetch(STORAGE_OBJECT_KEY)
        val fromAssets = dsl.select(STORAGE_OBJECT_KEY)
            .from(ASSETS)
            .where(USER_ID.ne(userId))
            .and(STORAGE_OBJECT_KEY.`in`(keys))
            .fetch(STORAGE_OBJECT_KEY)
        return (fromVideos + fromAssets).filterNotNull().toSet()
    }
}
