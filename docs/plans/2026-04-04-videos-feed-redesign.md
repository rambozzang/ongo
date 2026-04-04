# /videos 피드 재설계 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** /videos 페이지를 onGo DB 기반 영상 관리 → 각 SNS 플랫폼 API에서 모든 콘텐츠를 가져와 통합 피드로 보여주는 성과 모니터링 페이지로 전환

**Architecture:** 백엔드에 `GET /api/v1/videos/feed` 엔드포인트를 추가하여 연결된 각 채널의 영상 목록을 플랫폼 API에서 병렬 조회 → 통합 응답. Caffeine 캐시(5분)로 API 호출 최소화. 프론트엔드는 수정/삭제 제거, 조회수/좋아요/댓글 중심 테이블 + 영상 클릭 시 슬라이드 패널(댓글 인라인)

**Tech Stack:** Spring Boot + Kotlin, jOOQ, Caffeine Cache, Virtual Threads 병렬 호출, Vue 3 + Tailwind CSS

---

## Task 1: 플랫폼별 영상 목록 조회 API 인터페이스 추가

**Files:**
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/youtube/YouTubeApi.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/tiktok/TikTokApi.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/instagram/InstagramApi.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/naverclip/NaverClipApi.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/twitter/TwitterApi.kt`

**Step 1: YouTube API에 채널 영상 목록 엔드포인트 추가**

YouTube는 채널의 uploads playlist를 통해 영상 목록을 가져옴.

```kotlin
// YouTubeApi.kt에 추가
@GetExchange("/youtube/v3/playlistItems")
fun listPlaylistItems(
    @RequestParam("playlistId") playlistId: String,
    @RequestParam("part") part: String,
    @RequestParam("maxResults") maxResults: Int,
    @RequestParam("pageToken", required = false) pageToken: String?,
    @RequestHeader("Authorization") authorization: String,
): YouTubePlaylistItemListResponse
```

YouTubeDtos.kt에 응답 DTO 추가:

```kotlin
@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTubePlaylistItemListResponse(
    val items: List<PlaylistItem> = emptyList(),
    val nextPageToken: String? = null,
    val pageInfo: PageInfo? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PlaylistItem(
        val snippet: Snippet?,
        val contentDetails: ContentDetails?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Snippet(
        val title: String?,
        val description: String?,
        val thumbnails: Thumbnails?,
        val publishedAt: String?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Thumbnails(
        val default: Thumbnail?,
        val medium: Thumbnail?,
        val high: Thumbnail?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Thumbnail(val url: String?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ContentDetails(val videoId: String?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PageInfo(val totalResults: Int?)
}
```

**Step 2: TikTok API에 영상 목록 엔드포인트 추가**

```kotlin
// TikTokApi.kt에 추가
@PostExchange("/v2/video/list/")
fun listVideos(
    @RequestHeader("Authorization") authorization: String,
    @RequestBody request: TikTokVideoListRequest,
): TikTokVideoListResponse
```

TikTokDtos.kt에 추가:

```kotlin
data class TikTokVideoListRequest(
    val max_results: Int = 20,
    val cursor: Long? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TikTokVideoListResponse(
    val data: Data? = null,
    val error: TikTokError? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Data(
        val videos: List<VideoItem> = emptyList(),
        val cursor: Long? = null,
        val has_more: Boolean = false,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class VideoItem(
        val id: String,
        val title: String? = null,
        val cover_image_url: String? = null,
        val create_time: Long? = null,
        val like_count: Long? = null,
        val comment_count: Long? = null,
        val share_count: Long? = null,
        val view_count: Long? = null,
    )
}
```

**Step 3: Instagram API에 미디어 목록 엔드포인트 추가**

InstagramApi.kt에 추가:

```kotlin
@GetExchange("/{userId}/media")
fun listMedia(
    @PathVariable userId: String,
    @RequestParam("fields") fields: String,
    @RequestParam("limit") limit: Int,
    @RequestParam("after", required = false) after: String?,
    @RequestParam("access_token") accessToken: String,
): InstagramMediaListResponse
```

InstagramDtos.kt에 추가:

```kotlin
@JsonIgnoreProperties(ignoreUnknown = true)
data class InstagramMediaListResponse(
    val data: List<MediaItem> = emptyList(),
    val paging: Paging? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MediaItem(
        val id: String,
        val caption: String? = null,
        val media_type: String? = null,
        val media_url: String? = null,
        val thumbnail_url: String? = null,
        val permalink: String? = null,
        val timestamp: String? = null,
        val like_count: Int? = null,
        val comments_count: Int? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Paging(val cursors: Cursors? = null) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Cursors(val after: String?)
    }
}
```

**Step 4: Naver Clip API에 클립 목록 엔드포인트 추가**

NaverClipApi.kt에 추가:

```kotlin
@GetExchange("/clips")
fun listClips(
    @RequestHeader("Authorization") authorization: String,
    @RequestParam("page") page: Int,
    @RequestParam("size") size: Int,
): NaverClipListResponse
```

NaverClipDtos.kt에 추가:

```kotlin
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverClipListResponse(
    val clips: List<ClipItem> = emptyList(),
    val totalCount: Int? = null,
    val error: NaverClipError? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ClipItem(
        val clipId: String,
        val title: String? = null,
        val thumbnailUrl: String? = null,
        val clipUrl: String? = null,
        val viewCount: Long? = null,
        val likeCount: Long? = null,
        val commentCount: Long? = null,
        val createdAt: String? = null,
    )
}
```

**Step 5: Twitter API에 유저 트윗 목록 엔드포인트 추가**

TwitterApi.kt에 추가:

```kotlin
@GetExchange("/2/users/{userId}/tweets")
fun listUserTweets(
    @PathVariable userId: String,
    @RequestParam("max_results") maxResults: Int,
    @RequestParam("pagination_token", required = false) paginationToken: String?,
    @RequestParam("tweet.fields") tweetFields: String,
    @RequestParam("media.fields") mediaFields: String,
    @RequestParam("expansions") expansions: String,
    @RequestHeader("Authorization") authorization: String,
): TwitterTweetListResponse
```

TwitterDtos.kt에 추가:

```kotlin
@JsonIgnoreProperties(ignoreUnknown = true)
data class TwitterTweetListResponse(
    val data: List<TweetItem>? = null,
    val meta: Meta? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TweetItem(
        val id: String,
        val text: String? = null,
        val created_at: String? = null,
        val public_metrics: PublicMetrics? = null,
        val attachments: Attachments? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PublicMetrics(
        val impression_count: Long? = null,
        val like_count: Long? = null,
        val reply_count: Long? = null,
        val retweet_count: Long? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Attachments(val media_keys: List<String>? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Meta(val next_token: String? = null, val result_count: Int? = null)
}
```

**Step 6: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

**Step 7: 커밋**

```
feat: 플랫폼별 영상 목록 조회 API 인터페이스 추가

YouTube playlistItems, TikTok video/list, Instagram media,
Naver Clip clips, Twitter user tweets 엔드포인트 + DTO 추가
```

---

## Task 2: PlatformClient에 listVideos 메서드 구현

**Files:**
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/platform/PlatformClient.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/youtube/YouTubeClient.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/tiktok/TikTokClient.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/instagram/InstagramClient.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/naverclip/NaverClipClient.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/twitter/TwitterClient.kt`

**Step 1: PlatformClient 인터페이스에 listVideos 추가**

```kotlin
// PlatformClient.kt에 추가

data class PlatformFeedItem(
    val platformVideoId: String,
    val title: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val platformUrl: String? = null,
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val commentCount: Long = 0,
    val shareCount: Long = 0,
    val publishedAt: String? = null,
)

data class PlatformFeedResult(
    val items: List<PlatformFeedItem>,
    val nextPageToken: String? = null,
    val totalCount: Int? = null,
)

// PlatformClient 인터페이스에 추가
fun listVideos(
    accessToken: String,
    platformChannelId: String?,
    maxResults: Int = 20,
    pageToken: String? = null,
): PlatformFeedResult = PlatformFeedResult(emptyList())
```

**Step 2: YouTubeClient.listVideos 구현**

YouTube는 2단계: (1) 채널의 uploads playlist ID 조회 → (2) playlistItems.list로 영상 목록 → (3) videos.list로 통계 조회

```kotlin
override fun listVideos(accessToken: String, platformChannelId: String?, maxResults: Int, pageToken: String?): PlatformFeedResult {
    try {
        // 1. 채널의 uploads playlist ID 조회
        val channelResponse = youTubeApi.listChannels(
            part = "contentDetails",
            mine = true,
            authorization = "Bearer $accessToken",
        )
        val uploadsPlaylistId = channelResponse.items?.firstOrNull()
            ?.contentDetails?.relatedPlaylists?.get("uploads")
            ?: return PlatformFeedResult(emptyList())

        // 2. playlist items 조회
        val playlistResponse = youTubeApi.listPlaylistItems(
            playlistId = uploadsPlaylistId,
            part = "snippet,contentDetails",
            maxResults = maxResults,
            pageToken = pageToken,
            authorization = "Bearer $accessToken",
        )

        val videoIds = playlistResponse.items.mapNotNull { it.contentDetails?.videoId }
        if (videoIds.isEmpty()) return PlatformFeedResult(emptyList(), playlistResponse.nextPageToken)

        // 3. 통계 데이터 조회 (batch)
        val statsResponse = youTubeApi.listVideos(
            id = videoIds.joinToString(","),
            part = "statistics",
            authorization = "Bearer $accessToken",
        )
        val statsMap = statsResponse.items.associateBy { it.id }

        val items = playlistResponse.items.mapNotNull { item ->
            val videoId = item.contentDetails?.videoId ?: return@mapNotNull null
            val stats = statsMap[videoId]?.statistics
            PlatformFeedItem(
                platformVideoId = videoId,
                title = item.snippet?.title ?: "",
                description = item.snippet?.description,
                thumbnailUrl = item.snippet?.thumbnails?.high?.url
                    ?: item.snippet?.thumbnails?.medium?.url,
                platformUrl = "https://www.youtube.com/watch?v=$videoId",
                viewCount = stats?.viewCount?.toLongOrNull() ?: 0,
                likeCount = stats?.likeCount?.toLongOrNull() ?: 0,
                commentCount = stats?.commentCount?.toLongOrNull() ?: 0,
                publishedAt = item.snippet?.publishedAt,
            )
        }

        return PlatformFeedResult(
            items = items,
            nextPageToken = playlistResponse.nextPageToken,
            totalCount = playlistResponse.pageInfo?.totalResults,
        )
    } catch (e: Exception) {
        log.error("YouTube 영상 목록 조회 실패: {}", e.message)
        return PlatformFeedResult(emptyList())
    }
}
```

**주의**: YouTubeChannelListResponse DTO에 `contentDetails` 필드 추가 필요:
```kotlin
// YouTubeDtos.kt - YouTubeChannelListResponse.ChannelItem에 추가
val contentDetails: ContentDetails? = null,

@JsonIgnoreProperties(ignoreUnknown = true)
data class ContentDetails(
    val relatedPlaylists: Map<String, String>? = null,
)
```

**Step 3: TikTokClient.listVideos 구현**

```kotlin
override fun listVideos(accessToken: String, platformChannelId: String?, maxResults: Int, pageToken: String?): PlatformFeedResult {
    try {
        val cursor = pageToken?.toLongOrNull()
        val response = tikTokApi.listVideos(
            authorization = "Bearer $accessToken",
            request = TikTokVideoListRequest(max_results = maxResults, cursor = cursor),
        )
        val data = response.data ?: return PlatformFeedResult(emptyList())
        val items = data.videos.map { video ->
            PlatformFeedItem(
                platformVideoId = video.id,
                title = video.title ?: "",
                thumbnailUrl = video.cover_image_url,
                viewCount = video.view_count ?: 0,
                likeCount = video.like_count ?: 0,
                commentCount = video.comment_count ?: 0,
                shareCount = video.share_count ?: 0,
                publishedAt = video.create_time?.let {
                    java.time.Instant.ofEpochSecond(it).toString()
                },
            )
        }
        return PlatformFeedResult(
            items = items,
            nextPageToken = if (data.has_more) data.cursor?.toString() else null,
        )
    } catch (e: Exception) {
        log.error("TikTok 영상 목록 조회 실패: {}", e.message)
        return PlatformFeedResult(emptyList())
    }
}
```

**Step 4: InstagramClient.listVideos 구현**

```kotlin
override fun listVideos(accessToken: String, platformChannelId: String?, maxResults: Int, pageToken: String?): PlatformFeedResult {
    try {
        val userId = platformChannelId ?: "me"
        val response = instagramApi.listMedia(
            userId = userId,
            fields = "id,caption,media_type,media_url,thumbnail_url,permalink,timestamp,like_count,comments_count",
            limit = maxResults,
            after = pageToken,
            accessToken = accessToken,
        )
        val items = response.data
            .filter { it.media_type in listOf("VIDEO", "REELS") }
            .map { media ->
                PlatformFeedItem(
                    platformVideoId = media.id,
                    title = media.caption?.take(100) ?: "",
                    description = media.caption,
                    thumbnailUrl = media.thumbnail_url ?: media.media_url,
                    platformUrl = media.permalink,
                    likeCount = media.like_count?.toLong() ?: 0,
                    commentCount = media.comments_count?.toLong() ?: 0,
                    publishedAt = media.timestamp,
                )
            }
        return PlatformFeedResult(
            items = items,
            nextPageToken = response.paging?.cursors?.after,
        )
    } catch (e: Exception) {
        log.error("Instagram 미디어 목록 조회 실패: {}", e.message)
        return PlatformFeedResult(emptyList())
    }
}
```

**Step 5: NaverClipClient.listVideos 구현**

```kotlin
override fun listVideos(accessToken: String, platformChannelId: String?, maxResults: Int, pageToken: String?): PlatformFeedResult {
    try {
        val page = pageToken?.toIntOrNull() ?: 0
        val response = naverClipApi.listClips(
            authorization = "Bearer $accessToken",
            page = page,
            size = maxResults,
        )
        if (response.error != null) return PlatformFeedResult(emptyList())
        val items = response.clips.map { clip ->
            PlatformFeedItem(
                platformVideoId = clip.clipId,
                title = clip.title ?: "",
                thumbnailUrl = clip.thumbnailUrl,
                platformUrl = clip.clipUrl,
                viewCount = clip.viewCount ?: 0,
                likeCount = clip.likeCount ?: 0,
                commentCount = clip.commentCount ?: 0,
                publishedAt = clip.createdAt,
            )
        }
        val nextPage = if (items.size == maxResults) (page + 1).toString() else null
        return PlatformFeedResult(
            items = items,
            nextPageToken = nextPage,
            totalCount = response.totalCount,
        )
    } catch (e: Exception) {
        log.error("Naver Clip 목록 조회 실패: {}", e.message)
        return PlatformFeedResult(emptyList())
    }
}
```

**Step 6: TwitterClient.listVideos 구현**

```kotlin
override fun listVideos(accessToken: String, platformChannelId: String?, maxResults: Int, pageToken: String?): PlatformFeedResult {
    try {
        val userId = platformChannelId ?: run {
            val me = twitterApi.getMe(authorization = "Bearer $accessToken")
            me.data?.id ?: return PlatformFeedResult(emptyList())
        }
        val response = twitterApi.listUserTweets(
            userId = userId,
            maxResults = maxResults,
            paginationToken = pageToken,
            tweetFields = "created_at,public_metrics",
            mediaFields = "preview_image_url,url",
            expansions = "attachments.media_keys",
            authorization = "Bearer $accessToken",
        )
        val items = (response.data ?: emptyList()).map { tweet ->
            PlatformFeedItem(
                platformVideoId = tweet.id,
                title = tweet.text?.take(100) ?: "",
                description = tweet.text,
                platformUrl = "https://twitter.com/i/status/${tweet.id}",
                viewCount = tweet.public_metrics?.impression_count ?: 0,
                likeCount = tweet.public_metrics?.like_count ?: 0,
                commentCount = tweet.public_metrics?.reply_count ?: 0,
                shareCount = tweet.public_metrics?.retweet_count ?: 0,
                publishedAt = tweet.created_at,
            )
        }
        return PlatformFeedResult(
            items = items,
            nextPageToken = response.meta?.next_token,
        )
    } catch (e: Exception) {
        log.error("Twitter 트윗 목록 조회 실패: {}", e.message)
        return PlatformFeedResult(emptyList())
    }
}
```

**Step 7: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

**Step 8: 커밋**

```
feat: PlatformClient.listVideos 구현 — 5개 플랫폼 영상 목록 조회
```

---

## Task 3: VideoFeedUseCase + 캐시 + Controller 엔드포인트

**Files:**
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/video/VideoFeedUseCase.kt`
- Modify: `backend/onGo-api/src/main/kotlin/com/ongo/api/video/VideoController.kt`
- Modify: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/channel/PlatformClientPort.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/platform/PlatformClientPortAdapter.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/cache/CaffeineCacheConfig.kt`

**Step 1: PlatformClientPort에 listVideos 추가**

```kotlin
// PlatformClientPort.kt에 추가
fun listVideos(platform: Platform, accessToken: String, platformChannelId: String?, maxResults: Int, pageToken: String?): PlatformFeedPortResult

// 도메인 레벨 DTO
data class PlatformFeedPortResult(
    val items: List<FeedItemResult>,
    val nextPageToken: String? = null,
    val totalCount: Int? = null,
)

data class FeedItemResult(
    val platformVideoId: String,
    val title: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val platformUrl: String? = null,
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val commentCount: Long = 0,
    val shareCount: Long = 0,
    val publishedAt: String? = null,
)
```

**Step 2: PlatformClientPortAdapter에 구현**

```kotlin
override fun listVideos(platform: Platform, accessToken: String, platformChannelId: String?, maxResults: Int, pageToken: String?): PlatformFeedPortResult {
    val client = platformClientFactory.getClient(platform)
    val result = client.listVideos(accessToken, platformChannelId, maxResults, pageToken)
    return PlatformFeedPortResult(
        items = result.items.map { item ->
            FeedItemResult(
                platformVideoId = item.platformVideoId,
                title = item.title,
                description = item.description,
                thumbnailUrl = item.thumbnailUrl,
                platformUrl = item.platformUrl,
                viewCount = item.viewCount,
                likeCount = item.likeCount,
                commentCount = item.commentCount,
                shareCount = item.shareCount,
                publishedAt = item.publishedAt,
            )
        },
        nextPageToken = result.nextPageToken,
        totalCount = result.totalCount,
    )
}
```

**Step 3: Caffeine 캐시 추가**

```kotlin
// CaffeineCacheConfig.kt에 추가
"videoFeed" -> Caffeine.newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .maximumSize(200)
    .build<Any, Any>()
```

**Step 4: VideoFeedUseCase 생성**

```kotlin
package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.FeedItemResult
import com.ongo.domain.channel.PlatformClientPort
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class VideoFeedUseCase(
    private val channelRepository: ChannelRepository,
    private val platformClientPort: PlatformClientPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Cacheable(value = ["videoFeed"], key = "#userId + '-' + (#platform?.name() ?: 'ALL') + '-' + #page")
    fun getFeed(
        userId: Long,
        platform: Platform?,
        page: Int,
        size: Int,
        sort: String?,
    ): VideoFeedResponse {
        val channels = channelRepository.findByUserId(userId)
            .filter { it.status == "ACTIVE" }
            .filter { platform == null || it.platform == platform }

        if (channels.isEmpty()) {
            return VideoFeedResponse(items = emptyList(), platforms = emptyList())
        }

        // 각 채널에서 병렬로 영상 목록 조회
        val allItems = mutableListOf<VideoFeedItem>()
        val platformErrors = mutableListOf<String>()

        for (channel in channels) {
            try {
                val result = platformClientPort.listVideos(
                    platform = channel.platform,
                    accessToken = channel.accessToken,
                    platformChannelId = channel.platformChannelId,
                    maxResults = size,
                    pageToken = if (page > 0) page.toString() else null,
                )
                result.items.forEach { item ->
                    allItems.add(
                        VideoFeedItem(
                            platformVideoId = item.platformVideoId,
                            platform = channel.platform,
                            channelName = channel.channelName,
                            title = item.title,
                            description = item.description,
                            thumbnailUrl = item.thumbnailUrl,
                            platformUrl = item.platformUrl,
                            viewCount = item.viewCount,
                            likeCount = item.likeCount,
                            commentCount = item.commentCount,
                            shareCount = item.shareCount,
                            publishedAt = item.publishedAt,
                        )
                    )
                }
            } catch (e: Exception) {
                log.error("플랫폼 {} 피드 조회 실패: {}", channel.platform, e.message)
                platformErrors.add(channel.platform.name)
            }
        }

        // 정렬
        val sorted = when (sort) {
            "views" -> allItems.sortedByDescending { it.viewCount }
            "likes" -> allItems.sortedByDescending { it.likeCount }
            "comments" -> allItems.sortedByDescending { it.commentCount }
            else -> allItems.sortedByDescending { it.publishedAt }
        }

        return VideoFeedResponse(
            items = sorted.take(size),
            platforms = channels.map { it.platform }.distinct(),
            errors = platformErrors.ifEmpty { null },
        )
    }
}

data class VideoFeedItem(
    val platformVideoId: String,
    val platform: Platform,
    val channelName: String,
    val title: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val platformUrl: String? = null,
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val commentCount: Long = 0,
    val shareCount: Long = 0,
    val publishedAt: String? = null,
)

data class VideoFeedResponse(
    val items: List<VideoFeedItem>,
    val platforms: List<Platform>,
    val errors: List<String>? = null,
)
```

**Step 5: VideoController에 feed 엔드포인트 추가**

```kotlin
// VideoController.kt에 추가
@GetMapping("/feed")
fun getVideoFeed(
    @AuthenticationPrincipal userId: Long,
    @RequestParam(required = false) platform: Platform?,
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "20") size: Int,
    @RequestParam(defaultValue = "recent") sort: String,
): ResponseEntity<ResData<VideoFeedResponse>> {
    val result = videoFeedUseCase.getFeed(userId, platform, page, size, sort)
    return ResData.success(result).toResponseEntity()
}
```

VideoController 생성자에 `videoFeedUseCase: VideoFeedUseCase` 추가.

**Step 6: 빌드 + 테스트**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

**Step 7: 커밋**

```
feat: VideoFeedUseCase + GET /api/v1/videos/feed 엔드포인트

연결된 각 채널의 영상 목록을 플랫폼 API에서 조회하여 통합 피드 반환.
Caffeine 캐시(5분) 적용. 플랫폼/정렬 필터 지원.
```

---

## Task 4: 프론트엔드 API + 타입 + 스���어 추가

**Files:**
- Modify: `frontend/src/types/video.ts`
- Modify: `frontend/src/api/video.ts`
- Modify: `frontend/src/stores/video.ts`

**Step 1: 타입 추가**

```typescript
// video.ts에 추가
export interface VideoFeedItem {
  platformVideoId: string
  platform: Platform
  channelName: string
  title: string
  description: string | null
  thumbnailUrl: string | null
  platformUrl: string | null
  viewCount: number
  likeCount: number
  commentCount: number
  shareCount: number
  publishedAt: string | null
}

export interface VideoFeedResponse {
  items: VideoFeedItem[]
  platforms: Platform[]
  errors: string[] | null
}
```

**Step 2: API 추가**

```typescript
// video.ts api에 추가
feed(params: { platform?: Platform; page?: number; size?: number; sort?: string }) {
  return apiClient
    .get<ResData<VideoFeedResponse>>('/videos/feed', { params })
    .then(unwrapResponse)
},
```

**Step 3: 스토어에 feed 상태 추가**

```typescript
// stores/video.ts에 추가
const feedItems = ref<VideoFeedItem[]>([])
const feedPlatforms = ref<Platform[]>([])
const feedErrors = ref<string[] | null>(null)
const isFeedLoading = ref(false)
const feedFilter = ref<{ platform?: Platform; sort: string }>({ sort: 'recent' })

async function fetchFeed(page = 0, size = 20) {
  isFeedLoading.value = true
  try {
    const result = await videoApi.feed({
      platform: feedFilter.value.platform,
      page,
      size,
      sort: feedFilter.value.sort,
    })
    feedItems.value = result.items
    feedPlatforms.value = result.platforms
    feedErrors.value = result.errors
  } catch (e) {
    feedItems.value = []
  } finally {
    isFeedLoading.value = false
  }
}
```

**Step 4: 타입 체크**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 에러 없음

**Step 5: 커밋**

```
feat: 프론트엔드 VideoFeed 타입 + API + 스토어 추가
```

---

## Task 5: VideosView.vue 전면 재작성

**Files:**
- Rewrite: `frontend/src/views/VideosView.vue`

**Step 1: VideosView.vue 완전 재작성**

기존 파일을 전면 교체. 핵심 구조:

- PageHeader + 업로드 버튼
- 필터 바: 플랫폼 탭 (전체/YouTube/TikTok/...) + 정렬 드롭다운
- 테이블 (tablet+): 썸네일 | 제목 | 플랫폼 | 조회수 | 좋아요 | 댓글 | 공유 | 게시일
- 카드 (모바일): 썸네일 + 제목 + 지표 인라인
- 영상 클릭 → 슬라이드 패널 (VideoDetailPanel)
- 수정/삭제/선택모드/컨텍스트메뉴/스와이프 모두 제거
- EmptyState: "연결된 채널이 없습니다" / "영상이 없습니다"

제거 대상 컴포넌트 import:
- `SwipeableCard`, `BulkActionBar`, `RecycleModal`, `FavoriteButton`, `BatchProgressPanel`
- `ConfirmModal` (삭제 확인)
- 선택 모드 관련 모든 로직

유지:
- `PageHeader`, `PageGuide`, `LoadingSpinner`, `EmptyState`, `PlatformBadge`, `ExportDropdown`, `ScrollToTop`

**Step 2: 타입 체크 + 화면 확인**

Run: `cd frontend && npx vue-tsc --noEmit`

**Step 3: 커밋**

```
feat: VideosView 전면 재작성 — 플랫폼 피드 모니터링 페이지로 전환

수정/삭제/선택모드 제거, 플랫폼 API 기반 통합 피드,
조회수/좋아요/댓글/공유 성과 지표 중심 테이블 뷰
```

---

## Task 6: VideoDetailPanel (슬라이드 패널 + 인라인 댓글)

**Files:**
- Create: `frontend/src/components/video/VideoDetailPanel.vue`
- Modify: `frontend/src/views/VideosView.vue` (패널 연결)

**Step 1: VideoDetailPanel.vue 생성**

슬라이드 패널 구조:
- 헤더: 제목 + 플랫폼 배지 + 닫기 버튼
- 썸네일 (클릭 시 platformUrl로 이동)
- 성과 지표 카드 (조회수/좋아요/댓글/공유) 2x2 그리드
- 설명 (접기/펼치기)
- 최근 댓글 5개 목록 + 답글 입력 폼
- "전체 댓글 관리 →" 링크 (`/comments?platformVideoId=xxx&platform=YOUTUBE`)

댓글 데이터는 기존 `commentApi`를 활용하거나, 플랫폼 댓글 API를 직접 호출.

**Step 2: VideosView에 패널 연결**

```vue
<VideoDetailPanel
  v-if="selectedItem"
  :item="selectedItem"
  @close="selectedItem = null"
/>
```

테이블 행/카드 클릭 → `selectedItem = item`

**Step 3: 타입 체크 + 화면 확인**

Run: `cd frontend && npx vue-tsc --noEmit`

**Step 4: 커밋**

```
feat: VideoDetailPanel 슬라이드 패널 — 성과 지표 + 인라인 댓글
```

---

## Task 7: 백엔드 정리 + 이전 수정/삭제 플랫폼 동기화 제거

**Files:**
- Modify: `backend/onGo-application/src/main/kotlin/com/ongo/application/video/VideoQueryUseCase.kt`
- Delete: `backend/onGo-application/src/main/kotlin/com/ongo/application/video/PlatformSyncScheduler.kt`
- Modify: `backend/onGo-api/src/main/kotlin/com/ongo/api/video/VideoController.kt`

**Step 1: VideoQueryUseCase에서 플랫폼 동기화 메서드 제거**

- `syncMetadataToPlatforms()` 제거
- `deleteFromPlatforms()` 제거
- `updateVideo()`에서 `syncMetadataToPlatforms()` 호출 제거
- `deleteVideo()`에서 `deleteFromPlatforms()` 호출 제거
- `channelRepository`, `platformClientPort` 의존성은 유지 (다른 곳에서 사용 가능)

**Step 2: PlatformSyncScheduler 삭제**

이 파일은 Task 3의 `@Cacheable` 피드로 대체됨.

**Step 3: VideoController에서 update/delete 엔드포인트에 Deprecated 마킹**

완전 삭제하면 다른 곳(VideoDetailView 등)에서 에러 발생 가능 — 우선 `@Deprecated` 마킹.

**Step 4: 빌드 + 테스트**

Run: `./gradlew build` (테스트 포함)
Expected: BUILD SUCCESSFUL, all tests pass

**Step 5: 커밋**

```
refactor: 영상 수정/삭제 플랫폼 동기화 제거 + PlatformSyncScheduler 삭제

/videos 페이지가 플랫폼 피드 조회 방식으로 전환됨에 따라
불필요해진 수정/삭제 동기화 로직 정리
```

---

## Task 8: i18n + 최종 검증

**Files:**
- Modify: `frontend/src/locales/ko/common.json`
- Modify: `frontend/src/locales/en/common.json`

**Step 1: i18n 키 추가/수정**

```json
{
  "videos": {
    "title": "콘텐츠 피드",
    "description": "연결된 채널의 모든 콘텐츠를 한눈에 확인하세요",
    "allPlatforms": "전체",
    "sortRecent": "최신순",
    "sortViews": "조회수순",
    "sortLikes": "좋아요순",
    "sortComments": "댓글순",
    "views": "조회수",
    "likes": "좋아요",
    "comments": "댓글",
    "shares": "공유",
    "publishedAt": "게시일",
    "noChannels": "연결된 채널이 없습니다",
    "noChannelsDesc": "채널을 연결하면 모든 콘텐츠를 여기서 확인할 수 있습니다",
    "connectChannel": "채널 연결하기",
    "noVideos": "콘텐츠가 없습니다",
    "platformError": "{platform} 조회에 실패했습니다",
    "viewOnPlatform": "플랫폼에서 보기",
    "recentComments": "최근 댓글",
    "manageAllComments": "전체 댓글 관리",
    "replyPlaceholder": "답글 입력...",
    "reply": "답글"
  }
}
```

**Step 2: 전체 빌드 + 타입 체크**

Run: `./gradlew build` + `cd frontend && npx vue-tsc --noEmit`

**Step 3: 최종 커밋**

```
feat: /videos 피드 재설계 완료 — i18n + 최종 검증
```
