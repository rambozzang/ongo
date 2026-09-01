package com.ongo.api.asset

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * V113 URL 컬럼 확장의 문서 수준 가드.
 *
 * ## 왜 필요한가
 *
 * 운영(S3/R2) 다운로드 URL 은 SigV4 서명이 붙어 400~530 자다. 서명 쿼리만 ~314 자이고
 * 호스트·버킷·UUID 가 ~127 자라, 남는 자리는 파일명 몫뿐이다. 오브젝트 키의 한글은
 * 퍼센트 인코딩돼 **글자당 9 자**가 되므로 한글 파일명 하나면 500 자를 넘긴다.
 * 그때 INSERT 는 `22001 value too long` 으로 실패하고 사용자는 원인을 알 수 없다.
 *
 * `videos.file_url`(V1)과 `content_images.image_url`(V13)은 처음부터 TEXT 였다.
 * 에셋과 브랜드킷만 VARCHAR(500) 으로 남아 있었다.
 *
 * 실제 PostgreSQL 적용은 Testcontainers/운영 롤아웃에서 별도로 확인한다.
 */
class AssetUrlColumnMigrationTest {

    private val sql = File(
        "src/main/resources/db/migration/V113__asset_and_brand_url_text.sql",
    ).readText()

    private val normalized = sql.replace(Regex("\\s+"), " ").uppercase()

    /** **핵심.** 이 한 줄이 없으면 한글 파일명 에셋 업로드가 계속 실패한다. */
    @Test
    fun `에셋 파일 URL 컬럼을 TEXT 로 넓힌다`() {
        assertTrue(
            "ALTER TABLE ASSETS ALTER COLUMN FILE_URL TYPE TEXT" in normalized,
            "assets.file_url 확장이 없다 — 한글 파일명 업로드가 22001 로 실패한다",
        )
    }

    /** 브랜드킷은 에셋 URL 을 문자열로 복사해 저장하므로 같은 산술이 적용된다. */
    @Test
    fun `브랜드킷의 네 URL 컬럼을 모두 넓힌다`() {
        listOf("LOGO_URL", "INTRO_TEMPLATE_URL", "OUTRO_TEMPLATE_URL", "WATERMARK_URL").forEach { column ->
            assertTrue(
                "ALTER TABLE BRAND_KITS ALTER COLUMN $column TYPE TEXT" in normalized,
                "brand_kits.$column 이 VARCHAR(500) 으로 남아 저장이 실패한다",
            )
        }
    }

    /**
     * **데이터를 지우거나 다시 만들지 않는다.** 길이 제약만 떼는 변환이라 기존 값이 그대로
     * 남아야 한다. `DROP`·`TRUNCATE` 가 섞이면 되돌릴 수 없는 사고가 된다.
     */
    @Test
    fun `기존 데이터를 지우지 않는다`() {
        listOf("DROP COLUMN", "DROP TABLE", "TRUNCATE", "DELETE FROM").forEach { danger ->
            assertFalse(danger in normalized, "되돌릴 수 없는 문장이 섞였다: $danger")
        }
    }

    /** 넓히기만 한다 — 다시 좁히면 이미 저장된 긴 URL 이 잘린다. */
    @Test
    fun `컬럼을 다시 좁히지 않는다`() {
        assertFalse(
            Regex("TYPE VARCHAR").containsMatchIn(normalized),
            "TEXT 가 아닌 길이 제한 타입으로 바꾸고 있다",
        )
    }
}
