package com.ongo.application.credit

/**
 * 한 번의 차감이 **어디에서 얼마를 가져갔는지** 기록한 영수증.
 *
 * ## 왜 필요한가
 *
 * [CreditService.validateAndDeduct] 는 무료 크레딧을 먼저 쓰고 모자라면 구매 패키지를
 * 만료 임박순으로 차감한다. 그런데 예전 `refundCredit` 은 그 출처를 모른 채 언제나
 * `freeRemaining += amount` 만 했다. 결과는 **고객 자산의 손실**이다.
 *
 * - `free=0, purchased=100` 에서 5 를 쓰고 실패하면, 만료되지 않는 구매분 5 가
 *   **월말에 사라지는 무료분 5 로 바뀐다.** 고객이 돈 주고 산 것이 유통기한 있는 것이 된다.
 * - `free=2, purchased=100` 에서 5 를 쓰고 실패하면, 무료분은 `freeMonthly` 로 clamp 되어
 *   2 만 돌아오고 **구매분 3 은 어디에도 복구되지 않는다.** 그냥 없어진다.
 *
 * 그래서 차감이 스스로 출처를 들고 다닌다. 환불은 이 영수증을 되짚어 정확히 같은 자리로
 * 되돌린다. 잔액 총합만 맞추는 방식으로는 위 두 손실을 막을 수 없다.
 *
 * ## 부분 환불
 *
 * 번역은 한 번에 합산 차감하고 **언어별로** 실패·환불한다. 쇼츠 파이프라인은 단계별이다.
 * 그래서 영수증에서 필요한 만큼만 떼어낼 수 있어야 한다([takeForRefund]).
 *
 * 떼어내는 순서는 차감과 같다(무료 → 구매 FIFO). 어느 언어가 어느 패키지에서 나왔는지는
 * 애초에 정의되지 않으므로 임의로 정할 수밖에 없다. 중요한 것은 **전부 환불하면 전부
 * 제자리로 돌아온다**는 보존 성질이고, 그것은 이 순서로 보장된다.
 *
 * ## 중복 환불 방어
 *
 * 남은 환불 가능액을 스스로 줄인다. 같은 영수증으로 두 번 환불하면 두 번째는 남은 금액이
 * 없어 거절된다. 번역은 언어마다 별도 스레드에서 환불하므로 이 계산은 **동기화**된다.
 *
 * 다만 이것은 **프로세스 안에서만** 유효하다. 재시작 후 되살아난 작업은 이 객체를 잃는다.
 * 그 경우의 정확한 환불은 할당을 저장해야 가능하고 스키마 변경이 필요하다.
 */
