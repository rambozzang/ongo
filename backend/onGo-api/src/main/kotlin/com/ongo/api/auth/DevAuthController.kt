package com.ongo.api.auth

import com.ongo.api.auth.dto.AuthResponse
import com.ongo.api.auth.dto.UserResponse
import com.ongo.common.ResData
import com.ongo.domain.auth.AuthTokenPort
import com.ongo.domain.user.UserRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Profile("dev", "local")
@Tag(name = "인증 (개발용)", description = "개발/로컬 환경 전용 토큰 발급")
@RestController
@RequestMapping("/api/v1/auth")
class DevAuthController(
    private val authTokenPort: AuthTokenPort,
    private val userRepository: UserRepository,
) {

    @Operation(
        summary = "[DEV] 테스트 토큰 발급",
        description = "개발/테스트 환경 전용: 이메일로 사용자를 조회하여 JWT Access Token과 Refresh Token을 발급합니다. 운영 환경에서는 비활성화됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "토큰 발급 성공"),
        ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    )
    @PostMapping("/dev/token")
    fun devToken(
        @Parameter(description = "토큰을 발급받을 이메일") @RequestParam email: String,
    ): ResponseEntity<ResData<AuthResponse>> {
        val user = userRepository.findByEmail(email)
            ?: return ResponseEntity.status(404).body(
                ResData(success = false, error = "사용자를 찾을 수 없습니다: $email")
            )

        val userId = user.id!!
        val accessToken = authTokenPort.generateAccessToken(userId)
        val refreshToken = authTokenPort.generateRefreshToken(userId)

        return ResData.success(
            AuthResponse(
                accessToken = accessToken,
                refreshToken = refreshToken,
                user = UserResponse(
                    id = userId,
                    email = user.email,
                    name = user.name,
                    nickname = user.nickname,
                    profileImageUrl = user.profileImageUrl,
                    planType = user.planType.name,
                    onboardingCompleted = user.onboardingCompleted,
                ),
                isNewUser = false,
            ),
            "테스트 토큰이 발급되었습니다"
        )
    }
}
