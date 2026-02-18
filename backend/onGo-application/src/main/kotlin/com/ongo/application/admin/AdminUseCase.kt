package com.ongo.application.admin

import com.ongo.application.admin.dto.*
import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.common.config.PageResponse
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminUseCase(
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val channelRepository: ChannelRepository,
    private val storageQuotaUseCase: StorageQuotaUseCase,
) {

    private val log = LoggerFactory.getLogger(AdminUseCase::class.java)

    fun getUsers(query: String?, page: Int, size: Int): PageResponse<AdminUserListItem> {
        val offset = page * size
        val users = userRepository.findAll(offset, size, query)
        val totalCount = userRepository.countAll(query)

        val items = users.map { user ->
            val subscription = subscriptionRepository.findByUserId(user.id!!)
            val storageUsedBytes = storageQuotaUseCase.getCurrentUsage(user.id!!)
            val storageLimitBytes = storageQuotaUseCase.getEffectiveLimit(user.id!!)

            AdminUserListItem(
                id = user.id!!,
                name = user.name,
                email = user.email,
                role = user.role,
                planType = (subscription?.planType ?: user.planType).name,
                storageUsedBytes = storageUsedBytes,
                storageLimitBytes = storageLimitBytes,
                createdAt = user.createdAt?.toString(),
            )
        }

        return PageResponse.of(items, page, size, totalCount)
    }

    fun getUserDetail(userId: Long): AdminUserDetail {
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        val subscription = subscriptionRepository.findByUserId(userId)
        val storageUsedBytes = storageQuotaUseCase.getCurrentUsage(userId)
        val storageLimitBytes = storageQuotaUseCase.getEffectiveLimit(userId)
        val videoCount = videoRepository.countByUserId(userId)

        return AdminUserDetail(
            id = user.id!!,
            name = user.name,
            email = user.email,
            role = user.role,
            planType = (subscription?.planType ?: user.planType).name,
            storageUsedBytes = storageUsedBytes,
            storageLimitBytes = storageLimitBytes,
            storageQuotaOverride = subscription?.storageQuotaLimitBytes,
            videoCount = videoCount,
            createdAt = user.createdAt?.toString(),
        )
    }

    @Transactional
    fun updateStorageQuota(userId: Long, limitBytes: Long?) {
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        val subscription = subscriptionRepository.findByUserId(userId)
            ?: throw NotFoundException("구독", userId)

        log.info("[AUDIT] 관리자 스토리지 쿼터 변경: targetUserId={}, email={}, 이전={}bytes, 신규={}bytes",
            userId, user.email, subscription.storageQuotaLimitBytes, limitBytes)

        subscriptionRepository.update(
            subscription.copy(storageQuotaLimitBytes = limitBytes)
        )
    }

    // --- 1단계: CS 대응용 ---

    fun getUserVideos(targetUserId: Long, page: Int, size: Int): PageResponse<AdminVideoItem> {
        userRepository.findById(targetUserId) ?: throw NotFoundException("사용자", targetUserId)

        val videos = videoRepository.findByUserId(targetUserId, page, size)
        val totalCount = videoRepository.countByUserId(targetUserId)

        val videoIds = videos.mapNotNull { it.id }
        val uploadsByVideoId = videoUploadRepository.findByVideoIds(videoIds)

        val items = videos.map { video ->
            val uploads = uploadsByVideoId[video.id!!] ?: emptyList()
            AdminVideoItem(
                id = video.id!!,
                title = video.title,
                status = video.status,
                mediaType = video.mediaType.name,
                fileSizeBytes = video.fileSizeBytes,
                platforms = uploads.map { upload ->
                    AdminPlatformUploadItem(
                        platform = upload.platform,
                        status = upload.status,
                        platformUrl = upload.platformUrl,
                        errorMessage = upload.errorMessage,
                    )
                },
                createdAt = video.createdAt?.toString(),
            )
        }

        return PageResponse.of(items, page, size, totalCount)
    }

    fun getUserChannels(targetUserId: Long): List<AdminChannelItem> {
        userRepository.findById(targetUserId) ?: throw NotFoundException("사용자", targetUserId)

        return channelRepository.findByUserId(targetUserId).map { channel ->
            AdminChannelItem(
                id = channel.id!!,
                platform = channel.platform,
                channelName = channel.channelName,
                channelUrl = channel.channelUrl,
                subscriberCount = channel.subscriberCount,
                status = channel.status,
                tokenExpiresAt = channel.tokenExpiresAt?.toString(),
                connectedAt = channel.connectedAt?.toString(),
            )
        }
    }

    fun getUserSubscription(targetUserId: Long): AdminSubscriptionDetail {
        userRepository.findById(targetUserId) ?: throw NotFoundException("사용자", targetUserId)
        val subscription = subscriptionRepository.findByUserId(targetUserId)
            ?: throw NotFoundException("구독", targetUserId)

        return AdminSubscriptionDetail(
            planType = subscription.planType.name,
            status = subscription.status.name,
            price = subscription.price,
            billingCycle = subscription.billingCycle.name,
            currentPeriodStart = subscription.currentPeriodStart?.toString(),
            currentPeriodEnd = subscription.currentPeriodEnd?.toString(),
            nextBillingDate = subscription.nextBillingDate?.toString(),
            pendingPlanType = subscription.pendingPlanType?.name,
            storageQuotaOverride = subscription.storageQuotaLimitBytes,
            cancelledAt = subscription.cancelledAt?.toString(),
            createdAt = subscription.createdAt?.toString(),
        )
    }

    @Transactional
    fun updateUserRole(targetUserId: Long, newRole: String) {
        val validRoles = setOf("USER", "ADMIN")
        if (newRole !in validRoles) {
            throw BusinessException("INVALID_ROLE", "유효하지 않은 역할입니다: $newRole (허용: $validRoles)")
        }

        val user = userRepository.findById(targetUserId) ?: throw NotFoundException("사용자", targetUserId)

        log.info("[AUDIT] 관리자 역할 변경: targetUserId={}, email={}, 이전={}, 신규={}",
            targetUserId, user.email, user.role, newRole)

        userRepository.update(user.copy(role = newRole))
    }

    @Transactional
    fun deactivateUser(targetUserId: Long) {
        val user = userRepository.findById(targetUserId) ?: throw NotFoundException("사용자", targetUserId)

        if (user.role == "ADMIN") {
            throw BusinessException("CANNOT_DEACTIVATE_ADMIN", "관리자 계정은 비활성화할 수 없습니다")
        }

        log.info("[AUDIT] 관리자 사용자 비활성화: targetUserId={}, email={}", targetUserId, user.email)

        // 구독 상태를 SUSPENDED로 변경
        val subscription = subscriptionRepository.findByUserId(targetUserId)
        if (subscription != null) {
            subscriptionRepository.update(
                subscription.copy(status = com.ongo.common.enums.SubscriptionStatus.SUSPENDED)
            )
        }
    }

    @Transactional
    fun activateUser(targetUserId: Long) {
        val user = userRepository.findById(targetUserId) ?: throw NotFoundException("사용자", targetUserId)

        log.info("[AUDIT] 관리자 사용자 활성화: targetUserId={}, email={}", targetUserId, user.email)

        val subscription = subscriptionRepository.findByUserId(targetUserId)
        if (subscription != null && subscription.status == com.ongo.common.enums.SubscriptionStatus.SUSPENDED) {
            subscriptionRepository.update(
                subscription.copy(status = com.ongo.common.enums.SubscriptionStatus.ACTIVE)
            )
        }
    }
}
