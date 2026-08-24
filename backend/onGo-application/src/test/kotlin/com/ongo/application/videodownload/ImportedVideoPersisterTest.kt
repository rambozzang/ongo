package com.ongo.application.videodownload

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.common.FileStoragePort
import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.StorageQuotaExceededException
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * URL 임포트의 확정 구간.
 *
 * 이 클래스가 존재하는 이유가 곧 계약이다 — 한도 판정과 행 저장이 **같은 트랜잭션**에 있어야
 * checkQuota 가 잡은 사용자 행 잠금이 확정 시점까지 유지된다. 잠금이 검사 직후 풀리면 동시에
 * 들어온 두 임포트가 같은 사용량을 읽고 둘 다 통과한다.
 */
class ImportedVideoPersisterTest {

    private val videoRepository = mockk<VideoRepository>()
    private val fileStoragePort = mockk<FileStoragePort>(relaxed = true)
    private val storageQuotaUseCase = mockk<StorageQuotaUseCase>(relaxed = true)

    private lateinit var persister: ImportedVideoPersister

    private val downloaded = DownloadedVideo(
        path = Path.of("/tmp/does-not-need-to-exist.mp4"),
        title = "가져온 영상",
        originalFilename = "source.mp4",
        contentType = "video/mp4",
        size = 1_000L,
    )

