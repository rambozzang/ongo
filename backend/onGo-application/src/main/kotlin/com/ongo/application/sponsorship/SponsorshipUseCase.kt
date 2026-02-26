package com.ongo.application.sponsorship

import com.ongo.application.sponsorship.dto.*
import com.ongo.domain.sponsorship.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class SponsorshipUseCase(
    private val sponsorshipRepository: SponsorshipRepository,
    private val deliverableRepository: SponsorshipDeliverableRepository,
) {

    fun getSponsorships(userId: Long, status: String? = null): List<SponsorshipResponse> {
        val list = if (status != null) sponsorshipRepository.findByUserIdAndStatus(userId, status)
        else sponsorshipRepository.findByUserId(userId)
        return list.map { toResponse(it) }
    }

    fun getSponsorship(userId: Long, id: Long): SponsorshipResponse {
        val sp = sponsorshipRepository.findById(id)
            ?: throw IllegalArgumentException("스폰서십을 찾을 수 없습니다")
        return toResponse(sp)
    }

    @Transactional
    fun createSponsorship(userId: Long, request: CreateSponsorshipRequest): SponsorshipResponse {
        val saved = sponsorshipRepository.save(
            Sponsorship(
                userId = userId, brandName = request.brandName, contactName = request.contactName,
                contactEmail = request.contactEmail, dealValue = request.dealValue,
                currency = request.currency ?: "KRW",
                startDate = LocalDate.parse(request.startDate), endDate = LocalDate.parse(request.endDate),
                notes = request.notes,
            )
        )
        return toResponse(saved)
    }

    @Transactional
    fun updateSponsorship(userId: Long, id: Long, request: UpdateSponsorshipRequest): SponsorshipResponse {
        val sp = sponsorshipRepository.findById(id)
            ?: throw IllegalArgumentException("스폰서십을 찾을 수 없습니다")
        val updated = sponsorshipRepository.update(
            sp.copy(
                status = request.status ?: sp.status,
                dealValue = request.dealValue ?: sp.dealValue,
                paymentStatus = request.paymentStatus ?: sp.paymentStatus,
                paidAmount = request.paidAmount ?: sp.paidAmount,
                notes = request.notes ?: sp.notes,
            )
        )
        return toResponse(updated)
    }

    @Transactional
    fun deleteSponsorship(userId: Long, id: Long) {
        sponsorshipRepository.deleteById(id)
    }

    fun getSummary(userId: Long): SponsorshipSummaryResponse {
        val all = sponsorshipRepository.findByUserId(userId)
        val active = all.count { it.status in listOf("INQUIRY", "NEGOTIATION", "CONTRACTED", "IN_PROGRESS") }
        val totalRevenue = all.sumOf { it.paidAmount }
        val pending = all.filter { it.paymentStatus in listOf("PENDING", "INVOICED") }.sumOf { it.dealValue - it.paidAmount }
        val avgDeal = if (all.isNotEmpty()) all.map { it.dealValue }.average().toLong() else 0L
        val delivered = all.count { it.status in listOf("DELIVERED", "PAID") }
        val rate = if (all.isNotEmpty()) (delivered.toDouble() / all.size) * 100 else 0.0
        return SponsorshipSummaryResponse(all.size, active, totalRevenue, pending, rate, avgDeal)
    }

    @Transactional
    fun createDeliverable(userId: Long, request: CreateDeliverableRequest): DeliverableResponse {
        val saved = deliverableRepository.save(
            SponsorshipDeliverable(
                sponsorshipId = request.sponsorshipId, type = request.type, title = request.title,
                description = request.description, dueDate = LocalDate.parse(request.dueDate), platform = request.platform,
            )
        )
        return toDeliverableResponse(saved)
    }

    @Transactional
    fun completeDeliverable(userId: Long, id: Long): DeliverableResponse {
        val del = deliverableRepository.findById(id)
            ?: throw IllegalArgumentException("산출물을 찾을 수 없습니다")
        val updated = deliverableRepository.update(del.copy(isCompleted = true, completedAt = LocalDateTime.now()))
        return toDeliverableResponse(updated)
    }

    @Transactional
    fun deleteDeliverable(userId: Long, id: Long) {
        deliverableRepository.deleteById(id)
    }

    private fun toResponse(sp: Sponsorship): SponsorshipResponse {
        val deliverables = deliverableRepository.findBySponsorshipId(sp.id)
        return SponsorshipResponse(
            id = sp.id, brandName = sp.brandName, brandLogo = sp.brandLogo,
            contactName = sp.contactName, contactEmail = sp.contactEmail, status = sp.status,
            dealValue = sp.dealValue, currency = sp.currency,
            startDate = sp.startDate.toString(), endDate = sp.endDate.toString(),
            deliverables = deliverables.map { toDeliverableResponse(it) },
            notes = sp.notes, contractUrl = sp.contractUrl,
            paymentStatus = sp.paymentStatus, paidAmount = sp.paidAmount,
            createdAt = sp.createdAt.toString(), updatedAt = sp.updatedAt.toString(),
        )
    }

    private fun toDeliverableResponse(d: SponsorshipDeliverable) = DeliverableResponse(
        id = d.id, sponsorshipId = d.sponsorshipId, type = d.type, title = d.title,
        description = d.description, dueDate = d.dueDate.toString(), isCompleted = d.isCompleted,
        videoId = d.videoId, platform = d.platform, completedAt = d.completedAt?.toString(),
    )
}
