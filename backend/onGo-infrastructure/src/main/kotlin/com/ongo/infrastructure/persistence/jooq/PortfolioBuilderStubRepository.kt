package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.portfoliobuilder.Portfolio
import com.ongo.domain.portfoliobuilder.PortfolioRepository as PortfolioBuilderPortfolioRepository
import com.ongo.domain.portfoliobuilder.PortfolioSection
import com.ongo.domain.portfoliobuilder.PortfolioSectionRepository
import org.springframework.stereotype.Repository

@Repository
class PortfolioBuilderStubRepository : PortfolioBuilderPortfolioRepository {
    override fun findById(id: Long): Portfolio? = null
    override fun findByUserId(userId: Long): List<Portfolio> = emptyList()
    override fun save(portfolio: Portfolio): Portfolio = portfolio.copy(id = 1)
    override fun update(portfolio: Portfolio): Portfolio = portfolio
    override fun deleteById(id: Long) {}
}

@Repository
class PortfolioSectionStubRepository : PortfolioSectionRepository {
    override fun findByPortfolioId(portfolioId: Long): List<PortfolioSection> = emptyList()
    override fun save(section: PortfolioSection): PortfolioSection = section.copy(id = 1)
    override fun deleteByPortfolioId(portfolioId: Long) {}
}
