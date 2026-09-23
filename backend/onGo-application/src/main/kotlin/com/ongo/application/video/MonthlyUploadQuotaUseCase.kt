package com.ongo.application.video

import com.ongo.common.enums.PlanType
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.domain.storage.StorageQuotaPort
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.MonthlyUploadPolicy
import com.ongo.domain.video.VideoRepository
import org.springframework.stereotype.Service
import java.time.YearMonth

/**
 * 요금제의 **월 업로드 한도**를 강제하는 유일한 자리.
 *
 * ## 무엇이 깨져 있었나
 *
 * Free 5회·Starter 30회·Pro 100회는 요금표의 첫 줄인데, 검사는 옛 스트리밍 게시 경로
 * (`StreamPublishUseCase`) 한 곳에만 있었다. 지금 화면이 쓰는 presigned·멀티파트 업로드,
 * URL 가져오기, 에셋 전환, 초안 생성, CSV 가져오기는 전부 검사 없이 통과했다 — **무료 사용자도
 * 무제한으로 올릴 수 있었다.** 유료로 바꿀 가장 큰 이유가 서버에서 새고 있었던 셈이다.
 *
 * 게다가 카운트는 쇼츠 클립·재활용 사본까지 모든 영상 행을 셌다. 무엇을 셀지는
 * [MonthlyUploadPolicy] 가 정한다.
 *
 * ## 동시 요청
 *
 * 저장 용량 검사와 **같은 사용자 행 잠금**을 먼저 잡는다. 잠금이 없으면 동시에 들어온 업로드
 * 둘이 같은 횟수를 읽고 둘 다 통과해 한도를 넘긴다. 잠금은 호출한 트랜잭션과 함께 풀리므로,
 * **검사 후 영상 행 저장까지 같은 트랜잭션**에 있어야 한다. 같은 행을 잠그므로 용량 검사와
 * 순서가 엇갈려 교착되는 일도 없다.
 *
 * ## 달의 경계
 *
 * `videos.created_at` 은 서버 현지 시각(`TIMESTAMP`, 시간대 없음)으로 저장된다. 그래서 달의
 * 경계도 **저장된 값과 같은 기준**(JVM 기본 시간대)으로 자른다. 한국 시간으로 자르고 싶어도
 * 서버가 UTC 면 경계가 9시간 어긋나므로, 기준을 바꾸려면 저장 시각과 함께 바꿔야 한다.
 * 운영 서버의 시간대를 KST 로 두면 사용자 체감과 일치한다.
 */
@Service
class MonthlyUploadQuotaUseCase(
    private val userRepository: UserRepository,
    private val videoRepository: VideoRepository,
    private val storageQuotaPort: StorageQuotaPort,
) {

    /** 남은 횟수가 없으면 [PlanLimitExceededException] — 프런트의 업그레이드 안내가 그대로 동작한다. */
    fun check(userId: Long) {
        storageQuotaPort.lockUserForQuota(userId)
        val plan = planOf(userId)
        val used = usedThisMonth(userId)
        if (used >= plan.monthlyUploads) {
            throw PlanLimitExceededException("월간 업로드", plan.monthlyUploads)
        }
    }

    /**
     * 이번 달 사용량과 한도. 화면이 "이번 달 3/5" 를 보여 주려면 필요하다.
     * 잠그지 않는다 — 표시용이다.
     */
    fun usage(userId: Long): MonthlyUploadUsage {
        val plan = planOf(userId)
        return MonthlyUploadUsage(
            used = usedThisMonth(userId),
            limit = plan.monthlyUploads.takeIf { it != Int.MAX_VALUE },
        )
    }

    private fun planOf(userId: Long): PlanType =
        userRepository.findById(userId)?.planType ?: PlanType.FREE

    private fun usedThisMonth(userId: Long): Long =
        videoRepository.countByUserIdAndMonthAndSources(
            userId,
            YearMonth.now(),
            MonthlyUploadPolicy.COUNTED_SOURCES,
        )
}

/** [limit] 가 null 이면 무제한(Business). */
data class MonthlyUploadUsage(val used: Long, val limit: Int?)
