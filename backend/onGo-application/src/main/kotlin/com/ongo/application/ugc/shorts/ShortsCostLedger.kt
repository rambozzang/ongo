package com.ongo.application.ugc.shorts

import com.ongo.application.ai.ChatClientResolver
import com.ongo.common.enums.AiProvider
import com.ongo.domain.ugc.shorts.ShortsCostEntry
import com.ongo.domain.ugc.shorts.ShortsCostLedgerRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/** Best-effort cost measurement: persistence or provider metadata must never fail a paid run. */
@Service
class ShortsCostLedger(
    private val repository: ShortsCostLedgerRepository,
    /** 실제 호출이 고른 제공자를 같은 규칙으로 다시 얻는다 — 선택 규칙을 복사하지 않는다. */
    private val chatClientResolver: ChatClientResolver,
) {
    private val log = LoggerFactory.getLogger(ShortsCostLedger::class.java)

    fun recordTranscription(runId: Long, durationMs: Long?, model: String) {
        safelyRecord(
            ShortsCostEntry(
                runId = runId,
                kind = ShortsCostEntry.Kind.TRANSCRIPTION,
                stage = "TRANSCRIBE",
                provider = AiProvider.OPENAI.name,
                model = model,
                audioDurationSeconds = durationMs?.takeIf { it > 0 }
                    ?.let { BigDecimal.valueOf(it).divide(MILLIS_PER_SECOND, 3, RoundingMode.HALF_UP) },
            ),
        )
    }

    fun recordLlm(runId: Long, userId: Long, stage: String, response: ChatResponse?) {
        safelyRecord {
            val provider = chatClientResolver.resolveProvider(userId)
            val metadata = response?.metadata
            val usage = metadata?.usage
            // Spring AI substitutes EmptyUsage (0/0) when the provider omitted usage entirely.
            // Treat that sentinel as missing data, not as a free request.
            val usageReported = usage != null && (
                (usage.promptTokens ?: 0) > 0 ||
                    (usage.completionTokens ?: 0) > 0 ||
                    ((usage.nativeUsage as? Map<*, *>)?.isNotEmpty() == true)
                )
            ShortsCostEntry(
                runId = runId,
                kind = ShortsCostEntry.Kind.LLM,
                stage = stage,
                provider = provider.name,
                model = metadata?.model?.takeIf(String::isNotBlank) ?: UNKNOWN_MODEL,
                inputTokens = usage?.promptTokens?.takeIf { usageReported }?.toLong(),
                outputTokens = usage?.completionTokens?.takeIf { usageReported }?.toLong(),
            )
        }
    }

    private fun safelyRecord(entry: ShortsCostEntry) = safelyRecord { entry }

    private fun safelyRecord(entry: () -> ShortsCostEntry) {
        runCatching { repository.record(entry()) }
            .onFailure { log.warn("쇼츠 원가 기록 실패: 측정만 누락하고 실행은 계속합니다", it) }
    }

    companion object {
        private const val UNKNOWN_MODEL = "unknown"
        private val MILLIS_PER_SECOND = BigDecimal("1000")
    }
}
