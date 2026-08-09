package com.ongo.application.publicapi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.common.FileStoragePort
import com.ongo.application.video.GeneratedVideoFile
import com.ongo.application.video.VideoGenerationPort
import com.ongo.application.video.VideoGenerationSpec
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GeneratedVideoUseCaseTest {
    private val generator = mockk<VideoGenerationPort>()
    private val storage = mockk<FileStoragePort>()
    private val videos = mockk<VideoRepository>()
    private val writeGuard = mockk<UserWriteGuard>()
    private lateinit var useCase: GeneratedVideoUseCase
    private val temporaryDirectories = mutableListOf<java.nio.file.Path>()

    @BeforeEach
    fun setUp() {
        every { writeGuard.requireWritable(7L, any(), any()) } just runs
        useCase = GeneratedVideoUseCase(generator, storage, videos, writeGuard, jacksonObjectMapper())
    }

    @AfterEach
    fun tearDown() {
        temporaryDirectories.forEach { directory ->
            if (Files.exists(directory)) Files.walk(directory).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    @Test
    fun `image text slides are stored as a draft video`() {
        val directory = Files.createTempDirectory("generated-video-test-").also(temporaryDirectories::add)
        val file = directory.resolve("generated.mp4").also { Files.write(it, ByteArray(8) { 1 }) }
        every { generator.generate(any()) } returns GeneratedVideoFile(file, 8)
        every { storage.uploadByKey(any(), any(), "video/mp4", 8) } returns "https://cdn.example/generated.mp4"
        val saved = slot<Video>()
        every { videos.save(capture(saved)) } answers { saved.captured.copy(id = 91L) }

        val result = useCase.generate(
            7L,
            PublicGenerateVideoRequest(
                type = "image-text-slides",
                output = "vertical",
                customParams = jacksonObjectMapper().readTree("""{"prompt":"오늘의 팁","title":"오늘의 팁","tags":["tip"]}"""),
            ),
        )

        assertEquals(listOf(PublicGeneratedVideoResponse("91", "https://cdn.example/generated.mp4")), result)
        assertEquals("오늘의 팁", saved.captured.title)
        assertEquals("DRAFT", saved.captured.status.name)
        assertEquals("GENERATED", saved.captured.source.name)
        assertEquals("오늘의 팁", saved.captured.sourceReference?.get("prompt")?.asText())
        verify { generator.generate(match<VideoGenerationSpec> { it.prompt == "오늘의 팁" }) }
        assertTrue(!Files.exists(directory))
    }

    @Test
    fun `veo3 is rejected before an unavailable provider can be called`() {
        assertFailsWith<com.ongo.common.exception.BusinessException> {
            useCase.generate(
                7L,
                PublicGenerateVideoRequest("veo3", "vertical", jacksonObjectMapper().readTree("""{"prompt":"x"}""")),
            )
        }
        verify(exactly = 0) { generator.generate(any()) }
    }

    @Test
    fun `database failure compensates the uploaded generated object`() {
        val directory = Files.createTempDirectory("generated-video-test-").also(temporaryDirectories::add)
        val file = directory.resolve("generated.mp4").also { Files.write(it, ByteArray(8)) }
        every { generator.generate(any()) } returns GeneratedVideoFile(file, 8)
        every { storage.uploadByKey(any(), any(), any(), any()) } returns "https://cdn.example/generated.mp4"
        every { videos.save(any()) } throws IllegalStateException("db unavailable")
        every { storage.deleteByKey(any()) } just runs

        assertFailsWith<IllegalStateException> {
            useCase.generate(7L, PublicGenerateVideoRequest("image-text-slides", "horizontal", jacksonObjectMapper().readTree("""{"prompt":"x"}""")))
        }
        verify(exactly = 1) { storage.deleteByKey(any()) }
    }
}
