package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.MediaType
import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.Visibility
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
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
import java.time.LocalDateTime

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
    @Autowired lateinit var subscriptionRepository: SubscriptionRepository

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
        dsl.execute("DELETE FROM subscriptions WHERE user_id IN (SELECT id FROM users WHERE email = ?)", EMAIL)
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
        dsl.execute(
            "UPDATE video_uploads SET scheduled_at = ? WHERE video_id = ?",
            LocalDateTime.now().plusHours(1),
            videoId,
        )

        assertEquals(
            2,
            dsl.fetchOne("SELECT count(*) FROM video_uploads WHERE video_id = ?", videoId)!!
                .get(0, Int::class.java),
        )

        val targets = videoUploadRepository.findByVideoId(videoId)
        assertEquals(1, videoUploadRepository.cancelScheduledUploadsByIds(setOf(targets.first().id!!), LocalDateTime.now()))
        assertEquals(UploadStatus.CANCELLED, videoUploadRepository.findById(targets.first().id!!)!!.status)
        assertEquals(UploadStatus.UPLOADING, videoUploadRepository.findById(targets.last().id!!)!!.status)
    }

    @Test
    @DisplayName("활성 lease가 있는 예약 업로드는 취소되지 않는다")
    fun scheduledUploadWithActiveLeaseCannotBeCancelled() {
        val userId = insertUser("scheduled-lease")
        val channelId = insertChannel(userId, "scheduled-lease-channel", "lease")
        val videoId = dsl.fetchOne(
            """
            INSERT INTO videos (user_id, title, status)
            VALUES (?, 'scheduled lease video', 'DRAFT')
            RETURNING id
            """.trimIndent(),
            userId,
        )!!.get(0, Long::class.java)
        val now = LocalDateTime.now()
        val uploadId = dsl.fetchOne(
            """
            INSERT INTO video_uploads (video_id, platform, channel_id, status, scheduled_at, lease_until)
            VALUES (?, 'YOUTUBE', ?, 'UPLOADING', ?, ?)
            RETURNING id
            """.trimIndent(),
            videoId,
            channelId,
            now.plusHours(1),
            now.plusMinutes(5),
        )!!.get(0, Long::class.java)

        assertEquals(0, videoUploadRepository.cancelScheduledUploadsByIds(setOf(uploadId), now))
        assertEquals(UploadStatus.UPLOADING, videoUploadRepository.findById(uploadId)!!.status)
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

        val now = LocalDateTime.now()
        val scheduled = videoUploadRepository.update(upload.copy(scheduledAt = now.minusMinutes(1)))
        assertEquals(1, videoUploadRepository.findDueScheduledUploads(now).size)
        val retryQueued = videoUploadRepository.update(
            scheduled.copy(
                scheduledAt = null,
                nextRetryAt = now.minusSeconds(1),
            ),
        )
        assertEquals(1, videoUploadRepository.findDueRetryUploads(now).size)
        videoUploadRepository.update(
            retryQueued.copy(
                status = UploadStatus.PROCESSING,
                pollToken = "enum-poll-token",
                scheduledAt = null,
                nextRetryAt = now.minusSeconds(1),
            ),
        )
        assertEquals(1, videoUploadRepository.findDueProcessingUploads(now).size)
    }

    @Test
    @DisplayName("구독 플랜 enum 조회는 PostgreSQL에서 문자열 비교 오류 없이 동작한다")
    fun subscriptionPlanLookupUsesPostgresEnumSafePredicate() {
        val userId = dsl.fetchOne(
            """
            INSERT INTO users (email, name, provider, provider_id, role, plan_type)
            VALUES (?, 'subscription-enum-test', 'GOOGLE', 'video-upload-subscription-it', 'USER', 'FREE')
            RETURNING id
            """.trimIndent(),
            EMAIL,
        )!!.get(0, Long::class.java)

        subscriptionRepository.save(
            Subscription(
                userId = userId,
                planType = com.ongo.common.enums.PlanType.PRO,
                status = com.ongo.common.enums.SubscriptionStatus.ACTIVE,
            ),
        )

        assertEquals(1, subscriptionRepository.findByPlanType(com.ongo.common.enums.PlanType.PRO).size)
    }

    @Test
    @DisplayName("동일한 예약 업로드는 PostgreSQL 원자 claim으로 한 작업자만 획득한다")
    fun uploadLeaseIsAtomicAcrossWorkers() {
        val userId = insertUser("lease-claim")
        val videoId = videoRepository.save(
            Video(userId = userId, title = "lease claim video", status = UploadStatus.DRAFT),
        ).id!!
        val uploadId = videoUploadRepository.save(
            VideoUpload(videoId = videoId, platform = Platform.YOUTUBE, status = UploadStatus.UPLOADING),
        ).id!!
        val now = LocalDateTime.now()

        val first = videoUploadRepository.claim(
            uploadId,
            owner = "worker-a",
            now = now,
            leaseUntil = now.plusMinutes(5),
        )
        val second = videoUploadRepository.claim(
            uploadId,
            owner = "worker-b",
            now = now,
            leaseUntil = now.plusMinutes(5),
        )

        assertEquals("worker-a", first?.leaseOwner)
        assertEquals(1, first?.attemptCount)
        assertEquals(null, second)
        assertEquals("worker-a", videoUploadRepository.findById(uploadId)?.leaseOwner)
    }

    @Test
    @DisplayName("lease가 만료된 외부 전송은 자동 재전송하지 않고 UNCONFIRMED로 복구한다")
    fun expiredUploadWithoutPollTokenBecomesUnconfirmed() {
        val userId = insertUser("lease-recovery")
        val videoId = videoRepository.save(
            Video(userId = userId, title = "lease recovery video", status = UploadStatus.DRAFT),
        ).id!!
        val now = LocalDateTime.now()
        val uploadId = videoUploadRepository.save(
            VideoUpload(
                videoId = videoId,
                platform = Platform.YOUTUBE,
                status = UploadStatus.UPLOADING,
                leaseOwner = "dead-worker",
                leaseUntil = now.minusMinutes(1),
            ),
        ).id!!

        val recovered = videoUploadRepository.recoverExpiredLeases(now)
        val persisted = videoUploadRepository.findById(uploadId)!!

        assertEquals(listOf(uploadId), recovered.mapNotNull { it.id })
        assertEquals(UploadStatus.UNCONFIRMED, persisted.status)
        assertEquals(null, persisted.leaseOwner)
        assertEquals(null, persisted.leaseUntil)
        assertEquals(null, persisted.pollToken)
        assertEquals("작업 lease가 만료되어 게시 결과 확인이 필요합니다.", persisted.errorMessage)
    }

    private fun insertUser(providerIdSuffix: String): Long =
        dsl.fetchOne(
            """
            INSERT INTO users (email, name, provider, provider_id, role, plan_type)
            VALUES (?, 'lease-test', 'GOOGLE', ?, 'USER', 'FREE')
            RETURNING id
            """.trimIndent(),
            EMAIL,
            "video-upload-$providerIdSuffix-it",
        )!!.get(0, Long::class.java)

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
