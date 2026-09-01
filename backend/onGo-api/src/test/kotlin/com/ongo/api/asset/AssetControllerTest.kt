package com.ongo.api.asset

import com.ongo.application.asset.AssetUseCase
import com.ongo.application.asset.dto.AssetListResponse
import com.ongo.application.asset.dto.AssetResponse
import com.ongo.common.exception.FileValidationException
import com.ongo.domain.asset.AssetQuery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 업로드 **키 생성**과 목록 **입력 방어**를 고정한다.
 *
 * ## 왜 키 정제가 필요한가
 *
 * 오브젝트 키의 한글은 URL 에서 퍼센트 인코딩돼 글자당 9 자가 된다. 서명 쿼리(~314 자)와
 * 호스트·UUID(~127 자)만으로 이미 441 자라, 한글 파일명 하나면 presigned URL 이 500 자를
 * 넘긴다. 게다가 스토리지 어댑터는 키를 `^[a-zA-Z0-9\-_./]+$` 로 검사한다
 * (`S3StorageClient.validateStorageKey`) — 한글·공백이 섞이면 URL 발급에서 터진다.
 *
 * 두 문제 모두 **업로드 직후 로컬(MinIO)에서는 드러나지 않는다.** MinIO 는 서명도 길이도
 * 문제 삼지 않기 때문이다. 그래서 여기서 키 모양을 직접 못 박는다.
 */
class AssetControllerTest {

    private val assetUseCase = mockk<AssetUseCase>()
    private val controller = AssetController(assetUseCase)

    private val userId = 100L

