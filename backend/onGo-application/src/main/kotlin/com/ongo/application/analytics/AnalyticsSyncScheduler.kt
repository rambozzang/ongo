package com.ongo.application.analytics

import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.video.VideoUploadRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class AnalyticsSyncScheduler(
    private val channelRepository: ChannelRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val platformClientPort: PlatformClientPort,
    private val tokenEncryptionPort: TokenEncryptionPort
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 21600000) // 6시간마다
    fun syncAnalytics() {
        log.info("분석 데이터 동기화 시작")
        val channels = channelRepository.findAllActive()

        channels.forEach { channel ->
            try {
                val token = tokenEncryptionPort.decrypt(channel.accessToken)
                val uploads = videoUploadRepository.findByPlatformAndUserId(channel.platform, channel.userId)

                uploads.forEach { upload ->
                    val videoId = upload.platformVideoId
                    if (videoId != null) {
                        try {
                            val today = LocalDate.now()
                            val analytics = platformClientPort.getVideoAnalytics(
                                channel.platform, videoId, token, today.minusDays(1), today
                            )
                            analyticsRepository.upsert(AnalyticsDaily(
                                videoUploadId = upload.id!!,
                                date = today,
                                views = analytics.views.toInt(),
                                likes = analytics.likes.toInt(),
                                commentsCount = analytics.comments.toInt(),
                                shares = analytics.shares.toInt(),
                                watchTimeSeconds = analytics.watchTimeSeconds,
                                subscriberGained = analytics.subscriberGained,
                                impressions = analytics.impressions.toInt(),
                                avgViewDurationSeconds = analytics.avgViewDurationSeconds.toInt(),
                            ))
                        } catch (e: Exception) {
                            log.warn("영상 분석 동기화 실패 [uploadId=${upload.id}]: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("채널 분석 동기화 실패 [channelId=${channel.id}]: ${e.message}")
            }
        }
        log.info("분석 데이터 동기화 완료")
    }
}
