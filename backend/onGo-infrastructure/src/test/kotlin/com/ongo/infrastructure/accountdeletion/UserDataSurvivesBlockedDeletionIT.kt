package com.ongo.infrastructure.accountdeletion

import com.ongo.domain.accountdeletion.AccountDeletionPreflight
import com.ongo.domain.accountdeletion.UserFkScanner
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * 계정 삭제가 막힐 때 **사용자 데이터가 그대로 남는지** 실제 PostgreSQL 로 확인한다.
 *
 * 예전 경로는 `DELETE FROM users` 를 바로 실행했고, `users` 를 `ON DELETE CASCADE` 로
 * 참조하는 17개가 함께 사라졌다. 그 안에 `payments`, `subscriptions`,
 * `ai_credit_transactions` 가 있었다. 결제 이력이 감사 근거 없이 사라진다는 뜻이다.
 *
 * 목 테스트(`DeleteAccountNeverDeletesTest`)는 "저장소 메서드를 부르지 않는다"까지만
 * 증명한다. 실제 행이 남는지는 DB 가 있어야 안다. 여기서 그걸 본다.
 *
 * 실제 스캐너(`UserFkJooqScanner`)를 쓰므로 `pg_constraint` 조회와 행 수 세기가
 * 진짜 스키마에서 동작하는지도 함께 검증된다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class UserDataSurvivesBlockedDeletionIT {

    @Autowired lateinit var dsl: DSLContext
    @Autowired lateinit var scanner: UserFkScanner

    companion object {
        @Container @JvmStatic
        val pg = PostgreSQLContainer("postgres:16").apply {
            withDatabaseName("ongo_test")
            withUsername("test"); withPassword("test")
        }

        @JvmStatic @DynamicPropertySource
        fun props(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url") { pg.jdbcUrl }
            r.add("spring.datasource.username") { pg.username }
            r.add("spring.datasource.password") { pg.password }
        }

        private const val EMAIL = "survives@test.io"
    }

    private fun userId(): Long =
        dsl.fetchOne("SELECT id FROM users WHERE email = ?", EMAIL)!!.get(0, Long::class.java)

    private fun count(sql: String, vararg args: Any): Int =
        dsl.fetchOne(sql, *args)!!.get(0, Int::class.java)

    @BeforeEach
    fun setUp() {
        // 자식부터 지운다. competitors 는 NO ACTION 이라 남아 있으면 사용자 삭제가 막힌다.
        // (이 정리 코드가 바로 이 테스트가 다루는 결함에 걸렸었다)
        dsl.execute(
            "DELETE FROM competitors WHERE user_id IN (SELECT id FROM users WHERE email = ?)", EMAIL
        )
        dsl.execute("DELETE FROM users WHERE email = ?", EMAIL)

        dsl.execute(
            """
            INSERT INTO users (email, name, provider, provider_id, role, plan_type)
            VALUES (?, 'survives', 'GOOGLE', 'survives', 'USER', 'PRO')
            """.trimIndent(),
            EMAIL,
        )
        val uid = userId()
        dsl.execute("INSERT INTO videos (user_id, title) VALUES (?, 'survives-video')", uid)
        dsl.execute(
            """
            INSERT INTO payments (user_id, amount, currency, status, type)
            VALUES (?, 19900, 'KRW', 'COMPLETED', 'SUBSCRIPTION')
            """.trimIndent(),
            uid,
        )
        // 판단 미완(REVIEW_BLOCK) 데이터를 하나 넣어 사용자별 차단을 유발한다.
        dsl.execute(
            """
            INSERT INTO competitors (user_id, platform, platform_channel_id, channel_name)
            VALUES (?, 'YOUTUBE', 'survives-ch', '경쟁채널')
            """.trimIndent(),
            uid,
        )
    }

    @Test
    @DisplayName("차단된 사용자의 결제·영상 데이터가 한 건도 사라지지 않는다")
    fun blockedDeletionLeavesEveryRowIntact() {
        val uid = userId()
        val before = Triple(
            count("SELECT count(*) FROM payments WHERE user_id = ?", uid),
            count("SELECT count(*) FROM videos WHERE user_id = ?", uid),
            count("SELECT count(*) FROM competitors WHERE user_id = ?", uid),
        )
        assertEquals(Triple(1, 1, 1), before) { "사전 조건이 갖춰지지 않았다: $before" }

        val result = AccountDeletionPreflight.evaluate(
            actualFks = scanner.actualUserFks(),
            userRowCounter = { key -> scanner.countRowsFor(key, uid) },
        )

        assertTrue(result is AccountDeletionPreflight.Result.BlockedForUser) {
            "REVIEW_BLOCK 데이터를 가진 사용자는 사용자별 차단이어야 한다. 실제: $result"
        }
        assertTrue(
            (result as AccountDeletionPreflight.Result.BlockedForUser)
                .blocking.any { it.key.table == "competitors" }
        ) { "차단 사유에 competitors 가 있어야 한다" }

        val after = Triple(
            count("SELECT count(*) FROM payments WHERE user_id = ?", uid),
            count("SELECT count(*) FROM videos WHERE user_id = ?", uid),
            count("SELECT count(*) FROM competitors WHERE user_id = ?", uid),
        )
        assertEquals(before, after) { "판정만 했는데 데이터가 변했다: $before -> $after" }
        assertEquals(1, count("SELECT count(*) FROM users WHERE email = ?", EMAIL)) {
            "사용자 행이 사라졌다"
        }
    }

    @Test
    @DisplayName("실제 스키마의 users 참조 외래키를 전부 읽고 전역 차단이 걸리지 않는다")
    fun scannerReadsEveryUserFkAndRegistryIsComplete() {
        val fks = scanner.actualUserFks()

        assertTrue(fks.size > 100) { "users 참조 외래키를 ${fks.size}건만 읽었다" }

        // 레지스트리가 완전하면 전역 차단은 나오지 않는다. 나오면 새 외래키가 분류되지 않은 것이다.
        val result = AccountDeletionPreflight.evaluate(
            actualFks = fks,
            userRowCounter = { 0 },
        )
        assertTrue(result !is AccountDeletionPreflight.Result.BlockedGlobally) {
            "분류되지 않은 외래키가 있다: $result"
        }
    }
}
