package com.ongo.infrastructure.external.pinterest

import com.ongo.infrastructure.external.pinterest.dto.*
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.DeleteExchange
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange
interface PinterestApi {

    @PostExchange("/v5/media")
    fun registerMedia(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: PinterestMediaRequest,
    ): PinterestMediaResponse

    @GetExchange("/v5/media/{mediaId}")
    fun getMediaStatus(
        @org.springframework.web.bind.annotation.PathVariable("mediaId") mediaId: String,
        @RequestHeader("Authorization") authorization: String,
    ): PinterestMediaResponse

    @PostExchange("/v5/pins")
    fun createPin(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: PinterestPinRequest,
    ): PinterestPinResponse

    @GetExchange("/v5/pins/{pinId}")
    fun getPin(
        @org.springframework.web.bind.annotation.PathVariable("pinId") pinId: String,
        @RequestHeader("Authorization") authorization: String,
    ): PinterestPinResponse

    @DeleteExchange("/v5/pins/{pinId}")
    fun deletePin(
        @org.springframework.web.bind.annotation.PathVariable("pinId") pinId: String,
        @RequestHeader("Authorization") authorization: String,
    )

    @GetExchange("/v5/pins/{pinId}/analytics")
    fun getPinAnalytics(
        @org.springframework.web.bind.annotation.PathVariable("pinId") pinId: String,
        @RequestParam("start_date") startDate: String,
        @RequestParam("end_date") endDate: String,
        @RequestParam("metric_types") metricTypes: String,
        @RequestHeader("Authorization") authorization: String,
    ): PinterestPinAnalyticsResponse

    @GetExchange("/v5/user_account")
    fun getUserAccount(
        @RequestHeader("Authorization") authorization: String,
    ): PinterestUserResponse
}

@HttpExchange
interface PinterestOAuthApi {

    @PostExchange("/v5/oauth/token")
    fun exchangeToken(
        @RequestBody body: Map<String, String>,
    ): PinterestTokenResponse
}
