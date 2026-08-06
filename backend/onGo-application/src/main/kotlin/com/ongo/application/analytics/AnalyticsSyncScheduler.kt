package com.ongo.application.analytics

import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.accountdeletion.canWrite
import com.ongo.application.config.ExecutorConfig
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.video.VideoUploadRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.Semaphore

@Component
class AnalyticsSyncScheduler(
    private val channelRepository: ChannelRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val platformClientPort: PlatformClientPort,
    private val tokenEncryptionPort: TokenEncryptionPort,
    private val distributedLockPort: DistributedLockPort,
    private val userWriteGuard: UserWriteGuard,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val lockId = javaClass.name.hashCode().toLong()

    /** 동시 API 호출 제한 — 플랫폼 API 과부하 방지 */
    private val analyticsSemaphore = Semaphore(5)

    @Scheduled(fixedRate = 21600000) // 6시간마다
    fun syncAnalytics() {
        if (!distributedLockPort.tryLock(lockId)) {
            log.debug("다른 인스턴스에서 분석 데이터 동기화 실행 중, 스킵")
            return
        }
        try {
            log.info("분석 데이터 동기화 시작")
            val channels = channelRepository.findAllActive()

            ExecutorConfig.newVirtualExecutor().use { executor ->
                val futures = channels.map { channel ->
                    executor.submit<Unit> {
                        try {
                            // 사전 검사. 동결 계정이면 토큰 복호화도 플랫폼 API 호출도 하지 않는다.
                            // 실제 안전은 upsert 직전 재확인이 담보한다 — 아래 syncVideoAnalytics 참조.
                            val writable = userWriteGuard.canWrite(channel.userId) {
                                log.info("동결된 계정이라 채널 분석 동기화를 건너뛴다. channelId={} userId={}", channel.id, channel.userId)
                            }
                            if (!writable) return@submit

                            val token = tokenEncryptionPort.decrypt(channel.accessToken)
                            val uploads = videoUploadRepository.findByPlatformAndUserId(channel.platform, channel.userId)

                            uploads.forEach { upload ->
                                val videoId = upload.platformVideoId
                                val uploadId = upload.id
                                if (videoId != null && uploadId != null) {
                                    val today = LocalDate.now()
                                    val latestDate = analyticsRepository.findLatestDateByVideoUploadId(uploadId)
                                    val backfillStart = latestDate?.plusDays(1) ?: today.minusDays(1)
                                    val backfillEnd = today.minusDays(1)

                                    // 누락된 날짜 백필 (최대 7일씩 — API 과부하 방지)
                                    val missingDates = generateSequence(backfillStart) { it.plusDays(1) }
                                        .takeWhile { !it.isAfter(backfillEnd) }
                                        .take(7)
                                        .toList()

                                    missingDates.forEach { date ->
                                        analyticsSemaphore.acquire()
                                        try {
                                            syncVideoAnalytics(channel.platform, videoId, token, uploadId, date, channel.userId)
                                        } finally {
                                            analyticsSemaphore.release()
                                        }
                                    }

                                    // 오늘 데이터 동기화
                                    analyticsSemaphore.acquire()
                                    try {
                                        syncVideoAnalytics(channel.platform, videoId, token, uploadId, today, channel.userId)
                                    } finally {
                                        analyticsSemaphore.release()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            log.error("채널 분석 동기화 실패 [channelId=${channel.id}]: ${e.message}")
                        }
                    }
                }

                futures.forEach { future ->
                    try {
                        future.get()
                    } catch (e: Exception) {
                        log.error("분석 동기화 Future 처리 실패", e)
                    }
                }
            }

            log.info("분석 데이터 동기화 완료")
        } finally {
            distributedLockPort.releaseLock(lockId)
        }
    }

    private fun syncVideoAnalytics(platform: com.ongo.common.enums.Platform, videoId: String, token: String, uploadId: Long, date: LocalDate, userId: Long) {
        try {
            val analytics = platformClientPort.getVideoAnalytics(
                platform, videoId, token, date, date
            )
            // 플랫폼 API 호출이 끝났다. 쓰기 직전에 게이트를 다시 본다.
            // 위 호출은 네트워크라 그 사이 탈퇴 요청이 들어올 수 있다. 채널 단위
            // 사전 검사만 믿으면 동결된 계정에 쓴다.
            //
            // 예외를 던지지 않고 조기 반환한다. 아래 catch(Exception) 이 삼켜서
            // "영상 분석 동기화 실패" 로 기록하면 동결 건너뜀이 장애로 보인다.
            val stillWritable = userWriteGuard.canWrite(userId) {
                log.info("플랫폼 조회 중 계정이 동결돼 분석 저장을 건너뛴다. uploadId={} userId={}", uploadId, userId)
            }
            if (!stillWritable) return

            analyticsRepository.upsert(AnalyticsDaily(
                videoUploadId = uploadId,
                date = date,
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
            log.warn("영상 분석 동기화 실패 [uploadId=$uploadId, date=$date]: ${e.message}")
        }
    }
}
