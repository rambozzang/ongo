package com.ongo.domain.competitor

interface ChannelLookupPort {
    fun lookupChannel(platform: String, query: String): ChannelLookupResult
}

data class ChannelLookupResult(
    val found: Boolean,
    val platformChannelId: String? = null,
    val channelName: String? = null,
    val channelUrl: String? = null,
    /**
     * 구독자 수. **조회 응답에 그 값이 없으면 `null`** — `0` 이 아니다.
     *
     * YouTube 채널은 구독자 수를 숨길 수 있고(`hiddenSubscriberCount`), 그때 응답의
     * `statistics.subscriberCount` 가 통째로 빠진다. 예전에는 `?: 0` 으로 채워
     * **경쟁자 추가 미리보기에 "구독자 0명"** 이 뜨고 그 값이 그대로 저장됐다.
     *
     * 응답이 실제로 `"0"` 을 주면 그것은 관측이므로 `0` 이다.
     */
    val subscriberCount: Long? = null,
    /**
     * 총 조회수. **조회 응답에 그 값이 없으면 `null`** — `0` 이 아니다.
     *
     * 평균 조회수의 **분자**다. 재지 못한 자리를 0 으로 채우면 "조회수 0회" 라는 관측이
     * 되고, `videoCount` 가 있으면 평균까지 `0 / n = 0` 으로 계산돼 버린다.
     */
    val totalViews: Long? = null,
    /**
     * 영상 수. **조회 응답에 그 값이 없으면 `null`** — `0` 이 아니다.
     *
     * `subscriberCount` 와 같은 계약이다. 이 값은 평균 조회수의 **분모**라서, 재지 못한
     * 자리를 0 으로 채우면 "영상 0개" 라는 관측이 되고 평균도 계산되지 않은 채 사라진다.
     */
    val videoCount: Int? = null,
    val profileImageUrl: String? = null,
    val platform: String? = null,
    val requiresManualInput: Boolean = false,
    val message: String? = null,
)
