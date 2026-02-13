package com.ongo.domain.user

import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.PlanType
import java.time.LocalDateTime

/**
 * 사용자 정보를 담당하는 도메인 엔티티
 *
 * @property id 사용자 식별자
 * @property email 이메일 주소
 * @property name 이름
 * @property nickname 닉네임
 * @property profileImageUrl 프로필 이미지 URL
 * @property provider 소셜 로그인 제공자 (GOOGLE, KAKAO 등)
 * @property providerId 소셜 제공자별 유니크 ID
 * @property planType 사용중인 구독 플랜 (FREE, BUSINESS 등)
 * @property category 주 활동 카테고리
 * @property onboardingCompleted 온보딩 완료 여부
 * @property role 사용자 역할 (USER, ADMIN)
 */
data class User(
    val id: Long? = null,
    val email: String,
    val name: String,
    val nickname: String? = null,
    val profileImageUrl: String? = null,
    val provider: AuthProvider,
    val providerId: String,
    val planType: PlanType = PlanType.FREE,
    val category: String? = null,
    val onboardingCompleted: Boolean = false,
    val role: String = "USER",
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)

