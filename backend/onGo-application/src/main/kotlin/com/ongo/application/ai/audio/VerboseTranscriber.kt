package com.ongo.application.ai.audio

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt

/**
 * 전사 요청 하나를 보내고 **응답 원문 JSON**(`text`, `segments`, `words`)을 돌려준다.
 *
 * Spring AI 2.0 의 `OpenAiAudioTranscriptionModel.call` 은 VERBOSE_JSON 으로 요청해도 본문 글자만
 * 돌려주고 문장·단어 시각을 버린다. 그 결과 쇼츠는 시각 없는 전사만 받아 **자막이 비고 클립 선택에
 * 타임코드가 들어가지 않았다** — 오류 없이. 이 포트가 시각까지 담긴 원문을 받아 온다.
 */
fun interface VerboseTranscriber {
    fun transcribe(prompt: AudioTranscriptionPrompt): String
}
