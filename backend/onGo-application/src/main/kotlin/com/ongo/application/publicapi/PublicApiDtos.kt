package com.ongo.application.publicapi

import com.fasterxml.jackson.databind.JsonNode

/** Postiz public API의 핵심 요청 형태를 onGo 게시 모델에 매핑한다. */
data class CreatePublicPostRequest(
    val type: String = "now",
    val date: String? = null,
    val shortLink: Boolean = false,
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
)

data class PublicIntegrationRef(
    val id: String,
)

data class PublicPostValue(
    val content: String? = null,
    val title: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    /** Postiz는 media를 배열로 전달한다. onGo는 첫 번째 HTTPS/HTTP 영상을 사용한다. */
    val video: JsonNode? = null,
    val image: JsonNode? = null,
    /** onGo 확장 필드: 이미 업로드된 내부 영상 레코드를 직접 지정한다. */
    val videoId: Long? = null,
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
    val status: String,
)

data class PublicPostTargetResponse(
    val integrationId: String,
    val status: String,
    val platformUrl: String?,
    val error: String?,
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
)

data class PublicPostCreatedResponse(
    val postId: String,
    val integration: String,
)

data class PublicAvailableSlotResponse(
    val date: String,
)
