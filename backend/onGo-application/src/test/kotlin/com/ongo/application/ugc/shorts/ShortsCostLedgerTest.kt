package com.ongo.application.ugc.shorts

import com.ongo.common.enums.AiProvider
import com.ongo.domain.settings.UserSettingsRepository
import com.ongo.domain.ugc.shorts.ShortsCostEntry
import com.ongo.domain.ugc.shorts.ShortsCostLedgerRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ChatResponse
import com.ongo.application.ai.ChatClientRegistry
import com.ongo.application.ai.ChatClientResolver
import java.math.BigDecimal

class ShortsCostLedgerTest {
    private val repository = mockk<ShortsCostLedgerRepository>()
    private val registry = mockk<ChatClientRegistry>()
    private val settings = mockk<UserSettingsRepository>()
    private val ledger = ShortsCostLedger(repository, ChatClientResolver(registry, settings))

    @Test
    fun `transcription seconds are recorded precisely and persistence failure is swallowed`() {
        val entry = slot<ShortsCostEntry>()
        every { repository.record(capture(entry)) } throws IllegalStateException("database unavailable")

        ledger.recordTranscription(runId = 52, durationMs = 3_600_125, model = "whisper-1")

        assertEquals(BigDecimal("3600.125"), entry.captured.audioDurationSeconds)
        assertEquals("OPENAI", entry.captured.provider)
        assertEquals("whisper-1", entry.captured.model)
        verify(exactly = 1) { repository.record(any()) }
    }

    @Test
    fun `LLM provider follows resolver fallback and missing usage remains null`() {
        val entry = slot<ShortsCostEntry>()
        every { settings.findByUserId(88) } returns null
        every { registry.isProviderAvailable(any()) } answers { firstArg<AiProvider>() == AiProvider.QWEN }
        every { repository.record(capture(entry)) } answers { firstArg() }
        val response = ChatResponse(emptyList(), org.springframework.ai.chat.metadata.ChatResponseMetadata.builder()
            .model("qwen-plus")
            .build())

        ledger.recordLlm(runId = 52, userId = 88, stage = "SEGMENT", response = response)

        assertEquals("QWEN", entry.captured.provider)
        assertEquals("qwen-plus", entry.captured.model)
        assertNull(entry.captured.inputTokens)
        assertNull(entry.captured.outputTokens)
    }
}
