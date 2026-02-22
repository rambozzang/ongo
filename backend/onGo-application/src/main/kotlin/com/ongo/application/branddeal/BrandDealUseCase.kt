package com.ongo.application.branddeal

import com.ongo.application.branddeal.dto.*
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.branddeal.BrandDeal
import com.ongo.domain.branddeal.BrandDealRepository
import com.ongo.domain.branddeal.MediaKit
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class BrandDealUseCase(
    private val brandDealRepository: BrandDealRepository,
) {
    private val objectMapper = jacksonObjectMapper()

    fun getDeals(userId: Long, status: String?): List<BrandDealResponse> =
        brandDealRepository.findDealsByUserId(userId, status).map { it.toResponse() }

    fun getDeal(userId: Long, dealId: Long): BrandDealResponse {
        val deal = brandDealRepository.findDealById(dealId) ?: throw NotFoundException("딜", dealId)
        if (deal.userId != userId) throw BusinessException("FORBIDDEN", "해당 딜에 대한 권한이 없습니다")
        return deal.toResponse()
    }

    @Transactional
    fun createDeal(userId: Long, request: CreateBrandDealRequest): BrandDealResponse {
        val deal = brandDealRepository.saveDeal(
            BrandDeal(
                userId = userId,
                brandName = request.brandName,
                contactName = request.contactName,
                contactEmail = request.contactEmail,
                dealValue = request.dealValue,
                status = request.status,
                deadline = request.deadline?.let { LocalDate.parse(it) },
                notes = request.notes,
            )
        )
        return deal.toResponse()
    }

    @Transactional
    fun updateDeal(userId: Long, dealId: Long, request: UpdateBrandDealRequest): BrandDealResponse {
        val deal = brandDealRepository.findDealById(dealId) ?: throw NotFoundException("딜", dealId)
        if (deal.userId != userId) throw BusinessException("FORBIDDEN", "해당 딜에 대한 권한이 없습니다")
        val delivJson = request.deliverables?.let { objectMapper.writeValueAsString(it) }
        brandDealRepository.updateDeal(dealId, request.brandName, request.contactName, request.contactEmail, request.dealValue, request.status, request.deadline?.let { LocalDate.parse(it) }, delivJson, request.notes)
        return brandDealRepository.findDealById(dealId)!!.toResponse()
    }

    @Transactional
    fun deleteDeal(userId: Long, dealId: Long) {
        val deal = brandDealRepository.findDealById(dealId) ?: throw NotFoundException("딜", dealId)
        if (deal.userId != userId) throw BusinessException("FORBIDDEN", "해당 딜에 대한 권한이 없습니다")
        brandDealRepository.deleteDeal(dealId)
    }

    // Media Kit

    fun getMediaKit(userId: Long): MediaKitResponse? =
        brandDealRepository.findMediaKitByUserId(userId)?.toResponse()

    fun getPublicMediaKit(slug: String): MediaKitResponse? =
        brandDealRepository.findMediaKitBySlug(slug)?.takeIf { it.isPublic }?.toResponse()

    @Transactional
    fun saveOrUpdateMediaKit(userId: Long, request: UpdateMediaKitRequest): MediaKitResponse {
        val existing = brandDealRepository.findMediaKitByUserId(userId)
        if (existing != null) {
            brandDealRepository.updateMediaKit(
                existing.id!!,
                request.displayName, request.bio,
                request.categories?.let { objectMapper.writeValueAsString(it) },
                request.socialLinks?.let { objectMapper.writeValueAsString(it) },
                null, // statsSnapshot
                request.rateCard?.let { objectMapper.writeValueAsString(it) },
                request.isPublic, request.slug,
            )
            return brandDealRepository.findMediaKitByUserId(userId)!!.toResponse()
        } else {
            val kit = brandDealRepository.saveMediaKit(
                MediaKit(
                    userId = userId,
                    displayName = request.displayName ?: "My Media Kit",
                    bio = request.bio,
                    categories = request.categories?.let { objectMapper.writeValueAsString(it) },
                    socialLinks = request.socialLinks?.let { objectMapper.writeValueAsString(it) },
                    rateCard = request.rateCard?.let { objectMapper.writeValueAsString(it) },
                    isPublic = request.isPublic ?: false,
                    slug = request.slug,
                )
            )
            return kit.toResponse()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun BrandDeal.toResponse(): BrandDealResponse {
        val delivList = try { objectMapper.readValue(deliverables ?: "[]", List::class.java) as List<String> } catch (_: Exception) { emptyList() }
        return BrandDealResponse(
            id = id!!,
            brandName = brandName,
            contactName = contactName,
            contactEmail = contactEmail,
            dealValue = dealValue,
            currency = currency,
            status = status,
            deadline = deadline?.toString(),
            deliverables = delivList,
            notes = notes,
            createdAt = createdAt?.toString(),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun MediaKit.toResponse(): MediaKitResponse {
        val cats = try { objectMapper.readValue(categories ?: "[]", List::class.java) as List<String> } catch (_: Exception) { emptyList() }
        val links = try { objectMapper.readValue(socialLinks ?: "{}", Map::class.java) as Map<String, String> } catch (_: Exception) { emptyMap() }
        val rates = try { objectMapper.readValue(rateCard ?: "{}", Map::class.java) as Map<String, Any> } catch (_: Exception) { emptyMap() }
        return MediaKitResponse(
            id = id!!,
            displayName = displayName,
            bio = bio,
            categories = cats,
            socialLinks = links,
            rateCard = rates,
            isPublic = isPublic,
            slug = slug,
            createdAt = createdAt?.toString(),
        )
    }
}
