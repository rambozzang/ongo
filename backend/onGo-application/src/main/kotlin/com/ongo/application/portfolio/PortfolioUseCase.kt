package com.ongo.application.portfolio

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.portfolio.dto.*
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.portfolio.CreatorPortfolio
import com.ongo.domain.portfolio.PortfolioRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PortfolioUseCase(
    private val portfolioRepository: PortfolioRepository,
) {

    private val objectMapper = jacksonObjectMapper()

    fun getPortfolio(userId: Long): PortfolioResponse? {
        return portfolioRepository.findByUserId(userId)?.toResponse()
    }

    fun getPublicPortfolio(slug: String): PortfolioResponse {
        val portfolio = portfolioRepository.findBySlug(slug) ?: throw NotFoundException("포트폴리오", slug)
        if (!portfolio.isPublic) throw NotFoundException("포트폴리오", slug)
        return portfolio.toResponse()
    }

    @Transactional
    fun createOrUpdatePortfolio(userId: Long, request: CreatePortfolioRequest): PortfolioResponse {
        val existing = portfolioRepository.findByUserId(userId)
        if (existing != null) {
            val updated = existing.copy(
                displayName = request.displayName ?: existing.displayName,
                bio = request.bio ?: existing.bio,
                category = request.category ?: existing.category,
                profileImageUrl = request.profileImageUrl ?: existing.profileImageUrl,
                theme = request.theme,
                publicSlug = request.publicSlug ?: existing.publicSlug,
                showcaseVideos = request.showcaseVideos,
                brandHistory = request.brandHistory,
                isPublic = request.isPublic,
            )
            return portfolioRepository.update(updated).toResponse()
        }
        val portfolio = CreatorPortfolio(
            userId = userId,
            displayName = request.displayName,
            bio = request.bio,
            category = request.category,
            profileImageUrl = request.profileImageUrl,
            theme = request.theme,
            publicSlug = request.publicSlug,
            showcaseVideos = request.showcaseVideos,
            brandHistory = request.brandHistory,
            isPublic = request.isPublic,
        )
        return portfolioRepository.save(portfolio).toResponse()
    }

    @Transactional
    fun updatePortfolio(userId: Long, request: UpdatePortfolioRequest): PortfolioResponse {
        val portfolio = portfolioRepository.findByUserId(userId) ?: throw NotFoundException("포트폴리오", userId)
        val updated = portfolio.copy(
            displayName = request.displayName ?: portfolio.displayName,
            bio = request.bio ?: portfolio.bio,
            category = request.category ?: portfolio.category,
            profileImageUrl = request.profileImageUrl ?: portfolio.profileImageUrl,
            theme = request.theme ?: portfolio.theme,
            publicSlug = request.publicSlug ?: portfolio.publicSlug,
            showcaseVideos = request.showcaseVideos ?: portfolio.showcaseVideos,
            brandHistory = request.brandHistory ?: portfolio.brandHistory,
            isPublic = request.isPublic ?: portfolio.isPublic,
        )
        return portfolioRepository.update(updated).toResponse()
    }

    @Transactional
    fun updateProfile(userId: Long, request: UpdateProfileRequest): PortfolioResponse {
        val portfolio = portfolioRepository.findByUserId(userId) ?: throw NotFoundException("포트폴리오", userId)
        val updated = portfolio.copy(
            displayName = request.displayName ?: portfolio.displayName,
            bio = request.bio ?: portfolio.bio,
            category = request.category ?: portfolio.category,
            profileImageUrl = request.profileImageUrl ?: portfolio.profileImageUrl,
        )
        return portfolioRepository.update(updated).toResponse()
    }

    @Transactional
    fun updateShowcaseOrder(userId: Long, contentIds: List<Long>): PortfolioResponse {
        val portfolio = portfolioRepository.findByUserId(userId) ?: throw NotFoundException("포트폴리오", userId)
        val currentVideos = parseJsonArray(portfolio.showcaseVideos)
        val reordered = contentIds.mapIndexedNotNull { index, id ->
            val existing = currentVideos.find { (it["contentId"] as? Number)?.toLong() == id }
            existing?.also { it["order"] = index }
        }
        val updated = portfolio.copy(showcaseVideos = objectMapper.writeValueAsString(reordered))
        return portfolioRepository.update(updated).toResponse()
    }

    @Transactional
    fun addShowcase(userId: Long, request: AddShowcaseRequest): ShowcaseItemResponse {
        val portfolio = portfolioRepository.findByUserId(userId) ?: throw NotFoundException("포트폴리오", userId)
        val currentVideos = parseJsonArray(portfolio.showcaseVideos)
        val newOrder = currentVideos.size
        val newItem = mutableMapOf<String, Any>(
            "contentId" to request.contentId,
            "title" to (request.title ?: ""),
            "thumbnailUrl" to (request.thumbnailUrl ?: ""),
            "order" to newOrder,
        )
        currentVideos.add(newItem)
        val updated = portfolio.copy(showcaseVideos = objectMapper.writeValueAsString(currentVideos))
        portfolioRepository.update(updated)
        return ShowcaseItemResponse(id = request.contentId, order = newOrder)
    }

    @Transactional
    fun removeShowcase(userId: Long, contentId: Long): PortfolioResponse {
        val portfolio = portfolioRepository.findByUserId(userId) ?: throw NotFoundException("포트폴리오", userId)
        val currentVideos = parseJsonArray(portfolio.showcaseVideos)
        val filtered = currentVideos.filter { (it["contentId"] as? Number)?.toLong() != contentId }
        filtered.forEachIndexed { index, item -> item["order"] = index }
        val updated = portfolio.copy(showcaseVideos = objectMapper.writeValueAsString(filtered))
        return portfolioRepository.update(updated).toResponse()
    }

    @Transactional
    fun addCollaboration(userId: Long, request: AddCollaborationRequest): CollaborationResponse {
        val portfolio = portfolioRepository.findByUserId(userId) ?: throw NotFoundException("포트폴리오", userId)
        val currentHistory = parseJsonArray(portfolio.brandHistory)
        val maxId = currentHistory.maxOfOrNull { (it["id"] as? Number)?.toLong() ?: 0L } ?: 0L
        val newId = maxId + 1
        val newItem = mutableMapOf<String, Any>(
            "id" to newId,
            "brandName" to request.brandName,
            "description" to (request.description ?: ""),
            "date" to (request.date ?: ""),
            "logoUrl" to (request.logoUrl ?: ""),
        )
        currentHistory.add(newItem)
        val updated = portfolio.copy(brandHistory = objectMapper.writeValueAsString(currentHistory))
        portfolioRepository.update(updated)
        return CollaborationResponse(id = newId)
    }

    @Transactional
    fun removeCollaboration(userId: Long, collabId: Long): PortfolioResponse {
        val portfolio = portfolioRepository.findByUserId(userId) ?: throw NotFoundException("포트폴리오", userId)
        val currentHistory = parseJsonArray(portfolio.brandHistory)
        val filtered = currentHistory.filter { (it["id"] as? Number)?.toLong() != collabId }
        val updated = portfolio.copy(brandHistory = objectMapper.writeValueAsString(filtered))
        return portfolioRepository.update(updated).toResponse()
    }

    @Transactional
    fun updateSettings(userId: Long, request: UpdateSettingsRequest): PortfolioResponse {
        val portfolio = portfolioRepository.findByUserId(userId) ?: throw NotFoundException("포트폴리오", userId)
        val updated = portfolio.copy(
            theme = request.theme ?: portfolio.theme,
            publicSlug = request.publicSlug ?: portfolio.publicSlug,
            isPublic = request.isPublic ?: portfolio.isPublic,
        )
        return portfolioRepository.update(updated).toResponse()
    }

    fun exportPdf(userId: Long): ByteArray {
        val portfolio = portfolioRepository.findByUserId(userId) ?: throw NotFoundException("포트폴리오", userId)
        return generateSimplePdf(portfolio)
    }

    @Transactional
    fun deletePortfolio(userId: Long) {
        val portfolio = portfolioRepository.findByUserId(userId) ?: throw NotFoundException("포트폴리오", userId)
        portfolioRepository.delete(portfolio.id!!)
    }

    private fun parseJsonArray(jsonStr: String): MutableList<MutableMap<String, Any>> {
        return try {
            objectMapper.readValue(jsonStr, object : TypeReference<MutableList<MutableMap<String, Any>>>() {})
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun generateSimplePdf(portfolio: CreatorPortfolio): ByteArray {
        val sb = StringBuilder()
        sb.appendLine("=== ${portfolio.displayName ?: "Creator"} Portfolio ===")
        sb.appendLine()
        if (!portfolio.bio.isNullOrBlank()) {
            sb.appendLine("Bio: ${portfolio.bio}")
            sb.appendLine()
        }
        if (!portfolio.category.isNullOrBlank()) {
            sb.appendLine("Category: ${portfolio.category}")
            sb.appendLine()
        }
        sb.appendLine("Theme: ${portfolio.theme}")
        sb.appendLine("Public: ${if (portfolio.isPublic) "Yes" else "No"}")
        if (!portfolio.publicSlug.isNullOrBlank()) {
            sb.appendLine("Slug: ${portfolio.publicSlug}")
        }
        sb.appendLine()
        sb.appendLine("--- Showcase Videos ---")
        val videos = parseJsonArray(portfolio.showcaseVideos)
        if (videos.isEmpty()) {
            sb.appendLine("(none)")
        } else {
            videos.forEach { v ->
                sb.appendLine("- ${v["title"] ?: "Untitled"} (ID: ${v["contentId"]})")
            }
        }
        sb.appendLine()
        sb.appendLine("--- Brand History ---")
        val collabs = parseJsonArray(portfolio.brandHistory)
        if (collabs.isEmpty()) {
            sb.appendLine("(none)")
        } else {
            collabs.forEach { c ->
                sb.appendLine("- ${c["brandName"] ?: ""} : ${c["description"] ?: ""}")
            }
        }

        return buildMinimalPdf(sb.toString())
    }

    private fun buildMinimalPdf(text: String): ByteArray {
        val lines = text.lines()
        val streamContent = StringBuilder()
        streamContent.append("BT\n/F1 12 Tf\n")
        var y = 750
        for (line in lines) {
            if (y < 50) break
            val escaped = line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
            streamContent.append("1 0 0 1 50 $y Tm\n($escaped) Tj\n")
            y -= 16
        }
        streamContent.append("ET\n")
        val streamBytes = streamContent.toString().toByteArray(Charsets.ISO_8859_1)

        val output = java.io.ByteArrayOutputStream()
        val w = { s: String -> output.write(s.toByteArray(Charsets.ISO_8859_1)) }

        val offsets = mutableListOf<Int>()

        w("%PDF-1.4\n")

        offsets.add(output.size())
        w("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        offsets.add(output.size())
        w("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")

        offsets.add(output.size())
        w("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n")

        offsets.add(output.size())
        w("4 0 obj\n<< /Length ${streamBytes.size} >>\nstream\n")
        output.write(streamBytes)
        w("\nendstream\nendobj\n")

        offsets.add(output.size())
        w("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n")

        val xrefOffset = output.size()
        w("xref\n0 6\n")
        w("0000000000 65535 f \n")
        for (offset in offsets) {
            w(String.format("%010d 00000 n \n", offset))
        }
        w("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")

        return output.toByteArray()
    }

    private fun CreatorPortfolio.toResponse() = PortfolioResponse(
        id = id!!,
        userId = userId,
        displayName = displayName,
        bio = bio,
        category = category,
        profileImageUrl = profileImageUrl,
        theme = theme,
        publicSlug = publicSlug,
        showcaseVideos = showcaseVideos,
        brandHistory = brandHistory,
        isPublic = isPublic,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
