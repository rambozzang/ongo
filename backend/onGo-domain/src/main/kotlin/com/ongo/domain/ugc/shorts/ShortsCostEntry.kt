package com.ongo.domain.ugc.shorts

import java.math.BigDecimal
import java.time.Instant

/** One externally billable AI request made while processing a shorts pipeline run. */
data class ShortsCostEntry(
    val id: Long = 0,
    val runId: Long,
    val kind: Kind,
    val stage: String,
    val provider: String,
    val model: String,
    /** Populated for each speech-to-text request; null for LLM calls. */
    val audioDurationSeconds: BigDecimal? = null,
    /** Null means the provider did not report usage (never synthesize zero). */
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val recordedAt: Instant = Instant.now(),
) {
    enum class Kind { TRANSCRIPTION, LLM }
}
