package com.ongo.application.video

import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.PlanType
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.domain.contentsource.VideoSource
import com.ongo.domain.storage.StorageQuotaPort
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.MonthlyUploadPolicy
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 월 업로드 한도 — **요금표 첫 줄이 서버에서 실제로 지켜지는지** 고정한다.
 *
 * 예전에는 옛 스트리밍 경로 한 곳에서만 검사했고, 지금 화면이 쓰는 업로드·가져오기·에셋 전환은
 * 검사 없이 통과했다. 무료 사용자도 무제한으로 올릴 수 있었다 — 유료로 바꿀 이유가 서버에서
 * 새고 있었다.
 */
class MonthlyUploadQuotaUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val videoRepository = mockk<VideoRepository>()
    private val storageQuotaPort = mockk<StorageQuotaPort>(relaxed = true)
    private val useCase = MonthlyUploadQuotaUseCase(userRepository, videoRepository, storageQuotaPort)
    private val userId = 7L

    private fun plan(p: PlanType) {
        every { userRepository.findById(userId) } returns
            User(id = userId, email = "u@t.io", name = "u", provider = AuthProvider.GOOGLE, providerId = "p", planType = p)
    }

    private fun used(n: Long) {
        every {
            videoRepository.countByUserIdAndMonthAndSources(userId, any(), MonthlyUploadPolicy.COUNTED_SOURCES)
        } returns n
    }

    @Test
    fun `무료는 5번째까지 통과하고 6번째에서 막힌다`() {
        plan(PlanType.FREE)
        used(4)
        useCase.check(userId)

        used(5)
        val e = assertFailsWith<PlanLimitExceededException> { useCase.check(userId) }
        assertEquals("월간 업로드", e.feature)
        assertEquals(5, e.limit)
    }

    @Test
    fun `요금제마다 한도가 다르다`() {
        plan(PlanType.STARTER); used(29); useCase.check(userId)
        used(30); assertFailsWith<PlanLimitExceededException> { useCase.check(userId) }

        plan(PlanType.PRO); used(99); useCase.check(userId)
        used(100); assertFailsWith<PlanLimitExceededException> { useCase.check(userId) }
    }

    @Test
    fun `Business 는 막지 않는다`() {
        plan(PlanType.BUSINESS)
        used(1_000_000)
        useCase.check(userId)
    }

    /** 사용자 행이 없으면 가장 좁은 요금제로 본다. 유료로 추정하면 한도가 새는 쪽으로 틀린다. */
    @Test
    fun `요금제를 알 수 없으면 무료로 본다`() {
        every { userRepository.findById(userId) } returns null
        used(5)
        assertFailsWith<PlanLimitExceededException> { useCase.check(userId) }
    }

    /**
     * **동시 요청.** 잠금 없이 세면 두 업로드가 같은 횟수를 읽고 둘 다 통과한다.
     * 잠금이 **세기 전에** 잡혀야 한다.
     */
    @Test
    fun `세기 전에 사용자 행을 잠근다`() {
        plan(PlanType.PRO)
        used(0)

        useCase.check(userId)

        verifyOrder {
            storageQuotaPort.lockUserForQuota(userId)
            videoRepository.countByUserIdAndMonthAndSources(userId, any(), any())
        }
    }

    @Test
    fun `표시용 사용량은 무제한일 때 한도를 비운다`() {
        plan(PlanType.BUSINESS); used(3)
        val u = useCase.usage(userId)
        assertEquals(3, u.used)
        assertNull(u.limit)

        plan(PlanType.FREE)
        assertEquals(5, useCase.usage(userId).limit)
    }

    // ── 무엇을 세는가 ────────────────────────────────────────────────────

    /**
     * **핵심.** 쇼츠 결과물을 세면 쇼츠를 한 번 돌린 무료 사용자가 자기 영상을 못 올린다.
     * 사본을 세면 같은 콘텐츠를 두 번 센다.
     */
    @Test
    fun `원본만 세고 서버 생성물과 사본은 세지 않는다`() {
        assertTrue(MonthlyUploadPolicy.counts(VideoSource.UPLOAD_PC))
        assertTrue(MonthlyUploadPolicy.counts(VideoSource.GOOGLE_DRIVE))
        assertTrue(MonthlyUploadPolicy.counts(VideoSource.URL_IMPORT))
        assertFalse(MonthlyUploadPolicy.counts(VideoSource.GENERATED), "쇼츠 클립이 월 업로드를 먹는다")
        assertFalse(MonthlyUploadPolicy.counts(VideoSource.DERIVED), "재활용·반복 예약 사본을 두 번 센다")
    }

    /** 새 출처가 생기면 셀지 말지를 **의식적으로** 정해야 한다. 기본값으로 흘러가지 않게 한다. */
    @Test
    fun `모든 출처가 셀지 여부가 정해져 있다`() {
        val decided = MonthlyUploadPolicy.COUNTED_SOURCES + setOf(VideoSource.GENERATED, VideoSource.DERIVED)
        assertEquals(
            VideoSource.entries.toSet(), decided,
            "새 VideoSource 가 추가됐다 — MonthlyUploadPolicy 에서 셀지 정하고 이 테스트를 갱신할 것",
        )
    }
}
