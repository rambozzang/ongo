package com.ongo.domain.payment

interface PaymentRepository {
    fun findById(id: Long): Payment?

    /**
     * 결제 행을 `FOR UPDATE`로 잠근 채 조회한다.
     *
     * 웹훅은 중복 수신·동시 수신이 가능하다. 잠금 없이 읽으면 두 트랜잭션이 같은 결제를 동시에
     * `PENDING`으로 보고 크레딧을 두 번 지급할 수 있다. 상태 확인부터 지급·상태 변경까지를
     * 하나의 트랜잭션 안에서 직렬화하려면 이 메서드를 써야 한다.
     */
    fun findByIdForUpdate(id: Long): Payment?
    fun findByUserId(userId: Long, page: Int, size: Int): List<Payment>
    fun countByUserId(userId: Long): Long
    fun save(payment: Payment): Payment
    fun update(payment: Payment): Payment
    fun findByPaddleTransactionId(paddleTransactionId: String): Payment?
}
