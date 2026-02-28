package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.creatormarketplace.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
class MarketplaceListingJooqRepository(
    private val dsl: DSLContext,
) : MarketplaceListingRepository {

    companion object {
        private val TABLE = DSL.table("marketplace_listings")
        private val CREATOR_NAME = DSL.field("creator_name", String::class.java)
        private val SERVICE_TYPE = DSL.field("service_type", String::class.java)
        private val TITLE = DSL.field("title", String::class.java)
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val PRICE = DSL.field("price", Long::class.java)
        private val CURRENCY = DSL.field("currency", String::class.java)
        private val RATING = DSL.field("rating", BigDecimal::class.java)
        private val REVIEW_COUNT = DSL.field("review_count", Int::class.java)
        private val DELIVERY_DAYS = DSL.field("delivery_days", Int::class.java)
        private val IS_ACTIVE = DSL.field("is_active", Boolean::class.java)
    }

    override fun findById(id: Long): MarketplaceListing? =
        dsl.select().from(TABLE)
            .where(ID.eq(id))
            .fetchOne()?.toListing()

    override fun findByUserId(userId: Long): List<MarketplaceListing> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .fetch { it.toListing() }

    override fun findByServiceType(serviceType: String): List<MarketplaceListing> =
        dsl.select().from(TABLE)
            .where(SERVICE_TYPE.eq(serviceType))
            .and(IS_ACTIVE.eq(true))
            .fetch { it.toListing() }

    override fun save(listing: MarketplaceListing): MarketplaceListing {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, listing.userId)
            .set(CREATOR_NAME, listing.creatorName)
            .set(SERVICE_TYPE, listing.serviceType)
            .set(TITLE, listing.title)
            .set(DESCRIPTION, listing.description)
            .set(PRICE, listing.price)
            .set(CURRENCY, listing.currency)
            .set(RATING, listing.rating)
            .set(REVIEW_COUNT, listing.reviewCount)
            .set(DELIVERY_DAYS, listing.deliveryDays)
            .set(IS_ACTIVE, listing.isActive)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toListing(): MarketplaceListing =
        MarketplaceListing(
            id = get(ID),
            userId = get(USER_ID),
            creatorName = get(CREATOR_NAME),
            serviceType = get(SERVICE_TYPE),
            title = get(TITLE),
            description = get(DESCRIPTION),
            price = get(PRICE) ?: 0,
            currency = get(CURRENCY) ?: "KRW",
            rating = get(RATING) ?: BigDecimal.ZERO,
            reviewCount = get(REVIEW_COUNT) ?: 0,
            deliveryDays = get(DELIVERY_DAYS) ?: 1,
            isActive = get(IS_ACTIVE) ?: true,
            createdAt = localDateTime(CREATED_AT),
        )
}

@Repository
class MarketplaceOrderJooqRepository(
    private val dsl: DSLContext,
) : MarketplaceOrderRepository {

    companion object {
        private val TABLE = DSL.table("marketplace_orders")
        private val LISTING_ID = DSL.field("listing_id", Long::class.java)
        private val BUYER_ID = DSL.field("buyer_id", Long::class.java)
        private val SELLER_ID = DSL.field("seller_id", Long::class.java)
        private val BUYER_NAME = DSL.field("buyer_name", String::class.java)
        private val SELLER_NAME = DSL.field("seller_name", String::class.java)
        private val TOTAL_PRICE = DSL.field("total_price", Long::class.java)
        private val ORDER_DATE = DSL.field("order_date", LocalDateTime::class.java)
        private val DELIVERY_DATE = DSL.field("delivery_date", LocalDateTime::class.java)
    }

    override fun findByBuyerId(buyerId: Long): List<MarketplaceOrder> =
        dsl.select().from(TABLE)
            .where(BUYER_ID.eq(buyerId))
            .fetch { it.toOrder() }

    override fun findBySellerId(sellerId: Long): List<MarketplaceOrder> =
        dsl.select().from(TABLE)
            .where(SELLER_ID.eq(sellerId))
            .fetch { it.toOrder() }

    override fun save(order: MarketplaceOrder): MarketplaceOrder {
        val id = dsl.insertInto(TABLE)
            .set(LISTING_ID, order.listingId)
            .set(BUYER_ID, order.buyerId)
            .set(SELLER_ID, order.sellerId)
            .set(BUYER_NAME, order.buyerName)
            .set(SELLER_NAME, order.sellerName)
            .set(STATUS, order.status)
            .set(TOTAL_PRICE, order.totalPrice)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()!!.toOrder()
    }

    override fun updateStatus(id: Long, status: String) {
        dsl.update(TABLE)
            .set(STATUS, status)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toOrder(): MarketplaceOrder =
        MarketplaceOrder(
            id = get(ID),
            listingId = get(LISTING_ID),
            buyerId = get(BUYER_ID),
            buyerName = get(BUYER_NAME),
            sellerId = get(SELLER_ID),
            sellerName = get(SELLER_NAME),
            status = get(STATUS) ?: "PENDING",
            totalPrice = get(TOTAL_PRICE) ?: 0,
            orderDate = localDateTime(ORDER_DATE),
            deliveryDate = localDateTime(DELIVERY_DATE),
        )
}
