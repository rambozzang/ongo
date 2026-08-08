package com.ongo.application.schedule

import com.ongo.common.exception.AccountFrozenException
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.application.video.StreamPublishUseCase
import com.ongo.common.enums.ScheduleStatus
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.video.VideoUploadRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 예약 게시 상태 모니터링.
 *
 * onGo는 영상을 직접 저장하지 않으므로, 업로드 시점에 플랫폼 네이티브 스케줄링
 * (YouTube publishAt, TikTok schedule_time 등)을 활용하여 즉시 업로드합니다.
 *
 * 이 스케줄러는 플랫폼에 예약 상태로 업로드된 영상의 실제 게시 상태를
 * 주기적으로 확인하여 Schedule 레코드의 상태를 동기화합니다.
 */
@Component
class ScheduleExecutor(
    private val scheduleRepository: ScheduleRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val distributedLockPort: DistributedLockPort,
    private val streamPublishUseCase: StreamPublishUseCase,
    transactionManager: PlatformTransactionManager,
    private val userWriteGuard: UserWriteGuard,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 예약 1건을 독립 트랜잭션으로 묶는다.
     *
     * 같은 클래스 안에서 `@Transactional` 메서드를 자기호출하면 프록시를 타지 않아
     * 전파 설정이 무시된다. 그래서 애노테이션 대신 [TransactionTemplate]을 쓴다.
     */
    private val perItemTx = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    companion object {
        private val KST = ZoneId.of("Asia/Seoul")
        private const val OUTCOME_OK = "OK"
        private const val OUTCOME_PARTIAL_FAILURE = "PARTIAL_FAILURE"
    }

    private val lockId = javaClass.name.hashCode().toLong()

    /**
     * 예약 시간이 지난 SCHEDULED 상태의 레코드를 확인하여 상태를 동기화합니다.
     * - 플랫폼 업로드가 PUBLISHED → Schedule도 PUBLISHED
     * - 플랫폼 업로드가 FAILED → Schedule도 FAILED
     * - 아직 PROCESSING 중 → 다음 주기에 재확인
     */
    // 바깥 루프에는 트랜잭션을 두지 않는다. 예약 1건씩 REQUIRES_NEW 로 묶고 루프 바깥에서 잡는다.
    //
    // 예전에는 이 메서드에 @Transactional 이 붙어 전체 예약이 한 트랜잭션이었고 항목별로
    // 예외를 삼켰다. jOOQ 가 스프링 트랜잭션에 참여하게 되면서 한 건의 DB 오류가 트랜잭션을
    // abort 시키고, 이후 예약이 전부 실패하며 이미 갱신한 상태까지 롤백되는 구조가 됐다.
    // CreditScheduler / ABTestEvaluator 와 같은 결함이며 같은 방식으로 고친다.
    //
    // 배치 전체를 한 트랜잭션에 묶으면 안 되는 이유가 하나 더 있다. 이 루프는
    // streamPublishUseCase.executeScheduledUpload 로 실제 플랫폼 업로드를 트리거한다.
    // 외부 호출을 긴 트랜잭션 안에 두면 커넥션을 오래 점유하고, 롤백해도 외부 호출은
    // 되돌릴 수 없다.
    @Scheduled(fixedRate = 60000)
    fun syncScheduleStatuses() {
        val ran = distributedLockPort.withLock(lockId) { syncDueSchedules() }
        if (!ran) log.debug("다른 인스턴스에서 예약 상태 동기화 실행 중, 스킵")
    }

    private fun syncDueSchedules() {
        val now = LocalDateTime.now(KST)
        val dueSchedules = scheduleRepository.findDueSchedules(now)

        if (dueSchedules.isEmpty()) return
        log.debug("상태 확인할 예약 {}건", dueSchedules.size)

        val failed = mutableListOf<Long?>()
        dueSchedules.forEach { candidate ->
            try {
                perItemTx.executeWithoutResult {
                    // findDueSchedules is deliberately only a candidate scan. The old
                    // FOR UPDATE/SKIP LOCKED lived outside this transaction and did not
                    // protect the external publish trigger. Claim inside the item
                    // transaction so only one worker can proceed.
                    val schedule = if (candidate.status == ScheduleStatus.SCHEDULED) {
                        scheduleRepository.claimDue(candidate.id!!, now) ?: return@executeWithoutResult
                    } else {
                        // PROCESSING is already claimed. Only status sync runs;
                        // it must never create a second external publish event.
                        candidate
                    }

                    // 24시간 이상 상태 변화 없으면 타임아웃 처리
                    val timeoutHours = 24L
                    val isTimedOut = schedule.scheduledAt.plusHours(timeoutHours).isBefore(now)

                    val uploads = videoUploadRepository.findByVideoId(schedule.videoId)

                    // 예약 시각 전 이벤트는 listener가 보류한다. 시각이 되면
                    // 아직 큐에 남아 있는 UPLOADING row를 한 번만 다시 dispatch한다.
                    // 확인불가/실패 row를 자동 재전송하면 외부에는 이미 게시됐을 수
                    // 있어 중복 게시가 생기므로 수동 재확인/재시도만 허용한다.
                    val needsDispatch = uploads.isEmpty() || uploads.any {
                        it.status == UploadStatus.UPLOADING && it.scheduledAt == null
                    }
                    if (needsDispatch) {
                        // 여기서만 게이트를 본다. 이 분기는 **외부 플랫폼에 실제로 게시**한다.
                        // 삭제를 요청한 계정으로 새 콘텐츠를 외부에 올리는 것은 되돌릴 수 없다.
                        //
                        // 아래 상태 갱신 분기는 게이트를 보지 않는다. 이미 시작된 업로드의
                        // 결과를 반영하는 정합화라 새 콘텐츠를 만들지 않고, 막으면 예약이
                        // 잘못된 상태로 영원히 남는다. 금융 정합성과 같은 논리다 —
                        // 멈추는 쪽이 더 나쁘다.
                        try {
                            userWriteGuard.requireWritable(schedule.userId)
                        } catch (e: AccountFrozenException) {
                            log.info(
                                "동결된 계정이라 예약 게시를 건너뛴다. scheduleId={} userId={}",
                                schedule.id, schedule.userId,
                            )
                            return@executeWithoutResult
                        }
                        log.info("예약 업로드 트리거 [scheduleId={}, videoId={}]", schedule.id, schedule.videoId)
                        streamPublishUseCase.executeScheduledUpload(schedule)
                        return@executeWithoutResult
                    }

                    if (uploads.isEmpty()) {
                        if (isTimedOut) {
                            scheduleRepository.update(schedule.copy(status = ScheduleStatus.FAILED))
                            log.error("예약 타임아웃 — 업로드 레코드 없음 [scheduleId={}, videoId={}]", schedule.id, schedule.videoId)
                        } else {
                            log.debug("예약 {}에 대한 업로드 레코드가 아직 없습니다 [videoId={}]", schedule.id, schedule.videoId)
                        }
                        return@executeWithoutResult
                    }

                    val newStatus = when {
                        uploads.all { it.status == UploadStatus.PUBLISHED } -> ScheduleStatus.PUBLISHED
                        uploads.any { it.status == UploadStatus.PUBLISHED } && uploads.any {
                            it.status == UploadStatus.FAILED ||
                                it.status == UploadStatus.REJECTED ||
                                it.status == UploadStatus.UNCONFIRMED
                        } -> ScheduleStatus.PARTIALLY_PUBLISHED
                        uploads.all { it.status == UploadStatus.UNCONFIRMED } -> ScheduleStatus.UNCONFIRMED
                        uploads.any { it.status == UploadStatus.UNCONFIRMED } -> ScheduleStatus.UNCONFIRMED
                        uploads.all { it.status == UploadStatus.FAILED || it.status == UploadStatus.REJECTED } -> ScheduleStatus.FAILED
                        // 타임아웃 검사는 PROCESSING 분기보다 먼저 와야 한다. 뒤에 두면 진행 중인
                        // 업로드가 하나라도 있는 한 PROCESSING 이 먼저 매치되어 영원히 도달하지 못하고,
                        // 멈춘 예약이 매 주기 재조회되며 무한 누적된다.
                        isTimedOut -> {
                            log.error("예약 타임아웃 — {}시간 이상 완료되지 않음 [scheduleId={}]", timeoutHours, schedule.id)
                            ScheduleStatus.FAILED
                        }
                        uploads.any { it.status == UploadStatus.PROCESSING || it.status == UploadStatus.REVIEW } -> ScheduleStatus.PROCESSING
                        else -> null // 아직 업로드 진행 중 — 다음 주기에 재확인
                    }

                    if (newStatus != null && newStatus != schedule.status) {
                        scheduleRepository.update(schedule.copy(status = newStatus))
                        log.info("예약 상태 갱신 [scheduleId={}, {} → {}]", schedule.id, schedule.status, newStatus)
                    }
                }
            } catch (e: Exception) {
                failed += candidate.id
                log.error("예약 상태 동기화 실패. job=scheduleSync scheduleId={}", candidate.id, e)
            }
        }

        if (failed.isEmpty()) {
            log.debug("예약 상태 동기화 완료. job=scheduleSync total={} outcome={}", dueSchedules.size, OUTCOME_OK)
        } else {
            log.error(
                "예약 상태 동기화 일부 실패. job=scheduleSync failed={} total={} outcome={} failedScheduleIds={}",
                failed.size, dueSchedules.size, OUTCOME_PARTIAL_FAILURE, failed,
            )
        }
    }
}
