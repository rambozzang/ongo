package com.ongo.domain.portfoliobuilder

interface PortfolioSectionRepository {
    fun findByPortfolioId(portfolioId: Long): List<PortfolioSection>
    fun save(section: PortfolioSection): PortfolioSection
    fun deleteByPortfolioId(portfolioId: Long)
}
