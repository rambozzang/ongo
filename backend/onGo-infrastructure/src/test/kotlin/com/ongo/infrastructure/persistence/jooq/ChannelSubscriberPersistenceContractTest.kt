package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.Platform
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.EncryptedToken
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.conf.StatementType
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `channels.subscriber_count` 의 **읽기·쓰기 계약**.
 *
 * ## 무엇이 거짓이었나
 *
 * 컬럼은 `NOT NULL` 이 아니고 기본값만 `0` 이다(`V1__init_schema.sql:67`). 그런데 저장소가
 *
 * ```
 * subscriberCount = longValue(SUBSCRIBER_COUNT) ?: 0
 * ```
 *
 * 로 읽어, **NULL 로 저장된 행을 조회할 때마다 0 으로 되살렸다.** 저장된 사실이 조회
 * 시점에 바뀌는 셈이라, 어댑터에서 아무리 정직하게 null 을 넣어도 화면에는 "구독자 0명"
 * 이 떴다.
 *
 * 쓰기 쪽도 마찬가지로 null 을 그대로 NULL 로 보내야 한다. 0 으로 바꿔 저장하면
 * **재지 않았다는 사실이 DB 에서 영구히 사라진다.**
 */
class ChannelSubscriberPersistenceContractTest {

    private val ctx = DSL.using(SQLDialect.POSTGRES)

    /** `toChannel` 은 아래 컬럼을 모두 읽는다 — 하나라도 빠지면 매핑 자체가 실패한다. */
    private val workspaceId = DSL.field("workspace_id", Long::class.java)

    private val channelFields = arrayOf(
        Fields.ID, Fields.USER_ID, workspaceId, Fields.PLATFORM, Fields.PLATFORM_CHANNEL_ID,
        Fields.CHANNEL_NAME, Fields.CHANNEL_URL, Fields.SUBSCRIBER_COUNT, Fields.PROFILE_IMAGE_URL,
        Fields.ACCESS_TOKEN, Fields.REFRESH_TOKEN, Fields.TOKEN_EXPIRES_AT, Fields.STATUS,
        Fields.CONNECTED_AT, Fields.UPDATED_AT,
    )

    /** 한 행. [subscriberCount] 만 바꿔 가며 쓴다. */
    private fun channelRow(subscriberCount: Long?) = ctx.newResult(*channelFields).apply {
        add(
            ctx.newRecord(*channelFields).also {
                it.set(Fields.ID, 9L)
                it.set(Fields.USER_ID, 7L)
                it.set(Fields.PLATFORM, Platform.THREADS.name)
                it.set(Fields.PLATFORM_CHANNEL_ID, "ch-1")
                it.set(Fields.CHANNEL_NAME, "내 채널")
                it.set(Fields.SUBSCRIBER_COUNT, subscriberCount)
                it.set(Fields.ACCESS_TOKEN, "token")
                it.set(Fields.STATUS, "ACTIVE")
            },
        )
    }

    private fun repositoryReading(subscriberCount: Long?): ChannelJooqRepository {
        val rows = channelRow(subscriberCount)
        val provider = MockDataProvider { arrayOf(MockResult(rows.size, rows)) }
        return ChannelJooqRepository(DSL.using(MockConnection(provider), SQLDialect.POSTGRES))
    }

    // ── 읽기 ─────────────────────────────────────────────────────────────────

    /** **이 케이스가 저장된 NULL 을 0 으로 되살리던 자리다.** */
    @Test
    @DisplayName("NULL 로 저장된 구독자 수는 null 로 읽는다")
    fun nullColumnStaysNull() {
        val channel = repositoryReading(null).findById(9L)

        assertNull(channel?.subscriberCount, "저장된 NULL 을 0 으로 되살렸다")
    }

    /** **실제로 0 이 저장된 행은 0 이다.** 관측된 구독자 0 명. */
    @Test
    @DisplayName("0 으로 저장된 구독자 수는 0 으로 읽는다")
    fun storedZeroStaysZero() {
        val channel = repositoryReading(0L).findById(9L)

        assertEquals(0L, channel?.subscriberCount, "실측 0 을 잃었다")
    }

    @Test
    @DisplayName("저장된 값은 그대로 읽는다")
    fun storedCountIsPreserved() {
        val channel = repositoryReading(8_000L).findById(9L)

        assertEquals(8_000L, channel?.subscriberCount)
    }

    // ── 쓰기 ─────────────────────────────────────────────────────────────────

    /**
     * 실행된 SQL 을 값까지 렌더링해 잡는다. 바인드 파라미터로 두면 값이 `?` 로 나와
     * NULL 을 보냈는지 0 을 보냈는지 구분할 수 없다.
     */
    private fun executedSqlOnSave(subscriberCount: Long?): List<String> {
        val executed = mutableListOf<String>()
        val provider = MockDataProvider { context ->
            executed += context.sql()
            arrayOf(MockResult(1, channelRow(subscriberCount)))
        }
        val settings = Settings().withStatementType(StatementType.STATIC_STATEMENT)
        val repository = ChannelJooqRepository(
            DSL.using(MockConnection(provider), SQLDialect.POSTGRES, settings),
        )

        repository.save(
            Channel(
                userId = 7L,
                platform = Platform.THREADS,
                platformChannelId = "ch-1",
                channelName = "내 채널",
                subscriberCount = subscriberCount,
                accessToken = EncryptedToken("token"),
            ),
        )
        return executed
    }

    /*
     * INSERT 의 값 목록은 `..., channel_url, subscriber_count, profile_image_url, access_token`
     * 순서다. 앞뒤 두 칸은 이 픽스처에서 항상 `null` 이라, 그 사이 값만 바꿔 가며 앵커로
     * 쓸 수 있다. 그냥 `"0" in sql` 로 보면 다른 칸의 0 에도 걸려 항상 통과한다.
     */
    private fun subscriberSlot(value: String) = "null, $value, null, 'token'"

    /** **재지 않았다는 사실을 0 으로 바꿔 저장하지 않는다.** */
    @Test
    @DisplayName("구독자 수가 null 이면 NULL 로 저장한다")
    fun nullIsWrittenAsNull() {
        val sql = executedSqlOnSave(null).joinToString("\n")

        assertTrue(subscriberSlot("null") in sql, "NULL 을 보내지 않았다:\n$sql")
        assertFalse(subscriberSlot("0") in sql, "null 을 0 으로 바꿔 저장했다:\n$sql")
    }

    /** **실측 0 은 그대로 0 으로 저장한다.** */
    @Test
    @DisplayName("실측 0 은 0 으로 저장한다")
    fun measuredZeroIsWrittenAsZero() {
        val sql = executedSqlOnSave(0L).joinToString("\n")

        assertTrue(subscriberSlot("0") in sql, "실측 0 이 사라졌다:\n$sql")
    }
}
