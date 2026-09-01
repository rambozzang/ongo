package com.ongo.application.brandkit

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.ongo.application.brandkit.dto.BrandKitAsset
import com.ongo.application.brandkit.dto.CreateBrandKitRequest
import com.ongo.application.brandkit.dto.UpdateBrandKitRequest
import com.ongo.application.common.FileStoragePort
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.asset.Asset
import com.ongo.domain.asset.AssetRepository
import com.ongo.domain.brandkit.BrandKit
import com.ongo.domain.brandkit.BrandKitRepository
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 브랜드킷이 참조하는 파일의 **URL 수명과 소유권**을 고정한다.
 *
 * ## 무엇이 깨져 있었나
 *
 * 업로드 응답의 `fileUrl` 문자열만 복사해 저장했다. 운영(S3/R2)에서 그 값은 **7 일짜리
 * 서명 URL** 이라 8 일째부터 로고·워터마크가 통째로 깨졌다. 원본 에셋과 아무 관계도 없어
 * 에셋을 지워도 이쪽은 알지 못했다.
 *
 * 회귀가 **일주일 뒤에** 나타나는 종류라 눈으로는 잡히지 않는다. 그래서 계약으로 못 박는다.
 */
class BrandKitUseCaseTest {

    private val brandKitRepository = mockk<BrandKitRepository>()
    private val assetRepository = mockk<AssetRepository>()
    private val fileStoragePort = mockk<FileStoragePort>(relaxed = true)
    private val objectMapper = ObjectMapper().registerKotlinModule()

    private lateinit var useCase: BrandKitUseCase

