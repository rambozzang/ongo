package com.ongo.application.revenuesplit

import com.ongo.application.revenuesplit.dto.*
import com.ongo.domain.revenuesplit.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class RevenueSplitUseCase(
    private val splitRepository: RevenueSplitRepository,
    private val memberRepository: SplitMemberRepository,
) {

    fun getSplits(workspaceId: Long, status: String? = null): List<RevenueSplitResponse> {
        val list = if (status != null) splitRepository.findByWorkspaceIdAndStatus(workspaceId, status)
        else splitRepository.findByWorkspaceId(workspaceId)
        return list.map { toResponse(it) }
    }

    fun getSplit(workspaceId: Long, id: Long): RevenueSplitResponse {
        val split = splitRepository.findById(id)
            ?: throw IllegalArgumentException("수익 분배를 찾을 수 없습니다")
        return toResponse(split)
    }

    @Transactional
    fun createSplit(workspaceId: Long, request: CreateRevenueSplitRequest): RevenueSplitResponse {
        val saved = splitRepository.save(
            RevenueSplit(
                workspaceId = workspaceId, title = request.title, description = request.description,
                totalAmount = request.totalAmount, currency = request.currency, period = request.period,
            )
        )
        val members = request.members.map {
            SplitMember(
                splitId = saved.id, name = it.name, email = it.email, role = it.role,
                percentage = BigDecimal.valueOf(it.percentage),
                amount = (request.totalAmount * it.percentage / 100).toLong(),
            )
        }
        memberRepository.saveBatch(members)
        return toResponse(saved)
    }

    @Transactional
    fun deleteSplit(workspaceId: Long, id: Long) {
        memberRepository.deleteBySplitId(id)
        splitRepository.deleteById(id)
    }

    @Transactional
    fun approveSplit(workspaceId: Long, id: Long): RevenueSplitResponse {
        val split = splitRepository.findById(id)
            ?: throw IllegalArgumentException("수익 분배를 찾을 수 없습니다")
        val updated = splitRepository.update(split.copy(status = "APPROVED", updatedAt = LocalDateTime.now()))
        return toResponse(updated)
    }

    @Transactional
    fun distributeSplit(workspaceId: Long, id: Long): RevenueSplitResponse {
        val split = splitRepository.findById(id)
            ?: throw IllegalArgumentException("수익 분배를 찾을 수 없습니다")
        val updated = splitRepository.update(split.copy(status = "DISTRIBUTED", updatedAt = LocalDateTime.now()))
        val members = memberRepository.findBySplitId(id)
        members.forEach {
            memberRepository.update(it.copy(paymentStatus = "PAID", paidAt = LocalDateTime.now()))
        }
        return toResponse(updated)
    }

    fun getSummary(workspaceId: Long): RevenueSplitSummaryResponse {
        val all = splitRepository.findByWorkspaceId(workspaceId)
        val pending = all.filter { it.status in listOf("DRAFT", "PENDING", "APPROVED") }.sumOf { it.totalAmount }
        val distributed = all.filter { it.status == "DISTRIBUTED" }.sumOf { it.totalAmount }
        val allMembers = all.flatMap { memberRepository.findBySplitId(it.id) }
        val uniqueMembers = allMembers.map { it.email ?: it.name }.distinct().size
        val avg = if (all.isNotEmpty()) all.map { it.totalAmount }.average().toLong() else 0L
        return RevenueSplitSummaryResponse(all.size, pending, distributed, uniqueMembers, avg)
    }

    private fun toResponse(split: RevenueSplit): RevenueSplitResponse {
        val members = memberRepository.findBySplitId(split.id)
        return RevenueSplitResponse(
            id = split.id, title = split.title, description = split.description,
            totalAmount = split.totalAmount, currency = split.currency, status = split.status,
            period = split.period, members = members.map { toMemberResponse(it) },
            createdAt = split.createdAt.toString(), updatedAt = split.updatedAt.toString(),
        )
    }

    private fun toMemberResponse(m: SplitMember) = SplitMemberResponse(
        id = m.id, name = m.name, email = m.email, role = m.role,
        percentage = m.percentage.toDouble(), amount = m.amount,
        paymentStatus = m.paymentStatus, paidAt = m.paidAt?.toString(),
    )
}