    @BeforeEach
    fun setUp() {
        every { assetUseCase.createAsset(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            AssetResponse(
                id = 1L, filename = "f", originalFilename = "o", fileUrl = "https://r2.test/x",
                fileType = "VIDEO", fileSizeBytes = 10L, mimeType = "video/mp4",
                tags = emptyList(), folder = "default", width = null, height = null,
                durationSeconds = null, createdAt = null,
            )
        every { assetUseCase.listAssets(any(), any(), any(), any()) } returns
            AssetListResponse(assets = emptyList(), totalCount = 0)
    }

    /** mp4 헤더. `FileValidationUtil.validateAssetContent` 가 매직 바이트를 본다. */
    private fun mp4(name: String) = MockMultipartFile(
        "file", name, "video/mp4",
        byteArrayOf(0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x6D, 0x70, 0x34, 0x32) + ByteArray(64),
    )

    private fun uploadedKey(originalName: String): String {
        val key = slot<String>()
        every {
            assetUseCase.createAsset(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), capture(key),
            )
        } returns AssetResponse(
            id = 1L, filename = "f", originalFilename = originalName, fileUrl = "https://r2.test/x",
            fileType = "VIDEO", fileSizeBytes = 10L, mimeType = "video/mp4",
            tags = emptyList(), folder = "default", width = null, height = null,
            durationSeconds = null, createdAt = null,
        )
        controller.uploadAsset(userId, mp4(originalName), "default", null)
        return key.captured
    }

    /* ── 키 정제 ──────────────────────────────────────────────────────── */

    /**
     * **핵심 회귀.** 한글 파일명이 키에 그대로 들어가면 URL 길이와 키 검증이 함께 깨진다.
     */
    @Test
    @DisplayName("한글 파일명도 ASCII 안전 키로 저장한다")
    fun koreanFilenameBecomesAnAsciiSafeKey() {
        val key = uploadedKey("여름 브이로그 (최종).mp4")

        assertTrue(
            key.matches(Regex("^[a-zA-Z0-9\\-_./]+$")),
            "스토리지가 거부하고 URL 길이를 폭발시키는 키를 만들었다: $key",
        )
        assertTrue(key.startsWith("assets/$userId/"), "소유자 접두사가 아니다: $key")
        assertTrue(key.endsWith(".mp4"), "확장자를 잃었다: $key")
    }

    /** 경로 문자는 키를 다른 접두사로 옮긴다 — 정제에서 함께 걸러져야 한다. */
    @Test
    @DisplayName("경로 문자가 섞여도 소유자 접두사를 벗어나지 않는다")
    fun pathCharactersCannotEscapeTheOwnerPrefix() {
        // 컨트롤러가 `..`·`/`·`\` 가 든 파일명을 먼저 거절한다.
        listOf("../../etc/passwd.mp4", "a/b.mp4", "a\\b.mp4").forEach { name ->
            assertFailsWith<FileValidationException>("$name 을 통과시켰다") {
                controller.uploadAsset(userId, mp4(name), "default", null)
            }
        }
    }

    /** 정제해도 확장자·구분자는 남아야 사람이 읽을 수 있다. */
    @Test
    @DisplayName("ASCII 파일명은 그대로 유지한다")
    fun asciiFilenamesAreLeftAlone() {
        val key = uploadedKey("summer-vlog_final.mp4")

        assertTrue(key.endsWith("_summer-vlog_final.mp4"), "멀쩡한 이름을 바꿨다: $key")
    }

    /**
     * 저장 이름은 정제하되 **원본 이름은 그대로** 넘겨야 화면이 사람이 읽을 이름을 보여 준다.
     */
    @Test
    @DisplayName("원본 파일명은 정제하지 않고 그대로 전달한다")
    fun theOriginalNameIsPreservedForDisplay() {
        val original = slot<String>()
        every {
            assetUseCase.createAsset(
                any(), any(), capture(original), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            )
        } returns AssetResponse(
            id = 1L, filename = "f", originalFilename = "o", fileUrl = "https://r2.test/x",
            fileType = "VIDEO", fileSizeBytes = 10L, mimeType = "video/mp4",
            tags = emptyList(), folder = "default", width = null, height = null,
            durationSeconds = null, createdAt = null,
        )

        controller.uploadAsset(userId, mp4("여름 브이로그.mp4"), "default", null)

        assertEquals("여름 브이로그.mp4", original.captured)
    }

    /* ── 목록 입력 방어 ───────────────────────────────────────────────── */

    /**
     * **상한이 없으면 한 번에 전 행을 읽는다.** 게다가 행마다 다운로드 URL 을 새로 서명하는
     * 비용이 함께 든다. 다른 목록 질의도 같은 이유로 상한을 둔다.
     */
    @Test
    @DisplayName("size 는 1..100 으로 제한한다")
    fun pageSizeIsBounded() {
        val size = slot<Int>()
        every { assetUseCase.listAssets(any(), any(), any(), capture(size)) } returns
            AssetListResponse(assets = emptyList(), totalCount = 0)

        controller.listAssets(userId, null, null, null, null, 0, 1_000_000)
        assertEquals(100, size.captured, "상한을 넘겨 받았다")

        controller.listAssets(userId, null, null, null, null, 0, 0)
        assertEquals(1, size.captured, "0 건 조회는 무한 페이지가 된다")

        controller.listAssets(userId, null, null, null, null, 0, -5)
        assertEquals(1, size.captured, "음수 크기를 그대로 넘겼다")
    }

    /** 음수 페이지는 음수 offset 이 되어 SQL 이 터진다. */
    @Test
    @DisplayName("음수 page 는 첫 페이지로 되돌린다")
    fun negativePageIsClamped() {
        val page = slot<Int>()
        every { assetUseCase.listAssets(any(), any(), capture(page), any()) } returns
            AssetListResponse(assets = emptyList(), totalCount = 0)

        controller.listAssets(userId, null, null, null, null, -3, 20)

        assertEquals(0, page.captured)
    }

    /** 화면이 거는 조건이 그대로 서버 조건이 돼야 총계와 목록이 맞는다. */
    @Test
    @DisplayName("필터 파라미터를 조회 조건으로 그대로 넘긴다")
    fun filtersReachTheUseCase() {
        val query = slot<AssetQuery>()
        every { assetUseCase.listAssets(any(), capture(query), any(), any()) } returns
            AssetListResponse(assets = emptyList(), totalCount = 0)

        controller.listAssets(userId, "VIDEO", "brand-kit", "여름", "logo", 2, 24)

        assertEquals(AssetQuery(fileType = "VIDEO", folder = "brand-kit", search = "여름", tag = "logo"), query.captured)
    }

    @Test
    @DisplayName("조건이 없으면 빈 조회 조건을 넘긴다")
    fun noFiltersMeansAnEmptyQuery() {
        val query = slot<AssetQuery>()
        every { assetUseCase.listAssets(any(), capture(query), any(), any()) } returns
            AssetListResponse(assets = emptyList(), totalCount = 0)

        controller.listAssets(userId, null, null, null, null, 0, 20)

        assertEquals(AssetQuery(), query.captured)
    }

    /** 총계는 use case 가 준 값을 그대로 내보낸다 — 컨트롤러가 다시 세지 않는다. */
    @Test
    @DisplayName("총계는 서버가 센 값을 그대로 응답한다")
    fun totalCountIsPassedThrough() {
        every { assetUseCase.listAssets(any(), any(), any(), any()) } returns
            AssetListResponse(assets = emptyList(), totalCount = 137)

        val response = controller.listAssets(userId, null, null, null, null, 0, 20)

        assertEquals(137, response.body!!.data!!.totalCount)
        verify(exactly = 1) { assetUseCase.listAssets(any(), any(), any(), any()) }
    }
}
