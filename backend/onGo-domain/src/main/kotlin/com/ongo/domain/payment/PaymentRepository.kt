package com.ongo.domain.payment

interface PaymentRepository {
    fun findById(id: Long): Payment?
    fun findByUserId(userId: Long, page: Int, size: Int): List<Payment>
    fun countByUserId(userId: Long): Long
    fun save(payment: Payment): Payment
    fun update(payment: Payment): Payment
}
