package com.ongo.application.ugc

import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.accountdeletion.canWrite
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.ugc.analytics.MetricSnapshot
import com.ongo.domain.ugc.analytics.MetricSnapshotRepository
import com.ongo.domain.ugc.publishing.CampaignPostRepository
import com.ongo.domain.ugc.publishing.PostStatus
import com.ongo.common.enums.Platform
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * UGC 외부 게시물의 실제 플랫폼 지표를 주기적으로 가져온다.
 *
 * 브랜드가 숫자를 수동 입력해야만 하는 상태로 두면 UGC 보상과 성과가 신뢰를 잃는다.
 * 크리에이터가 등록한 postId와 연결된 채널 토큰으로 최신 스냅샷을 저장하고,
 * 토큰이 없거나 플랫폼이 일시 장애인 게시물은 다음 주기에 재시도한다.
 */
@Component
class CampaignMetricsSyncScheduler(
    private val campaignPostRepository: CampaignPostRepository,
    private val metricSnapshotRepository: MetricSnapshotRepository,
    private val channelRepository: ChannelRepository,
    private val platformClientPort: PlatformClientPort,
    private val tokenEncryptionPort: TokenEncryptionPort,
    private val userWriteGuard: UserWriteGuard,
    private val distributedLockPort: DistributedLockPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 21600000)
    fun syncMetrics() {
        val ran = distributedLockPort.withLock(javaClass.name.hashCode().toLong()) { syncAll() }
        if (!ran) log.debug("다른 인스턴스에서 UGC 지표 동기화 실행 중, 스킵")
    }

    private fun syncAll() {
        campaignPostRepository.findAll()
            .asSequence()
            .filter { it.platformPostId != null && it.status in SYNCABLE_STATUSES }
            .forEach { post -> syncPost(post.id!!, post.creatorId, post.platform, post.platformPostId!!) }
    }

    private fun syncPost(postId: Long, creatorId: Long, platformName: String, platformPostId: String) {
        val writable = userWriteGuard.canWrite(creatorId) { e ->
            log.info("동결된 크리에이터의 UGC 지표 동기화를 건너뜁니다. creatorId={} reason={}", creatorId, e.message)
        }
        if (!writable) return

        val platform = runCatching { Platform.valueOf(platformName) }.getOrNull() ?: return
        val channel = channelRepository.findByUserIdAndPlatform(creatorId, platform) ?: return
        runCatching {
            val analytics = platformClientPort.getVideoAnalytics(
                platform = platform,
                platformVideoId = platformPostId,
                accessToken = PlainToken(tokenEncryptionPort.decrypt(channel.accessToken).value),
                startDate = LocalDate.now(),
                endDate = LocalDate.now(),
            )
            metricSnapshotRepository.save(
                MetricSnapshot(
                    campaignPostId = postId,
                    capturedAt = LocalDateTime.now(),
                    views = analytics.views,
                    likes = analytics.likes,
                    comments = analytics.comments,
                    shares = analytics.shares,
                ),
            )
        }.onFailure { e ->
            log.warn("UGC 게시물 지표 동기화 실패 postId={} platform={} reason={}", postId, platform, e.message)
        }
    }

    companion object {
        private val SYNCABLE_STATUSES = setOf(PostStatus.EXTERNAL, PostStatus.PUBLISHED, PostStatus.PUBLISHING)
    }
}
