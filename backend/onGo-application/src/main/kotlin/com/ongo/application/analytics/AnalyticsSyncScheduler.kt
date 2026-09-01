package com.ongo.application.analytics

import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.accountdeletion.canWrite
import com.ongo.application.config.ExecutorConfig
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.RevenueMeasurement
import com.ongo.domain.analytics.RevenueStatus
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

    private companion object {
        /**
         * 수익 재조회 기간. `estimatedRevenue` 는 확정까지 며칠 걸리고 월말에 조정되므로
         * 이미 저장한 날짜도 이 기간 안에서는 계속 다시 묻는다.
         */
        const val REVENUE_LOOKBACK_DAYS = 30
    }

    @Scheduled(fixedRate = 21600000) // 6시간마다
    fun syncAnalytics() {
        // tryLock/releaseLock 은 획득과 해제가 다른 커넥션에서 일어나 락이 누수된다.
        // PostgreSQL 자문 락은 세션 범위라 다른 커넥션에서 해제해도 풀리지 않는다.
        // withLock 은 한 커넥션 안에서 획득·해제를 끝낸다.
        val ran = distributedLockPort.withLock(lockId) { syncAllChannels() }
        if (!ran) log.debug("다른 인스턴스에서 분석 데이터 동기화 실행 중, 스킵")
    }

    private fun syncAllChannels() {
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

                        val token = tokenEncryptionPort.decrypt(channel.accessToken).value
                        val uploads = videoUploadRepository.findByPlatformAndUserId(channel.platform, channel.userId)

                        uploads.forEach { upload ->
                            val videoId = upload.platformVideoId
                            val uploadId = upload.id
                            if (videoId != null && uploadId != null) {
                              // 업로드 하나의 실패가 같은 채널의 나머지 영상을 막지 않는다.
                              runCatching {
                                val today = LocalDate.now()
                                val latestDate = analyticsRepository.findLatestDateByVideoUploadId(uploadId)

                                /*
                                 * 이미 수집한 날짜를 읽어 **빈 날짜**를 고른다.
                                 *
                                 * 예전에는 `MAX(date) + 1` 부터 앞으로만 훑어서, 날짜 D 가
                                 * 실패한 뒤 D+1 이 저장되면 D 를 영원히 건너뛰었다. 수익은
                                 * 분석 행이 있어야 갱신되므로 그 날짜의 수익도 함께 유실됐다.
                                 */
                                val windowStart = today.minusDays(AnalyticsSyncWindow.GAP_WINDOW_DAYS.toLong())
                                val existingDates = analyticsRepository
                                    .findByVideoUploadIdAndDateRange(uploadId, windowStart, today.minusDays(1))
                                    .map { it.date }
                                    .toSet()
                                val missingDates =
                                    AnalyticsSyncWindow.datesToSync(today, latestDate, existingDates)

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

                                // 수익은 별도 호출이다. 위 백필 로직을 타지 않는다.
                                analyticsSemaphore.acquire()
                                try {
                                    syncVideoRevenue(channel.platform, videoId, token, uploadId, today, channel.userId)
                                } finally {
                                    analyticsSemaphore.release()
                                }
                              }.onFailure { e ->
                                  log.warn("영상 동기화 실패로 건너뛴다. uploadId={}: {}", uploadId, e.message)
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
    }

    private fun syncVideoAnalytics(platform: com.ongo.common.enums.Platform, videoId: String, token: String, uploadId: Long, date: LocalDate, userId: Long) {
        try {
            val analytics = platformClientPort.getVideoAnalytics(
                platform, videoId, com.ongo.domain.channel.PlainToken(token), date, date
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

    /**
     * 광고 수익 동기화. **일반 분석과 완전히 분리된 경로다.**
     *
     * 두 가지 이유로 위 백필 로직(`최신 날짜 이후만 조회`)을 쓰지 않는다.
     *
     * 1. `estimatedRevenue` 는 확정까지 며칠 걸리고 월말에 조정된다. 한 번 행이 생긴
     *    날짜를 다시 보지 않으면 확정 전 값이 영구히 굳는다.
     * 2. 수익 조회는 별도 OAuth scope 라 실패 확률이 다르다. 여기서 실패해도 같은 주기의
     *    일반 분석 저장은 이미 끝나 있어야 한다.
     *
     * 그래서 매 실행마다 최근 [REVENUE_LOOKBACK_DAYS] 일을 통째로 다시 묻는다. API 호출은
     * 영상당 1회다 — 일별 차원(dimensions=day)으로 한 번에 받는다.
     */
    private fun syncVideoRevenue(
        platform: com.ongo.common.enums.Platform,
        videoId: String,
        token: String,
        uploadId: Long,
        today: LocalDate,
        userId: Long,
    ) {
        // 수익을 수집하지 않는 플랫폼은 부르지 않는다. UNSUPPORTED 행을 30일치 쓰는 것은
        // 낭비고, DB 기본값이 이미 UNSUPPORTED 라 결과도 같다.
        if (!PlatformMetricAvailability.isAvailable(platform.name, PlatformMetricAvailability.REVENUE_MICRO)) {
            return
        }

        try {
            // 오늘은 어차피 비어 있다. 확정 지연을 감안해 어제까지만 본다.
            val end = today.minusDays(1)
            val start = end.minusDays(REVENUE_LOOKBACK_DAYS - 1L)
            if (end.isBefore(start)) return

            val report = platformClientPort.getVideoRevenue(
                platform, videoId, com.ongo.domain.channel.PlainToken(token), start, end
            )

            // 플랫폼 호출이 끝났다. 쓰기 직전에 게이트를 다시 본다 — 그 사이 탈퇴 요청이
            // 들어올 수 있다. 일반 분석 경로와 같은 이유다.
            val stillWritable = userWriteGuard.canWrite(userId) {
                log.info("수익 조회 중 계정이 동결돼 저장을 건너뛴다. uploadId={} userId={}", uploadId, userId)
            }
            if (!stillWritable) return

            generateSequence(start) { it.plusDays(1) }
                .takeWhile { !it.isAfter(end) }
                .forEach { date ->
                    // 응답에 없는 날짜는 PENDING 이다. 0 원으로 채우면 확정 전 금액을
                    // "0 원 확정"으로 굳혀 버린다. 저장 계층이 기존 실측값을 지키므로
                    // PENDING 이 이미 확인된 금액을 덮지 않는다.
                    //
                    // 분석 행이 없는 날짜는 updateRevenue 가 0 행을 갱신하고 끝난다 —
                    // 여기서 행을 만들면 조회수 0 짜리 가짜 분석 데이터가 최적 업로드
                    // 시간 추천에 섞인다. 일반 분석이 그 날짜를 채우면 다음 주기가
                    // 수익을 붙인다.
                    val measurement = when (report.status) {
                        RevenueStatus.MEASURED -> report.daily[date] ?: RevenueMeasurement.PENDING
                        RevenueStatus.PENDING -> RevenueMeasurement.PENDING
                        RevenueStatus.PERMISSION_REQUIRED -> RevenueMeasurement.PERMISSION_REQUIRED
                        RevenueStatus.UNSUPPORTED -> RevenueMeasurement.UNSUPPORTED
                        RevenueStatus.ERROR -> RevenueMeasurement.ERROR
                    }
                    analyticsRepository.updateRevenue(uploadId, date, measurement)
                }

            if (report.status == RevenueStatus.PERMISSION_REQUIRED) {
                log.info(
                    "수익 조회 권한이 없어 PERMISSION_REQUIRED 로 기록한다. 채널 재연동이 필요하다. uploadId={} userId={}",
                    uploadId, userId,
                )
            }
        } catch (e: Exception) {
            // 일반 분석은 이미 저장됐다. 수익 실패가 그것을 되돌리지 않는다.
            log.warn("영상 수익 동기화 실패 [uploadId=$uploadId]: ${e.message}")
        }
    }
}
