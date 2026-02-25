package com.ongo.application.commerce

import com.ongo.application.commerce.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.commerce.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommerceUseCase(
    private val commerceRepository: CommerceRepository,
) {

    fun listPlatforms(userId: Long): List<CommercePlatformResponse> {
        return commerceRepository.findPlatformsByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun connectPlatform(userId: Long, request: ConnectPlatformRequest): CommercePlatformResponse {
        val platform = CommercePlatform(
            userId = userId,
            platformType = request.platformType,
            platformName = request.platformName,
            accessTokenEnc = request.accessToken,
        )
        return commerceRepository.savePlatform(platform).toResponse()
    }

    @Transactional
    fun disconnectPlatform(userId: Long, platformId: Long) {
        val platform = commerceRepository.findPlatformById(platformId) ?: throw NotFoundException("커머스 플랫폼", platformId)
        if (platform.userId != userId) throw ForbiddenException("해당 플랫폼에 대한 권한이 없습니다")
        commerceRepository.deletePlatform(platformId)
    }

    fun listProducts(userId: Long): List<CommerceProductResponse> {
        return commerceRepository.findProductsByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun createProduct(userId: Long, request: CreateProductRequest): CommerceProductResponse {
        val product = CommerceProduct(
            userId = userId,
            commercePlatformId = request.commercePlatformId,
            productName = request.productName,
            productUrl = request.productUrl,
            imageUrl = request.imageUrl,
            price = request.price,
            affiliateLink = request.affiliateLink,
        )
        return commerceRepository.saveProduct(product).toResponse()
    }

    @Transactional
    fun updateProduct(userId: Long, productId: Long, request: UpdateProductRequest): CommerceProductResponse {
        val product = commerceRepository.findProductById(productId) ?: throw NotFoundException("상품", productId)
        if (product.userId != userId) throw ForbiddenException("해당 상품에 대한 권한이 없습니다")
        val updated = product.copy(
            productName = request.productName ?: product.productName,
            productUrl = request.productUrl ?: product.productUrl,
            imageUrl = request.imageUrl ?: product.imageUrl,
            price = request.price ?: product.price,
            affiliateLink = request.affiliateLink ?: product.affiliateLink,
        )
        return commerceRepository.updateProduct(updated).toResponse()
    }

    @Transactional
    fun deleteProduct(userId: Long, productId: Long) {
        val product = commerceRepository.findProductById(productId) ?: throw NotFoundException("상품", productId)
        if (product.userId != userId) throw ForbiddenException("해당 상품에 대한 권한이 없습니다")
        commerceRepository.deleteProduct(productId)
    }

    @Transactional
    fun linkProductToVideo(userId: Long, request: LinkProductVideoRequest): ProductVideoLinkResponse {
        val link = ProductVideoLink(
            productId = request.productId,
            videoId = request.videoId,
        )
        return commerceRepository.saveVideoLink(link).toResponse()
    }

    fun getVideoLinks(videoId: Long): List<ProductVideoLinkResponse> {
        return commerceRepository.findVideoLinksByVideoId(videoId).map { it.toResponse() }
    }

    private fun CommercePlatform.toResponse() = CommercePlatformResponse(
        id = id!!,
        platformType = platformType,
        platformName = platformName,
        status = status,
        createdAt = createdAt,
    )

    private fun CommerceProduct.toResponse() = CommerceProductResponse(
        id = id!!,
        commercePlatformId = commercePlatformId,
        productName = productName,
        productUrl = productUrl,
        imageUrl = imageUrl,
        price = price,
        affiliateLink = affiliateLink,
        createdAt = createdAt,
    )

    private fun ProductVideoLink.toResponse() = ProductVideoLinkResponse(
        id = id!!,
        productId = productId,
        videoId = videoId,
        clicks = clicks,
        conversions = conversions,
        revenue = revenue,
        createdAt = createdAt,
    )
}
