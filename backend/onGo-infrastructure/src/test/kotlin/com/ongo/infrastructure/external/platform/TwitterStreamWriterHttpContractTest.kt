package com.ongo.infrastructure.external.platform

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.common.enums.Visibility
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.infrastructure.external.twitter.TwitterApi
import com.ongo.infrastructure.external.twitter.TwitterConfig
import com.ongo.infrastructure.external.twitter.TwitterMediaApi
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

/**
 * MockK만으로는 Twitter API의 실제 path/query/header와 multipart append body를 검증할 수 없다.
 * 이 테스트는 INIT -> APPEND -> FINALIZE -> CREATE TWEET 전체 wire contract를 고정한다.
 */
class TwitterStreamWriterHttpContractTest {
    private lateinit var apiServer: MockWebServer
    private lateinit var uploadServer: MockWebServer

    @BeforeEach
    fun setUp() {
        apiServer = MockWebServer().apply { start() }
        uploadServer = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        apiServer.shutdown()
        uploadServer.shutdown()
    }

    @Test
    fun `Twitter sends media upload and tweet creation wire contract`() {
        uploadServer.enqueue(json("""{"media_id_string":"media-1"}"""))
        uploadServer.enqueue(MockResponse().setResponseCode(200))
        uploadServer.enqueue(json("""{"media_id_string":"media-1"}"""))
        apiServer.enqueue(json("""{"data":{"id":"tweet-1","text":"테스트 영상"}}"""))

        val config = mockk<TwitterConfig>()
        every { config.getUploadBaseUrl() } returns uploadServer.url("/upload").toString().removeSuffix("/")

        val writer = TwitterStreamWriter(
            twitterApi = proxy(apiServer, TwitterApi::class.java),
            twitterMediaApi = proxy(uploadServer, TwitterMediaApi::class.java),
            twitterConfig = config,
            fileTransferHelper = PlatformFileTransferHelper(ObjectMapper()),
        )

        writer.initSession(meta(), PlainToken("twitter-token"), null, 4, null)
        writer.writeChunk("test".toByteArray(), 0, 4)
        val result = writer.complete()

        assertThat(result.published).isTrue()
        assertThat(result.platformVideoId).isEqualTo("tweet-1")
        assertThat(result.platformUrl).isEqualTo("https://twitter.com/i/status/tweet-1")

        val init = uploadServer.takeRequest()
        assertThat(init.path).contains("/1.1/media/upload.json")
        assertThat(init.path).contains("command=INIT")
        assertThat(init.path).contains("total_bytes=4")
        assertThat(init.getHeader("Authorization")).isEqualTo("Bearer twitter-token")

        val append = uploadServer.takeRequest()
        assertThat(append.path).isEqualTo("/upload/1.1/media/upload.json")
        assertThat(append.getHeader("Authorization")).isEqualTo("Bearer twitter-token")
        assertThat(append.body.readUtf8())
            .contains("name=\"command\"")
            .contains("APPEND")
            .contains("name=\"media_id\"")
            .contains("media-1")
            .contains("name=\"segment_index\"")
            .contains("test")

        val finalize = uploadServer.takeRequest()
        assertThat(finalize.path).contains("/1.1/media/upload.json")
        assertThat(finalize.path).contains("command=FINALIZE")
        assertThat(finalize.path).contains("media_id=media-1")

        val tweet = apiServer.takeRequest()
        assertThat(tweet.method).isEqualTo("POST")
        assertThat(tweet.path).isEqualTo("/2/tweets")
        assertThat(tweet.getHeader("Authorization")).isEqualTo("Bearer twitter-token")
        assertThat(tweet.body.readUtf8())
            .contains("테스트 영상")
            .contains("설명")
            .contains("media-1")
            .contains("\"reply_settings\":\"following\"")
            .contains("\"community_id\":\"community-1\"")
            .contains("\"made_with_ai\":true")
    }

    @Test
    fun `Twitter creates Postiz text thread with reply chain`() {
        uploadServer.enqueue(json("""{"media_id_string":"media-thread"}"""))
        uploadServer.enqueue(MockResponse().setResponseCode(200))
        uploadServer.enqueue(json("""{"media_id_string":"media-thread"}"""))
        apiServer.enqueue(json("""{"data":{"id":"tweet-root","text":"root"}}"""))
        apiServer.enqueue(json("""{"data":{"id":"tweet-reply","text":"reply"}}"""))

        val config = mockk<TwitterConfig>()
        every { config.getUploadBaseUrl() } returns uploadServer.url("/upload").toString().removeSuffix("/")
        val writer = TwitterStreamWriter(
            twitterApi = proxy(apiServer, TwitterApi::class.java),
            twitterMediaApi = proxy(uploadServer, TwitterMediaApi::class.java),
            twitterConfig = config,
            fileTransferHelper = PlatformFileTransferHelper(ObjectMapper()),
        )

        writer.initSession(
            meta(customSettingsJson = """{"__postiz_thread":[{"content":"후속 답글"}]}"""),
            PlainToken("twitter-token"),
            null,
            4,
            null,
        )
        writer.writeChunk("test".toByteArray(), 0, 4)
        val result = writer.complete()

        assertThat(result.published).isTrue()
        assertThat(apiServer.takeRequest().body.readUtf8()).contains("테스트 영상")
        val reply = apiServer.takeRequest()
        assertThat(reply.path).isEqualTo("/2/tweets")
        assertThat(reply.body.readUtf8())
            .contains("후속 답글")
            .contains("in_reply_to_tweet_id")
            .contains("tweet-root")
    }

    private fun json(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun meta(customSettingsJson: String? = """{"who_can_reply_post":"following","community":"community-1","made_with_ai":true}""") = VideoPlatformMeta(
        videoUploadId = 1L,
        title = "테스트 영상",
        description = "설명",
        tags = listOf("tag"),
        visibility = Visibility.PUBLIC,
        customSettingsJson = customSettingsJson,
    )

    private inline fun <reified T : Any> proxy(server: MockWebServer, type: Class<T>): T {
        val restClient = PlatformRestClientSupport
            .builder(server.url("/").toString().removeSuffix("/"))
            .build()
        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(type)
    }
}
