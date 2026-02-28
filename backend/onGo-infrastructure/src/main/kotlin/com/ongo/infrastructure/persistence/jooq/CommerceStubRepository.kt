package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.commerce.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
class CommerceJooqRepository(
    private val dsl: DSLContext,
) : CommerceRepository {

    companion object {
        // commerce_platforms
        private val PLATFORMS_TABLE = DSL.table("commerce_platforms")
        private val PLATFORM_TYPE = DSL.field("platform_type", String::class.java)
        private val PLATFORM_NAME = DSL.field("platform_name", String::class.java)
        private val ACCESS_TOKEN_ENC = DSL.field("access_token_enc", String::class.java)
        private val STATUS = DSL.field("status", String::class.java)

        // commerce_products
        private val PRODUCTS_TABLE = DSL.table("commerce_products")
        private val COMMERCE_PLATFORM_ID = DSL.field("commerce_platform_id", Long::class.java)
        private val PRODUCT_NAME = DSL.field("product_name", String::class.java)
        private val PRODUCT_URL = DSL.field("product_url", String::class.java)
        private val IMAGE_URL = DSL.field("image_url", String::class.java)
        private val PRICE = DSL.field("price", BigDecimal::class.java)
        private val AFFILIATE_LINK = DSL.field("affiliate_link", String::class.java)

        // product_video_links
        private val LINKS_TABLE = DSL.table("product_video_links")
        private val PRODUCT_ID = DSL.field("product_id", Long::class.java)
        private val CLICKS = DSL.field("clicks", Int::class.java)
        private val CONVERSIONS = DSL.field("conversions", Int::class.java)
        private val REVENUE = DSL.field("revenue", BigDecimal::class.java)
    }

    // === CommercePlatform ===

    override fun findPlatformById(id: Long): CommercePlatform? =
        dsl.select().from(PLATFORMS_TABLE).where(ID.eq(id)).fetchOne()?.toPlatform()

    override fun findPlatformsByUserId(userId: Long): List<CommercePlatform> =
        dsl.select().from(PLATFORMS_TABLE).where(USER_ID.eq(userId)).fetch { it.toPlatform() }

    override fun savePlatform(platform: CommercePlatform): CommercePlatform {
        val id = dsl.insertInto(PLATFORMS_TABLE)
            .set(USER_ID, platform.userId)
            .set(PLATFORM_TYPE, platform.platformType)
            .set(PLATFORM_NAME, platform.platformName)
            .set(ACCESS_TOKEN_ENC, platform.accessTokenEnc)
            .set(STATUS, platform.status)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findPlatformById(id)!!
    }

    override fun deletePlatform(id: Long) {
        dsl.deleteFrom(PLATFORMS_TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toPlatform(): CommercePlatform = CommercePlatform(
        id = get(ID),
        userId = get(USER_ID),
        platformType = get(PLATFORM_TYPE),
        platformName = get(PLATFORM_NAME),
        accessTokenEnc = get(ACCESS_TOKEN_ENC),
        status = get(STATUS) ?: "CONNECTED",
        createdAt = localDateTime(CREATED_AT),
    )

    // === CommerceProduct ===

    override fun findProductById(id: Long): CommerceProduct? =
        dsl.select().from(PRODUCTS_TABLE).where(ID.eq(id)).fetchOne()?.toProduct()

    override fun findProductsByUserId(userId: Long): List<CommerceProduct> =
        dsl.select().from(PRODUCTS_TABLE).where(USER_ID.eq(userId)).fetch { it.toProduct() }

    override fun saveProduct(product: CommerceProduct): CommerceProduct {
        val id = dsl.insertInto(PRODUCTS_TABLE)
            .set(USER_ID, product.userId)
            .set(COMMERCE_PLATFORM_ID, product.commercePlatformId)
            .set(PRODUCT_NAME, product.productName)
            .set(PRODUCT_URL, product.productUrl)
            .set(IMAGE_URL, product.imageUrl)
            .set(PRICE, product.price)
            .set(AFFILIATE_LINK, product.affiliateLink)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findProductById(id)!!
    }

    override fun updateProduct(product: CommerceProduct): CommerceProduct {
        dsl.update(PRODUCTS_TABLE)
            .set(COMMERCE_PLATFORM_ID, product.commercePlatformId)
            .set(PRODUCT_NAME, product.productName)
            .set(PRODUCT_URL, product.productUrl)
            .set(IMAGE_URL, product.imageUrl)
            .set(PRICE, product.price)
            .set(AFFILIATE_LINK, product.affiliateLink)
            .where(ID.eq(product.id))
            .execute()
        return findProductById(product.id!!)!!
    }

    override fun deleteProduct(id: Long) {
        dsl.deleteFrom(PRODUCTS_TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toProduct(): CommerceProduct = CommerceProduct(
        id = get(ID),
        userId = get(USER_ID),
        commercePlatformId = get("commerce_platform_id")?.let { (it as Number).toLong() },
        productName = get(PRODUCT_NAME),
        productUrl = get(PRODUCT_URL),
        imageUrl = get(IMAGE_URL),
        price = get(PRICE),
        affiliateLink = get(AFFILIATE_LINK),
        createdAt = localDateTime(CREATED_AT),
    )

    // === ProductVideoLink ===

    override fun findVideoLinksByProductId(productId: Long): List<ProductVideoLink> =
        dsl.select().from(LINKS_TABLE).where(PRODUCT_ID.eq(productId)).fetch { it.toLink() }

    override fun findVideoLinksByVideoId(videoId: Long): List<ProductVideoLink> =
        dsl.select().from(LINKS_TABLE).where(VIDEO_ID.eq(videoId)).fetch { it.toLink() }

    override fun saveVideoLink(link: ProductVideoLink): ProductVideoLink {
        val id = dsl.insertInto(LINKS_TABLE)
            .set(PRODUCT_ID, link.productId)
            .set(VIDEO_ID, link.videoId)
            .set(CLICKS, link.clicks)
            .set(CONVERSIONS, link.conversions)
            .set(REVENUE, link.revenue)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return dsl.select().from(LINKS_TABLE).where(ID.eq(id)).fetchOne()!!.toLink()
    }

    override fun deleteVideoLink(id: Long) {
        dsl.deleteFrom(LINKS_TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toLink(): ProductVideoLink = ProductVideoLink(
        id = get(ID),
        productId = get(PRODUCT_ID),
        videoId = get(VIDEO_ID),
        clicks = get(CLICKS) ?: 0,
        conversions = get(CONVERSIONS) ?: 0,
        revenue = get(REVENUE) ?: BigDecimal.ZERO,
        createdAt = localDateTime(CREATED_AT),
    )
}