class CreditAllocation internal constructor(
    val userId: Long,
    val featureName: String,
    freeAmount: Int,
    purchasedPortions: List<PurchasedPortion>,
) {
    /** 구매 패키지 한 건에서 가져간 몫. [purchasedCreditId] 는 `ai_purchased_credits.id`. */
    data class PurchasedPortion(val purchasedCreditId: Long, val amount: Int)

    /** 무료 크레딧에서 가져간 몫. 차감 시점 값이며 환불로 줄어들지 않는다. */
    val freeAmount: Int = freeAmount

    /** 구매 패키지에서 가져간 몫. 차감 순서(만료 임박순)를 유지한다. */
    val purchasedPortions: List<PurchasedPortion> = purchasedPortions.toList()

    /** 이 차감의 총액. */
    val total: Int = freeAmount + purchasedPortions.sumOf { it.amount }

    private val lock = Any()
    private var freeRefundable = freeAmount
    private var purchasedRefundable = purchasedPortions.map { it.purchasedCreditId to it.amount }.toMutableList()

    /** 아직 환불하지 않고 남은 금액. */
    val refundableRemaining: Int
        get() = synchronized(lock) { freeRefundable + purchasedRefundable.sumOf { it.second } }

    /**
     * [amount] 만큼을 환불용으로 떼어낸다. 떼어낸 만큼 [refundableRemaining] 이 줄어든다.
     *
     * @return 떼어낸 몫. 남은 금액이 0 이거나 [amount] 가 0 이하면 `null` —
     *   **중복 환불은 예외가 아니라 no-op** 이다. 환불 경로는 대개 이미 실패를 처리하는
     *   중이라, 여기서 던지면 원래 오류를 가린다.
     *
     * 남은 금액보다 많이 요청하면 **있는 만큼만** 준다. 차감하지 않은 크레딧을 만들어내는
     * 것이 훨씬 위험하다.
     */
    fun takeForRefund(amount: Int): Refund? = synchronized(lock) {
        if (amount <= 0) return null
        var want = minOf(amount, freeRefundable + purchasedRefundable.sumOf { it.second })
        if (want <= 0) return null

        val takenFree = minOf(freeRefundable, want)
        freeRefundable -= takenFree
        want -= takenFree

        val takenPurchased = mutableListOf<PurchasedPortion>()
        val iterator = purchasedRefundable.listIterator()
        while (iterator.hasNext() && want > 0) {
            val (id, available) = iterator.next()
            val take = minOf(available, want)
            if (take > 0) {
                takenPurchased += PurchasedPortion(id, take)
                iterator.set(id to (available - take))
                want -= take
            }
        }

        Refund(userId, featureName, takenFree, takenPurchased)
    }

    /** 남은 전부를 떼어낸다. 이미 다 환불했으면 `null`. */
    fun takeAllForRefund(): Refund? = takeForRefund(refundableRemaining)

    /**
     * 떼어냈던 몫을 **도로 넣는다.** DB 반영이 실패했을 때 쓴다.
     *
     * [takeForRefund] 는 떼어내는 순간 남은 금액을 줄인다. 그 뒤 트랜잭션이 롤백되면
     * 크레딧은 돌아가지 않았는데 영수증만 소진되어 **재시도가 영구히 막힌다** — 고객은
     * 결과도 크레딧도 잃는다. 그래서 실패 경로가 반드시 이것을 불러 원상복구한다.
     *
     * 되돌리는 순서는 떼어낸 순서의 역이 아니라 **원래 자리**다. 무료분은 무료분으로,
     * 패키지 몫은 같은 패키지 항목으로 돌아간다. 그래야 다시 떼어냈을 때 같은 분해가 나온다.
     */
    fun restore(refund: Refund) = synchronized(lock) {
        freeRefundable += refund.freeAmount
        for (portion in refund.purchasedPortions) {
            val index = purchasedRefundable.indexOfFirst { it.first == portion.purchasedCreditId }
            if (index >= 0) {
                purchasedRefundable[index] = portion.purchasedCreditId to
                    (purchasedRefundable[index].second + portion.amount)
            } else {
                purchasedRefundable += portion.purchasedCreditId to portion.amount
            }
        }
    }

    /**
     * 실제로 되돌릴 몫. [CreditAllocation] 과 달리 불변이며, 이미 떼어낸 결과다.
     */
    data class Refund(
        val userId: Long,
        val featureName: String,
        val freeAmount: Int,
        val purchasedPortions: List<PurchasedPortion>,
    ) {
        val total: Int get() = freeAmount + purchasedPortions.sumOf { it.amount }
    }

    companion object {
        /**
         * 차감이 일어나지 않은 경우의 빈 영수증(금액 0 등). 환불하면 아무 일도 하지 않는다.
         */
        fun empty(userId: Long, featureName: String) =
            CreditAllocation(userId, featureName, 0, emptyList())

        /**
         * 저장해 둔 분해로 영수증을 복원한다.
         *
         * 프로세스를 넘어서는 환불(파이프라인 취소·재시작 뒤 복구 정산)은 차감 당시의
         * 객체를 가질 수 없다. 그 경로는 분해를 DB 에 남겨 두고 여기서 되살린다.
         *
         * 복원된 영수증은 **매번 전액이 남은 상태**다. 중복 환불 방어는 이 객체가 아니라
         * 호출부의 DB 조건부 갱신이 해야 한다 — 인메모리 카운터는 재시작을 견디지 못한다.
         */
        fun restored(
            userId: Long,
            featureName: String,
            freeAmount: Int,
            purchasedAmounts: Map<Long, Int>,
        ) = CreditAllocation(
            userId,
            featureName,
            freeAmount,
            purchasedAmounts.map { (id, amount) -> PurchasedPortion(id, amount) },
        )
    }
}
