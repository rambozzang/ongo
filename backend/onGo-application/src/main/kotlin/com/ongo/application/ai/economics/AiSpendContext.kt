package com.ongo.application.ai.economics

/**
 * 지금 실행 중인 AI 호출이 **쓸 수 있는 원가 예산**. 크레딧을 받은 곳이 연다.
 *
 * 모든 채팅 모델 호출은 이 예산을 확인하는 가로채기(`AiCostGuardAdvisor`)를 지난다.
 * 예산이 열려 있지 않으면 호출을 거부한다 — **크레딧 없이 유료 모델을 부르는 경로가 구조적으로 존재할 수 없다.**
 * 한 예산 안에서 여러 번 부르면 앞선 호출의 실제 원가만큼 줄어든다.
 *
 * 스레드에 묶인다. 다른 스레드(비동기 작업자)에서 부른다면 그 작업자가 자기 예산을 다시 연다.
 */
object AiSpendContext {
    class Budget internal constructor(val label: String, val limitKrw: Double) {
        @Volatile var spentKrw: Double = 0.0
            private set

        val remainingKrw: Double get() = (limitKrw - spentKrw).coerceAtLeast(0.0)

        @Synchronized
        fun record(costKrw: Double) {
            spentKrw += costKrw.coerceAtLeast(0.0)
        }
    }

    private val current = ThreadLocal<Budget?>()

    fun current(): Budget? = current.get()

    /**
     * [limitKrw] 원까지 쓸 수 있는 예산을 열고 [block] 을 실행한다. 이미 열린 예산이 있으면
     * **더 작은 쪽**을 쓴다 — 안쪽 호출이 바깥 크레딧보다 많이 쓸 수 없다.
     */
    fun <T> withBudget(limitKrw: Double, label: String, block: () -> T): T {
        val outer = current.get()
        val budget = Budget(label, if (outer == null) limitKrw else minOf(limitKrw, outer.remainingKrw))
        current.set(budget)
        try {
            return block()
        } finally {
            outer?.record(budget.spentKrw)
            current.set(outer)
        }
    }
}
