package com.ongo.application.ugc.shorts

import com.ongo.application.common.FileStoragePort
import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.ShortsClip
import com.ongo.domain.ugc.shorts.ShortsClipRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.application.activitylog.ActivityLogActions
import com.ongo.application.activitylog.ActivityLogUseCase
import io.mockk.*
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 렌더 산출물 확정.
 *
 * 여기서 실패하면 R2 에 파일은 남는데 그걸 가리키는 행이 없다. 아무도 찾지 못한 채 매달
 * 과금되는 고아이고, 쇼츠 파이프라인은 한 실행에서 클립을 여러 개 만들기 때문에 누수가
 * 클립 수만큼 곱해진다. 저장비의 실제 새는 구멍이라 후속 실패를 전부 덮어야 한다.
 */
class RenderedClipPersisterTest {

    private val videoRepository = mockk<VideoRepository>()
    private val clipRepository = mockk<ShortsClipRepository>(relaxed = true)
    private val stateService = mockk<ShortsRenderJobStateService>(relaxed = true)
    private val fileStoragePort = mockk<FileStoragePort>(relaxed = true)

    /** 퍼널 계측 통로. 연결이 성립한 뒤에만 사건이 나가야 한다. */
    private val eventPublisher = mockk<org.springframework.context.ApplicationEventPublisher>(relaxed = true)

    private lateinit var persister: RenderedClipPersister

