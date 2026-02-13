package com.ongo.domain.competitor

interface ChannelLookupPort {
    fun lookupChannel(platform: String, query: String): ChannelLookupResult
}

data class ChannelLookupResult(
    val found: Boolean,
    val platformChannelId: String? = null,
    val channelName: String? = null,
    val channelUrl: String? = null,
    val subscriberCount: Long = 0,
    val totalViews: Long = 0,
    val videoCount: Int = 0,
    val profileImageUrl: String? = null,
    val platform: String? = null,
    val requiresManualInput: Boolean = false,
    val message: String? = null,
)
