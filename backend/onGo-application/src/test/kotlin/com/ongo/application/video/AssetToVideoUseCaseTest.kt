package com.ongo.application.video

import com.ongo.application.common.FileStoragePort
import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.common.enums.MediaType
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.StorageQuotaExceededException
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.asset.Asset
import com.ongo.domain.asset.AssetRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 에셋을 영상 초안으로 승격하는 경로를 고정한다.
 *
 * ## 무엇을 지키는가
 *
 * 이 경로는 **남의 파일을 사용자 영상으로 만들어 줄 수 있는** 자리다. 소유권·타입·저장 키를
 * 하나라도 느슨하게 보면 그렇게 된다. 동시에 저장공간을 실제로 한 벌 더 쓰므로 쿼터를
 * 우회하면 원가가 샌다. 그리고 스토리지 복사는 트랜잭션 밖이라 실패하면 아무도 못 찾는
 * 사본이 남는다.
 *
 * 세 가지를 각각 못 박는다: **거절 조건**, **복사 정책**, **보상**.
 */
class AssetToVideoUseCaseTest {

    private val assetRepository = mockk<AssetRepository>()
    private val videoRepository = mockk<VideoRepository>()
    private val storageQuotaUseCase = mockk<StorageQuotaUseCase>(relaxed = true)
    private val fileStoragePort = mockk<FileStoragePort>(relaxed = true)
    private val userWriteGuard = mockk<UserWriteGuard>(relaxed = true)

    private lateinit var useCase: AssetToVideoUseCase

