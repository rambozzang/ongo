package com.ongo.application.video

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 조각 계획 계산. 여기가 틀리면 서명 크기가 틀려 **모든 조각이 거부**되거나,
 * 마지막 조각이 빠져 파일이 잘린 채 완료된다.
 */
class MultipartUploadPlanTest {

    private val mib = 1024L * 1024

    @Test
    fun `나누어떨어지지 않으면 마지막 조각만 짧다`() {
        val plan = MultipartUploadPlan.forSize(40 * mib)
        assertEquals(3, plan.partCount)
        assertEquals(16 * mib, plan.sizeOf(1))
        assertEquals(16 * mib, plan.sizeOf(2))
        assertEquals(8 * mib, plan.sizeOf(3))
    }

    @Test
    fun `나누어떨어지면 마지막 조각도 꽉 찬다`() {
        val plan = MultipartUploadPlan.forSize(32 * mib)
        assertEquals(2, plan.partCount)
        assertEquals(16 * mib, plan.sizeOf(2))
    }

    /** 조각 크기 합이 원본과 정확히 같아야 한다 — 1 바이트라도 어긋나면 파일이 깨진다. */
    @Test
    fun `모든 조각 크기의 합은 원본 크기와 같다`() {
        listOf(1L, 5 * mib, 16 * mib - 1, 16 * mib + 1, 2L * 1024 * mib, 2L * 1024 * mib + 7).forEach { size ->
            val plan = MultipartUploadPlan.forSize(size)
            assertEquals(size, (1..plan.partCount).sumOf(plan::sizeOf), "size=$size")
        }
    }

    @Test
    fun `작은 파일은 한 조각이다`() {
        val plan = MultipartUploadPlan.forSize(1_000)
        assertEquals(1, plan.partCount)
        assertEquals(1_000, plan.sizeOf(1))
    }

    /** 2 GB 는 16 MiB 로 128 조각이다. */
    @Test
    fun `2GB 는 128 조각이다`() {
        assertEquals(128, MultipartUploadPlan.forSize(2L * 1024 * mib).partCount)
    }

    /** S3 는 조각 10,000 개까지다. 넘으면 조각을 키워야 한다. */
    @Test
    fun `아주 큰 파일은 조각 수가 10000 을 넘지 않게 조각을 키운다`() {
        val size = 200L * 1024 * 1024 * 1024 // 200 GiB
        val plan = MultipartUploadPlan.forSize(size)
        assertTrue(plan.partCount <= MultipartUploadPlan.MAX_PARTS)
        assertTrue(plan.partSize > MultipartUploadPlan.DEFAULT_PART_SIZE)
        assertEquals(size, (1..plan.partCount).sumOf(plan::sizeOf))
    }

    @Test
    fun `범위 밖 조각 번호는 거부한다`() {
        val plan = MultipartUploadPlan.forSize(40 * mib)
        assertFailsWith<IllegalArgumentException> { plan.sizeOf(0) }
        assertFailsWith<IllegalArgumentException> { plan.sizeOf(4) }
    }

    /** S3 는 마지막을 제외한 조각이 5 MiB 미만이면 완료를 거부한다. */
    @Test
    fun `5MiB 미만 조각 크기는 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { MultipartUploadPlan(100 * mib, 4 * mib) }
    }

    @Test
    fun `0 바이트는 거부한다`() {
        assertFailsWith<IllegalArgumentException> { MultipartUploadPlan.forSize(0) }
    }
}
