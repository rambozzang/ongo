package com.ongo.application.subscription

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus

/**
 * 콜백을 그대로 실행하는 트랜잭션 매니저. **테스트 전용**이며 운영 코드에는 없다.
 *
 * `TransactionTemplate` 은 매니저 없이 만들 수 없다. 여기서 검증하려는 것은 트랜잭션
 * 경계가 아니라 **어떤 항목이 어떤 순서로 처리되는가**이므로, 경계는 실제 DB 통합
 * 테스트(Testcontainers)에 맡기고 여기서는 실행만 통과시킨다.
 *
 * commit/rollback 을 무시하는 것이 이 클래스의 한계다. "한 건 실패가 다른 건을 막지
 * 않는다"는 호출 흐름으로 검증되지만, "실패한 건이 실제로 롤백된다"는 여기서 증명되지
 * 않는다.
 */
class DummyTransactionManagerForTest : PlatformTransactionManager {
    override fun getTransaction(definition: TransactionDefinition?): TransactionStatus =
        SimpleTransactionStatus()

    override fun commit(status: TransactionStatus) = Unit

    override fun rollback(status: TransactionStatus) = Unit
}