    private val key = "shorts/run-1/clip-2-123456.mp4"
    private val clip = ShortsClip(id = 2L, runId = 1L, seq = 3, startMs = 0, endMs = 1_000, title = "클립")

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        persister = RenderedClipPersister(
            videoRepository, clipRepository, stateService, fileStoragePort, eventPublisher,
        )
    }

    @AfterEach
    fun tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    private fun persist() = persister.persist(
        jobId = "job-1",
        clip = clip,
        userId = 100L,
        objectKey = key,
        fileUrl = "https://storage/$key",
        sizeBytes = 5_000L,
    )

    /** 동기화를 켠 채 실행하고 등록된 콜백을 돌려준다 — DB 없이 커밋/롤백 시점을 재현한다. */
    private fun withSynchronization(block: () -> Unit): List<TransactionSynchronization> {
        TransactionSynchronizationManager.initSynchronization()
        block()
        return TransactionSynchronizationManager.getSynchronizations()
    }

    private fun givenVideoSaved() {
        every { videoRepository.save(any()) } answers { firstArg<Video>().copy(id = 7L) }
    }

    /*
     * 비동기 runRender 안의 private 메서드에 애노테이션을 달면 자기호출이라 프록시를 타지
     * 않아 트랜잭션이 아예 생기지 않는다. 그러면 세 저장이 각각 커밋돼 중간 실패 시
     * 클립만 RENDERED 이고 job 은 미완인 어중간한 상태가 남는다.
     */
    @Test
    fun `persist is transactional so the three writes commit together`() {
        val method = RenderedClipPersister::class.java.methods.first { it.name == "persist" }

        assertTrue(
            method.isAnnotationPresent(Transactional::class.java),
            "persist 에 @Transactional 이 없다. 트랜잭션이 없으면 영상 저장·클립 연결·완료 표시가 " +
                "따로 커밋돼 중간 실패 시 어중간한 상태가 남는다.",
        )
    }

    @Test
    fun `saves the video, links the clip and marks the job completed`() {
        givenVideoSaved()

        assertEquals(7L, persist())

        verifyOrder {
            videoRepository.save(any())
            clipRepository.update(match { it.renderedVideoId == 7L && it.status == ClipStatus.RENDERED })
            stateService.markCompleted("job-1", 7L)
        }
        verify(exactly = 0) { fileStoragePort.deleteByKey(any()) }
    }

    // ---- 후속 실패는 전부 정확히 한 번 정리 ----

    @Test
    fun `deletes the object exactly once when the video row fails to save`() {
        every { videoRepository.save(any()) } throws IllegalStateException("DB 장애")

        val syncs = withSynchronization { assertFailsWith<IllegalStateException> { persist() } }
        syncs.forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

        verify(exactly = 1) { fileStoragePort.deleteByKey(key) }
    }

    @Test
    fun `deletes the object exactly once when linking the clip fails`() {
        givenVideoSaved()
        every { clipRepository.update(any()) } throws IllegalStateException("클립 갱신 실패")

        val syncs = withSynchronization { assertFailsWith<IllegalStateException> { persist() } }
        syncs.forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

        verify(exactly = 1) { fileStoragePort.deleteByKey(key) }
    }

    @Test
    fun `deletes the object exactly once when marking the job completed fails`() {
        givenVideoSaved()
        every { stateService.markCompleted(any(), any()) } throws IllegalStateException("상태 전이 실패")

        val syncs = withSynchronization { assertFailsWith<IllegalStateException> { persist() } }
        syncs.forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

        verify(exactly = 1) { fileStoragePort.deleteByKey(key) }
    }

    /*
     * 커밋은 persist 가 반환된 뒤 프록시에서 일어난다. 메서드 안의 catch 는 이미 지나갔으므로
     * 롤백 콜백이 없으면 이 경로에서만 고아가 계속 쌓인다.
     */
    @Test
    fun `deletes the object exactly once when the commit fails after the method returns`() {
        givenVideoSaved()

        val syncs = withSynchronization { persist() }
        verify(exactly = 0) { fileStoragePort.deleteByKey(any()) }

        syncs.forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

        verify(exactly = 1) { fileStoragePort.deleteByKey(key) }
    }

    @Test
    fun `does not delete anything when the transaction commits`() {
        givenVideoSaved()

        val syncs = withSynchronization { persist() }
        syncs.forEach { it.afterCompletion(TransactionSynchronization.STATUS_COMMITTED) }

        verify(exactly = 0) { fileStoragePort.deleteByKey(any()) }
    }

    /*
     * catch 와 롤백 콜백이 겹쳐도 실제 삭제는 한 번이어야 한다. 두 번 지우면 같은 키를
     * 다시 올린 재시도의 결과물을 지울 위험이 생긴다.
     */
    @Test
    fun `deletes exactly once when both the catch and the rollback fire`() {
        givenVideoSaved()
        every { clipRepository.update(any()) } throws IllegalStateException("클립 갱신 실패")

        val syncs = withSynchronization { assertFailsWith<IllegalStateException> { persist() } }
        verify(exactly = 1) { fileStoragePort.deleteByKey(key) }

        syncs.forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

        verify(exactly = 1) { fileStoragePort.deleteByKey(key) }
    }

    /*
     * 정리 대상은 방금 올린 이 키 하나뿐이다. shorts/run-* 프리픽스를 통째로 지우면 같은
     * 실행에서 **성공한** 다른 클립까지 날아간다 — 사용자의 영구 영상 자산이다.
     */
    @Test
    fun `only removes its own object key and never the run prefix`() {
        every { videoRepository.save(any()) } throws IllegalStateException("DB 장애")

        val syncs = withSynchronization { assertFailsWith<IllegalStateException> { persist() } }
        syncs.forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }

        verify(exactly = 1) { fileStoragePort.deleteByKey(key) }
        verify(exactly = 0) { fileStoragePort.deleteByKey(match { it != key }) }
    }

    // ---- 퍼널 계측 ----

    /**
     * 영상 저장 → 클립 RENDERED 연결 → job COMPLETED 가 **모두** 성공한 뒤에만 사건이
     * 나간다. 사건에는 사용자·실행·클립 식별자만 담고 URL·스토리지 키·파일명은 담지 않는다.
     */
    @Test
    fun `publishes a clip available event once everything is linked`() {
        givenVideoSaved()

        persist()

        verify(exactly = 1) {
            eventPublisher.publishEvent(
                ShortsClipAvailableEvent(userId = 100L, runId = clip.runId, clipId = clip.id),
            )
        }
    }

    /**
     * 클립 연결이 실패하면 고객이 받아갈 수 있는 것이 없다. 그때 사건이 나가면 퍼널이
     * 도달하지 않은 첫 가치를 도달로 센다.
     */
    @Test
    fun `publishes nothing when the clip link fails`() {
        givenVideoSaved()
        every { clipRepository.update(any()) } throws IllegalStateException("클립 갱신 실패")

        assertFailsWith<IllegalStateException> { persist() }

        verify(exactly = 0) { eventPublisher.publishEvent(any<ShortsClipAvailableEvent>()) }
    }

    /** job 완료 표시가 실패해도 마찬가지다 — 세 저장이 함께 성립해야 한 건이다. */
    @Test
    fun `publishes nothing when marking the job completed fails`() {
        givenVideoSaved()
        every { stateService.markCompleted(any(), any()) } throws IllegalStateException("job 상태 전이 실패")

        assertFailsWith<IllegalStateException> { persist() }

        verify(exactly = 0) { eventPublisher.publishEvent(any<ShortsClipAvailableEvent>()) }
    }

    @Test
    fun `publishes nothing when the video record cannot be saved`() {
        every { videoRepository.save(any()) } throws IllegalStateException("DB 장애")

        assertFailsWith<IllegalStateException> { persist() }

        verify(exactly = 0) { eventPublisher.publishEvent(any<ShortsClipAvailableEvent>()) }
    }
}

