package com.ongo.application.publicapi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.video.TextToSpeechPort
import com.ongo.common.exception.BusinessException
import org.springframework.stereotype.Service

/** Implements the Postiz video/function discovery contract for onGo video types. */
@Service
class VideoFunctionUseCase(
    private val textToSpeechPort: TextToSpeechPort,
    private val objectMapper: ObjectMapper,
) {
    fun execute(request: PublicVideoFunctionRequest): JsonNode {
        require(request.identifier.trim() == IMAGE_TEXT_SLIDES) {
            "지원하지 않는 영상 identifier입니다: ${request.identifier}"
        }
        return when (request.functionName.trim()) {
            LOAD_VOICES -> objectMapper.createObjectNode().set<JsonNode>(
                "voices",
                objectMapper.valueToTree(textToSpeechPort.availableVoices()),
            )
            else -> throw BusinessException(
                "VIDEO_FUNCTION_UNAVAILABLE",
                "지원하지 않는 video function입니다: ${request.functionName}",
            )
        }
    }

    companion object {
        const val IMAGE_TEXT_SLIDES = "image-text-slides"
        const val LOAD_VOICES = "loadVoices"
    }
}
