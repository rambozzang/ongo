package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Platform
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformConfigurationAdapterTest {

    @Test
    fun `short provider credentials are reported as unavailable`() {
        val adapter = adapter(
            tiktokClientKey = "12",
            tiktokClientSecret = "valid-tiktok-secret",
        )

        val status = adapter.status(Platform.TIKTOK)

        assertFalse(status.configured)
    }

    @Test
    fun `valid provider credentials remain available`() {
        val adapter = adapter(
            tiktokClientKey = "valid-tiktok-key",
            tiktokClientSecret = "valid-tiktok-secret",
        )

        val status = adapter.status(Platform.TIKTOK)

        assertTrue(status.configured)
    }

    @Test
    fun `placeholder provider credentials are reported as unavailable`() {
        val adapter = adapter(
            tiktokClientKey = "your-tiktok-client-key",
            tiktokClientSecret = "valid-tiktok-secret",
        )

        val status = adapter.status(Platform.TIKTOK)

        assertFalse(status.configured)
    }

    private fun adapter(
        googleClientId: String = "",
        googleClientSecret: String = "",
        tiktokClientKey: String = "",
        tiktokClientSecret: String = "",
        instagramAppId: String = "",
        instagramAppSecret: String = "",
        twitterClientId: String = "",
        twitterClientSecret: String = "",
        facebookAppId: String = "",
        facebookAppSecret: String = "",
        threadsAppId: String = "",
        threadsAppSecret: String = "",
        pinterestAppId: String = "",
        pinterestAppSecret: String = "",
        linkedinClientId: String = "",
        linkedinClientSecret: String = "",
        wordpressClientId: String = "",
        wordpressClientSecret: String = "",
        dailymotionApiKey: String = "",
        dailymotionApiSecret: String = "",
        vimeoClientId: String = "",
        vimeoClientSecret: String = "",
        tumblrConsumerKey: String = "",
        tumblrConsumerSecret: String = "",
    ) = PlatformConfigurationAdapter(
        googleClientId,
        googleClientSecret,
        tiktokClientKey,
        tiktokClientSecret,
        instagramAppId,
        instagramAppSecret,
        twitterClientId,
        twitterClientSecret,
        facebookAppId,
        facebookAppSecret,
        threadsAppId,
        threadsAppSecret,
        pinterestAppId,
        pinterestAppSecret,
        linkedinClientId,
        linkedinClientSecret,
        wordpressClientId,
        wordpressClientSecret,
        dailymotionApiKey,
        dailymotionApiSecret,
        vimeoClientId,
        vimeoClientSecret,
        tumblrConsumerKey,
        tumblrConsumerSecret,
    )
}
