package com.ongo.application.portfoliobuilder

import com.ongo.application.portfoliobuilder.dto.*
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.portfoliobuilder.Portfolio
import com.ongo.domain.portfoliobuilder.PortfolioRepository
import com.ongo.domain.portfoliobuilder.PortfolioSection
import com.ongo.domain.portfoliobuilder.PortfolioSectionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PortfolioBuilderUseCase(
    private val portfolioRepository: PortfolioRepository,
    private val sectionRepository: PortfolioSectionRepository,
) {

    fun getPortfolios(userId: Long): List<PortfolioResponse> {
        return portfolioRepository.findByUserId(userId).map { p ->
            val sections = sectionRepository.findByPortfolioId(p.id!!)
            p.toResponse(sections)
        }
    }

    @Transactional
    fun create(userId: Long, request: CreatePortfolioRequest): PortfolioResponse {
        val portfolio = Portfolio(
            userId = userId,
            title = request.title,
            template = request.template,
        )
        val saved = portfolioRepository.save(portfolio)
        return saved.toResponse(emptyList())
    }

    @Transactional
    fun publish(userId: Long, id: Long): PortfolioResponse {
        val portfolio = portfolioRepository.findById(id)
            ?: throw NotFoundException("포트폴리오", id)
        val updated = portfolio.copy(isPublished = true, publicUrl = "https://ongo.io/p/${portfolio.id}")
        val result = portfolioRepository.update(updated)
        val sections = sectionRepository.findByPortfolioId(id)
        return result.toResponse(sections)
    }

    fun getSummary(userId: Long): PortfolioBuilderSummaryResponse {
        val portfolios = portfolioRepository.findByUserId(userId)
        val published = portfolios.count { it.isPublished }
        val totalViews = portfolios.sumOf { it.viewCount }
        val avgSections = if (portfolios.isNotEmpty()) {
            portfolios.map { sectionRepository.findByPortfolioId(it.id!!).size }.average().toInt()
        } else 0
        val topTemplate = portfolios.groupBy { it.template }.maxByOrNull { it.value.size }?.key ?: ""
        return PortfolioBuilderSummaryResponse(
            totalPortfolios = portfolios.size,
            publishedCount = published,
            totalViews = totalViews,
            avgSections = avgSections,
            topTemplate = topTemplate,
        )
    }

    private fun Portfolio.toResponse(sections: List<PortfolioSection>) = PortfolioResponse(
        id = id!!, title = title, description = description, template = template,
        theme = theme, isPublished = isPublished, publicUrl = publicUrl,
        viewCount = viewCount, sections = sections.map { it.toSectionResponse() },
        createdAt = createdAt, updatedAt = updatedAt,
    )

    private fun PortfolioSection.toSectionResponse() = PortfolioSectionResponse(
        id = id!!, sectionType = sectionType, title = title,
        content = content, order = sortOrder, isVisible = isVisible,
    )
}