    private val userId = 100L
    private val assetId = 7L
    private val newVideoId = 42L

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = AssetToVideoUseCase(
            assetRepository, videoRepository, storageQuotaUseCase, fileStoragePort, userWriteGuard,
        )
    }

    private fun asset(
        owner: Long = userId,
        fileType: String = "VIDEO",
        storageObjectKey: String? = "assets/100/uuid_clip.mp4",
        fileSizeBytes: Long? = 50_000_000L,
        filename: String = "uuid_clip.mp4",
        originalFilename: String? = "여름 브이로그.mp4",
    ) = Asset(
        id = assetId,
        userId = owner,
        filename = filename,
        originalFilename = originalFilename,
        fileUrl = "https://r2.test/assets/100/uuid_clip.mp4?sig=x",
        storageObjectKey = storageObjectKey,
        fileType = fileType,
        fileSizeBytes = fileSizeBytes,
        mimeType = "video/mp4",
        tags = emptyList(),
        folder = "default",
    )

    /** 성공 경로의 공통 배선. 저장은 id 를 붙여 돌려준다(실제 리포지토리와 같은 계약). */
    private fun givenPromotable(row: Asset = asset()) {
        every { assetRepository.findById(assetId) } returns row
        every { videoRepository.save(any()) } answers { firstArg<Video>().copy(id = newVideoId) }
        every { videoRepository.update(any()) } answers { firstArg() }
        every { fileStoragePort.copyByKey(any(), any()) } returns "https://r2.test/videos/42/uuid_clip.mp4?sig=new"
    }

    /* ── 거절 조건 ────────────────────────────────────────────────────── */

    /** **남의 에셋으로 내 영상을 만들 수 없다.** 여기가 뚫리면 id 를 바꿔 가며 남의 파일을 가져간다. */
    @Test
    @DisplayName("타인의 에셋은 거절하고 아무것도 만들지 않는다")
    fun rejectsAnotherUsersAsset() {
        every { assetRepository.findById(assetId) } returns asset(owner = 999L)

        assertFailsWith<ForbiddenException> { useCase.promote(userId, assetId) }

        verify(exactly = 0) { fileStoragePort.copyByKey(any(), any()) }
        verify(exactly = 0) { videoRepository.save(any()) }
    }

    @Test
    @DisplayName("없는 에셋은 404 다")
    fun rejectsMissingAsset() {
        every { assetRepository.findById(assetId) } returns null

        assertFailsWith<NotFoundException> { useCase.promote(userId, assetId) }
    }

    /**
     * 에셋 업로드는 pdf·zip·docx 도 받는다(`AssetController` 화이트리스트). 그걸 영상 게시
     * 경로에 넣으면 플랫폼이 거절할 때까지 아무도 모른다.
     */
    @Test
    @DisplayName("영상이 아닌 에셋은 거절한다")
    fun rejectsNonVideoAssets() {
        listOf("IMAGE", "AUDIO", "TEMPLATE").forEach { type ->
            clearAllMocks()
            every { assetRepository.findById(assetId) } returns asset(fileType = type)

            assertFailsWith<AssetNotPromotableException>("$type 을 승격시켰다") {
                useCase.promote(userId, assetId)
            }
            verify(exactly = 0) { fileStoragePort.copyByKey(any(), any()) }
            verify(exactly = 0) { videoRepository.save(any()) }
        }
    }

    /**
     * **키가 없으면 거절한다.** `file_url` 에서 되짚고 싶겠지만 하지 않는다 — 서명·경로
     * 형식이 어댑터마다 달라 빗나가고, 빗나간 키는 남의 오브젝트를 복사해 온다.
     */
    @Test
    @DisplayName("저장 키가 없는 과거 에셋은 거절하고 키를 추측하지 않는다")
    fun rejectsLegacyAssetsWithoutAStoredKey() {
        listOf(null, "", "   ").forEach { key ->
            clearAllMocks()
            every { assetRepository.findById(assetId) } returns asset(storageObjectKey = key)

            assertFailsWith<AssetNotPromotableException>("키=$key 를 승격시켰다") {
                useCase.promote(userId, assetId)
            }
            verify(exactly = 0) { fileStoragePort.copyByKey(any(), any()) }
        }
    }

    @Test
    @DisplayName("크기를 모르는 에셋은 거절한다")
    fun rejectsAssetsWithoutASize() {
        listOf(null, 0L, -1L).forEach { size ->
            clearAllMocks()
            every { assetRepository.findById(assetId) } returns asset(fileSizeBytes = size)

            assertFailsWith<AssetNotPromotableException>("크기=$size 를 승격시켰다") {
                useCase.promote(userId, assetId)
            }
            verify(exactly = 0) { storageQuotaUseCase.checkQuota(any(), any(), any()) }
            verify(exactly = 0) { fileStoragePort.copyByKey(any(), any()) }
        }
    }

    /* ── 쿼터 ─────────────────────────────────────────────────────────── */

    /**
     * **복사는 저장공간을 실제로 한 벌 더 쓴다.** 원본 크기만큼 추가로 차감해야 하고,
     * 한 바이트도 옮기기 전에 거절해야 고아 사본이 남지 않는다.
     */
    @Test
    @DisplayName("원본 크기만큼 쿼터를 검사한 뒤에 복사한다")
    fun checksQuotaForTheFullCopySizeBeforeCopying() {
        givenPromotable()

        useCase.promote(userId, assetId)

        verify(exactly = 1) { storageQuotaUseCase.checkQuota(userId, 50_000_000L, any()) }
    }

    @Test
    @DisplayName("쿼터를 넘기면 복사도 행 생성도 하지 않는다")
    fun quotaFailureStopsBeforeAnySideEffect() {
        every { assetRepository.findById(assetId) } returns asset()
        every { storageQuotaUseCase.checkQuota(any(), any(), any()) } throws
            StorageQuotaExceededException(limitBytes = 1_000, usedBytes = 900, requiredBytes = 50_000_000L)

        assertFailsWith<StorageQuotaExceededException> { useCase.promote(userId, assetId) }

        verify(exactly = 0) { fileStoragePort.copyByKey(any(), any()) }
        verify(exactly = 0) { videoRepository.save(any()) }
    }

    /* ── 복사 정책 ────────────────────────────────────────────────────── */

    /**
     * **핵심.** 사본은 새 영상 전용 경로에 둔다. 에셋 키를 영상 행이 그대로 가리키면
     * 에셋을 정리한 사용자가 이미 게시한 영상까지 잃는다.
     */
    @Test
    @DisplayName("사본은 새 영상 id 아래에 두고 원본 에셋 키는 건드리지 않는다")
    fun copiesIntoTheNewVideoPrefixAndLeavesTheAssetAlone() {
        givenPromotable()
        val source = slot<String>()
        val target = slot<String>()
        every { fileStoragePort.copyByKey(capture(source), capture(target)) } returns "https://r2.test/videos/42/x.mp4"

        useCase.promote(userId, assetId)

        assertEquals("assets/100/uuid_clip.mp4", source.captured, "원본 키를 바꿔 읽었다")
        assertTrue(
            target.captured.startsWith("videos/$newVideoId/"),
            "사본이 영상 전용 경로 밖에 있다: ${target.captured}",
        )
        // 승격은 이동이 아니다 — 원본 에셋은 그대로 남아야 한다.
        verify(exactly = 0) { fileStoragePort.deleteByKey("assets/100/uuid_clip.mp4") }
        verify(exactly = 0) { assetRepository.delete(any()) }
    }

    /**
     * 어댑터가 키를 `^[a-zA-Z0-9\-_./]+$` 로 검사한다. 한글·공백이 섞인 이름을 그대로 쓰면
     * URL 발급 단계에서 터지고, 그때는 이미 사본이 만들어진 뒤다.
     */
    @Test
    @DisplayName("사본 키에 안전한 문자만 쓴다")
    fun copyKeyIsSanitised() {
        givenPromotable(asset(filename = "여름 브이로그 (최종).mp4"))
        val target = slot<String>()
        every { fileStoragePort.copyByKey(any(), capture(target)) } returns "https://r2.test/videos/42/x.mp4"

        useCase.promote(userId, assetId)

        assertTrue(
            target.captured.matches(Regex("^[a-zA-Z0-9\\-_./]+$")),
            "스토리지가 거부할 키를 만들었다: ${target.captured}",
        )
    }

    /* ── 만들어진 행 ──────────────────────────────────────────────────── */

    /**
     * 화면이 이어서 편집·예약할 수 있으려면 게시 파이프라인이 기대하는 모양이어야 한다.
     * `fileUrl` 은 **어댑터가 돌려준 값만** 쓴다 — 조립한 URL 은 눌러 보기 전까지 구분되지 않는다.
     */
    @Test
    @DisplayName("DRAFT 영상 행이 크기·키·URL·미디어 타입을 정확히 갖는다")
    fun createsADraftVideoWithTheCopiedObject() {
        givenPromotable()
        val saved = slot<Video>()
        val updated = slot<Video>()
        every { videoRepository.save(capture(saved)) } answers { firstArg<Video>().copy(id = newVideoId) }
        every { videoRepository.update(capture(updated)) } answers { firstArg() }

        val result = useCase.promote(userId, assetId)

        assertEquals(newVideoId, result.videoId)
        assertEquals(UploadStatus.DRAFT, saved.captured.status)
        assertEquals(MediaType.VIDEO, saved.captured.mediaType)
        assertEquals(userId, saved.captured.userId)
        assertEquals(50_000_000L, saved.captured.fileSizeBytes)

        assertEquals("https://r2.test/videos/42/uuid_clip.mp4?sig=new", updated.captured.fileUrl)
        assertEquals("videos/42/uuid_clip.mp4", updated.captured.storageObjectKey)
        assertEquals(UploadStatus.DRAFT, updated.captured.status)
    }

    /** 제목은 원본 파일명 기반이고 100자를 넘지 않는다. */
    @Test
    @DisplayName("제목은 원본 파일명에서 확장자를 뗀 값이다")
    fun titleComesFromTheOriginalFilename() {
        givenPromotable()
        val saved = slot<Video>()
        every { videoRepository.save(capture(saved)) } answers { firstArg<Video>().copy(id = newVideoId) }

        useCase.promote(userId, assetId)

        assertEquals("여름 브이로그", saved.captured.title)
    }

    @Test
    @DisplayName("제목은 100자를 넘지 않는다")
    fun titleIsBounded() {
        givenPromotable(asset(originalFilename = "가".repeat(300) + ".mp4"))
        val saved = slot<Video>()
        every { videoRepository.save(capture(saved)) } answers { firstArg<Video>().copy(id = newVideoId) }

        useCase.promote(userId, assetId)

        assertEquals(100, saved.captured.title.length)
    }

    /* ── 보상 ─────────────────────────────────────────────────────────── */

    /**
     * 복사 호출이 던져도 **사본이 이미 만들어졌을 수 있다** — `copyByKey` 는 복사한 뒤 URL 을
     * 발급하므로 후처리에서 터지면 오브젝트만 남는다. 그래서 키를 호출 전에 등록한다.
     */
    @Test
    @DisplayName("복사가 실패해도 만들어졌을 수 있는 사본을 지운다")
    fun discardsTheCopyWhenCopyThrows() {
        givenPromotable()
        every { fileStoragePort.copyByKey(any(), any()) } throws IllegalStateException("스토리지 장애")

        assertFailsWith<IllegalStateException> { useCase.promote(userId, assetId) }

        verify(exactly = 1) { fileStoragePort.deleteByKey("videos/42/uuid_clip.mp4") }
    }

    /** 행 갱신이 실패하면 사본을 가리킬 근거가 사라진다 — 되돌린다. */
    @Test
    @DisplayName("행 갱신이 실패하면 사본을 지운다")
    fun discardsTheCopyWhenTheRowUpdateFails() {
        givenPromotable()
        every { videoRepository.update(any()) } throws RuntimeException("DB 오류")

        assertFailsWith<RuntimeException> { useCase.promote(userId, assetId) }

        verify(exactly = 1) { fileStoragePort.deleteByKey("videos/42/uuid_clip.mp4") }
    }

    /**
     * 여기까지 예외 없이 끝나도 **커밋이 실패**할 수 있다. 그러면 행은 사라지는데 사본은
     * 남는다 — `try/catch` 로는 닿지 않는 창이다.
     */
    @Test
    @DisplayName("커밋이 실패하면 사본을 지운다")
    fun discardsTheCopyWhenTheCommitFails() {
        givenPromotable()

        TransactionSynchronizationManager.initSynchronization()
        try {
            useCase.promote(userId, assetId)
            // 성공 경로에서는 아직 아무것도 지우지 않는다.
            verify(exactly = 0) { fileStoragePort.deleteByKey(any()) }

            TransactionSynchronizationManager.getSynchronizations()
                .forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

            verify(exactly = 1) { fileStoragePort.deleteByKey("videos/42/uuid_clip.mp4") }
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    /** 커밋되면 아무것도 지우지 않는다 — 방금 만든 사본을 스스로 지우면 안 된다. */
    @Test
    @DisplayName("커밋되면 사본을 지우지 않는다")
    fun keepsTheCopyWhenTheTransactionCommits() {
        givenPromotable()

        TransactionSynchronizationManager.initSynchronization()
        try {
            useCase.promote(userId, assetId)
            TransactionSynchronizationManager.getSynchronizations()
                .forEach { it.afterCompletion(TransactionSynchronization.STATUS_COMMITTED) }

            verify(exactly = 0) { fileStoragePort.deleteByKey(any()) }
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    /**
     * **정리가 실패하면 다음 보상이 다시 시도한다.**
     *
     * 스토리지가 잠깐 흔들려 삭제가 실패했을 뿐인데 키를 목록에서 빼 버리면, 롤백 보상이
     * 이미 등록돼 있는데도 두 번째 기회가 사라진다. 사본은 영구 고아가 되고 우리는 그
     * 용량을 계속 낸다 — 되돌린다고 믿는 코드가 남기는 고아라 더 나쁘다.
     */
    @Test
    @DisplayName("보상 삭제가 실패하면 롤백 보상이 같은 키를 다시 시도한다")
    fun failedCleanupIsRetriedByTheRollbackCompensation() {
        givenPromotable()
        every { videoRepository.update(any()) } throws RuntimeException("DB 오류")
        every { fileStoragePort.deleteByKey(any()) } throws IllegalStateException("스토리지 장애")

        TransactionSynchronizationManager.initSynchronization()
        try {
            assertFailsWith<RuntimeException> { useCase.promote(userId, assetId) }
            // catch 경로에서 한 번 시도했고 실패했다.
            verify(exactly = 1) { fileStoragePort.deleteByKey("videos/42/uuid_clip.mp4") }

            TransactionSynchronizationManager.getSynchronizations()
                .forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

            verify(exactly = 2) { fileStoragePort.deleteByKey("videos/42/uuid_clip.mp4") }
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    /**
     * 재시도가 성공하면 목록에서 빠져 **그 뒤로는 다시 지우지 않는다.** 성공한 키가 남으면
     * "지웠다" 로그가 중복돼 고아를 추적할 때 어느 것이 실제인지 알 수 없다.
     */
    @Test
    @DisplayName("재시도가 성공하면 그 뒤 보상에서는 다시 지우지 않는다")
    fun aSuccessfulRetryRemovesTheKeyFromFurtherCompensation() {
        givenPromotable()
        every { videoRepository.update(any()) } throws RuntimeException("DB 오류")
        // 첫 시도만 실패하고 그다음부터는 성공한다.
        every { fileStoragePort.deleteByKey(any()) } throws IllegalStateException("스토리지 장애") andThen Unit

        TransactionSynchronizationManager.initSynchronization()
        try {
            assertFailsWith<RuntimeException> { useCase.promote(userId, assetId) }
            val syncs = TransactionSynchronizationManager.getSynchronizations()

            syncs.forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }
            syncs.forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

            // catch(실패) + 첫 보상(성공) = 2회. 그 뒤로는 목록이 비어 호출이 없다.
            verify(exactly = 2) { fileStoragePort.deleteByKey("videos/42/uuid_clip.mp4") }
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    /** 두 보상 경로가 겹쳐도 한 번만 지운다. */
    @Test
    @DisplayName("롤백 보상은 이미 지운 키를 다시 지우지 않는다")
    fun compensationIsNotAppliedTwice() {
        givenPromotable()
        every { videoRepository.update(any()) } throws RuntimeException("DB 오류")

        TransactionSynchronizationManager.initSynchronization()
        try {
            assertFailsWith<RuntimeException> { useCase.promote(userId, assetId) }
            TransactionSynchronizationManager.getSynchronizations()
                .forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

            verify(exactly = 1) { fileStoragePort.deleteByKey("videos/42/uuid_clip.mp4") }
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    /* ── 탈퇴 가드 ────────────────────────────────────────────────────── */

    /** 탈퇴 진행 중인 계정은 새 데이터를 만들 수 없다 — 다른 쓰기 경로와 같은 게이트다. */
    @Test
    @DisplayName("쓰기가 막힌 계정은 승격할 수 없다")
    fun respectsTheAccountWriteGuard() {
        every { userWriteGuard.requireWritable(userId) } throws IllegalStateException("탈퇴 처리 중")

        assertFailsWith<IllegalStateException> { useCase.promote(userId, assetId) }

        verify(exactly = 0) { assetRepository.findById(any()) }
        verify(exactly = 0) { fileStoragePort.copyByKey(any(), any()) }
    }
}
