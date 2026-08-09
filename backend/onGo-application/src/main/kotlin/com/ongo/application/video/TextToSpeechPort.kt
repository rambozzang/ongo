package com.ongo.application.video

data class TextToSpeechVoice(
    val id: String,
    val name: String,
)

data class GeneratedAudioFile(
    val path: java.nio.file.Path,
    val sizeBytes: Long,
    val contentType: String = "audio/mpeg",
)

/** Provider boundary for optional narrated video generation. */
interface TextToSpeechPort {
    fun availableVoices(): List<TextToSpeechVoice>
    fun synthesize(text: String, voiceId: String): GeneratedAudioFile
}
