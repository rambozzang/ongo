package com.ongo.application.competitor

import com.ongo.common.exception.AccountFrozenException
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.competitor.ChannelLookupPort
import com.ongo.domain.competitor.CompetitorAnalyticsDaily
import com.ongo.domain.competitor.CompetitorRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class CompetitorSyncScheduler(
    private val competitorRepository: CompetitorRepository,
    private val channelLookupPort: ChannelLookupPort,
    private val userWriteGuard: UserWriteGuard,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 3 * * *") // 매일 새벽 3시
    fun syncCompetitorData() {
        log.info("경쟁자 데이터 동기화 시작")
        val competitors = competitorRepository.findAll()
        val today = LocalDate.now()

        // 동결된 계정은 건너뛴다. 스케줄러는 HTTP 필터를 지나지 않으므로 여기서 직접 본다.
        //
        // 사용자당 한 번만 조회한다. 경쟁 채널은 사용자별로 여러 개라 항목마다 물으면
        // 같은 답을 반복해서 받게 된다.
        //
        // 조회가 실패해도 건너뛴다. 가드가 fail-closed 라 예외를 던지고, 여기서 그걸
        // "쓰기 허용"으로 해석하면 삭제 진행 중인 계정에 데이터가 들어간다.
        // 이번 회차를 거르는 쪽이 낫다 — 다음 실행에서 다시 시도한다.
        //
        // 주의: 이 캐시는 **외부 호출을 아끼기 위한 사전 검사**일 뿐이다.
        // 실제 쓰기 직전에는 캐시를 쓰지 않고 다시 확인한다(아래 참조).
        val writable = HashMap<Long, Boolean>()
        fun canWritePrecheck(userId: Long): Boolean = writable.getOrPut(userId) {
            try {
                userWriteGuard.requireWritable(userId)
                true
            } catch (e: AccountFrozenException) {
                log.info("동결된 계정이라 경쟁자 동기화를 건너뛴다. userId={} 사유={}", userId, e.message)
                false
            }
        }

        // 쓰기 직전 재확인. 캐시를 쓰지 않는다.
        //
        // 사전 검사와 실제 쓰기 사이에 외부 채널 조회가 끼어 있다. 네트워크 호출이라
        // 수백 ms 에서 수 초가 걸릴 수 있고, **그 사이에 사용자가 탈퇴를 요청할 수 있다.**
        // 사전 검사 결과만 믿으면 동결된 계정에 쓰게 된다.
        //
        // 사용자 행을 외부 호출 내내 잠그는 방법도 있지만, 외부 I/O 를 트랜잭션 안에
        // 두는 것이라 쓰지 않는다. 재확인과 실제 쓰기 사이에 남는 창은 외부 I/O 가 없어
        // 훨씬 짧다. 완전한 원자성이 필요해지면 쓰기 자체를
        // `WHERE deletion_state = 'ACTIVE'` 조건부로 바꿔야 한다.
        fun canWriteNow(userId: Long): Boolean =
            try {
                userWriteGuard.requireWritable(userId)
                true
            } catch (e: AccountFrozenException) {
                log.info(
                    "외부 조회 중 계정이 동결돼 쓰기를 건너뛴다. userId={} 사유={}",
                    userId, e.message,
                )
                false
            }

        var skipped = 0
        competitors.forEach { competitor ->
            if (!canWritePrecheck(competitor.userId)) {
                skipped++
                return@forEach
            }
            try {
                val result = channelLookupPort.lookupChannel(
                    platform = competitor.platform,
                    query = competitor.platformChannelId,
                )
                if (result.found) {
                    // 외부 호출이 끝났다. 쓰기 전에 게이트를 다시 본다.
                    if (!canWriteNow(competitor.userId)) {
                        skipped++
                        return@forEach
                    }

                    // Update competitor snapshot
                    competitorRepository.update(competitor.copy(
                        subscriberCount = result.subscriberCount,
                        totalViews = result.totalViews,
                        videoCount = result.videoCount,
                        avgViews = if (result.videoCount > 0) result.totalViews / result.videoCount else 0,
                        lastSyncedAt = LocalDateTime.now(),
                    ))

                    // Save daily analytics
                    competitorRepository.upsertAnalytics(CompetitorAnalyticsDaily(
                        competitorId = competitor.id!!,
                        date = today,
                        subscriberCount = result.subscriberCount,
                        videoCount = result.videoCount,
                        avgViews = if (result.videoCount > 0) result.totalViews / result.videoCount else 0,
                        totalViews = result.totalViews,
                    ))
                    log.debug("경쟁자 동기화 완료: {} ({})", competitor.channelName, competitor.platform)
                }
            } catch (e: Exception) {
                log.warn("경쟁자 동기화 실패 [{}]: {}", competitor.channelName, e.message)
            }
        }
        log.info("경쟁자 데이터 동기화 완료: {}건 (동결로 건너뜀 {}건)", competitors.size - skipped, skipped)
    }
}
