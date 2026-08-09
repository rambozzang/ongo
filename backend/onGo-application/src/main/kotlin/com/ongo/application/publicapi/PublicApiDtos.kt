package com.ongo.application.publicapi

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDateTime

/** Postiz public API의 핵심 요청 형태를 onGo 게시 모델에 매핑한다. */
data class CreatePublicPostRequest(
    val type: String = "now",
    val date: String? = null,
    val shortLink: Boolean = false,
    /** Postiz republishes an existing post when value.id is supplied. */
    val republish: Boolean = false,
    val tags: List<JsonNode> = emptyList(),
    val order: String? = null,
    val inter: Int? = null,
    val videoId: Long? = null,
    val posts: List<PublicPostItem> = emptyList(),
)

data class PublicPostItem(
    val integration: PublicIntegrationRef,
    val value: List<PublicPostValue> = emptyList(),
    val settings: JsonNode? = null,
    val videoId: Long? = null,
    /** Postiz groups related channel posts under this optional identifier. */
    val group: String? = null,
)

data class PublicIntegrationRef(
    val id: String,
)

data class PublicPostValue(
    /** Postiz child-post identifier. onGo accepts it only for the settings/update contract. */
    val id: String? = null,
    val content: String? = null,
    /** Delay in seconds relative to the group publish time. */
    val delay: Int = 0,
    val title: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    /** Postiz는 media를 배열로 전달한다. onGo는 첫 번째 HTTPS/HTTP 영상을 사용한다. */
    val video: JsonNode? = null,
    val image: JsonNode? = null,
    /** onGo 확장 필드: 이미 업로드된 내부 영상 레코드를 직접 지정한다. */
    val videoId: Long? = null,
)

data class UpdatePublicPostSettingsRequest(
    val settings: JsonNode,
    /** Required when one onGo public post targets more than one channel. */
    val integrationId: String? = null,
)

data class PublicPostSettingsResponse(
    val postId: String,
    val publishDate: String?,
)

data class ChangePublicPostStatusRequest(
    val status: String,
    val date: String? = null,
)

data class PublicIntegrationResponse(
    val id: String,
    val name: String,
    val identifier: String,
    val provider: String,
    val picture: String?,
    val disabled: Boolean,
    val profile: String?,
    val status: String,
    val customer: PublicCustomerResponse? = null,
)

data class PublicCustomerResponse(
    val id: String,
    val name: String,
)

data class PublicPostTargetResponse(
    val integrationId: String,
    val status: String,
    val platformUrl: String?,
    val error: String?,
    val providerIdentifier: String? = null,
    val name: String? = null,
    val picture: String? = null,
)

data class PublicPostResponse(
    val id: String,
    val type: String,
    val status: String,
    /** Postiz change-status 응답과의 호환 필드: DRAFT 또는 QUEUE. */
    val state: String,
    val date: String?,
    val videoId: Long,
    val error: String?,
    val posts: List<PublicPostTargetResponse>,
    val content: String? = null,
)

/** Exact Postiz list item shape; the API wraps these in `{ "posts": [...] }`. */
data class PublicPostListItem(
    val id: String,
    val content: String,
    val publishDate: String?,
    val releaseURL: String?,
    val state: String,
    val integration: Map<String, String>?,
)

data class PublicPostCreatedResponse(
    val postId: String,
    val integration: String,
)

data class PublicAvailableSlotResponse(
    val date: String,
)

data class PublicIntegrationSettingsResponse(
    val id: String,
    val provider: String,
    val title: PublicFieldLimit,
    val description: PublicFieldLimit,
    val tags: PublicFieldLimit,
    val scheduling: Boolean,
    val directVideoUpload: Boolean,
    val cloudVideoUpload: Boolean,
    val maxFileSizeBytes: Long,
    val acceptedExtensions: Set<String>,
    val unavailableReason: String?,
    /** Postiz-compatible discovery payload backed by the provider capability registry. */
    val output: PublicIntegrationSettingsOutput,
)

data class PublicIntegrationSettingsOutput(
    val rules: String,
    val maxLength: Int,
    val settings: JsonNode,
    val tools: List<PublicIntegrationToolResponse>,
)

data class PublicIntegrationToolResponse(
    val methodName: String,
    val description: String,
    val dataSchema: JsonNode,
)

data class PublicIntegrationToolRequest(
    val methodName: String,
    val data: JsonNode? = null,
)

data class PublicIntegrationToolResult(
    val output: JsonNode,
)

data class PublicFieldLimit(
    val maxLength: Int? = null,
    val maxCount: Int? = null,
)

data class PublicConnectionResponse(
    val connected: Boolean,
)

data class PublicOAuthUrlResponse(
    val url: String,
)

data class PublicRemoteMediaUploadRequest(
    val url: String,
    val filename: String? = null,
)

data class PublicGenerateVideoRequest(
    val type: String,
    val output: String,
    val customParams: JsonNode,
)

data class PublicVideoFunctionRequest(
    val functionName: String,
    val identifier: String,
    val params: JsonNode? = null,
)

data class PublicGeneratedVideoResponse(
    val id: String,
    val path: String,
)

data class PublicMissingContentResponse(
    val id: String,
    val url: String?,
)

data class PublicReleaseIdRequest(
    val releaseId: String,
    /** 여러 채널 게시에서 어떤 외부 integration을 연결할지 명시한다. */
    val integrationId: String? = null,
)

data class PublicGroupResponse(
    val id: String,
    val name: String,
)

data class PublicNotificationResponse(
    val id: String,
    val content: String,
    val link: String?,
    val createdAt: LocalDateTime?,
)

data class PublicNotificationListResponse(
    val notifications: List<PublicNotificationResponse>,
    val total: Long,
    val page: Int,
    val limit: Int,
    val hasMore: Boolean,
)
