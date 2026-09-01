package com.ongo.application.asset

import com.ongo.application.common.FileStoragePort
import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.common.exception.ForbiddenException
import com.ongo.domain.asset.Asset
import com.ongo.domain.asset.AssetQuery
import com.ongo.domain.asset.AssetRepository
import com.ongo.domain.brandkit.BrandKitRepository
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 에셋의 **다운로드 URL 수명**과 **삭제 근거**를 고정한다.
 *
 * ## 무엇이 깨져 있었나
 *
 * 업로드 시점에 받은 URL이 `assets.file_url`에 그대로 저장되고 조회할 때마다 그 값이
 * 나갔다. 운영(S3/R2)에서 그 값은 **7일짜리 서명 URL**이라, 8일째부터 라이브러리의
 * 썸네일·미리보기·다운로드가 전부 403이 된다. 파일은 멀쩡한데 사용자에게는 라이브러리가
 * 통째로 사라진 것으로 보인다.
 *
 * 코드만 봐서는 드러나지 않는다 — 업로드 직후에 열어 보면 언제나 정상이고, 회귀는
 * **일주일 뒤에** 나타난다. 그래서 여기서 계약으로 못 박는다.
 */
class AssetUseCaseTest {

    private val assetRepository = mockk<AssetRepository>()
    private val storageQuotaUseCase = mockk<StorageQuotaUseCase>(relaxed = true)
    private val fileStoragePort = mockk<FileStoragePort>(relaxed = true)
    private val brandKitRepository = mockk<BrandKitRepository>()

    private lateinit var useCase: AssetUseCase

