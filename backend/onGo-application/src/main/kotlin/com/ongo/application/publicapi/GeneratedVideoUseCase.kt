package com.ongo.application.publicapi

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.common.FileStoragePort
import com.ongo.application.video.GeneratedVideoFile
import com.ongo.application.video.VideoGenerationPort
import com.ongo.application.video.VideoGenerationSpec
import com.ongo.application.video.VideoOrientation
import com.ongo.application.video.TextToSpeechPort
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.BusinessException
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.contentsource.VideoSource
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.util.UUID

/** Postiz generate-video 결과를 영속 Video로 만들어 즉시 발행 대상으로 연결한다. */
@Service
class GeneratedVideoUseCase(
    private val videoGenerationPort: VideoGenerationPort,
    private val fileStoragePort: FileStoragePort,
    private val videoRepository: VideoRepository,
    private val userWriteGuard: UserWriteGuard,
    private val objectMapper: ObjectMapper,
    private val textToSpeechPort: TextToSpeechPort,
) {

    @Transactional
    fun generate(userId: Long, request: PublicGenerateVideoRequest): List<PublicGeneratedVideoResponse> {
        userWriteGuard.requireWritable(userId)
        val type = request.type.trim().lowercase()
        if (type != "image-text-slides") {
            throw BusinessException(
                "VIDEO_GENERATION_UNAVAILABLE",
                "현재 서버에서 지원하는 생성 방식은 image-text-slides입니다. veo3는 영상 생성 공급자 연결 후 제공됩니다.",
            )
        }
        val orientation = when (request.output.trim().lowercase()) {
            "vertical" -> VideoOrientation.VERTICAL
            "horizontal" -> VideoOrientation.HORIZONTAL
            else -> throw IllegalArgumentException("output은 vertical 또는 horizontal이어야 합니다")
        }
        val params = request.customParams.takeIf { it.isObject }
            ?: throw IllegalArgumentException("customParams는 JSON object여야 합니다")
        val prompt = params.path("prompt").asText("").trim()
        require(prompt.isNotBlank() && prompt.length <= MAX_PROMPT_LENGTH) {
            "customParams.prompt는 1~${MAX_PROMPT_LENGTH}자여야 합니다"
        }
        val voice = params.path("voice").asText("").trim().takeIf { it.isNotBlank() }
        if (voice != null && textToSpeechPort.availableVoices().none { it.id == voice }) {
            throw BusinessException(
                "VIDEO_VOICE_UNAVAILABLE",
                "요청한 음성을 사용할 수 없습니다. /video/function에서 사용 가능한 음성을 확인하세요.",
            )
        }

        val title = params.path("title").asText("").trim().takeIf { it.isNotBlank() }
            ?: prompt.lineSequence().firstOrNull().orEmpty().take(100)
        val tags = params.path("tags").takeIf { it.isArray }
            ?.mapNotNull { it.asText(null)?.trim()?.takeIf(String::isNotBlank) }
            ?.distinct()
            ?.take(MAX_TAGS)
            ?: emptyList()

        val generated = videoGenerationPort.generate(VideoGenerationSpec(prompt, orientation, voice))
        val key = "generated/$userId/${UUID.randomUUID()}.mp4"
        try {
            val fileUrl = Files.newInputStream(generated.path).use { input ->
                fileStoragePort.uploadByKey(key, input, generated.contentType, generated.sizeBytes)
            }
            val sourceReference = objectMapper.createObjectNode().apply {
                put("type", type)
                put("output", request.output.trim().lowercase())
                put("prompt", prompt)
                voice?.let { put("voice", it) }
            }
            val video = videoRepository.save(
                Video(
                    userId = userId,
                    title = title,
                    description = prompt,
                    tags = tags,
                    fileUrl = fileUrl,
                    fileSizeBytes = generated.sizeBytes,
                    originalFilename = "generated-${UUID.randomUUID()}.mp4",
                    status = UploadStatus.DRAFT,
                    source = VideoSource.GENERATED,
                    sourceReference = sourceReference,
                ),
            )
            val videoId = video.id ?: throw IllegalStateException("생성 영상 레코드를 만들지 못했습니다")
            return listOf(PublicGeneratedVideoResponse(videoId.toString(), fileUrl))
        } catch (error: Exception) {
            runCatching { fileStoragePort.deleteByKey(key) }
            throw error
        } finally {
            deleteGeneratedFile(generated)
        }
    }

    private fun deleteGeneratedFile(file: GeneratedVideoFile) {
        runCatching {
            Files.walk(file.path.parent).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    companion object {
        private const val MAX_PROMPT_LENGTH = 2_000
        private const val MAX_TAGS = 30
    }
}
