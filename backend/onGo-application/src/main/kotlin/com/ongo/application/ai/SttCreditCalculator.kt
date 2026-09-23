package com.ongo.application.ai

import com.ongo.application.ai.economics.AiUnitEconomics
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** 전사 크레딧을 SttUseCase 가 실제로 쓰는 모델 설정에서 계산한다. */
@Component
class SttCreditCalculator(
    val unitEconomics: AiUnitEconomics = AiUnitEconomics(),
    @param:Value("\${spring.ai.openai.audio.transcription.options.model:whisper-1}")
    private val model: String = "whisper-1",
) {
    fun creditsPer10Minutes(): Int = unitEconomics.sttCreditsPer10Minutes(model)

    /**
     * 원본 길이에 비례한 전사 크레딧. **길이를 모르면 매기지 않는다** — 예전에는 모르면 10분치만 받아,
     * 길이 측정에 실패한 3시간 영상이 10분 값으로 전사됐다. 호출부가 먼저 길이를 재야 한다.
     */
    fun creditsForDuration(durationMs: Long): Int {
        val creditsPerWindow = creditsPer10Minutes()
        require(durationMs > 0) { "원본 길이는 0보다 커야 합니다: $durationMs" }
        val windows = (durationMs - 1) / BILLING_WINDOW_MS + 1
        return Math.toIntExact(windows * creditsPerWindow)
    }

    companion object {
        const val BILLING_WINDOW_MS: Long = 10 * 60 * 1000L
    }
}