    private val userId = 100L

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = BrandKitUseCase(brandKitRepository, objectMapper, assetRepository, fileStoragePort)
        every { brandKitRepository.save(any()) } answers { firstArg<BrandKit>().copy(id = 1L) }
        every { brandKitRepository.update(any()) } answers { firstArg() }
    }

    private fun storedAsset(
        id: Long = 7L,
        owner: Long = userId,
        storageObjectKey: String? = "assets/100/uuid_logo.png",
    ) = Asset(
        id = id,
        userId = owner,
        filename = "uuid_logo.png",
        originalFilename = "로고.png",
        fileUrl = "https://r2.test/assets/100/uuid_logo.png?sig=old",
        storageObjectKey = storageObjectKey,
        fileType = "IMAGE",
        fileSizeBytes = 1_024L,
        mimeType = "image/png",
        tags = emptyList(),
        folder = "brand-kit",
    )

    private fun kitAsset(
        assetId: Long? = 7L,
        type: String = "logo",
        url: String = "https://r2.test/assets/100/uuid_logo.png?sig=old",
    ) = BrandKitAsset(
        id = 1L, name = "로고.png", type = type, url = url,
        format = "PNG", size = "1 KB", uploadedAt = "2026-08-01T00:00:00Z",
        assetId = assetId,
    )

    private fun createRequest(vararg assets: BrandKitAsset) =
        CreateBrandKitRequest(name = "기본", assets = assets.toList())

    /* ── 정상: 저장 키로 새 URL ───────────────────────────────────────── */

    /** **핵심 회귀.** 저장된 문자열을 그대로 돌려주면 7 일 뒤 로고가 깨진다. */
    @Test
    @DisplayName("assetId 가 있으면 저장 키로 새 URL 을 발급해 응답한다")
    fun freshUrlIsIssuedFromTheStoredKey() {
        every { assetRepository.findById(7L) } returns storedAsset()
        every { fileStoragePort.downloadUrlByKey("assets/100/uuid_logo.png") } returns
            "https://r2.test/assets/100/uuid_logo.png?sig=fresh"

        val response = useCase.createBrandKit(userId, createRequest(kitAsset()))

        assertEquals("https://r2.test/assets/100/uuid_logo.png?sig=fresh", response.assets.single().url)
        verify(exactly = 1) { fileStoragePort.downloadUrlByKey("assets/100/uuid_logo.png") }
    }

    /** 조회 경로도 같아야 한다 — 저장할 때만 신선하면 다음 날 열었을 때 깨진다. */
    @Test
    @DisplayName("목록 조회도 매번 새 URL 을 발급한다")
    fun listAlsoReissues() {
        every { assetRepository.findById(7L) } returns storedAsset()
        every { fileStoragePort.downloadUrlByKey(any()) } returns "https://r2.test/fresh"
        every { brandKitRepository.findByUserId(userId) } returns listOf(
            BrandKit(
                id = 1L, userId = userId, name = "기본",
                assetsJson = objectMapper.writeValueAsString(listOf(kitAsset())),
            ),
        )

        val kits = useCase.listBrandKits(userId)

        assertEquals("https://r2.test/fresh", kits.single().assets.single().url)
    }

    /* ── 스칼라 URL 정합 ──────────────────────────────────────────────── */

    /**
     * 스칼라 컬럼과 `assets` 배열이 같은 파일을 가리키는데 한쪽만 갱신되면, 화면 위치에
     * 따라 로고가 보였다 안 보였다 한다.
     */
    @Test
    @DisplayName("타입별 스칼라 URL 도 같은 신선한 값을 쓴다")
    fun scalarUrlsFollowTheTypedAsset() {
        every { assetRepository.findById(any()) } answers { storedAsset(id = firstArg()) }
        every { fileStoragePort.downloadUrlByKey(any()) } answers { "https://r2.test/fresh/${firstArg<String>()}" }

        val response = useCase.createBrandKit(
            userId,
            CreateBrandKitRequest(
                name = "기본",
                logoUrl = "https://r2.test/stale-logo",
                watermarkUrl = "https://r2.test/stale-watermark",
                introTemplateUrl = "https://r2.test/stale-intro",
                outroTemplateUrl = "https://r2.test/stale-outro",
                assets = listOf(
                    kitAsset(assetId = 1L, type = "logo"),
                    kitAsset(assetId = 2L, type = "watermark"),
                    kitAsset(assetId = 3L, type = "intro"),
                    kitAsset(assetId = 4L, type = "outro"),
                ),
            ),
        )

        val logo = response.assets.first { it.type == "logo" }.url
        val watermark = response.assets.first { it.type == "watermark" }.url
        assertEquals(logo, response.logoUrl, "스칼라 로고가 배열과 다른 값을 가리킨다")
        assertEquals(watermark, response.watermarkUrl)
        assertEquals(response.assets.first { it.type == "intro" }.url, response.introTemplateUrl)
        assertEquals(response.assets.first { it.type == "outro" }.url, response.outroTemplateUrl)
    }

    /** 그 타입의 에셋이 없으면 넘어온 값을 그대로 둔다 — 없는 값을 지어내지 않는다. */
    @Test
    @DisplayName("타입에 맞는 에셋이 없으면 요청 값을 유지한다")
    fun scalarFallsBackWhenNoTypedAssetExists() {
        val response = useCase.createBrandKit(
            userId,
            CreateBrandKitRequest(name = "기본", logoUrl = "https://external.test/logo.png"),
        )

        assertEquals("https://external.test/logo.png", response.logoUrl)
        verify(exactly = 0) { fileStoragePort.downloadUrlByKey(any()) }
    }

    /* ── 거절 ─────────────────────────────────────────────────────────── */

    /**
     * **남의 에셋을 내 브랜드킷에 붙일 수 없다.** 여기가 뚫리면 id 를 바꿔 가며 남의 파일에
     * 접근할 수 있는 링크를 우리가 만들어 준다.
     */
    @Test
    @DisplayName("다른 사용자의 에셋은 거절한다")
    fun rejectsAnotherUsersAsset() {
        every { assetRepository.findById(7L) } returns storedAsset(owner = 999L)

        assertFailsWith<ForbiddenException> {
            useCase.createBrandKit(userId, createRequest(kitAsset()))
        }

        verify(exactly = 0) { fileStoragePort.downloadUrlByKey(any()) }
        verify(exactly = 0) { brandKitRepository.save(any()) }
    }

    @Test
    @DisplayName("없는 에셋은 거절한다")
    fun rejectsMissingAsset() {
        every { assetRepository.findById(7L) } returns null

        assertFailsWith<NotFoundException> {
            useCase.createBrandKit(userId, createRequest(kitAsset()))
        }

        verify(exactly = 0) { brandKitRepository.save(any()) }
    }

    /**
     * 저장 위치를 모르는 에셋은 URL 을 새로 발급할 수 없다. 붙여 두면 지금은 되는 것처럼
     * 보이다가 7 일 뒤 조용히 깨진다 — 붙일 때 막는 편이 낫다.
     */
    @Test
    @DisplayName("저장 키가 없는 예전 에셋은 거절한다")
    fun rejectsKeylessLegacyAsset() {
        listOf(null, "", "   ").forEach { key ->
            clearAllMocks()
            every { brandKitRepository.save(any()) } answers { firstArg<BrandKit>().copy(id = 1L) }
            every { assetRepository.findById(7L) } returns storedAsset(storageObjectKey = key)

            assertFailsWith<BrandKitAssetNotLinkableException>("키=$key 를 통과시켰다") {
                useCase.createBrandKit(userId, createRequest(kitAsset()))
            }
            verify(exactly = 0) { brandKitRepository.save(any()) }
        }
    }

    /** 수정 경로도 같은 문을 지나야 한다 — 생성만 막으면 수정으로 우회된다. */
    @Test
    @DisplayName("수정도 같은 검증을 거친다")
    fun updateValidatesTheSameWay() {
        every { brandKitRepository.findById(1L) } returns BrandKit(id = 1L, userId = userId, name = "기본")
        every { assetRepository.findById(7L) } returns storedAsset(owner = 999L)

        assertFailsWith<ForbiddenException> {
            useCase.updateBrandKit(userId, 1L, UpdateBrandKitRequest(assets = listOf(kitAsset())))
        }

        verify(exactly = 0) { brandKitRepository.update(any()) }
    }

    /* ── 레거시 호환 ──────────────────────────────────────────────────── */

    /**
     * **`assetId` 가 없으면 아무것도 하지 않는다.**
     *
     * 저장된 문자열에서 키를 되짚어 되살리고 싶겠지만 하지 않는다 — 경로·서명 형식이
     * 어댑터마다 달라 추측이 빗나가고, 빗나간 키는 남의 파일을 가리킨다. 그 URL 은 이미
     * 만료됐을 수 있으며, 사용자가 파일을 다시 올리면 `assetId` 가 붙는다.
     */
    @Test
    @DisplayName("assetId 가 없는 예전 항목은 저장된 URL 을 그대로 돌려준다")
    fun legacyEntriesKeepTheirStoredUrl() {
        val legacy = kitAsset(assetId = null, url = "https://r2.test/legacy?sig=old")

        val response = useCase.createBrandKit(userId, createRequest(legacy))

        assertEquals("https://r2.test/legacy?sig=old", response.assets.single().url)
        verify(exactly = 0) { assetRepository.findById(any()) }
        verify(exactly = 0) { fileStoragePort.downloadUrlByKey(any()) }
    }

    /**
     * 예전 형식(`assetId` 필드가 아예 없는 JSON)도 그대로 읽혀야 한다. 못 읽으면 기존
     * 브랜드킷이 통째로 빈 목록이 된다.
     */
    @Test
    @DisplayName("assetId 필드가 없는 예전 JSON 도 읽는다")
    fun oldJsonWithoutTheFieldStillParses() {
        val oldJson = """
            [{"id":1,"name":"로고.png","type":"logo","url":"https://r2.test/legacy",
              "format":"PNG","size":"1 KB","uploadedAt":"2026-08-01T00:00:00Z"}]
        """.trimIndent()
        every { brandKitRepository.findByUserId(userId) } returns listOf(
            BrandKit(id = 1L, userId = userId, name = "기본", assetsJson = oldJson),
        )

        val assets = useCase.listBrandKits(userId).single().assets

        assertEquals(1, assets.size, "예전 JSON 을 못 읽어 목록이 비었다")
        assertEquals("https://r2.test/legacy", assets.single().url)
        assertEquals(null, assets.single().assetId)
    }

    /* ── 읽기는 던지지 않는다 ─────────────────────────────────────────── */

    /**
     * 참조하던 에셋이 나중에 지워졌다고 조회가 500 이 되면 사용자는 자기 브랜드킷을 영영
     * 열지 못한다 — 색상·폰트·가이드라인까지 함께 막힌다. 확인하지 못한 항목은 저장된
     * 문자열을 그대로 두고 **URL 을 발급하지는 않는다.**
     */
    @Test
    @DisplayName("참조하던 에셋이 사라져도 조회는 살아 있다")
    fun readingSurvivesADeletedReference() {
        every { assetRepository.findById(7L) } returns null
        every { brandKitRepository.findByUserId(userId) } returns listOf(
            BrandKit(
                id = 1L, userId = userId, name = "기본",
                assetsJson = objectMapper.writeValueAsString(listOf(kitAsset(url = "https://r2.test/stale"))),
            ),
        )

        val assets = useCase.listBrandKits(userId).single().assets

        assertEquals("https://r2.test/stale", assets.single().url)
        verify(exactly = 0) { fileStoragePort.downloadUrlByKey(any()) }
    }

    /** 남의 에셋을 가리키는 예전 데이터에도 **URL 을 발급해 주지 않는다.** */
    @Test
    @DisplayName("남의 에셋을 가리키는 데이터에는 URL 을 발급하지 않는다")
    fun neverSignsForAnotherUsersAssetOnRead() {
        every { assetRepository.findById(7L) } returns storedAsset(owner = 999L)
        every { brandKitRepository.findByUserId(userId) } returns listOf(
            BrandKit(
                id = 1L, userId = userId, name = "기본",
                assetsJson = objectMapper.writeValueAsString(listOf(kitAsset(url = "https://r2.test/stale"))),
            ),
        )

        val assets = useCase.listBrandKits(userId).single().assets

        assertEquals("https://r2.test/stale", assets.single().url)
        verify(exactly = 0) { fileStoragePort.downloadUrlByKey(any()) }
    }

    /** 발급이 실패해도 조회는 계속된다 — 저장된 값은 우리가 실제로 발급했던 진짜 값이다. */
    @Test
    @DisplayName("URL 발급이 실패하면 저장된 URL 로 응답한다")
    fun reissueFailureFallsBackToTheStoredUrl() {
        every { assetRepository.findById(7L) } returns storedAsset()
        every { fileStoragePort.downloadUrlByKey(any()) } throws IllegalStateException("스토리지 장애")
        every { brandKitRepository.findByUserId(userId) } returns listOf(
            BrandKit(
                id = 1L, userId = userId, name = "기본",
                assetsJson = objectMapper.writeValueAsString(listOf(kitAsset(url = "https://r2.test/stale"))),
            ),
        )

        val assets = useCase.listBrandKits(userId).single().assets

        assertEquals("https://r2.test/stale", assets.single().url)
    }
}
