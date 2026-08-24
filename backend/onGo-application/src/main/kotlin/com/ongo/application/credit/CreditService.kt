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
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class CreditService(
    private val creditRepository: CreditRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun validateAndDeduct(userId: Long, feature: AiFeature) {
        validateAndDeduct(userId, feature.creditCost, feature.name)
    }

    @Transactional
    fun validateAndDeduct(userId: Long, amount: Int, featureName: String) {
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
        if (remaining > 0) {
            for (pkg in purchasedCredits) {
                if (remaining <= 0) break
                val deduct = minOf(pkg.remaining, remaining)
                creditRepository.updatePurchasedCredit(
                    pkg.copy(
                        remaining = pkg.remaining - deduct,
                        status = if (pkg.remaining - deduct == 0) "EXHAUSTED" else pkg.status,
                    )
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

    @Transactional
    fun refundCredit(userId: Long, amount: Int, featureName: String) {
        val credit = creditRepository.findByUserIdForUpdate(userId)
            ?: throw CreditNotFoundException(userId)

        val newFreeRemaining = minOf(credit.freeRemaining + amount, credit.freeMonthly)

        /*
         * balance 는 더하지 않고 **다시 센다.**
         *
         * 예전에는 `credit.balance + amount` 였는데, 바로 위에서 freeRemaining 이 freeMonthly 로
         * clamp 되면 실제로는 amount 보다 적게 복구된다. 그런데 balance 에는 amount 전부를
         * 더하니, clamp 된 만큼이 어디에도 없는 잔액(phantom)으로 남아 있었다.
         *
         * balance 의 정의는 "freeRemaining + 활성 구매분"이다. 그 정의대로 계산하면
         * 어긋날 여지가 없다.
         */
        val purchasedTotal = creditRepository.findActivePurchasedCredits(userId).sumOf { it.remaining }
        val newBalance = newFreeRemaining + purchasedTotal

        creditRepository.update(
            credit.copy(
                freeRemaining = newFreeRemaining,
                balance = newBalance,
                updatedAt = LocalDateTime.now(),
            )
        )

        creditRepository.saveTransaction(
            AiCreditTransaction(
                userId = userId,
                type = CreditTransactionType.REFUND,
                amount = amount,
                balanceAfter = newBalance,
                feature = featureName,
            )
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
