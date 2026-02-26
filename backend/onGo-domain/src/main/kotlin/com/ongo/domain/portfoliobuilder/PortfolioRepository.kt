package com.ongo.domain.portfoliobuilder

interface PortfolioRepository {
    fun findById(id: Long): Portfolio?
    fun findByUserId(userId: Long): List<Portfolio>
    fun save(portfolio: Portfolio): Portfolio
    fun update(portfolio: Portfolio): Portfolio
    fun deleteById(id: Long)
}
