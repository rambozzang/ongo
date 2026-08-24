package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_SHORTS_PIPELINE_RUNS
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * `ugc_shorts_pipeline_runs.source_duration_ms` (V101) 의 스키마 계약을 고정한다.
 *
 * 이 값은 전사 크레딧 산정의 근거다. 왕복이 깨지거나 갱신으로 바뀌면 사용자가 인용받은
 * 금액과 실제 청구액이 달라지는데, 그 차이는 원장에만 남아 아무도 보지 않는다.
 * 단위 테스트는 매핑을 검증할 수 없으므로 실제 스키마에 대고 확인한다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ShortsRunSourceDurationIT {

    @Autowired lateinit var runRepo: PipelineRunRepository
    @Autowired lateinit var dsl: DSLContext

    companion object {
        @Container @JvmStatic
        val pg = PostgreSQLContainer("postgres:16").apply {
            withDatabaseName("ongo_test"); withUsername("test"); withPassword("test")
        }

        @JvmStatic @DynamicPropertySource
        fun props(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url") { pg.jdbcUrl }
            r.add("spring.datasource.username") { pg.username }
            r.add("spring.datasource.password") { pg.password }
        }

        private const val SIXTY_MINUTES_MS = 60L * 60 * 1000
    }

    @BeforeEach
    fun setup() {
        dsl.deleteFrom(UGC_SHORTS_PIPELINE_RUNS).execute()
    }

    private fun newRun(sourceDurationMs: Long?) = PipelineRun(
        workspaceId = 1, userId = 1, sourceVideoId = 1, sourceDurationMs = sourceDurationMs,
    )

    @Test
    fun `source duration survives insert and read`() {
        val saved = runRepo.save(newRun(SIXTY_MINUTES_MS))

        assertEquals(SIXTY_MINUTES_MS, saved.sourceDurationMs)
        assertEquals(SIXTY_MINUTES_MS, runRepo.findById(saved.id)!!.sourceDurationMs)
    }

    /* 이 컬럼 도입 이전 행과 같은 상태. 소급 추정하지 않으므로 NULL 로 읽혀야 한다. */
    @Test
    fun `null source duration reads back as null`() {
        val saved = runRepo.save(newRun(null))

        assertNull(saved.sourceDurationMs)
        assertNull(runRepo.findById(saved.id)!!.sourceDurationMs)
    }

    /*
     * 청구 근거는 실행이 만들어질 때 인용된 값이다. 갱신이 이 값을 바꿀 수 있으면 재실행이
     * 첫 견적과 다른 금액을 낸다. update 는 이 컬럼을 건드리지 않으므로, 도메인 객체에
     * 다른 값을 담아 갱신해도 저장된 값이 유지돼야 한다.
     */
    @Test
    fun `update does not overwrite the quoted duration`() {
        val saved = runRepo.save(newRun(SIXTY_MINUTES_MS))

        runRepo.update(saved.copy(sourceDurationMs = 1L, clipCount = 3))

        val reloaded = runRepo.findById(saved.id)!!
        assertEquals(SIXTY_MINUTES_MS, reloaded.sourceDurationMs)
        assertEquals(3, reloaded.clipCount)
    }

    /*
     * 0 이나 음수는 길이가 아니다. 애플리케이션이 이미 걸러내지만 그 검사를 우회한 경로가
     * 생기면 조용히 0 크레딧 청구가 되므로, 스키마가 마지막 방어선이어야 한다.
     */
    @Test
    fun `non positive source duration is rejected by the schema`() {
        assertThrows(DataIntegrityViolationException::class.java) {
            runRepo.save(newRun(0))
        }
        assertThrows(DataIntegrityViolationException::class.java) {
            runRepo.save(newRun(-1))
        }
    }
}
