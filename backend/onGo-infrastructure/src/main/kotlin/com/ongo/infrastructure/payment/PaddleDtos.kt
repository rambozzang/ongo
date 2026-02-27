package com.ongo.infrastructure.payment

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaddleSubscription(
    val id: String = "",
    val status: String = "",
    @JsonProperty("customer_id") val customerId: String = "",
    @JsonProperty("current_billing_period") val currentBillingPeriod: PaddleBillingPeriod? = null,
    @JsonProperty("next_billed_at") val nextBilledAt: String? = null,
    @JsonProperty("scheduled_change") val scheduledChange: PaddleScheduledChange? = null,
    val items: List<PaddleSubscriptionItem> = emptyList(),
    @JsonProperty("custom_data") val customData: Map<String, Any>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaddleBillingPeriod(
    @JsonProperty("starts_at") val startsAt: String = "",
    @JsonProperty("ends_at") val endsAt: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaddleScheduledChange(
    val action: String = "",
    @JsonProperty("effective_at") val effectiveAt: String = "",
    @JsonProperty("resume_at") val resumeAt: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaddleSubscriptionItem(
    val price: PaddlePrice? = null,
    val quantity: Int = 1,
    val status: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaddlePrice(
    val id: String = "",
    @JsonProperty("product_id") val productId: String = "",
    val name: String? = null,
    @JsonProperty("unit_price") val unitPrice: PaddleMoney? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaddleMoney(
    val amount: String = "0",
    @JsonProperty("currency_code") val currencyCode: String = "KRW",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaddleTransaction(
    val id: String = "",
    val status: String = "",
    @JsonProperty("customer_id") val customerId: String = "",
    @JsonProperty("subscription_id") val subscriptionId: String? = null,
    @JsonProperty("invoice_id") val invoiceId: String? = null,
    @JsonProperty("invoice_number") val invoiceNumber: String? = null,
    val details: PaddleTransactionDetails? = null,
    val payments: List<PaddlePaymentAttempt> = emptyList(),
    @JsonProperty("custom_data") val customData: Map<String, Any>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaddleTransactionDetails(
    val totals: PaddleTransactionTotals? = null,
    @JsonProperty("line_items") val lineItems: List<PaddleLineItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaddleTransactionTotals(
    val total: String = "0",
    val subtotal: String = "0",
    val tax: String = "0",
    @JsonProperty("currency_code") val currencyCode: String = "KRW",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaddleLineItem(
    @JsonProperty("price_id") val priceId: String = "",
    val quantity: Int = 1,
    val totals: PaddleLineItemTotals? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaddleLineItemTotals(
    val total: String = "0",
    val subtotal: String = "0",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaddlePaymentAttempt(
    @JsonProperty("payment_method_id") val paymentMethodId: String? = null,
    val amount: String = "0",
    val status: String = "",
    @JsonProperty("error_code") val errorCode: String? = null,
    @JsonProperty("method_details") val methodDetails: PaddleMethodDetails? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaddleMethodDetails(
    val type: String = "",
    val card: PaddleCardDetails? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaddleCardDetails(
    val type: String = "",
    val last4: String = "",
    @JsonProperty("expiry_month") val expiryMonth: Int = 0,
    @JsonProperty("expiry_year") val expiryYear: Int = 0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaddleInvoiceResponse(
    val data: PaddleInvoiceData? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaddleInvoiceData(
    val url: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaddleWebhookEvent(
    @JsonProperty("event_id") val eventId: String = "",
    @JsonProperty("event_type") val eventType: String = "",
    @JsonProperty("occurred_at") val occurredAt: String = "",
    val data: Map<String, Any> = emptyMap(),
)
