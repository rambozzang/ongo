package com.ongo.api.analytics

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * V115 참여 지표 basis 도입의 문서 수준 가드.
 *
 * ## 왜 필요한가
 *
 * 어댑터 13개 중 YouTube 하나만 기간값을 준다. 나머지 12개는 평생 누적 카운터를 주는데
 * 그것이 날짜별 행에 그대로 저장돼 기간 합산됐다 — 30일 창에서 조회수 약 30배.
 *
 * 이 마이그레이션은 **기존 행을 지우지 않고 다시 라벨링한다.** 누적 플랫폼 행에 들어
 * 있던 숫자는 쓰레기가 아니라 관측 시점의 평생 누적 스냅샷이고, 우리가 그것을 "그 날의
 * 증분" 이라 잘못 부르고 있었을 뿐이다. 값은 `*_total` 로 옮기고 basis 로 합계에서 뺀다.
 *
 * 실제 PostgreSQL 적용은 Testcontainers/운영 롤아웃에서 별도로 확인한다.
 */
class EngagementBasisMigrationTest {

    private val sql = File(
        "src/main/resources/db/migration/V115__analytics_engagement_basis.sql",
    ).readText()

    private val normalized = sql.replace(Regex("\\s+"), " ").uppercase()

    /** **핵심.** 이 컬럼이 없으면 합계가 계속 부풀어 오른다. */
    @Test
    fun `참여 지표의 의미를 담는 컬럼을 추가한다`() {
        assertTrue(
            "ENGAGEMENT_BASIS VARCHAR(20) NOT NULL DEFAULT 'LEGACY_CUMULATIVE'" in normalized,
            "engagement_basis 컬럼이 없다 — 누적값과 증분을 구분할 수 없다",
        )
    }

    /**
     * **기본값이 LEGACY_CUMULATIVE 인 것이 fail-closed 의 핵심이다.**
     *
     * INCREMENTAL 이 기본값이면, basis 설정을 빠뜨린 행이 조용히 합계에 섞여 숫자를
     * 부풀린다. 빠지는 쪽은 눈에 띄고 섞이는 쪽은 눈에 띄지 않는다.
     */
    @Test
    fun `라벨 없는 행은 합계에서 빠지도록 기본값을 잡는다`() {
        assertFalse(
            "DEFAULT 'INCREMENTAL'" in normalized,
            "기본값이 INCREMENTAL 이면 라벨 누락이 합계를 조용히 부풀린다",
        )
    }

    /** 다음 주기의 차분을 낼 기준선. 이 칸이 없으면 증분을 영원히 계산할 수 없다. */
    @Test
    fun `누적 스냅샷 칸 네 개를 추가한다`() {
        listOf("VIEWS_TOTAL", "LIKES_TOTAL", "COMMENTS_TOTAL", "SHARES_TOTAL").forEach { column ->
            assertTrue(
                "$column BIGINT" in normalized,
                "$column 이 없다 — 다음 주기의 증분을 구할 기준선이 사라진다",
            )
        }
    }

    /** 오타로 새 라벨이 새어 들어오면 화면이 그 행을 어느 쪽으로도 해석하지 못한다. */
    @Test
    fun `허용 라벨을 제약으로 못 박는다`() {
        assertTrue(
            "CHECK (ENGAGEMENT_BASIS IN ('INCREMENTAL', 'BASELINE', 'LEGACY_CUMULATIVE'))" in normalized,
            "engagement_basis CHECK 제약이 없다",
        )
        // PostgreSQL 의 ADD CONSTRAINT 에는 IF NOT EXISTS 가 없다. 선행 DROP 이 없으면
        // 락 상한에 걸려 중단된 뒤 재시도할 수 없다.
        assertTrue(
            "DROP CONSTRAINT IF EXISTS CHK_ANALYTICS_ENGAGEMENT_BASIS" in normalized,
            "선행 DROP 이 없어 이 마이그레이션은 한 번만 적용할 수 있다",
        )
    }

    /**
     * YouTube 행은 처음부터 Analytics API 의 기간값이라 지금도 합산해서 맞다.
     * 이 백필이 없으면 **정상이던 YouTube 데이터까지 화면에서 사라진다.**
     */
    @Test
    fun `기존 YouTube 행을 INCREMENTAL 로 표시한다`() {
        assertTrue(
            "SET ENGAGEMENT_BASIS = 'INCREMENTAL'" in normalized,
            "YouTube 행 백필이 없다 — 멀쩡한 기간값 데이터가 합계에서 빠진다",
        )
        assertTrue(
            "VU.PLATFORM = 'YOUTUBE'" in normalized,
            "플랫폼 조건이 없다 — 누적 플랫폼까지 INCREMENTAL 이 되면 수정 자체가 무의미하다",
        )
    }

    /** 누적 플랫폼의 기존 값은 스냅샷 칸으로 옮겨 보존한다. 지우지 않는다. */
    @Test
    fun `누적 플랫폼의 기존 값을 스냅샷 칸으로 옮긴다`() {
        assertTrue(
            "VIEWS_TOTAL = COALESCE(AD.VIEWS_TOTAL, AD.VIEWS)" in normalized,
            "기존 누적값을 스냅샷으로 옮기지 않는다 — 기준선 없이 처음부터 다시 쌓아야 한다",
        )
        assertTrue(
            "VU.PLATFORM <> 'YOUTUBE'" in normalized,
            "누적 플랫폼 조건이 없다",
        )
    }

    /**
     * **차분으로 과거를 복원하지 않는다.**
     *
     * 백필 루프가 오늘의 누적값을 과거 날짜들에 써 넣었기 때문에, 그 행들의 차분은
     * 대부분 0 이고 하루만 거대한 값이 된다. 그것은 복원이 아니라 날조다.
     */
    @Test
    fun `기존 데이터를 지우거나 테이블을 다시 만들지 않는다`() {
        listOf("DROP COLUMN", "DROP TABLE", "TRUNCATE", "DELETE FROM").forEach { danger ->
            assertFalse(danger in normalized, "되돌릴 수 없는 문장이 섞였다: $danger")
        }
    }

    /**
     * 이 ALTER 는 부모와 모든 월별 파티션에 ACCESS EXCLUSIVE 를 잡고, 구 서비스가 살아
     * 있는 동안 돈다. 서버 측 상한이 없으면 롱 리드 뒤에 줄을 서서 운영 전체를 막는다.
     * 클라이언트 타임아웃으로는 끊을 수 없다 — JVM 을 죽여도 서버의 ALTER 는 계속 돈다.
     */
    @Test
    fun `파티션 락을 오래 붙들지 않도록 서버 측 상한을 건다`() {
        assertTrue(
            "SET LOCAL LOCK_TIMEOUT" in normalized,
            "lock_timeout 이 없다 — 롱 리드 뒤에 줄을 서면 운영 조회가 전부 막힌다",
        )
    }
}