/**
 * 커밋 뒤 기록을 맡는 리스너.
 *
 * 스프링 컨텍스트 없이 검증한다 — 애노테이션은 **호출 시점**만 정하고, 무엇을 기록할지는
 * 이 메서드의 평범한 코드다. "커밋 뒤에만 불린다"는 계약은 애노테이션 자체와 발행 쪽
 * 테스트가 함께 고정한다.
 */
class ShortsClipAvailableActivityListenerTest {

    private val activityLogUseCase = mockk<ActivityLogUseCase>(relaxed = true)
    private val listener = ShortsClipAvailableActivityListener(activityLogUseCase)

    private val event = ShortsClipAvailableEvent(userId = 7L, runId = 11L, clipId = 22L)

    /**
     * entityId 는 **실행 id** 다. 퍼널의 단위가 사용자와 실행이지 클립이 아니다 —
     * clipId 를 쓰면 순서 조건 집계가 클립 단위로 흩어진다.
     */
    @Test
    fun `records the run scoped availability action`() {
        listener.onClipAvailable(event)

        verify(exactly = 1) {
            activityLogUseCase.logActivityIndependently(
                userId = 7L,
                action = ActivityLogActions.SHORTS_CLIP_AVAILABLE,
                entityType = ActivityLogActions.ENTITY_SHORTS_RUN,
                entityId = 11L,
            )
        }
        // 산출물 트랜잭션에 묶이는 경로는 쓰지 않는다.
        verify(exactly = 0) {
            activityLogUseCase.logActivity(any(), any(), any(), any(), any(), any(), any())
        }
    }

    /**
     * **핵심 신뢰성 경계.** 기록이 던져도 리스너 밖으로 나가면 안 된다. 이 시점에 영상과
     * 연결은 이미 커밋돼 있고, 예외가 새면 렌더 실행 스레드나 연결 API 응답이 깨진다.
     *
     * 협력자의 내부 삼킴이 아니라 리스너 경계가 막는지 보려고 mock 은 `throws` 만 둔다.
     */
    @Test
    fun `never lets a logging failure escape`() {
        val failing = mockk<ActivityLogUseCase>()
        every {
            failing.logActivityIndependently(any(), any(), any(), any())
        } throws RuntimeException("활동 로그 저장 실패")

        ShortsClipAvailableActivityListener(failing).onClipAvailable(event)

        verify(exactly = 1) { failing.logActivityIndependently(any(), any(), any(), any()) }
    }

    /** 사건은 식별자만 담는다. URL·스토리지 키·파일명·렌더 spec 이 섞이면 안 된다. */
    @Test
    fun `event carries identifiers only`() {
        val fields = ShortsClipAvailableEvent::class.java.declaredFields
            .map { it.name }
            .filterNot { it == "\$stable" }
            .toSet()

        assertEquals(setOf("userId", "runId", "clipId"), fields)
    }

    /**
     * 커밋 뒤에만 불린다는 계약을 애노테이션으로 고정한다. 직접 호출 테스트로는 시점을
     * 검증할 수 없으므로, 선언 자체를 본다.
     */
    @Test
    fun `handler is bound to the after-commit phase`() {
        val method = ShortsClipAvailableActivityListener::class.java
            .getDeclaredMethod("onClipAvailable", ShortsClipAvailableEvent::class.java)
        val annotation = method.getAnnotation(TransactionalEventListener::class.java)

        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase)
    }
}
