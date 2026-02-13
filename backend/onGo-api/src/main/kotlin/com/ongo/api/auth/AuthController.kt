package com.ongo.api.auth

import com.ongo.api.auth.dto.AuthResponse
import com.ongo.api.auth.dto.RefreshTokenRequest
import com.ongo.api.auth.dto.SocialLoginRequest
import com.ongo.api.auth.dto.UserResponse
import com.ongo.api.config.CurrentUser
import com.ongo.application.auth.AuthUseCase
import com.ongo.application.auth.dto.AuthResult
import com.ongo.common.ResData
import com.ongo.domain.auth.AuthTokenPort
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 인증 및 사용자 계정 관련 API를 제공하는 컨트롤러
 */
@Tag(name = "인증", description = "소셜 로그인 (Google/Kakao), 토큰 갱신, 로그아웃")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authUseCase: AuthUseCase,
    private val authTokenPort: AuthTokenPort,
) {

    @Operation(
        summary = "소셜 로그인",
        description = "Google 또는 Kakao OAuth 인가 코드를 사용하여 로그인합니다. 신규 사용자는 자동으로 회원가입됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "로그인 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (인가 코드 누락 또는 유효하지 않은 provider)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @PostMapping("/login/{provider}")
    fun socialLogin(
        @Parameter(description = "OAuth 제공자 (google, kakao)") @PathVariable provider: String,
        @Valid @RequestBody request: SocialLoginRequest,
    ): ResponseEntity<ResData<AuthResponse>> {
        val result = authUseCase.socialLogin(provider, request.code, request.redirectUri)
        val response = toAuthResponse(result)
        return ResData.success(response, if (result.isNewUser) "회원가입이 완료되었습니다" else "로그인되었습니다")
    }

    @Operation(
        summary = "토큰 갱신",
        description = "Refresh Token을 사용하여 새로운 Access Token과 Refresh Token을 발급받습니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "토큰 갱신 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (Refresh Token 누락)"),
        ApiResponse(responseCode = "401", description = "인증 실패 (만료되었거나 유효하지 않은 Refresh Token)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @PostMapping("/refresh")
    fun refreshToken(
        @Valid @RequestBody request: RefreshTokenRequest,
    ): ResponseEntity<ResData<AuthResponse>> {
        val result = authUseCase.refreshToken(request.refreshToken)
        return ResData.success(toAuthResponse(result), "토큰이 갱신되었습니다")
    }

    @Operation(
        summary = "로그아웃",
        description = "현재 사용자의 Refresh Token을 무효화하여 로그아웃 처리합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "로그아웃 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @PostMapping("/logout")
    fun logout(@Parameter(hidden = true) @CurrentUser userId: Long): ResponseEntity<ResData<Nothing>> {
        authUseCase.logout(userId)
        return ResponseEntity.ok(ResData(success = true, message = "로그아웃되었습니다"))
    }

    @Operation(summary = "프로필 조회", description = "현재 로그인한 사용자의 프로필 정보를 조회합니다.")
    @GetMapping("/profile")
    fun getProfile(@Parameter(hidden = true) @CurrentUser userId: Long): ResponseEntity<ResData<UserResponse>> {
        val result = authUseCase.getProfile(userId)
        return ResData.success(
            UserResponse(
                id = result.id,
                email = result.email,
                name = result.name,
                nickname = result.nickname,
                profileImageUrl = result.profileImageUrl,
                planType = result.planType,
                onboardingCompleted = result.onboardingCompleted,
            )
        )
    }

    @Operation(summary = "프로필 수정", description = "사용자의 닉네임과 카테고리를 수정합니다.")
    @PutMapping("/profile")
    fun updateProfile(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: Map<String, String?>,
    ): ResponseEntity<ResData<UserResponse>> {
        val result = authUseCase.updateProfile(userId, request["nickname"], request["category"])
        return ResData.success(
            UserResponse(
                id = result.id,
                email = result.email,
                name = result.name,
                nickname = result.nickname,
                profileImageUrl = result.profileImageUrl,
                planType = result.planType,
                onboardingCompleted = result.onboardingCompleted,
            ),
            "프로필이 수정되었습니다"
        )
    }

    @Operation(summary = "온보딩 완료", description = "사용자의 온보딩을 완료 처리합니다.")
    @PostMapping("/onboarding/complete")
    fun completeOnboarding(@Parameter(hidden = true) @CurrentUser userId: Long): ResponseEntity<ResData<Nothing>> {
        authUseCase.completeOnboarding(userId)
        return ResponseEntity.ok(ResData(success = true, message = "온보딩이 완료되었습니다"))
    }

    @Operation(summary = "계정 삭제", description = "현재 로그인한 사용자의 계정을 삭제합니다.")
    @DeleteMapping("/account")
    fun deleteAccount(@Parameter(hidden = true) @CurrentUser userId: Long): ResponseEntity<ResData<Nothing>> {
        authUseCase.deleteAccount(userId)
        return ResponseEntity.ok(ResData(success = true, message = "계정이 삭제되었습니다"))
    }

    @Operation(
        summary = "개발용 로그인",
        description = "개발/테스트 환경에서 admin 계정으로 로그인합니다. DB에 admin 사용자가 없으면 자동 생성됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Admin 로그인 성공")
    )
    @PostMapping("/dev-login")
    fun devLogin(): ResponseEntity<ResData<AuthResponse>> {
        val result = authUseCase.devLogin()
        return ResData.success(
            toAuthResponse(result),
            "Admin 계정으로 로그인되었습니다"
        )
    }

    private fun toAuthResponse(result: AuthResult): AuthResponse {
        return AuthResponse(
            accessToken = result.accessToken,
            refreshToken = result.refreshToken,
            user = UserResponse(
                id = result.user.id,
                email = result.user.email,
                name = result.user.name,
                nickname = result.user.nickname,
                profileImageUrl = result.user.profileImageUrl,
                planType = result.user.planType,
                onboardingCompleted = result.user.onboardingCompleted,
            ),
            isNewUser = result.isNewUser,
        )
    }
}

