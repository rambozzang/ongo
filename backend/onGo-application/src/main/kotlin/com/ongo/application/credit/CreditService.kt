package com.ongo.application.credit

import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.CreditPackage
import com.ongo.common.enums.CreditTransactionType
import com.ongo.common.enums.PlanType
import com.ongo.common.exception.CreditNotFoundException
import com.ongo.common.exception.InsufficientCreditException
import com.ongo.domain.credit.AiCredit
import com.ongo.domain.credit.AiCreditTransaction
import com.ongo.domain.credit.AiPurchasedCredit
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.event.CreditDeductedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class CreditService(
    private val creditRepository: CreditRepository,
    private val eventPublisher: ApplicationEventPublisher,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 차감과 환불 각각의 커밋 경계.
     *
     * 둘 다 `REQUIRES_NEW` 다. 호출자의 트랜잭션에 참여하면 그 트랜잭션이 끝날 때까지
     * 커밋되지 않아, "차감을 먼저 확정한다" 는 [withCredits] 의 전제가 무너진다.
     */
    private val deductTx = requiresNew(transactionManager)
    private val refundTx = requiresNew(transactionManager)

    private fun requiresNew(manager: PlatformTransactionManager) =
        TransactionTemplate(manager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    /**
     * 크레딧을 **먼저 확정 차감한 뒤** 외부 작업을 실행하고, 실패하면 환불한다.
     *
     * ## 왜 이 경계가 필요한가
     *
     * 예전에는 유료 AI 유스케이스가 통째로 `@Transactional` 이었고 그 안에서 LLM 을 불렀다.
     * [validateAndDeduct] 는 `ai_credits` 행을 `FOR UPDATE` 로 잠그므로, 모델이 30초 걸리면
     * **그 30초 동안 행 잠금과 DB 커넥션이 함께 묶였다.** 동시 AI 사용자가 커넥션 풀 크기를
     * 넘으면 결제·구독·게시 API 까지 같이 멈춘다. 모델 지연이 곧 서비스 전체 장애가 되는
     * 구조였고, 이는 `BillingScheduler` 에서 이미 제거한 것과 같은 안티패턴이다.
     *
     * ## 환불이 이제 진짜로 필요하다
     *
     * 예전 구조에서는 예외가 나면 바깥 트랜잭션이 롤백되어 차감 자체가 사라졌다. 그래서
     * catch 안의 `refundCredit` 도 함께 롤백되는 **사실상 죽은 코드**였다. 차감을 먼저
     * 커밋하는 지금은 반대다 — 실패 시 환불하지 않으면 고객이 받지 못한 결과에 크레딧을
     * 낸다. 그 보장을 호출자마다 다시 구현하지 않도록 여기 한 곳에 둔다.
     *
     * ## block 은 트랜잭션 밖에서 돈다
     *
     * 단, 호출자가 `@Transactional` 이면 그 경계가 그대로 살아 있다. 이 함수를 쓰는
     * 유스케이스는 AI 메서드에서 `@Transactional` 을 **제거해야** 효과가 있다.
     *
     * @throws InsufficientCreditException 잔액이 부족하면 [block] 을 실행하지 않는다.
     */
    fun <T> withCredits(userId: Long, amount: Int, featureName: String, block: () -> T): T {
        /*
         * 차감이 돌려준 **영수증**을 들고 있는다. 실패하면 이 영수증으로 되돌린다.
         * 금액만 들고 환불하면 구매분이 무료분으로 바뀐다 — [CreditAllocation] 참고.
         */
        val allocation = deductTx.execute { validateAndDeduct(userId, amount, featureName) }
            ?: CreditAllocation.empty(userId, featureName)
        return try {
            block()
        } catch (e: Throwable) {
            /*
             * 환불 실패가 원래 오류를 가리면 안 된다. 사용자가 보는 것은 "AI 호출 실패"
             * 여야 하고, 환불이 안 된 사실은 로그로 남겨 운영이 복구한다.
             */
            runCatching { refundTx.execute { refundAllocation(allocation) } }
                .onFailure { failure ->
                    log.error(
                        "크레딧 환불에 실패했습니다. 수동 확인이 필요합니다. userId={} amount={} feature={}",
                        userId, amount, featureName, failure,
                    )
                }
            throw e
        }
    }

    fun <T> withCredits(userId: Long, feature: AiFeature, block: () -> T): T =
        withCredits(userId, feature.creditCost, feature.name, block)

    @Transactional
    fun validateAndDeduct(userId: Long, feature: AiFeature): CreditAllocation =
        validateAndDeduct(userId, feature.creditCost, feature.name)

    /**
     * @return 무료분과 **구매 패키지별 몫**을 담은 영수증([CreditAllocation]).
     *   환불은 이 영수증으로 해야 출처가 보존된다 — [refundAllocation] 참고.
     *   반환값을 무시해도 컴파일된다(기존 호출부 호환). 다만 그 경로는 정확한 환불을 잃는다.
     */
    @Transactional
    fun validateAndDeduct(userId: Long, amount: Int, featureName: String): CreditAllocation {
        // 1. 비관적 락으로 크레딧 조회 (FOR UPDATE)
        val credit = creditRepository.findByUserIdForUpdate(userId)
            ?: throw CreditNotFoundException(userId)

        // 총 가용 크레딧 확인
        val purchasedCredits = creditRepository.findActivePurchasedCreditsForUpdate(userId)
        val totalAvailable = credit.freeRemaining + purchasedCredits.sumOf { it.remaining }

        if (totalAvailable < amount) {
            throw InsufficientCreditException(amount, totalAvailable)
        }

        var remaining = amount

        // 2. 무료 크레딧 우선 차감
        var freeDeducted = 0
        if (credit.freeRemaining > 0 && remaining > 0) {
            freeDeducted = minOf(credit.freeRemaining, remaining)
            remaining -= freeDeducted
        }

        // 3. 구매 크레딧 차감 (만료 임박순 FIFO - expiresAt ASC 정렬)
        //
        // **어느 패키지에서 얼마를 뺐는지 기록한다.** 이 목록이 없으면 환불이 출처를
        // 되짚지 못하고, 구매분이 무료분으로 바뀌거나 그냥 사라진다.
        val purchasedPortions = mutableListOf<CreditAllocation.PurchasedPortion>()
        if (remaining > 0) {
            for (pkg in purchasedCredits) {
                if (remaining <= 0) break
                val deduct = minOf(pkg.remaining, remaining)
                if (deduct <= 0) continue
                creditRepository.updatePurchasedCredit(
                    pkg.copy(
                        remaining = pkg.remaining - deduct,
                        status = if (pkg.remaining - deduct == 0) "EXHAUSTED" else pkg.status,
                    )
                )
                // id 가 없는 행은 되돌릴 수 없다. 조회 결과라 정상적으로는 항상 있다.
                pkg.id?.let { purchasedPortions += CreditAllocation.PurchasedPortion(it, deduct) }
                    ?: log.error(
                        "구매 크레딧 행에 id 가 없어 환불 출처를 기록하지 못했다. userId={} amount={}",
                        userId, deduct,
                    )
                remaining -= deduct
            }
        }

        // 4. 잔액 계산 및 갱신
        val newFreeRemaining = credit.freeRemaining - freeDeducted
        val newPurchasedTotal = creditRepository.findActivePurchasedCredits(userId).sumOf { it.remaining }
        val newBalance = newFreeRemaining + newPurchasedTotal

        creditRepository.update(
            credit.copy(
                freeRemaining = newFreeRemaining,
                balance = newBalance,
                updatedAt = LocalDateTime.now(),
            )
        )

        // 5. 트랜잭션 기록
        creditRepository.saveTransaction(
            AiCreditTransaction(
                userId = userId,
                type = CreditTransactionType.DEDUCT,
                amount = -amount,
                balanceAfter = newBalance,
                feature = featureName,
            )
        )

        // 6. 잔여 크레딧 이벤트 발행 (알림용)
        eventPublisher.publishEvent(
            CreditDeductedEvent(
                userId = userId,
                amount = amount,
                feature = AiFeature.entries.find { it.name == featureName } ?: AiFeature.META_GENERATION,
                remainingBalance = newBalance,
            )
        )

        return CreditAllocation(userId, featureName, freeDeducted, purchasedPortions)
    }

    @Transactional(readOnly = true)
    fun getBalance(userId: Long): CreditBalanceInfo {
        val credit = creditRepository.findByUserId(userId)
            ?: return CreditBalanceInfo(0, 0, 0, 0, LocalDate.now().withDayOfMonth(1).plusMonths(1))

        val purchasedTotal = creditRepository.findActivePurchasedCredits(userId).sumOf { it.remaining }
        return CreditBalanceInfo(
            totalBalance = credit.freeRemaining + purchasedTotal,
            freeRemaining = credit.freeRemaining,
            freeMonthly = credit.freeMonthly,
            purchasedBalance = purchasedTotal,
            freeResetDate = credit.freeResetDate,
        )
    }

    /**
     * 크레딧 원장이 존재하는지만 확인한다. **읽기만 하며 아무것도 만들지 않는다.**
     *
     * ## 왜 결제창을 열기 전에 확인하는가
     *
     * 결제 완료 경로(`applyPlanEntitlement`, `addPurchasedCredits`)는 원장이 없으면
     * [CreditNotFoundException] 을 던지고, 그 예외는 완료 트랜잭션 전체를 롤백시킨다 —
     * `payments` 의 COMPLETED 기록까지 함께. 그런데 그 시점에는 **PG 승인이 이미 끝나
     * 있다.** 결과는 "카드는 빠져나갔는데 우리 쪽에는 아무 기록도 없는" 상태이고,
     * 재시도는 매번 같은 지점에서 실패해 웹훅이 DEAD_LETTER 로 떨어진다.
     *
     * 그래서 **돈이 움직이기 전에** 판정한다. 여기서 막으면 고객은 결제창을 보지 못하고
     * 잃는 것도 없다.
     *
     * ## 왜 만들어 주지 않는가
     *
     * 없는 원장을 자동 생성하면 그 계정의 **과거 상태를 추측**하게 된다. 이번 달 무료분을
     * 이미 썼는지 알 수 없고, 시드를 후하게 잡으면 없던 무료 크레딧이 생기며 박하게 잡으면
     * 쓴 적 없는 몫을 빼앗는다. 어느 쪽이든 원장에 근거 없는 숫자가 들어간다.
     *
     * 원장이 없다는 것은 **데이터 사고**이지 초기화가 필요한 정상 상태가 아니다. 조용히
     * 메워 사고를 감추지 않고 실패로 남겨 운영자가 원인을 보게 한다.
     *
     * ## 이 가드가 닫지 못하는 것
     *
     * 체크아웃과 결제 완료 사이에 원장이 사라지면(계정 삭제 경로가 `ai_credits` 를 지운다)
     * 여전히 완료 시점에서 터진다. 또 자동 갱신 결제와 인바운드 웹훅에는 체크아웃 단계가
     * 없어 이 가드를 지나지 않는다. 그 경로들은 별도 조치가 필요하다.
     *
     * @throws CreditNotFoundException 원장이 없을 때.
     */
    @Transactional(readOnly = true)
    fun ensureAccountPresence(userId: Long) {
        creditRepository.findByUserId(userId) ?: throw CreditNotFoundException(userId)
    }

    @Transactional
    fun initializeCredits(userId: Long, planType: PlanType) {
        val nextMonth = LocalDate.now().withDayOfMonth(1).plusMonths(1)
        val credit = creditRepository.save(
            AiCredit(
                userId = userId,
                balance = planType.freeCredits,
                freeMonthly = planType.freeCredits,
                freeRemaining = planType.freeCredits,
                freeResetDate = nextMonth,
            )
        )

        creditRepository.saveTransaction(
            AiCreditTransaction(
                userId = userId,
                type = CreditTransactionType.FREE_RESET,
                amount = planType.freeCredits,
                balanceAfter = credit.balance,
                feature = "PLAN_INIT",
            )
        )
    }

    /**
     * 플랜 전환에 맞춰 무료 크레딧 권한을 적용한다.
     *
     * ## 왜 필요한가
     *
     * 지금까지 `startTrial`·PortOne 결제 완료·트라이얼 만료 어디에서도 `ai_credits` 를
     * 건드리지 않았다. 그래서 STARTER 를 결제해도 무료 크레딧은 FREE 기준(30)에 머물렀고,
     * 쇼츠 실행 한 번(37)조차 완주할 수 없었다. 구독은 바뀌었는데 쓸 수 있는 것은 그대로인
     * 상태가 결제 직후에 남는다.
     *
     * ## 잠금 순서
     *
     * `ai_credits` 를 먼저 `FOR UPDATE` 로 잡고 그 다음 활성 구매분을 잡는다. 이 순서는
     * [revokeCredits] 와 같다 — 두 경로가 반대 순서로 잡으면 교착이 생긴다.
     *
     * ## 규칙
     *
     * - **유료 플랜 부여**(체험 시작·결제 활성화·상위 전환): `freeMonthly` 와 `freeRemaining`
     *   을 모두 대상 플랜 값으로 **설정**한다. 결제한 달에 쓸 수 있는 양이 결제 이전 사용량에
     *   따라 달라지면 "얼마를 산 것인지"를 설명할 수 없다.
     * - **FREE 로 하향**(체험 만료·연체·취소·예약 하향): `freeMonthly` 는 FREE 값,
     *   `freeRemaining` 은 **기존 값과의 min**. 상향과 대칭이 아닌 이유는, 하향에서 값을
     *   올려주면 유료 권한이 무료 사용자에게 새어나가기 때문이다.
     *
     * 구매 크레딧은 어느 방향에서도 건드리지 않는다 — 플랜과 무관한 별도 원장이다.
     * `freeResetDate` 도 보존한다. 전환이 리셋 주기를 바꾸면 주기를 당기려고 플랜을
     * 오가는 경로가 생긴다.
     *
     * @param reason 감사 기록에 남길 전환 사유. 값이 아니라 **왜 바뀌었는지**가 나중에 필요하다.
     */
    @Transactional
    fun applyPlanEntitlement(userId: Long, targetPlan: PlanType, reason: String) {
        val credit = creditRepository.findByUserIdForUpdate(userId)
            ?: throw CreditNotFoundException(userId)
        val purchasedTotal = creditRepository.findActivePurchasedCreditsForUpdate(userId)
            .sumOf { it.remaining }

        val newFreeMonthly = targetPlan.freeCredits
        val newFreeRemaining = if (targetPlan == PlanType.FREE) {
            // 하향은 올려주지 않는다.
            minOf(credit.freeRemaining, newFreeMonthly)
        } else {
            newFreeMonthly
        }
        // balance 의 정의 그대로 다시 센다. 증감분을 더하는 방식은 clamp 와 어긋난다.
        val newBalance = newFreeRemaining + purchasedTotal
        val delta = newFreeRemaining - credit.freeRemaining

        creditRepository.update(
            credit.copy(
                freeMonthly = newFreeMonthly,
                freeRemaining = newFreeRemaining,
                balance = newBalance,
                updatedAt = LocalDateTime.now(),
            )
        )

        /*
         * delta 가 0 이어도 기록한다. 전환이 일어났다는 사실 자체가 감사 대상이고,
         * 나중에 "결제했는데 왜 크레딧이 그대로냐"를 확인할 근거가 이 행뿐이다.
         *
         * 기존 PG enum 을 재사용한다(FREE_RESET). 새 enum 값은 마이그레이션과 함께 넣지
         * 않으면 삽입 시점에 깨진다 — REVOKE 가 정확히 그렇게 누락돼 있었다.
         */
        creditRepository.saveTransaction(
            AiCreditTransaction(
                userId = userId,
                type = CreditTransactionType.FREE_RESET,
                amount = delta,
                balanceAfter = newBalance,
                feature = "PLAN_ENTITLEMENT:$reason:${targetPlan.name}",
            )
        )
    }

    /**
     * 차감 영수증대로 **출처를 되짚어** 환불한다. 남은 전부를 되돌린다.
     *
     * 같은 영수증으로 두 번 불러도 두 번째는 남은 금액이 없어 아무 일도 하지 않는다.
     */
    @Transactional
    fun refundAllocation(allocation: CreditAllocation) {
        applyRefund(allocation, allocation.takeAllForRefund())
    }

    /**
     * 영수증에서 [amount] 만큼만 환불한다. 번역(언어별)·파이프라인(단계별) 부분 환불용.
     *
     * 남은 금액보다 많이 요청하면 있는 만큼만 돌려준다. 차감하지 않은 크레딧을
     * 만들어내는 쪽이 훨씬 위험하다.
     */
    @Transactional
    fun refundAllocation(allocation: CreditAllocation, amount: Int) {
        applyRefund(allocation, allocation.takeForRefund(amount))
    }

    /**
     * 떼어낸 몫을 실제로 되돌린다.
     *
     * ## 실패하면 영수증도 되돌린다
     *
     * [CreditAllocation.takeForRefund] 는 떼어내는 순간 남은 금액을 줄인다. 그 뒤 이
     * 트랜잭션이 롤백되면 **크레딧은 안 돌아갔는데 영수증만 소진되어 재시도가 막힌다.**
     * 그래서 롤백 시 [CreditAllocation.restore] 로 원상복구한다.
     *
     * 트랜잭션이 활성이면 `afterCompletion` 으로 붙인다. 여기서 예외 없이 끝나도 커밋
     * 자체가 실패할 수 있는데, try/catch 만으로는 그 경우를 잡지 못한다. 트랜잭션이
     * 없으면(단위 테스트 등) 예외 경로에서 직접 되돌린다.
     */
    private fun applyRefund(allocation: CreditAllocation, refund: CreditAllocation.Refund?) {
        if (refund == null || refund.total <= 0) return

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCompletion(status: Int) {
                        if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                            allocation.restore(refund)
                            log.warn(
                                "환불 트랜잭션이 롤백되어 영수증을 복원했다. 재시도 가능하다. userId={} amount={} feature={}",
                                refund.userId, refund.total, refund.featureName,
                            )
                        }
                    }
                },
            )
        }

        try {
            writeRefund(refund)
        } catch (e: Throwable) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) allocation.restore(refund)
            throw e
        }
    }

    private fun writeRefund(refund: CreditAllocation.Refund) {

        val userId = refund.userId
        val credit = creditRepository.findByUserIdForUpdate(userId)
            ?: throw CreditNotFoundException(userId)

        // ── 구매분: 가져온 그 패키지로 되돌린다 ──────────────────────────────
        //
        // 소진된 패키지는 EXHAUSTED 라 findActivePurchasedCredits* 에 잡히지 않는다.
        // 되돌릴 대상이 바로 그 행이므로 id 로 잠근다.
        val byId = creditRepository
            .findPurchasedCreditsByIdsForUpdate(userId, refund.purchasedPortions.map { it.purchasedCreditId })
            .associateBy { it.id }
        val now = LocalDateTime.now()
        for (portion in refund.purchasedPortions) {
            val pkg = byId[portion.purchasedCreditId]
            if (pkg == null) {
                // 행이 사라졌다면 되돌릴 곳이 없다. 무료분으로 옮기지 않는다 —
                // 그것이 애초에 고치려는 결함이다. 운영이 확인할 수 있게 남긴다.
                log.error(
                    "환불 대상 구매 크레딧 행을 찾지 못했다. 수기 복구 필요. userId={} purchasedCreditId={} amount={}",
                    userId, portion.purchasedCreditId, portion.amount,
                )
                continue
            }
            val restored = pkg.remaining + portion.amount
            val expired = pkg.expiresAt.isBefore(now)
            if (expired) {
                // 만료된 패키지를 ACTIVE 로 되살리면 유효기간을 늘려주는 셈이다.
                // remaining 은 원장 정합성을 위해 되돌리되 상태는 그대로 둔다.
                log.warn(
                    "만료된 구매 크레딧으로 환불했다. 고객이 쓸 수 없다. userId={} purchasedCreditId={} amount={}",
                    userId, portion.purchasedCreditId, portion.amount,
                )
            }
            creditRepository.updatePurchasedCredit(
                pkg.copy(
                    remaining = restored,
                    status = if (!expired && pkg.status == "EXHAUSTED" && restored > 0) "ACTIVE" else pkg.status,
                ),
            )
        }

        // ── 무료분 ─────────────────────────────────────────────────────────
        //
        // freeMonthly 로 clamp 하는 것은 유지한다. 차감과 환불 사이에 월 리셋이나 플랜
        // 전환이 있었다면 이미 채워져 있고, 그 위에 더하면 없던 무료 크레딧이 생긴다.
        val newFreeRemaining = minOf(credit.freeRemaining + refund.freeAmount, credit.freeMonthly)
        val clampedAway = (credit.freeRemaining + refund.freeAmount) - newFreeRemaining
        if (clampedAway > 0) {
            log.warn(
                "무료 크레딧 환불이 월 한도에 걸려 일부가 반영되지 않았다. userId={} 미반영={} feature={}",
                userId, clampedAway, refund.featureName,
            )
        }

        val purchasedTotal = creditRepository.findActivePurchasedCredits(userId).sumOf { it.remaining }
        val newBalance = newFreeRemaining + purchasedTotal

        creditRepository.update(
            credit.copy(
                freeRemaining = newFreeRemaining,
                balance = newBalance,
                updatedAt = now,
            ),
        )

        creditRepository.saveTransaction(
            AiCreditTransaction(
                userId = userId,
                type = CreditTransactionType.REFUND,
                amount = refund.total,
                balanceAfter = newBalance,
                feature = refund.featureName,
            ),
        )
    }

    /**
     * **더 이상 동작하지 않는다. 호출하면 항상 예외다.**
     *
     * ## 왜 남겨 두고 막는가
     *
     * 이 메서드는 차감이 어디에서 나갔는지 모른 채 전액을 무료분에 얹었다. 그래서
     * 구매분에서 나간 크레딧이 **월말에 사라지는 무료분으로 바뀌고**, `freeMonthly`
     * clamp 에 걸린 몫은 그대로 증발했다. 고객이 돈 주고 산 자산이 줄어드는 것이다.
     *
     * 운영 호출자는 전부 [refundAllocation] 으로 옮겼고 지금은 0 개다. 그런데 메서드가
     * 살아 있으면 **다음 사람이 아무 생각 없이 다시 부른다** — 시그니처가 편하고
     * `@Deprecated` 경고는 무시하기 쉽다. 그때 잘못된 환불이 조용히 커밋된다.
     *
     * 지워도 되지만 남긴다. 계약 테스트들이 "이 경로를 타지 않는다"를 이 심볼로 단언하고
     * 있어, 지우면 그 단언이 함께 사라진다. 대신 **부르는 순간 시끄럽게 실패**시킨다.
     *
     * ## 무엇을 쓸 것인가
     *
     * [validateAndDeduct] 가 돌려주는 [CreditAllocation] 으로 [refundAllocation] 을 쓴다.
     * 차감 시점의 객체를 가질 수 없는 경로(재시작 뒤 정산 등)는 분해를 DB 에 저장하고
     * [CreditAllocation.restored] 로 복원한다 — `ai_pipeline_jobs.credit_allocation`,
     * `video_translations.credit_allocation` 이 그 예다.
     *
     * 출처를 끝내 알 수 없으면 **자동 환불하지 않는다.** 추측해서 넣는 것보다 수기 정산
     * 대상으로 남기는 편이 낫다.
     *
     * @throws UnsupportedOperationException 언제나.
     */
    @Deprecated(
        "출처가 보존되지 않아 구매 크레딧을 무료 크레딧으로 바꾼다. refundAllocation 을 쓸 것.",
        ReplaceWith("refundAllocation(allocation)"),
        level = DeprecationLevel.ERROR,
    )
    fun refundCredit(userId: Long, amount: Int, featureName: String): Nothing {
        /*
         * 던지기 전에 남긴다. 호출부가 runCatching 으로 감싸면 예외가 조용히 사라질 수
         * 있는데, 그때도 "누가 이 경로를 불렀는가"는 추적할 수 있어야 한다.
         */
        log.error(
            "출처 불명 환불이 호출됐다. 이 경로는 비활성이며 아무것도 환불하지 않았다. " +
                "userId={} amount={} feature={} — refundAllocation 으로 교체해야 한다",
            userId, amount, featureName,
        )
        throw UnsupportedOperationException(
            "refundCredit 은 차감 출처를 보존하지 않아 비활성화됐습니다. " +
                "validateAndDeduct 가 돌려주는 CreditAllocation 으로 refundAllocation 을 사용하세요.",
        )
    }

    /**
     * 구매 크레딧을 회수한다(환불·관리자 회수).
     *
     * @param credits 회수할 **크레딧 수**. 결제 금액이 아니다.
     *
     * 무료 월 크레딧은 결제와 무관하므로 절대 건드리지 않는다. 예전에는 balance 에서 뺀 뒤
     * freeRemaining 을 그 값으로 clamp 해서, 환불 한 번에 그 달 무료 크레딧까지 전부
     * 사라졌다(₩4,900 환불 -> 4900 을 빼면 balance 가 0 이 되고 freeRemaining 도 0).
     *
     * 회수는 최근 구매분부터 되돌린다. 남은 잔여분보다 많이 요청되면 있는 만큼만 회수한다.
     */
    @Transactional
    fun revokeCredits(userId: Long, credits: Int, reason: String) {
        val credit = creditRepository.findByUserIdForUpdate(userId)
            ?: throw CreditNotFoundException(userId)

        var unrevoked = credits
        creditRepository.findActivePurchasedCreditsForUpdate(userId)
            .sortedByDescending { it.purchasedAt ?: LocalDateTime.MIN }
            .forEach { purchased ->
                if (unrevoked <= 0) return@forEach
                val deduct = minOf(purchased.remaining, unrevoked)
                if (deduct > 0) {
                    creditRepository.updatePurchasedCredit(
                        purchased.copy(remaining = purchased.remaining - deduct)
                    )
                    unrevoked -= deduct
                }
            }

        val revoked = credits - unrevoked
        val newBalance = (credit.balance - revoked).coerceAtLeast(0)

        creditRepository.update(
            credit.copy(
                balance = newBalance,
                updatedAt = LocalDateTime.now(),
            )
        )

        creditRepository.saveTransaction(
            AiCreditTransaction(
                userId = userId,
                type = CreditTransactionType.REVOKE,
                amount = -revoked,
                balanceAfter = newBalance,
                feature = reason,
            )
        )
    }

    /**
     * 결제 완료된 크레딧 패키지를 지급한다.
     *
     * 반드시 `ai_purchased_credits` 에 행을 만들어야 한다. 잔액 조회(CreditQueryUseCase)와
     * 차감(validateAndDeduct) 모두 `ai_credits.balance` 가 아니라 이 테이블의 remaining 합계를
     * 사용하기 때문이다. 예전에는 balance 만 올리고 이 행을 만들지 않아, 결제해도 조회·차감
     * 어느 쪽에도 반영되지 않았다(결제 후 잔액이 그대로 0).
     *
     * 지급량은 결제 금액이 아니라 패키지에 정의된 크레딧 수다. 호출부가 금액을 그대로
     * 넘기던 문제가 있어 파라미터를 패키지 자체로 바꿨다.
     */
    @Transactional
    fun addPurchasedCredits(userId: Long, creditPackage: CreditPackage, referenceId: Long) {
        val credit = creditRepository.findByUserIdForUpdate(userId)
            ?: throw CreditNotFoundException(userId)

        creditRepository.savePurchasedCredit(
            AiPurchasedCredit(
                userId = userId,
                packageName = creditPackage.name,
                totalCredits = creditPackage.credits,
                remaining = creditPackage.credits,
                price = creditPackage.price,
                expiresAt = LocalDateTime.now().plusDays(creditPackage.validDays.toLong()),
            )
        )

        // balance 는 표시용 누적 집계로만 유지한다(차감 판정에는 쓰이지 않는다).
        val newBalance = credit.balance + creditPackage.credits
        creditRepository.update(
            credit.copy(balance = newBalance, updatedAt = LocalDateTime.now())
        )

        creditRepository.saveTransaction(
            AiCreditTransaction(
                userId = userId,
                type = CreditTransactionType.CHARGE,
                amount = creditPackage.credits,
                balanceAfter = newBalance,
                referenceId = referenceId,
            )
        )
    }
}

data class CreditBalanceInfo(
    val totalBalance: Int,
    val freeRemaining: Int,
    val freeMonthly: Int,
    val purchasedBalance: Int,
    val freeResetDate: LocalDate,
)
