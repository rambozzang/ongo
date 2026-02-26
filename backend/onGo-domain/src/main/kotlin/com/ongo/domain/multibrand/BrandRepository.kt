package com.ongo.domain.multibrand

interface BrandRepository {
    fun findById(id: Long): Brand?
    fun findByUserId(userId: Long): List<Brand>
    fun save(brand: Brand): Brand
    fun update(brand: Brand): Brand
    fun delete(id: Long)
}
