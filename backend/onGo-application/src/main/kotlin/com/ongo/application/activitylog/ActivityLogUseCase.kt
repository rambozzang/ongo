package com.ongo.application.activitylog

import com.ongo.application.activitylog.dto.ActivityLogListResponse
import com.ongo.application.activitylog.dto.ActivityLogResponse
import com.ongo.domain.activitylog.ActivityLog
import com.ongo.domain.activitylog.ActivityLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

@Service
class ActivityLogUseCase(
    private val activityLogRepository: ActivityLogRepository,
    transactionManager: PlatformTransactionManager,
) {

    /**
     * 바깥 트랜잭션이 롤백돼도 살아남는 기록용 트랜잭션.
     *
     * 같은 클래스 안에서 `@Transactional` 메서드를 자기호출하면 프록시를 타지 않아 전파
     * 설정이 무시된다. 그래서 애노테이션 대신 [TransactionTemplate] 을 쓴다
     * (`CreditScheduler` 와 같은 이유·같은 방식).
     */
    private val independentTx = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    private val log = LoggerFactory.getLogger(ActivityLogUseCase::class.java)

    fun listLogs(
        userId: Long,
        page: Int,
        size: Int,
        action: String?,
        entityType: String?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
    ): ActivityLogListResponse {
        val logs = activityLogRepository.findByUserId(userId, page, size, action, entityType, startDate, endDate)
        val total = activityLogRepository.countByUserId(userId, action, entityType, startDate, endDate)
        return ActivityLogListResponse(
            logs = logs.map { it.toResponse() },
            totalElements = total,
            page = page,
            size = size,
        )
    }

    @Transactional
    fun logActivity(
        userId: Long,
        action: String,
        entityType: String? = null,
        entityId: Long? = null,
        details: String = "{}",
        ipAddress: String? = null,
        userAgent: String? = null,
    ): ActivityLogResponse {
        val log = ActivityLog(
            userId = userId,
            action = action,
            entityType = entityType,
            entityId = entityId,
            details = details,
            ipAddress = ipAddress,
            userAgent = userAgent,
        )
        return activityLogRepository.save(log).toResponse()
    }

    /**
     * 바깥 트랜잭션과 **무관하게** 기록한다. 호출자가 곧 예외를 던져 롤백될 때 쓴다.
     *
     * ## 왜 따로 두는가
     *
     * 거절당한 요청은 그 자체가 세어야 할 사건이다. 예를 들어 크레딧 부족으로 쇼츠 실행
     * 생성이 막히면 그 트랜잭션은 통째로 롤백되는데, 일반 기록은 그 롤백에 함께 휩쓸려
     * 사라진다. 세려던 사건이 세려는 행위 때문에 없어지는 셈이다.
     *
     * **일반 기록에는 쓰지 말 것.** 성공 사건은 업무 트랜잭션과 함께 커밋돼야 한다.
     * 여기로 기록하면 롤백된 작업에도 성공 흔적이 남는다.
     *
     * 기록 실패는 삼킨다. 측정 때문에 업무 흐름의 실패 사유가 바뀌면 안 된다 —
     * 호출자는 자기 예외를 그대로 던져야 한다.
     */
    fun logActivityIndependently(
        userId: Long,
        action: String,
        entityType: String? = null,
        entityId: Long? = null,
    ) {
        runCatching {
            independentTx.executeWithoutResult {
                activityLogRepository.save(
                    ActivityLog(
                        userId = userId,
                        action = action,
                        entityType = entityType,
                        entityId = entityId,
                    ),
                )
            }
        }.onFailure { log.warn("활동 로그 기록 실패. action={} userId={}", action, userId, it) }
    }

    private fun ActivityLog.toResponse(): ActivityLogResponse = ActivityLogResponse(
        id = id!!,
        userId = userId,
        action = action,
        entityType = entityType,
        entityId = entityId,
        details = details,
        ipAddress = ipAddress,
        createdAt = createdAt,
    )
}
