package com.ongo.domain.portfolio

interface PortfolioRepository {
    fun findById(id: Long): CreatorPortfolio?
    fun findByUserId(userId: Long): CreatorPortfolio?
    fun findBySlug(slug: String): CreatorPortfolio?
    fun save(portfolio: CreatorPortfolio): CreatorPortfolio
    fun update(portfolio: CreatorPortfolio): CreatorPortfolio
    fun delete(id: Long)
}
