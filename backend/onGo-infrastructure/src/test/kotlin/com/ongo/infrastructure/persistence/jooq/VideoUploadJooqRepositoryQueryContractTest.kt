package com.ongo.infrastructure.persistence.jooq

import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * video_uploads.status is a PostgreSQL enum.  A plain jOOQ String bind renders
 * `status = ?`, which PostgreSQL rejects as `upload_status = varchar` when
 * server-side prepared statements are enabled.  The scheduler must compare
 * the text projection instead, while writes use the explicit enum cast.
 */
class VideoUploadJooqRepositoryQueryContractTest {

    private val executed = mutableListOf<String>()
    private lateinit var repository: VideoUploadJooqRepository

    @BeforeEach
    fun setUp() {
        executed.clear()
        val empty = DSL.using(SQLDialect.POSTGRES).newResult()
        val provider = MockDataProvider { ctx ->
            executed += ctx.sql()
            arrayOf(MockResult(0, empty))
        }
        repository = VideoUploadJooqRepository(
            DSL.using(MockConnection(provider), SQLDialect.POSTGRES),
        )
    }

    @Test
    @DisplayName("예약·재시도·폴링 조회는 PostgreSQL enum을 text projection으로 비교한다")
    fun schedulerQueriesDoNotBindUploadStatusAsVarchar() {
        val now = LocalDateTime.of(2026, 8, 10, 12, 0)

        repository.findDueScheduledUploads(now)
        repository.findDueRetryUploads(now)
        repository.findDueProcessingUploads(now)
        repository.recoverExpiredLeases(now)

        assertTrue(executed.isNotEmpty())
        executed.forEach { sql ->
            val normalized = sql.lowercase()
            assertTrue(
                normalized.contains("status::text"),
                "upload_status 비교가 varchar bind로 렌더링되었습니다: $sql",
            )
        }
    }
}
