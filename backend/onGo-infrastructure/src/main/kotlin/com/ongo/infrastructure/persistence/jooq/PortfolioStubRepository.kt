package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.portfolio.CreatorPortfolio
import com.ongo.domain.portfolio.PortfolioRepository
import org.springframework.stereotype.Repository

@Repository
class PortfolioStubRepository : PortfolioRepository {
    override fun findById(id: Long): CreatorPortfolio? = null
    override fun findByUserId(userId: Long): CreatorPortfolio? = null
    override fun findBySlug(slug: String): CreatorPortfolio? = null
    override fun save(portfolio: CreatorPortfolio): CreatorPortfolio = portfolio.copy(id = 1)
    override fun update(portfolio: CreatorPortfolio): CreatorPortfolio = portfolio
    override fun delete(id: Long) {}
}
