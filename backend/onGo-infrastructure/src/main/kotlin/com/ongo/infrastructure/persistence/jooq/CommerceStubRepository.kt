package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.commerce.*
import org.springframework.stereotype.Repository

@Repository
class CommerceStubRepository : CommerceRepository {
    override fun findPlatformById(id: Long): CommercePlatform? = null
    override fun findPlatformsByUserId(userId: Long): List<CommercePlatform> = emptyList()
    override fun savePlatform(platform: CommercePlatform): CommercePlatform = platform.copy(id = 1)
    override fun deletePlatform(id: Long) {}

    override fun findProductById(id: Long): CommerceProduct? = null
    override fun findProductsByUserId(userId: Long): List<CommerceProduct> = emptyList()
    override fun saveProduct(product: CommerceProduct): CommerceProduct = product.copy(id = 1)
    override fun updateProduct(product: CommerceProduct): CommerceProduct = product
    override fun deleteProduct(id: Long) {}

    override fun findVideoLinksByProductId(productId: Long): List<ProductVideoLink> = emptyList()
    override fun findVideoLinksByVideoId(videoId: Long): List<ProductVideoLink> = emptyList()
    override fun saveVideoLink(link: ProductVideoLink): ProductVideoLink = link.copy(id = 1)
    override fun deleteVideoLink(id: Long) {}
}
