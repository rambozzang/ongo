package com.ongo.infrastructure.persistence.jooq

import org.jooq.DSLContext
import org.jooq.Table
import org.junit.jupiter.api.Assertions.assertTrue
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
 * `Tables.kt` 가 선언한 테이블이 마이그레이션 적용 결과에 실제로 존재하는지 고정한다.
 *
 * 이 가드가 없어서 실제로 사고가 났다. `competitor_analytics_daily` 는 `Tables.kt` 와
 * jOOQ 쿼리에 있었지만 `CREATE TABLE` 이 어느 마이그레이션에도 없었고, 전수 조사에서
 * 같은 상태의 테이블이 6개 더 나왔다. 그중 4개는 `@Profile("wip")` 없는 컨트롤러가 쓰는
 * 살아 있는 경로라 사용자에게 500 이 나가는 상태였다(V61, V62 에서 수정).
 *
 * 컴파일은 통과한다. `DSL.table("...")` 은 문자열이라 스키마를 모른다. 그래서 이 결함은
 * 런타임에 `relation does not exist` 로만 드러나고, 해당 경로를 지나는 테스트가 없으면
 * CI 도 잡지 못한다. 실제로 그랬다.
 *
 * 소스를 파싱하지 않고 리플렉션으로 읽는다. 파싱은 포맷이 바뀌면 조용히 헛돌지만
 * 리플렉션은 컴파일된 실제 선언을 본다.
 *
 * 스키마는 `onGo-api` 의 `db/migration` 을 Flyway 가 그대로 적용한 결과다
 * (`onGo-infrastructure/build.gradle.kts` 가 test classpath 로 공유한다).
 * 즉 여기서 통과하면 운영에 적용될 마이그레이션 기준으로 통과한 것이다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class SchemaDriftGuardIT {

    @Autowired lateinit var dsl: DSLContext

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

        /**
         * 테이블이 없어도 되는 선언. 늘리려면 근거가 있어야 한다.
         *
         * 이 둘은 `ABTestResultRepository` / `TestVariantRepository` 가 쓰는데, 두 인터페이스를
         * `onGo-application`·`onGo-api` 에서 주입받는 곳이 0건이다. jOOQ 어댑터만 있고 소비자가
         * 없는 죽은 코드라 테이블을 만들지 않기로 했다.
         *
         * 헷갈리기 쉬운 지점: `TestVariantRepository` 는 `ABTestVariantRepository` 와 다른
         * 인터페이스다. 후자는 매시간 도는 `ABTestEvaluator` 가 쓰지만 `ab_tests` /
         * `ab_test_variants` 만 참조하고 둘 다 실존한다.
         *
         * 근거: `docs/plans/schema-drift-audit.md` §2.1
         */
        private val DEAD_CODE_TABLES = setOf("ab_test_results", "test_variants")
    }

    /** `Tables` 오브젝트가 선언한 모든 jOOQ 테이블 이름. */
    private fun declaredTableNames(): List<String> =
        Tables::class.java.declaredFields
            .filter { Table::class.java.isAssignableFrom(it.type) }
            .map { field ->
                field.isAccessible = true
                (field.get(Tables) as Table<*>).name
            }

    /**
     * 실존하는 실테이블 이름.
     *
     * `BASE TABLE` 로 한정한다. 무엇이 포함되는지 명시해 둔다.
     * - 뷰: 이 스키마에는 0건이다(`CREATE VIEW` 가 마이그레이션에 없다). 생기면 여기서
     *   제외되므로, 뷰를 `Tables.kt` 에 선언하게 되면 이 조건을 함께 고쳐야 한다.
     * - 파티션: `analytics_daily` 는 `PARTITION BY RANGE (date)` 다. 부모가 `BASE TABLE`
     *   로 잡히므로 선언과 대조하는 데 문제가 없다. 월별 자식 파티션도 `BASE TABLE` 이지만
     *   `Tables.kt` 에는 없고, 이 가드는 선언 → 실존 방향만 보므로 영향이 없다.
     */
    private fun existingTableNames(): Set<String> =
        dsl.fetch(
            """
            SELECT table_name FROM information_schema.tables
            WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
            """.trimIndent()
        ).map { it.get(0, String::class.java) }.toSet()

    @Test
    @DisplayName("Tables.kt 가 선언한 테이블은 마이그레이션에 전부 존재해야 한다")
    fun everyDeclaredTableExists() {
        val declared = declaredTableNames()
        val existing = existingTableNames()

        // 리플렉션이 헛돌면(선언 0건) 이 테스트는 아무것도 검증하지 못한 채 통과한다.
        // 통과의 의미를 지키기 위해 먼저 확인한다.
        assertTrue(declared.size > 100) {
            "Tables 오브젝트에서 테이블 선언을 ${declared.size}개만 읽었다. " +
                "리플렉션이 깨졌을 가능성이 높다. 이 상태의 통과는 의미가 없다"
        }

        val missing = declared.filterNot { it in existing || it in DEAD_CODE_TABLES }.sorted()

        assertTrue(missing.isEmpty()) {
            buildString {
                append("Tables.kt 가 선언했지만 마이그레이션에 없는 테이블이 ${missing.size}개 있다.\n")
                append("컴파일은 통과하지만 이 테이블을 쓰는 경로는 런타임에 ")
                append("relation does not exist 로 실패한다.\n")
                missing.forEach { append("  - $it\n") }
                append("\n마이그레이션을 추가하거나, 죽은 코드라면 근거와 함께 ")
                append("DEAD_CODE_TABLES 에 넣어라.")
            }
        }
    }

    @Test
    @DisplayName("죽은 코드 예외 목록은 실제로 없는 테이블만 담아야 한다")
    fun deadCodeExceptionsStayJustified() {
        val existing = existingTableNames()
        val nowExisting = DEAD_CODE_TABLES.filter { it in existing }.sorted()

        // 예외로 둔 테이블이 나중에 만들어졌다면 예외를 유지할 이유가 사라진다.
        // 남겨두면 그 테이블은 이 가드의 사각지대가 된다.
        assertTrue(nowExisting.isEmpty()) {
            "DEAD_CODE_TABLES 에 있는데 실제로는 존재하는 테이블이 있다: " +
                "${nowExisting.joinToString()}. 예외 목록에서 빼라"
        }
    }
}
