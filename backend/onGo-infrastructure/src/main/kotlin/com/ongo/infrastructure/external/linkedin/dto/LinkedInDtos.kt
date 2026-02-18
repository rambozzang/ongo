package com.ongo.infrastructure.external.linkedin.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

data class LinkedInRegisterUploadRequest(
    val registerUploadRequest: RegisterRequest,
) {
    data class RegisterRequest(
        val owner: String, // urn:li:person:{id}
        val recipes: List<String> = listOf("urn:li:digitalmediaRecipe:feedshare-video"),
        val serviceRelationships: List<ServiceRelationship> = listOf(
            ServiceRelationship()
        ),
    )

    data class ServiceRelationship(
        val relationshipType: String = "OWNER",
        val identifier: String = "urn:li:userGeneratedContent",
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class LinkedInRegisterUploadResponse(
    val value: UploadValue?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class UploadValue(
        val asset: String?, // urn:li:digitalmediaAsset:{id}
        val uploadMechanism: UploadMechanism?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class UploadMechanism(
        @JsonProperty("com.linkedin.digitalmedia.uploading.MediaUploadHttpRequest")
        val httpRequest: HttpRequest?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class HttpRequest(
        val uploadUrl: String?,
    )
}

data class LinkedInUgcPostRequest(
    val author: String, // urn:li:person:{id}
    val lifecycleState: String = "PUBLISHED",
    val specificContent: SpecificContent,
    val visibility: Visibility,
) {
    data class SpecificContent(
        @JsonProperty("com.linkedin.ugc.ShareContent")
        val shareContent: ShareContent,
    )

    data class ShareContent(
        val shareCommentary: ShareCommentary,
        val shareMediaCategory: String = "VIDEO",
        val media: List<ShareMedia>,
    )

    data class ShareCommentary(
        val text: String,
    )

    data class ShareMedia(
        val status: String = "READY",
        val media: String, // asset URN
        val title: MediaTitle,
        val description: MediaDescription,
    )

    data class MediaTitle(
        val text: String,
    )

    data class MediaDescription(
        val text: String,
    )

    data class Visibility(
        @JsonProperty("com.linkedin.ugc.MemberNetworkVisibility")
        val memberNetworkVisibility: String, // PUBLIC, CONNECTIONS
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class LinkedInUgcPostResponse(
    val id: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LinkedInProfileResponse(
    val id: String,
    val localizedFirstName: String?,
    val localizedLastName: String?,
    val vanityName: String?,
    @JsonProperty("profilePicture") val profilePicture: ProfilePicture?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ProfilePicture(
        @JsonProperty("displayImage~") val displayImage: DisplayImage?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DisplayImage(
        val elements: List<ImageElement>?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ImageElement(
        val identifiers: List<ImageIdentifier>?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ImageIdentifier(
        val identifier: String?,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class LinkedInShareStatsResponse(
    val elements: List<ShareStats>?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ShareStats(
        val totalShareStatistics: TotalStats?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TotalStats(
        val shareCount: Long?,
        val likeCount: Long?,
        val commentCount: Long?,
        val impressionCount: Long?,
        val clickCount: Long?,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class LinkedInTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("refresh_token") val refreshToken: String?,
    @JsonProperty("expires_in") val expiresIn: Long,
    @JsonProperty("refresh_token_expires_in") val refreshTokenExpiresIn: Long?,
)

// --- Comment DTOs ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class LinkedInCommentsResponse(
    val elements: List<LinkedInCommentElement>? = null,
    val paging: LinkedInPaging? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LinkedInCommentElement(
        @JsonProperty("\$URN") val urn: String? = null,
        val message: LinkedInMessage? = null,
        val actor: String? = null,
        val created: LinkedInTime? = null,
        val likesSummary: LikesSummary? = null,
        val commentsSummary: CommentsSummary? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LinkedInMessage(
        val text: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LinkedInTime(
        val time: Long? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LikesSummary(
        val totalLikes: Int? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CommentsSummary(
        val totalFirstLevelComments: Int? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LinkedInPaging(
        val total: Int? = null,
        val start: Int? = null,
        val count: Int? = null,
    )
}

data class LinkedInCommentRequest(
    val message: LinkedInMessageBody,
    val actor: String,
) {
    data class LinkedInMessageBody(
        val text: String,
    )
}

data class LinkedInLikeRequest(
    val actor: String,
    @JsonProperty("object") val targetObject: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LinkedInCommentResponse(
    @JsonProperty("\$URN") val urn: String? = null,
)
