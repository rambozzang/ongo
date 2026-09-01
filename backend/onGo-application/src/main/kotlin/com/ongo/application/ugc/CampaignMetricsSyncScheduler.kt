package com.ongo.application.ugc

import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.accountdeletion.canWrite
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.application.analytics.PlatformMetricAvailability
import com.ongo.domain.ugc.analytics.MetricSnapshot
import com.ongo.domain.ugc.analytics.MetricSnapshotSource
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
        val today = LocalDate.now()
        campaignPostRepository.findAll()
            .asSequence()
            .filter { it.platformPostId != null && it.status in SYNCABLE_STATUSES }
            .forEach { post ->
                /*
                 * 플랫폼 API의 기간 집계 결과를 게시물의 누적 성과로 보여 주려면 오늘 하루가
                 * 아니라 캠페인 게시물이 생성된 날부터 조회해야 한다. 오늘만 조회하면
                 * `findLatestByCampaignPostId`가 최신 하루치만 선택해 보상·성과판단이 매번
                 * 하루 성과로 축소된다. 생성일이 미래로 잘못 저장된 데이터는 오늘로 제한하고,
                 * 생성일이 없는 레거시 행은 기존처럼 오늘 하루만 조회한다.
                 */
                val startDate = post.createdAt?.toLocalDate()
                    ?.let { if (it.isAfter(today)) today else it }
                    ?: today
                syncPost(
                    postId = post.id!!,
                    creatorId = post.creatorId,
                    platformName = post.platform,
                    platformPostId = post.platformPostId!!,
                    startDate = startDate,
                    endDate = today,
                )
            }
    }

    private fun syncPost(
        postId: Long,
        creatorId: Long,
        platformName: String,
        platformPostId: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ) {
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
                startDate = startDate,
                endDate = endDate,
            )
            /*
             * **플랫폼이 주지 않는 지표를 측정값으로 저장하지 않는다.**
             *
             * Facebook·WordPress·Vimeo 는 공유 수를, Pinterest 는 댓글 수를 API 로 주지
             * 않는다. 클라이언트는 그 자리에 0 을 채워 돌려주는데, 그대로 저장하면
             * 캠페인 합계가 "공유 0회" 라는 측정 결과가 된다 — 물어보지 않았을 뿐이다.
             *
             * 어느 지표를 못 물어보는지는 [PlatformMetricAvailability] 한 곳에서 온다.
             * 대시보드·수익 경로가 이미 쓰는 것과 같은 근거다.
             *
             * 값 자체는 그대로 저장한다. 지우면 나중에 플랫폼이 지원을 시작했을 때
             * 되짚을 근거가 없어지고, 판정은 [MetricSnapshot.measured] 가 한다.
             */
            val unavailable = MetricSnapshot.ALL_METRICS
                .filterNot { PlatformMetricAvailability.isAvailable(platform.name, it) }
                .toSet()

            metricSnapshotRepository.save(
                MetricSnapshot(
                    campaignPostId = postId,
                    capturedAt = LocalDateTime.now(),
                    views = analytics.views,
                    likes = analytics.likes,
                    comments = analytics.comments,
                    shares = analytics.shares,
                    source = MetricSnapshotSource.PLATFORM_SYNC,
                    unavailableMetrics = unavailable,
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
