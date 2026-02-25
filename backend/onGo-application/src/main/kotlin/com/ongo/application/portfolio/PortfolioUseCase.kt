package com.ongo.application.portfolio

import com.ongo.application.portfolio.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.portfolio.CreatorPortfolio
import com.ongo.domain.portfolio.PortfolioRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PortfolioUseCase(
    private val portfolioRepository: PortfolioRepository,
) {

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
    fun deletePortfolio(userId: Long) {
        val portfolio = portfolioRepository.findByUserId(userId) ?: throw NotFoundException("포트폴리오", userId)
        portfolioRepository.delete(portfolio.id!!)
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