    private val sourceReference = ObjectMapper().createObjectNode().put("provider", "YOUTUBE")

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        persister = ImportedVideoPersister(videoRepository, fileStoragePort, storageQuotaUseCase)
    }

    private fun stream(): InputStream = ByteArrayInputStream(ByteArray(4))

    private fun persist() = persister.persist(
        userId = 100L,
        downloaded = downloaded,
        objectKey = "videos/100/imports/x.mp4",
        title = "제목",
        originalFilename = "source.mp4",
        sourceReference = sourceReference,
        openStream = ::stream,
    )

    /*
     * 프록시 기반이라 애노테이션이 빠지면 트랜잭션 자체가 생기지 않고, 잠금은 checkQuota 의
     * 암묵적 커밋과 함께 즉시 풀린다. 눈에 보이는 증상이 없어 놓치기 쉬워 명시적으로 고정한다.
     */
    @Test
    fun `persist is transactional so the quota lock survives until the row is saved`() {
        val method = ImportedVideoPersister::class.java.methods.first { it.name == "persist" }

        assertTrue(
            method.isAnnotationPresent(Transactional::class.java),
            "persist 에 @Transactional 이 없다. 트랜잭션이 없으면 checkQuota 의 사용자 행 잠금이 " +
                "검사 직후 풀려 동시 임포트가 한도를 우회한다.",
        )
    }

    @Test
    fun `checks the quota before uploading and saves the row in the same transaction`() {
        every { fileStoragePort.uploadByKey(any(), any(), any(), any()) } returns "https://storage/x.mp4"
        every { videoRepository.save(any()) } answers { firstArg<Video>().copy(id = 7L) }

        val video = persist()

        assertEquals(7L, video.id)
        verifyOrder {
            storageQuotaUseCase.checkQuota(100L, 1_000L, null)
            fileStoragePort.uploadByKey("videos/100/imports/x.mp4", any(), "video/mp4", 1_000L)
            videoRepository.save(any())
        }
    }

    @Test
    fun `does not upload or save when the quota is exceeded`() {
        every { storageQuotaUseCase.checkQuota(100L, 1_000L, null) } throws
            StorageQuotaExceededException(limitBytes = 100L, usedBytes = 100L, requiredBytes = 1_000L)

        assertFailsWith<StorageQuotaExceededException> { persist() }

        verify(exactly = 0) { fileStoragePort.uploadByKey(any(), any(), any(), any()) }
        verify(exactly = 0) { videoRepository.save(any()) }
    }

    @Test
    fun `deletes the object when the upload itself fails`() {
        every { fileStoragePort.uploadByKey(any(), any(), any(), any()) } throws IllegalStateException("스토리지 장애")

        assertFailsWith<BusinessException> { persist() }

        verify(exactly = 1) { fileStoragePort.deleteByKey("videos/100/imports/x.mp4") }
        verify(exactly = 0) { videoRepository.save(any()) }
    }

    /*
     * DB 쓰기가 실패했는데 오브젝트를 남기면 과금은 되는데 어떤 행도 가리키지 않는 고아가 된다.
     */
    @Test
    fun `deletes the object when the database write fails`() {
        every { fileStoragePort.uploadByKey(any(), any(), any(), any()) } returns "https://storage/x.mp4"
        every { videoRepository.save(any()) } throws IllegalStateException("DB 장애")

        assertFailsWith<IllegalStateException> { persist() }

        verify(exactly = 1) { fileStoragePort.deleteByKey("videos/100/imports/x.mp4") }
    }

    // ---- 커밋 실패까지 포함한 "정확히 한 번" 정리 ----

    /**
     * 트랜잭션 동기화를 켠 채로 persist 를 돌리고, 등록된 콜백을 직접 호출해 완료 상태를 흉내낸다.
     * 실제 DB 없이 커밋/롤백 시점의 동작을 재현하기 위한 것이다.
     */
    private fun persistWithSynchronization(block: () -> Unit = {}): List<TransactionSynchronization> {
        TransactionSynchronizationManager.initSynchronization()
        try {
            block()
            return TransactionSynchronizationManager.getSynchronizations()
        } finally {
            // getSynchronizations 로 목록을 받아둔 뒤 정리한다.
        }
    }

    private fun clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    /*
     * 커밋은 persist 가 반환된 뒤 프록시에서 일어난다. 커밋이 실패하면 행은 롤백되는데
     * 메서드 안의 try/catch 는 이미 지나가 버려, 콜백이 없으면 오브젝트만 남는 고아가 된다.
     */
    @Test
    fun `deletes the object exactly once when the commit fails after the method returns`() {
        every { fileStoragePort.uploadByKey(any(), any(), any(), any()) } returns "https://storage/x.mp4"
        every { videoRepository.save(any()) } answers { firstArg<Video>().copy(id = 7L) }

        val syncs = persistWithSynchronization { persist() }
        try {
            // 메서드가 성공적으로 반환한 시점에는 아직 아무것도 지우지 않는다.
            verify(exactly = 0) { fileStoragePort.deleteByKey(any()) }

            // 커밋 실패 = 롤백으로 완료.
            syncs.forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

            verify(exactly = 1) { fileStoragePort.deleteByKey("videos/100/imports/x.mp4") }
        } finally {
            clearSynchronization()
        }
    }

    @Test
    fun `does not delete anything when the transaction commits`() {
        every { fileStoragePort.uploadByKey(any(), any(), any(), any()) } returns "https://storage/x.mp4"
        every { videoRepository.save(any()) } answers { firstArg<Video>().copy(id = 7L) }

        val syncs = persistWithSynchronization { persist() }
        try {
            syncs.forEach { it.afterCompletion(TransactionSynchronization.STATUS_COMMITTED) }

            verify(exactly = 0) { fileStoragePort.deleteByKey(any()) }
        } finally {
            clearSynchronization()
        }
    }

    /*
     * 메서드 안에서 던진 예외는 catch 도 타고 롤백 콜백도 탄다. 두 경로가 겹쳐도 실제 삭제는
     * 한 번이어야 한다 — 두 번 지우면 다른 요청이 방금 올린 같은 키를 지울 위험이 생긴다.
     */
    @Test
    fun `deletes the object exactly once when both the catch and the rollback fire`() {
        every { fileStoragePort.uploadByKey(any(), any(), any(), any()) } returns "https://storage/x.mp4"
        every { videoRepository.save(any()) } throws IllegalStateException("DB 장애")

        val syncs = persistWithSynchronization {
            assertFailsWith<IllegalStateException> { persist() }
        }
        try {
            verify(exactly = 1) { fileStoragePort.deleteByKey("videos/100/imports/x.mp4") }

            syncs.forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

            verify(exactly = 1) { fileStoragePort.deleteByKey("videos/100/imports/x.mp4") }
        } finally {
            clearSynchronization()
        }
    }

    @Test
    fun `upload failure also deletes exactly once across both paths`() {
        every { fileStoragePort.uploadByKey(any(), any(), any(), any()) } throws IllegalStateException("스토리지 장애")

        val syncs = persistWithSynchronization {
            assertFailsWith<BusinessException> { persist() }
        }
        try {
            syncs.forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

            verify(exactly = 1) { fileStoragePort.deleteByKey("videos/100/imports/x.mp4") }
        } finally {
            clearSynchronization()
        }
    }

    /*
     * 한도 초과는 업로드 전에 막히므로 지울 오브젝트가 없다. 존재하지 않는 키를 지우려 들면
     * 다른 요청의 오브젝트를 건드릴 위험만 생긴다.
     */
    @Test
    fun `does not delete anything when the quota check rejects before any upload`() {
        every { storageQuotaUseCase.checkQuota(100L, 1_000L, null) } throws
            StorageQuotaExceededException(limitBytes = 100L, usedBytes = 100L, requiredBytes = 1_000L)

        val syncs = persistWithSynchronization {
            assertFailsWith<StorageQuotaExceededException> { persist() }
        }
        try {
            syncs.forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

            verify(exactly = 0) { fileStoragePort.deleteByKey(any()) }
        } finally {
            clearSynchronization()
        }
    }
}
