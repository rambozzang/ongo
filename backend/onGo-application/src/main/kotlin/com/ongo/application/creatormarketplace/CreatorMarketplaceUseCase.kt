package com.ongo.application.creatormarketplace

import com.ongo.application.creatormarketplace.dto.*
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.creatormarketplace.MarketplaceListing
import com.ongo.domain.creatormarketplace.MarketplaceListingRepository
import com.ongo.domain.creatormarketplace.MarketplaceOrder
import com.ongo.domain.creatormarketplace.MarketplaceOrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreatorMarketplaceUseCase(
    private val listingRepository: MarketplaceListingRepository,
    private val orderRepository: MarketplaceOrderRepository,
) {

    fun getListings(serviceType: String?): List<MarketplaceListingResponse> {
        val listings = if (serviceType != null) {
            listingRepository.findByServiceType(serviceType)
        } else {
            listingRepository.findByUserId(0) // all listings
        }
        return listings.map { it.toResponse() }
    }

    @Transactional
    fun createListing(userId: Long, request: CreateListingRequest): MarketplaceListingResponse {
        val listing = MarketplaceListing(
            userId = userId,
            creatorName = request.creatorName,
            serviceType = request.serviceType,
            title = request.title,
            description = request.description,
            price = request.price,
            deliveryDays = request.deliveryDays,
        )
        return listingRepository.save(listing).toResponse()
    }

    fun getOrders(userId: Long): List<MarketplaceOrderResponse> {
        val buyerOrders = orderRepository.findByBuyerId(userId)
        val sellerOrders = orderRepository.findBySellerId(userId)
        return (buyerOrders + sellerOrders).distinctBy { it.id }.map { it.toResponse() }
    }

    @Transactional
    fun placeOrder(userId: Long, listingId: Long, buyerName: String): MarketplaceOrderResponse {
        val listing = listingRepository.findById(listingId)
            ?: throw NotFoundException("마켓플레이스 리스팅", listingId)
        val order = MarketplaceOrder(
            listingId = listingId,
            buyerId = userId,
            buyerName = buyerName,
            sellerId = listing.userId,
            sellerName = listing.creatorName,
            totalPrice = listing.price,
        )
        return orderRepository.save(order).toResponse()
    }

    fun getSummary(userId: Long): MarketplaceSummaryResponse {
        val listings = listingRepository.findByUserId(userId)
        val orders = orderRepository.findBySellerId(userId)
        val activeOrders = orders.count { it.status in listOf("PENDING", "ACCEPTED", "IN_PROGRESS") }
        return MarketplaceSummaryResponse(
            totalListings = listings.size,
            activeOrders = activeOrders,
            totalRevenue = orders.filter { it.status == "COMPLETED" }.sumOf { it.totalPrice },
            avgRating = if (listings.isNotEmpty()) listings.map { it.rating.toDouble() }.average() else 0.0,
            topServiceType = listings.groupBy { it.serviceType }.maxByOrNull { it.value.size }?.key ?: "",
        )
    }

    private fun MarketplaceListing.toResponse() = MarketplaceListingResponse(
        id = id!!,
        creatorName = creatorName,
        serviceType = serviceType,
        title = title,
        description = description,
        price = price,
        currency = currency,
        rating = rating,
        reviewCount = reviewCount,
        deliveryDays = deliveryDays,
        isActive = isActive,
        createdAt = createdAt,
    )

    private fun MarketplaceOrder.toResponse() = MarketplaceOrderResponse(
        id = id!!,
        listingId = listingId,
        buyerName = buyerName,
        sellerName = sellerName,
        status = status,
        totalPrice = totalPrice,
        orderDate = orderDate,
        deliveryDate = deliveryDate,
    )
}
