package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.ShortsCostEntry
import com.ongo.domain.ugc.shorts.ShortsCostLedgerRepository
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_SHORTS_COST_LEDGER
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal

/** V117 and the jOOQ adapter are verified against PostgreSQL, not an in-memory substitute. */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ShortsCostLedgerIT {

    @Autowired lateinit var runRepository: PipelineRunRepository
    @Autowired lateinit var ledgerRepository: ShortsCostLedgerRepository
    @Autowired lateinit var dsl: DSLContext

    companion object {
        @Container @JvmStatic
        val pg = PostgreSQLContainer("postgres:16").apply {
            withDatabaseName("ongo_test"); withUsername("test"); withPassword("test")
        }

        @JvmStatic @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { pg.jdbcUrl }
            registry.add("spring.datasource.username") { pg.username }
            registry.add("spring.datasource.password") { pg.password }
        }
    }

    @BeforeEach
    fun cleanLedger() {
        dsl.deleteFrom(UGC_SHORTS_COST_LEDGER).execute()
    }

    @Test
    fun `V117 stores measured transcription duration and preserves unavailable token usage as null`() {
        val run = runRepository.save(PipelineRun(workspaceId = 1, userId = 1, sourceVideoId = 1))

        ledgerRepository.record(
            ShortsCostEntry(
                runId = run.id,
                kind = ShortsCostEntry.Kind.TRANSCRIPTION,
                stage = "TRANSCRIBE",
                provider = "OPENAI",
                model = "whisper-1",
                audioDurationSeconds = BigDecimal("3600.000"),
            ),
        )
        ledgerRepository.record(
            ShortsCostEntry(
                runId = run.id,
                kind = ShortsCostEntry.Kind.LLM,
                stage = "SEGMENT",
                provider = "QWEN",
                model = "qwen-plus",
                inputTokens = 123,
                outputTokens = null,
            ),
        )

        val kind = DSL.field("kind", String::class.java)
        val provider = DSL.field("provider", String::class.java)
        val model = DSL.field("model", String::class.java)
        val duration = DSL.field("audio_duration_seconds", BigDecimal::class.java)
        val inputTokens = DSL.field("input_tokens", Long::class.javaObjectType)
        val outputTokens = DSL.field("output_tokens", Long::class.javaObjectType)
        val runId = DSL.field("run_id", Long::class.javaObjectType)
        val id = DSL.field("id", Long::class.javaObjectType)
        val rows = dsl.select(kind, provider, model, duration, inputTokens, outputTokens)
            .from(UGC_SHORTS_COST_LEDGER)
            .where(runId.eq(run.id))
            .orderBy(id)
            .fetch()

        assertEquals(2, rows.size)
        assertEquals("TRANSCRIPTION", rows[0].get(kind))
        assertEquals("OPENAI", rows[0].get(provider))
        assertEquals("whisper-1", rows[0].get(model))
        assertEquals(BigDecimal("3600.000"), rows[0].get(duration))
        assertNull(rows[0].get(inputTokens))
        assertNull(rows[0].get(outputTokens))

        assertEquals("LLM", rows[1].get(kind))
        assertEquals("QWEN", rows[1].get(provider))
        assertEquals("qwen-plus", rows[1].get(model))
        assertEquals(123L, rows[1].get(inputTokens))
        assertNull(rows[1].get(outputTokens))
    }
}
