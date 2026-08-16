package com.ongo.infrastructure.persistence.jooq

import com.fasterxml.jackson.databind.JsonNode
import com.ongo.application.ai.dto.AiBatchItemStatus
import com.ongo.application.ai.dto.AiBatchOperation
import com.ongo.application.ai.dto.AiBatchRequest
import com.ongo.application.ai.dto.AiBatchResponse
import com.ongo.application.ai.dto.BatchStatus
import com.ongo.application.ai.dto.ItemStatus
import com.ongo.domain.ai.AiPipeline
import com.ongo.domain.ai.AiPipelineStep
import com.ongo.domain.ai.PipelineStatus
import com.ongo.domain.ai.PipelineStepStatus
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
import java.time.LocalDateTime

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class AiPipelineJooqRepositoryIT {

    @Autowired lateinit var dsl: DSLContext
    @Autowired lateinit var repository: AiPipelineJooqRepository
    @Autowired lateinit var batchRepository: AiBatchJooqRepository

    private var userId: Long = 0
    private var videoId: Long = 0

    @BeforeEach
    fun setUp() {
        val suffix = System.nanoTime()
        userId = dsl.fetchOne(
            """
            INSERT INTO users(email, name, provider, provider_id)
            VALUES (?, 'AI test', 'GOOGLE'::auth_provider, ?)
            RETURNING id
            """.trimIndent(),
            "ai-pipeline-$suffix@example.com",
            "ai-pipeline-$suffix",
        )!!.get("id", Long::class.java)
        videoId = dsl.fetchOne(
            "INSERT INTO videos(user_id, title) VALUES (?, 'pipeline video') RETURNING id",
            userId,
        )!!.get("id", Long::class.java)
    }

    @Test
    fun `pipeline state and JSON results survive save reload and claim`() {
        val pipeline = AiPipeline(
            id = "pipeline-${System.nanoTime()}",
            userId = userId,
            videoId = videoId,
            channelId = 123L,
            steps = listOf(AiPipelineStep.STT, AiPipelineStep.SUGGEST_SCHEDULE),
            totalCreditsCharged = 13,
            discountApplied = false,
        )
        pipeline.stepStatuses[AiPipelineStep.STT] = PipelineStepStatus.COMPLETED
        pipeline.results[AiPipelineStep.STT] = mapOf("text" to "안녕하세요")

        repository.save(pipeline)

        val loaded = repository.findById(pipeline.id)
        assertNotNull(loaded)
        assertEquals(userId, loaded!!.userId)
        assertEquals(123L, loaded.channelId)
        assertEquals(PipelineStepStatus.COMPLETED, loaded.stepStatuses[AiPipelineStep.STT])
        val sttResult = loaded.results[AiPipelineStep.STT] as JsonNode
        assertEquals("안녕하세요", sttResult.path("text").asText())

        val claimed = repository.claimForExecution(
            id = pipeline.id,
            now = LocalDateTime.now(),
            staleBefore = LocalDateTime.now().minusMinutes(30),
        )
        assertEquals(PipelineStatus.RUNNING, claimed?.status)
    }

    @Test
    fun `batch item updates are durable and claimable`() {
        val request = AiBatchRequest(
            videoIds = listOf(videoId),
            operation = AiBatchOperation.GENERATE_META,
        )
        val batch = AiBatchResponse(
            batchId = "batch-${System.nanoTime()}",
            userId = userId,
            totalItems = 1,
            status = BatchStatus.PENDING,
            items = listOf(
                AiBatchItemStatus(videoId = videoId, videoTitle = "pipeline video", status = ItemStatus.PENDING),
            ),
        )

        batchRepository.save(batch, request)
        batchRepository.updateItem(
            batchId = batch.batchId,
            index = 0,
            status = ItemStatus.COMPLETED,
            result = mapOf("title" to "생성된 제목"),
        )

        val loaded = batchRepository.findById(batch.batchId)!!
        assertEquals(ItemStatus.COMPLETED, loaded.response.items.single().status)
        assertEquals(
            "생성된 제목",
            (loaded.response.items.single().result as Map<*, *>)["title"],
        )
        assertEquals(BatchStatus.PROCESSING, batchRepository.claimForExecution(
            batch.batchId,
            LocalDateTime.now(),
            LocalDateTime.now().minusMinutes(30),
        )?.response?.status)
    }

    companion object {
        @Container @JvmStatic
        val pg = PostgreSQLContainer("postgres:16").apply {
            withDatabaseName("ongo_test")
            withUsername("test")
            withPassword("test")
        }

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { pg.jdbcUrl }
            registry.add("spring.datasource.username") { pg.username }
            registry.add("spring.datasource.password") { pg.password }
        }
    }
}
