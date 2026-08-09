package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.MediaType
import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.Visibility
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import com.ongo.domain.video.VideoPlatformMetaRepository
import com.ongo.domain.video.VideoRepository
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * 멀티 계정 게시의 DB 계약을 실제 Flyway 스키마에 대해 고정한다.
 *
 * 같은 플랫폼에 연결된 두 계정은 한 영상의 독립적인 게시 대상이어야 한다.
 * 이 제약이 다시 (video_id, platform) 유일키로 돌아가면 애플리케이션 단위 테스트는
 * 통과해도 실제 게시 요청이 두 번째 행에서 409/500으로 깨진다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class VideoUploadChannelTargetIT {

    @Autowired lateinit var dsl: DSLContext
    @Autowired lateinit var channelRepository: ChannelRepository
    @Autowired lateinit var videoUploadRepository: VideoUploadRepository
    @Autowired lateinit var videoPlatformMetaRepository: VideoPlatformMetaRepository
    @Autowired lateinit var videoRepository: VideoRepository

    companion object {
        @Container @JvmStatic
        val pg = PostgreSQLContainer("postgres:16").apply {
            withDatabaseName("ongo_test")
            withUsername("test")
            withPassword("test")
        }

        @JvmStatic @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { pg.jdbcUrl }
            registry.add("spring.datasource.username") { pg.username }
            registry.add("spring.datasource.password") { pg.password }
        }

        private const val EMAIL = "video-upload-channel-target-it@test.io"
    }

    @AfterEach
    fun cleanup() {
        dsl.execute("DELETE FROM video_uploads WHERE video_id IN (SELECT id FROM videos WHERE user_id IN (SELECT id FROM users WHERE email = ?))", EMAIL)
        dsl.execute("DELETE FROM videos WHERE user_id IN (SELECT id FROM users WHERE email = ?)", EMAIL)
        dsl.execute("DELETE FROM channels WHERE user_id IN (SELECT id FROM users WHERE email = ?)", EMAIL)
        dsl.execute("DELETE FROM users WHERE email = ?", EMAIL)
    }

    @Test
    @DisplayName("같은 플랫폼의 두 연결 계정은 한 영상에 각각 게시할 수 있다")
    fun samePlatformAccountsAreIndependentTargets() {
        val userId = dsl.fetchOne(
            """
            INSERT INTO users (email, name, provider, provider_id, role, plan_type)
            VALUES (?, 'target-test', 'GOOGLE', 'video-upload-channel-target-it', 'USER', 'FREE')
            RETURNING id
            """.trimIndent(),
            EMAIL,
        )!!.get(0, Long::class.java)
        val firstChannelId = insertChannel(userId, "youtube-account-a", "A")
        val secondChannelId = insertChannel(userId, "youtube-account-b", "B")
        val videoId = dsl.fetchOne(
            """
            INSERT INTO videos (user_id, title, status)
            VALUES (?, 'target-test-video', 'DRAFT')
            RETURNING id
            """.trimIndent(),
            userId,
        )!!.get(0, Long::class.java)

        dsl.execute(
            "INSERT INTO video_uploads (video_id, platform, channel_id, status) VALUES (?, 'YOUTUBE', ?, 'UPLOADING')",
            videoId,
            firstChannelId,
        )
        dsl.execute(
            "INSERT INTO video_uploads (video_id, platform, channel_id, status) VALUES (?, 'YOUTUBE', ?, 'UPLOADING')",
            videoId,
            secondChannelId,
        )

        assertEquals(
            2,
            dsl.fetchOne("SELECT count(*) FROM video_uploads WHERE video_id = ?", videoId)!!
                .get(0, Int::class.java),
        )
    }

    @Test
    @DisplayName("게시 핵심 저장소는 PostgreSQL enum 컬럼에 안전하게 round-trip한다")
    fun corePublishingRepositoriesRoundTripPostgresEnums() {
        val userId = dsl.fetchOne(
            """
            INSERT INTO users (email, name, provider, provider_id, role, plan_type)
            VALUES (?, 'enum-test', 'GOOGLE', 'video-upload-enum-it', 'USER', 'FREE')
            RETURNING id
            """.trimIndent(),
            EMAIL,
        )!!.get(0, Long::class.java)

        val channel = channelRepository.save(
            Channel(
                userId = userId,
                platform = Platform.YOUTUBE,
                platformChannelId = "enum-channel",
                channelName = "enum channel",
                accessToken = EncryptedToken("encrypted-token"),
            )
        )
        val video = Video(
            userId = userId,
            title = "enum video",
            mediaType = MediaType.VIDEO,
            status = UploadStatus.DRAFT,
        )
        val savedVideo = videoRepository.save(video).id!!
        val upload = videoUploadRepository.save(
            VideoUpload(
                videoId = savedVideo,
                platform = Platform.YOUTUBE,
                channelId = channel.id,
                status = UploadStatus.UPLOADING,
            )
        )
        val meta = videoPlatformMetaRepository.save(
            VideoPlatformMeta(
                videoUploadId = upload.id!!,
                title = video.title,
                visibility = Visibility.PUBLIC,
            )
        )

        assertEquals(Platform.YOUTUBE, channelRepository.findById(channel.id!!)!!.platform)
        assertEquals(UploadStatus.UPLOADING, videoUploadRepository.findById(upload.id!!)!!.status)
        assertEquals(Visibility.PUBLIC, videoPlatformMetaRepository.findByVideoUploadId(upload.id!!)!!.visibility)
        assertEquals(meta.id, videoPlatformMetaRepository.findByVideoUploadId(upload.id!!)!!.id)
    }

    private fun insertChannel(userId: Long, platformChannelId: String, name: String): Long =
        dsl.fetchOne(
            """
            INSERT INTO channels (user_id, platform, platform_channel_id, channel_name, access_token)
            VALUES (?, 'YOUTUBE', ?, ?, 'encrypted-test-token')
            RETURNING id
            """.trimIndent(),
            userId,
            platformChannelId,
            "채널 $name",
        )!!.get(0, Long::class.java)
}
