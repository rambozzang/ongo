package com.ongo.domain.commerce

interface CommerceRepository {
    fun findPlatformById(id: Long): CommercePlatform?
    fun findPlatformsByUserId(userId: Long): List<CommercePlatform>
    fun savePlatform(platform: CommercePlatform): CommercePlatform
    fun deletePlatform(id: Long)

    fun findProductById(id: Long): CommerceProduct?
    fun findProductsByUserId(userId: Long): List<CommerceProduct>
    fun saveProduct(product: CommerceProduct): CommerceProduct
    fun updateProduct(product: CommerceProduct): CommerceProduct
    fun deleteProduct(id: Long)

    fun findVideoLinksByProductId(productId: Long): List<ProductVideoLink>
    fun findVideoLinksByVideoId(videoId: Long): List<ProductVideoLink>
    fun saveVideoLink(link: ProductVideoLink): ProductVideoLink
    fun deleteVideoLink(id: Long)
}
