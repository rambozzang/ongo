package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.ChannelStatus
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.video.VideoUploadRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

@Service
class VideoFeedUseCase(
    private val channelRepository: ChannelRepository,
    private val platformClientPort: PlatformClientPort,
    private val tokenEncryptionPort: TokenEncryptionPort,
    private val videoUploadRepository: VideoUploadRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 플랫폼 피드를 모아 돌려준다.
     *
     * ## 페이지 이동은 **커서**로만 한다
     *
     * 각 플랫폼의 목록 API 는 **불투명한 continuation token** 으로 다음 페이지를 준다
     * (YouTube `nextPageToken`, Instagram `paging.cursors.after`, Twitter `meta.next_token`,
     * TikTok `cursor`). 숫자 페이지 번호를 그 자리에 넣을 수 없다.
     *
     * 예전에는 `pageToken = page.toString()` 이었다. YouTube 는 `"1"` 을 유효하지 않은
     * 토큰으로 보고 오류를 냈고(그 플랫폼 영상이 통째로 사라졌다), TikTok 은 `1` 을
     * 커서(타임스탬프)로 읽어 엉뚱한 구간을 돌려줬다. 게다가 플랫폼이 준 `nextPageToken`
     * 은 버려져 **다음 페이지로 갈 방법 자체가 없었다.**
     *
     * 그래서 응답이 채널별 다음 토큰([VideoFeedResponse.nextPageTokens])을 실어 보내고,
     * 호출자는 그것을 [channelPageTokens] 로 그대로 돌려준다.
     *
     * ## 숫자 [page] 를 지원하지 않는 이유
     *
     * 채널마다 커서가 독립적이라 "N 번째 페이지" 를 만들려면 채널마다 N 번 왕복해야 하고,
     * 그렇게 모아도 정렬은 각 채널의 부분 구간 위에서만 이뤄져 **전역 순서가 되지 않는다.**
     * 잘못된 목록을 조용히 돌려주느니 지원하지 않는다고 분명히 말한다.
     *
     * @param channelPageTokens 채널 ID → 그 채널의 다음 페이지 토큰. 첫 페이지는 비운다.
     * @throws BusinessException [page] 가 0 이 아닐 때.
     */
    @Cacheable(
        value = ["videoFeed"],
        key = "#userId + '-' + (#platform?.name ?: 'ALL') + '-' + #page + '-' + #size + " +
            "'-' + (#sort ?: 'recent') + '-' + #channelPageTokens.hashCode()",
    )
    fun getFeed(
        userId: Long,
        platform: Platform?,
        page: Int,
        size: Int,
        sort: String?,
        channelPageTokens: Map<Long, String> = emptyMap(),
    ): VideoFeedResponse {
        if (page != 0) {
            throw BusinessException(
                "FEED_PAGE_UNSUPPORTED",
                "피드는 페이지 번호로 이동할 수 없습니다. 응답의 nextPageTokens 를 channelToken 으로 전달해주세요.",
            )
        }

        val channels = channelRepository.findByUserId(userId)
            .filter { it.status == ChannelStatus.ACTIVE }
            .filter { platform == null || it.platform == platform }

        if (channels.isEmpty()) {
            return VideoFeedResponse(items = emptyList(), platforms = emptyList())
        }

        // The provider feed contains external IDs, not onGo video IDs. Resolve
        // the link once so local AI tools never mistake a provider ID for our
        // database primary key. A lookup failure must not hide a healthy feed;
        // it only makes local-only actions unavailable.
        val ownedUploads = runCatching { videoUploadRepository.findByUserId(userId) }
            .onFailure { log.warn("onGo 영상-플랫폼 게시 연결 조회 실패: userId={}", userId, it) }
            .getOrDefault(emptyList())

        val allItems = ConcurrentLinkedQueue<VideoFeedItem>()
        val platformErrors = ConcurrentLinkedQueue<String>()
        /*
         * 채널별 다음 페이지 토큰. 여러 가상 스레드가 동시에 쓰므로 동시성 맵을 쓴다.
         * 토큰을 주지 않은 채널은 여기에 들어오지 않는다 — 그 채널은 더 볼 것이 없다는 뜻이다.
         */
        val nextTokens = ConcurrentHashMap<Long, String>()

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val tasks = channels.map { channel ->
                executor.submit {
                    try {
                        val decryptedToken = tokenEncryptionPort.decrypt(channel.accessToken)
                        val result = platformClientPort.listVideos(
                            platform = channel.platform,
                            accessToken = decryptedToken,
                            platformChannelId = channel.platformChannelId,
                            maxResults = size,
                            // 채널마다 커서가 다르다. 없으면 첫 페이지다.
                            pageToken = channelPageTokens[channel.id],
                        )
                        // 일부 provider 어댑터는 외부 오류를 예외 대신 결과로 돌려준다.
                        // 이 신호를 버리면 빈 목록이 정상 응답처럼 보여 기존 콘텐츠를 숨긴다.
                        if (!result.errorMessage.isNullOrBlank()) {
                            platformErrors.add(channel.platform.name)
                        }
                        result.nextPageToken?.let { token ->
                            channel.id?.let { nextTokens[it] = token }
                        }
                        result.items.forEach { item ->
                            allItems.add(
                                VideoFeedItem(
                                    videoId = ownedUploads.firstOrNull {
                                        it.platform == channel.platform &&
                                            it.platformVideoId == item.platformVideoId &&
                                            it.channelId == channel.id
                                    }?.videoId ?: ownedUploads.firstOrNull {
                                        it.platform == channel.platform &&
                                            it.platformVideoId == item.platformVideoId &&
                                            it.channelId == null
                                    }?.videoId,
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
            }
            tasks.forEach { task ->
                try {
                    task.get()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@forEach
                }
            }
        }

        /*
         * **미측정을 0 으로 줄 세우지 않는다.**
         *
         * 예전에는 값이 없으면 `0` 이라 그 영상이 "가장 성과가 낮은 영상" 자리에 놓였다.
         * Instagram 은 목록 API 가 조회수를 아예 주지 않아, 조회수 정렬에서 Instagram
         * 영상이 통째로 맨 아래에 깔렸다.
         *
         * 내림차순이므로 `nullsFirst`(=null 을 가장 작게 봄)를 쓴다. 뒤집히면서 알 수 없는
         * 항목이 목록 **끝**에 모인다 — `nullsLast` 를 넣으면 정반대로 맨 위에 올라온다.
         */
        fun byMetricDesc(pick: (VideoFeedItem) -> Long?) =
            allItems.sortedWith(compareByDescending(nullsFirst<Long>(), pick))

        val sorted = when (sort) {
            "views" -> byMetricDesc { it.viewCount }
            "likes" -> byMetricDesc { it.likeCount }
            "comments" -> byMetricDesc { it.commentCount }
            else -> allItems.sortedByDescending { it.publishedAt }
        }

        return VideoFeedResponse(
            nextPageTokens = nextTokens.toMap(),
            items = sorted.take(size),
            platforms = channels.map { it.platform }.distinct(),
            errors = platformErrors.toList().ifEmpty { null },
        )
    }
}

data class VideoFeedItem(
    /** Internal onGo video ID, when this provider post was published from our library. */
    val videoId: Long? = null,
    val platformVideoId: String,
    val platform: Platform,
    val channelName: String,
    val title: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val platformUrl: String? = null,
    /**
     * 피드 지표. **플랫폼이 주지 않거나 응답에 없으면 `null`** — `0` 이 아니다.
     *
     * 이 값은 어디에도 저장되지 않고 `/videos/feed` 응답으로 화면에 바로 나간다. 그래서
     * `analytics_daily`(`NOT NULL DEFAULT 0`) 와 달리 **`null` 을 그대로 실어 보낼 수 있다.**
     *
     * 예전에는 `?: 0` 이었다. Instagram 미디어 목록은 조회수를 주지 않는데 그 자리가 `0` 이
     * 되어, 목록 화면이 "조회수 0" 을 그리고 **조회수 정렬에서 모든 Instagram 영상이 맨
     * 아래로 밀렸다.** 응답이 실제로 0 을 주면 그 0 은 관측이므로 그대로 둔다.
     */
    val viewCount: Long? = null,
    val likeCount: Long? = null,
    val commentCount: Long? = null,
    val shareCount: Long? = null,
    val publishedAt: String? = null,
)

data class VideoFeedResponse(
    /**
     * 채널 ID → 그 채널의 **다음 페이지 토큰**. 다음 요청에 `channelToken=<채널ID>:<토큰>`
     * 으로 그대로 돌려주면 이어서 볼 수 있다.
     *
     * 더 볼 것이 없는 채널은 여기에 들어오지 않는다. 맵이 비어 있으면 마지막 페이지다.
     * 토큰은 플랫폼이 준 **불투명한 값**이므로 해석하거나 만들어내면 안 된다.
     */
    val nextPageTokens: Map<Long, String> = emptyMap(),
    val items: List<VideoFeedItem>,
    val platforms: List<Platform>,
    val errors: List<String>? = null,
)