    private val userId = 100L

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = AssetUseCase(assetRepository, storageQuotaUseCase, fileStoragePort, brandKitRepository)
        // 기본은 "쓰는 곳 없음" — 참조 시나리오만 따로 세운다.
        every { brandKitRepository.findNamesReferencingAsset(any(), any()) } returns emptyList()
        every { assetRepository.count(userId, any()) } returns 1
    }

    private fun asset(
        id: Long = 7L,
        owner: Long = userId,
        storedUrl: String = "https://r2.test/assets/100/uuid_a.png?X-Amz-Expires=604800&sig=old",
        storageObjectKey: String? = "assets/100/uuid_a.png",
        filename: String = "uuid_a.png",
    ) = Asset(
        id = id,
        userId = owner,
        filename = filename,
        originalFilename = "a.png",
        fileUrl = storedUrl,
        storageObjectKey = storageObjectKey,
        fileType = "IMAGE",
        fileSizeBytes = 1_024L,
        mimeType = "image/png",
        tags = listOf("brand"),
        folder = "default",
        createdAt = LocalDateTime.now(),
    )

    private fun givenList(vararg rows: Asset) {
        every { assetRepository.findByUserId(userId, any(), any(), any()) } returns rows.toList()
    }

    /* ── (a) 저장 키로 새 URL을 발급한다 ───────────────────────────────── */

    /**
     * **핵심 회귀.** 저장된 값을 그대로 돌려주면 7일 뒤 목록 전체가 깨진 링크가 된다.
     */
    @Test
    @DisplayName("목록은 저장된 키로 새 다운로드 URL을 발급해 돌려준다")
    fun listReissuesTheDownloadUrlFromTheStoredKey() {
        givenList(asset())
        every { fileStoragePort.downloadUrlByKey("assets/100/uuid_a.png") } returns
            "https://r2.test/assets/100/uuid_a.png?X-Amz-Expires=604800&sig=fresh"

        val response = useCase.listAssets(userId, AssetQuery(), 0, 20)

        assertEquals(
            "https://r2.test/assets/100/uuid_a.png?X-Amz-Expires=604800&sig=fresh",
            response.assets.single().fileUrl,
            "저장된 URL을 그대로 돌려줬다 — 7일 뒤 깨진다",
        )
        verify(exactly = 1) { fileStoragePort.downloadUrlByKey("assets/100/uuid_a.png") }
    }

    /** 발급은 **행마다** 일어나야 한다. 한 건만 갱신하고 나머지를 두면 목록이 반쯤 깨진다. */
    @Test
    @DisplayName("여러 건도 각각 새 URL을 받는다")
    fun everyRowGetsItsOwnFreshUrl() {
        givenList(
            asset(id = 1L, storageObjectKey = "assets/100/one.png"),
            asset(id = 2L, storageObjectKey = "assets/100/two.png"),
        )
        every { fileStoragePort.downloadUrlByKey("assets/100/one.png") } returns "https://r2.test/one?sig=fresh"
        every { fileStoragePort.downloadUrlByKey("assets/100/two.png") } returns "https://r2.test/two?sig=fresh"

        val urls = useCase.listAssets(userId, AssetQuery(), 0, 20).assets.map { it.fileUrl }

        assertEquals(listOf("https://r2.test/one?sig=fresh", "https://r2.test/two?sig=fresh"), urls)
    }

    /** 필터 경로도 같은 응답을 만든다 — 목록 진입점이 셋이라 하나만 고치면 나머지가 남는다. */
    @Test
    @DisplayName("타입·폴더 필터 조회도 새 URL을 발급한다")
    fun filteredQueriesAlsoReissue() {
        every { assetRepository.findByUserId(userId, any(), any(), any()) } returns listOf(asset())
        every { fileStoragePort.downloadUrlByKey(any()) } returns "https://r2.test/fresh"

        val byType = useCase.listAssets(userId, AssetQuery(fileType = "IMAGE"), 0, 20)
        val byFolder = useCase.listAssets(userId, AssetQuery(folder = "brand-kit"), 0, 20)

        assertEquals("https://r2.test/fresh", byType.assets.single().fileUrl)
        assertEquals("https://r2.test/fresh", byFolder.assets.single().fileUrl)
    }

    /* ── 필터별 총계 ──────────────────────────────────────────────────── */

    /**
     * **총계는 목록과 같은 조건으로 세야 한다.**
     *
     * 예전에는 걸러서 조회해 놓고 총계는 `countByUserId(userId)` — 전체 개수 — 를 줬다.
     * 화면이 총계를 쓰지 않아 드러나지 않았을 뿐, 페이지네이션이 붙으면 "영상 3개"를
     * 보여 주면서 "총 240개"라고 말하고 존재하지 않는 페이지로 넘어간다.
     */
    @Test
    @DisplayName("총계는 목록과 같은 조건으로 센다")
    fun totalCountUsesTheSameFilterAsTheList() {
        val listQuery = slot<AssetQuery>()
        val countQuery = slot<AssetQuery>()
        every { assetRepository.findByUserId(userId, capture(listQuery), any(), any()) } returns listOf(asset())
        every { assetRepository.count(userId, capture(countQuery)) } returns 3

        val query = AssetQuery(fileType = "VIDEO", search = "여름", tag = "brand")
        val response = useCase.listAssets(userId, query, 0, 20)

        assertEquals(3, response.totalCount)
        assertEquals(query, listQuery.captured, "목록이 다른 조건으로 조회했다")
        assertEquals(query, countQuery.captured, "총계가 목록과 다른 조건으로 셌다")
    }

    /** 페이지 번호와 크기는 그대로 전달돼야 한다 — 101 번째 이후에 닿는 유일한 수단이다. */
    @Test
    @DisplayName("요청한 페이지와 크기를 그대로 조회에 넘긴다")
    fun pagingArgumentsReachTheRepository() {
        every { assetRepository.findByUserId(userId, any(), 4, 24) } returns listOf(asset())
        every { assetRepository.count(userId, any()) } returns 120

        val response = useCase.listAssets(userId, AssetQuery(), 4, 24)

        assertEquals(120, response.totalCount)
        verify(exactly = 1) { assetRepository.findByUserId(userId, any(), 4, 24) }
    }

    /* ── (b) 레거시는 건드리지 않는다 ─────────────────────────────────── */

    /**
     * **키가 없으면 발급을 시도조차 하지 않는다.**
     *
     * URL에서 키를 되짚어 발급하고 싶겠지만, 경로·서명 형식이 어댑터마다 달라 추측이
     * 빗나간다. 빗나간 키는 남의 오브젝트를 가리킨다. 추측을 새 경로의 근거로 넓히지 않는다.
     */
    @Test
    @DisplayName("저장 키가 없는 과거 행은 저장된 URL을 그대로 쓰고 발급을 시도하지 않는다")
    fun legacyRowsKeepTheStoredUrlWithoutGuessing() {
        givenList(asset(storageObjectKey = null))

        val response = useCase.listAssets(userId, AssetQuery(), 0, 20)

        assertEquals(
            "https://r2.test/assets/100/uuid_a.png?X-Amz-Expires=604800&sig=old",
            response.assets.single().fileUrl,
        )
        verify(exactly = 0) { fileStoragePort.downloadUrlByKey(any()) }
    }

    /** 빈 문자열도 "키 없음"이다. 빈 키로 발급을 부르면 어댑터가 버킷 루트를 가리킨다. */
    @Test
    @DisplayName("저장 키가 빈 문자열이어도 발급을 시도하지 않는다")
    fun blankKeyIsTreatedAsMissing() {
        givenList(asset(storageObjectKey = "   "))

        useCase.listAssets(userId, AssetQuery(), 0, 20)

        verify(exactly = 0) { fileStoragePort.downloadUrlByKey(any()) }
    }

    /* ── (d) 발급 실패 정책 ───────────────────────────────────────────── */

    /**
     * **실패해도 목록은 살아 있어야 한다.**
     *
     * 저장된 URL은 우리가 실제로 발급했던 진짜 값이라 7일 안이면 지금도 열린다. 예외를
     * 올리면 오브젝트 하나 때문에 목록 전체가 500이 되고, 빈 문자열을 주면 아직 살아 있는
     * 링크까지 죽인다. 성공을 가장하는 값(가짜 URL)은 어느 쪽도 만들지 않는다.
     */
    @Test
    @DisplayName("URL 발급이 실패하면 저장된 URL로 응답하고 목록은 계속 준다")
    fun reissueFailureFallsBackToTheStoredUrl() {
        givenList(asset())
        every { fileStoragePort.downloadUrlByKey(any()) } throws IllegalStateException("스토리지 장애")

        val response = useCase.listAssets(userId, AssetQuery(), 0, 20)

        assertEquals(
            "https://r2.test/assets/100/uuid_a.png?X-Amz-Expires=604800&sig=old",
            response.assets.single().fileUrl,
            "발급 실패를 지어낸 값으로 덮었다",
        )
        assertEquals(1, response.assets.size, "한 건의 발급 실패가 목록 전체를 지웠다")
    }

    /** 한 건이 실패해도 나머지는 새 URL을 받아야 한다. */
    @Test
    @DisplayName("한 건의 발급 실패가 다른 건의 재발급을 막지 않는다")
    fun oneFailureDoesNotBlockTheOthers() {
        givenList(
            asset(id = 1L, storageObjectKey = "assets/100/bad.png", storedUrl = "https://r2.test/bad?sig=old"),
            asset(id = 2L, storageObjectKey = "assets/100/good.png", storedUrl = "https://r2.test/good?sig=old"),
        )
        every { fileStoragePort.downloadUrlByKey("assets/100/bad.png") } throws IllegalStateException("스토리지 장애")
        every { fileStoragePort.downloadUrlByKey("assets/100/good.png") } returns "https://r2.test/good?sig=fresh"

        val urls = useCase.listAssets(userId, AssetQuery(), 0, 20).assets.map { it.fileUrl }

        assertEquals(listOf("https://r2.test/bad?sig=old", "https://r2.test/good?sig=fresh"), urls)
    }

    /* ── (c) 삭제는 저장된 키를 쓴다 ──────────────────────────────────── */

    /**
     * **삭제는 추측하지 않는다.**
     *
     * 조립한 키는 지금 포맷과 우연히 일치할 뿐이다. 규칙이 한 번이라도 바뀌면 조용히 다른
     * 곳을 지운다 — 못 지운 파일은 아무도 못 찾는 고아가 되고, 잘못 맞은 경우는 되돌릴 수 없다.
     */
    @Test
    @DisplayName("삭제는 저장된 키를 그대로 넘긴다")
    fun deleteUsesTheStoredKey() {
        val row = asset(storageObjectKey = "assets/100/actual-key.png", filename = "different-name.png")
        every { assetRepository.findById(7L) } returns row
        every { assetRepository.delete(7L) } returns Unit

        useCase.deleteAsset(userId, 7L)

        verify(exactly = 1) { fileStoragePort.deleteByKey("assets/100/actual-key.png") }
        verify(exactly = 0) { fileStoragePort.deleteByKey("assets/100/different-name.png") }
    }

    /* ── 사용 중인 에셋 보호 ──────────────────────────────────────────── */

    /**
     * **핵심.** 쓰는 곳이 있으면 지우지 않는다.
     *
     * 지우고 나면 되돌릴 수 없다. 브랜드킷이 그 파일을 가리키고 있으면 로고가 아무 예고
     * 없이 깨지고, 화면에는 이유가 표시되지 않는다.
     */
    @Test
    @DisplayName("브랜드 키트가 쓰고 있으면 삭제하지 않는다")
    fun refusesToDeleteAnAssetInUse() {
        every { assetRepository.findById(7L) } returns asset()
        every { brandKitRepository.findNamesReferencingAsset(userId, 7L) } returns listOf("여름 브랜드")

        val error = assertFailsWith<AssetInUseException> { useCase.deleteAsset(userId, 7L) }

        assertEquals(true, error.message!!.contains("여름 브랜드"), "어느 브랜드 키트인지 알려 주지 않는다: ${error.message}")
    }

    /** **스토리지도 DB 도 건드리지 않아야 한다.** 지운 뒤에 알려 주는 것은 의미가 없다. */
    @Test
    @DisplayName("거절 시 스토리지와 DB 를 건드리지 않는다")
    fun refusalTouchesNothing() {
        every { assetRepository.findById(7L) } returns asset()
        every { brandKitRepository.findNamesReferencingAsset(userId, 7L) } returns listOf("여름 브랜드")

        assertFailsWith<AssetInUseException> { useCase.deleteAsset(userId, 7L) }

        verify(exactly = 0) { fileStoragePort.deleteByKey(any()) }
        verify(exactly = 0) { assetRepository.delete(any()) }
    }

    /** 여러 곳에서 쓰면 전부 알려 준다 — 하나만 고치고 다시 실패하게 두지 않는다. */
    @Test
    @DisplayName("여러 브랜드 키트가 쓰면 모두 알려 준다")
    fun namesEveryBrandKitInUse() {
        every { assetRepository.findById(7L) } returns asset()
        every { brandKitRepository.findNamesReferencingAsset(userId, 7L) } returns listOf("여름 브랜드", "겨울 브랜드")

        val error = assertFailsWith<AssetInUseException> { useCase.deleteAsset(userId, 7L) }

        assertEquals(true, error.message!!.contains("여름 브랜드"), error.message)
        assertEquals(true, error.message!!.contains("겨울 브랜드"), error.message)
    }

    /** 쓰는 곳이 없으면 예전처럼 지운다 — 안전망이 정상 삭제를 막으면 안 된다. */
    @Test
    @DisplayName("쓰는 곳이 없으면 그대로 삭제한다")
    fun deletesWhenNothingReferencesIt() {
        every { assetRepository.findById(7L) } returns asset()
        every { assetRepository.delete(7L) } returns Unit

        useCase.deleteAsset(userId, 7L)

        verify(exactly = 1) { fileStoragePort.deleteByKey("assets/100/uuid_a.png") }
        verify(exactly = 1) { assetRepository.delete(7L) }
    }

    /**
     * **내 브랜드킷만 본다.** 남의 참조 때문에 내 에셋을 못 지우면 그 자체가 남의 데이터를
     * 알려 주는 통로가 된다.
     */
    @Test
    @DisplayName("참조 조회는 요청한 사용자 범위로만 한다")
    fun referenceLookupIsScopedToTheRequester() {
        every { assetRepository.findById(7L) } returns asset()
        every { assetRepository.delete(7L) } returns Unit

        useCase.deleteAsset(userId, 7L)

        verify(exactly = 1) { brandKitRepository.findNamesReferencingAsset(userId, 7L) }
    }

    /**
     * `assetId` 가 없는 예전 브랜드킷 항목은 이 에셋과 이어져 있다는 증거가 없다. URL
     * 문자열로 맞춰 보는 추측은 하지 않으므로 조회가 빈 목록을 주고, 삭제는 그대로 진행된다.
     * 그 항목의 URL 은 원래도 만료된 값이라 이 삭제로 새로 나빠지지 않는다.
     */
    @Test
    @DisplayName("assetId 가 없는 예전 브랜드킷 항목은 삭제를 막지 않는다")
    fun legacyBrandKitEntriesDoNotBlockDeletion() {
        every { assetRepository.findById(7L) } returns asset()
        every { brandKitRepository.findNamesReferencingAsset(userId, 7L) } returns emptyList()
        every { assetRepository.delete(7L) } returns Unit

        useCase.deleteAsset(userId, 7L)

        verify(exactly = 1) { assetRepository.delete(7L) }
    }

    /** 소유권 검사가 먼저다 — 남의 에셋이면 참조를 조회할 이유도 없다. */
    @Test
    @DisplayName("남의 에셋은 참조를 조회하기 전에 거절한다")
    fun ownershipIsCheckedBeforeTheReferenceLookup() {
        every { assetRepository.findById(7L) } returns asset(owner = 999L)

        assertFailsWith<ForbiddenException> { useCase.deleteAsset(userId, 7L) }

        verify(exactly = 0) { brandKitRepository.findNamesReferencingAsset(any(), any()) }
    }

    /** 키가 없는 과거 행만 예전 조립 방식으로 떨어진다. 그 경로는 유지한다. */
    @Test
    @DisplayName("저장 키가 없는 과거 행만 파일명으로 조립한 키를 쓴다")
    fun deleteFallsBackOnlyForLegacyRows() {
        val row = asset(storageObjectKey = null, filename = "legacy.png")
        every { assetRepository.findById(7L) } returns row
        every { assetRepository.delete(7L) } returns Unit

        useCase.deleteAsset(userId, 7L)

        verify(exactly = 1) { fileStoragePort.deleteByKey("assets/100/legacy.png") }
    }

    /** 스토리지 삭제가 실패해도 행은 지운다 — 사용자를 붙잡아 두지 않고 로그로 추적한다. */
    @Test
    @DisplayName("스토리지 삭제가 실패해도 행 삭제는 진행한다")
    fun deleteContinuesWhenStorageFails() {
        every { assetRepository.findById(7L) } returns asset()
        every { fileStoragePort.deleteByKey(any()) } throws IllegalStateException("스토리지 장애")
        every { assetRepository.delete(7L) } returns Unit

        useCase.deleteAsset(userId, 7L)

        verify(exactly = 1) { assetRepository.delete(7L) }
    }

    /** 남의 에셋은 조회도 삭제도 되지 않는다 — 키를 알아도 소유자가 아니면 지울 수 없다. */
    @Test
    @DisplayName("남의 에셋은 삭제할 수 없고 스토리지도 건드리지 않는다")
    fun deleteRejectsAnotherUsersAsset() {
        every { assetRepository.findById(7L) } returns asset(owner = 999L)

        assertFailsWith<ForbiddenException> { useCase.deleteAsset(userId, 7L) }

        verify(exactly = 0) { fileStoragePort.deleteByKey(any()) }
        verify(exactly = 0) { assetRepository.delete(any()) }
    }
}
