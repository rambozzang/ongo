package com.ongo.api.auth

import com.ongo.api.config.ClientAddressResolver
import com.ongo.api.config.CurrentUserArgumentResolver
import com.ongo.api.config.GlobalExceptionHandler
import com.ongo.application.auth.AuthOAuthAuthorizationUseCase
import com.ongo.application.auth.AuthUseCase
import com.ongo.application.auth.dto.AccountDeletionStatusResponse
import com.ongo.common.exception.AccountDeletionBlockedException
import com.ongo.domain.auth.AuthTokenPort
import io.mockk.every
import io.mockk.Runs
import io.mockk.mockk
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * `DELETE /api/v1/auth/account` 가 **실제 라우팅과 advice 를 거쳐** 409 를 돌려주는지 고정한다.
 *
 * `AccountDeletionBlockedResponseTest` 는 `GlobalExceptionHandler` 메서드를 직접 부른다.
 * 그건 매핑 로직은 증명하지만 **advice 가 이 경로에 실제로 붙는지는 증명하지 않는다.**
 * `@ExceptionHandler` 등록을 빠뜨리거나 다른 advice 가 먼저 잡으면 500 이 나가는데,
 * 직접 호출 테스트는 그걸 통과시킨다.
 *
 * 전체 스프링 컨텍스트를 띄우지 않고 standalone MockMvc 를 쓴다. `onGo-api` 의
 * `application.yml` 이 `JWT_SECRET` 등을 요구해서 `@SpringBootTest` 는 환경 변수 없이
 * 뜨지 않는다. standalone 으로도 라우팅·인자 해석·advice 라는 검증 대상은 모두 지난다.
 * 실제 `CurrentUserArgumentResolver` 와 실제 `GlobalExceptionHandler` 를 그대로 쓴다.
 *
 * 보안 필터는 지나지 않는다. 인증 요구 자체는 `SecurityConfig` 의 책임이라 여기서 다루지 않는다.
 */
class DeleteAccountRouteTest {

    private val authUseCase = mockk<AuthUseCase>()
    private lateinit var mockMvc: MockMvc

    private companion object {
        const val USER_ID = 42L
        const val PATH = "/api/v1/auth/account"
    }

    @BeforeEach
    fun setUp() {
        val controller = AuthController(
            authUseCase = authUseCase,
            authTokenPort = mockk<AuthTokenPort>(relaxed = true),
            authRateLimiter = mockk(relaxed = true),
            oAuthStateManager = mockk(relaxed = true),
            authOAuthAuthorizationUseCase = mockk<AuthOAuthAuthorizationUseCase>(relaxed = true),
            // 이 경로는 상한을 지나지 않지만 컨트롤러가 요구하므로 실제 구현을 넣는다.
            clientAddressResolver = ClientAddressResolver(),
        )
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(CurrentUserArgumentResolver())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    @Test
    @DisplayName("판단 미완 데이터가 있으면 409 와 안정적인 코드가 나간다")
    fun blockedByPolicyReturnsConflict() {
        every { authUseCase.deleteAccount(USER_ID) } throws AccountDeletionBlockedException(
            code = AccountDeletionBlockedException.CODE_POLICY_REVIEW,
            message = "보관 정책 확인이 필요한 데이터가 있어 계정 삭제를 바로 진행할 수 없습니다. 고객지원에 문의해 주세요.",
            supportReference = "review-block:competitors_user_id_fkey",
        )

        mockMvc.perform(delete(PATH))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value(AccountDeletionBlockedException.CODE_POLICY_REVIEW))

        verify(exactly = 1) { authUseCase.deleteAccount(USER_ID) }
    }

    @Test
    @DisplayName("정책 승인된 요청은 실제 삭제 완료가 아니라 202 접수로 응답한다")
    fun acceptedDeletionReturnsAccepted() {
        every { authUseCase.deleteAccount(USER_ID) } just Runs

        mockMvc.perform(delete(PATH))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("계정 삭제 요청이 접수되었습니다. 처리가 완료되면 다시 로그인할 수 없습니다."))

        verify(exactly = 1) { authUseCase.deleteAccount(USER_ID) }
    }

    @Test
    @DisplayName("처리 절차가 없을 때도 200 이 아니라 409 다")
    fun notReadyReturnsConflictNotSuccess() {
        every { authUseCase.deleteAccount(USER_ID) } throws AccountDeletionBlockedException(
            code = AccountDeletionBlockedException.CODE_NOT_READY,
            message = "계정 삭제 처리를 준비 중입니다. 고객지원에 문의해 주세요.",
            supportReference = "not-ready:deletable=3",
        )

        // 예전 컨트롤러는 무조건 200 "계정이 삭제되었습니다" 를 돌려줬다.
        // 지우지 않았는데 지웠다고 말하면 안 된다.
        mockMvc.perform(delete(PATH))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value(AccountDeletionBlockedException.CODE_NOT_READY))
    }

    @Test
    @DisplayName("응답 본문에 테이블·컬럼 이름이 새지 않는다")
    fun responseBodyHidesSchemaNames() {
        every { authUseCase.deleteAccount(USER_ID) } throws AccountDeletionBlockedException(
            code = AccountDeletionBlockedException.CODE_POLICY_REVIEW,
            message = "보관 정책 확인이 필요한 데이터가 있어 계정 삭제를 바로 진행할 수 없습니다. 고객지원에 문의해 주세요.",
            supportReference = "review-block:competitors_user_id_fkey,comments_user_id_fkey",
        )

        val body = mockMvc.perform(delete(PATH))
            .andExpect(status().isConflict)
            .andReturn().response.contentAsString

        listOf("competitors", "comments", "user_id", "fkey", "supportReference").forEach {
            assert(!body.contains(it)) { "응답 본문에 '$it' 가 새어나갔다: $body" }
        }
    }

    @Test
    @DisplayName("사전 점검 실패도 500 이 아니라 409 로 나간다")
    fun preflightFailureIsConflictNotServerError() {
        every { authUseCase.deleteAccount(USER_ID) } throws AccountDeletionBlockedException(
            code = AccountDeletionBlockedException.CODE_PREFLIGHT_FAILED,
            message = "계정 삭제 요청을 처리하지 못했습니다. 잠시 후 다시 시도하거나 고객지원에 문의해 주세요.",
            supportReference = "preflight-error:IllegalStateException",
        )

        // 일반 Exception 핸들러가 먼저 잡으면 500 이 나간다. advice 순서가 깨졌다는 뜻이다.
        mockMvc.perform(delete(PATH))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value(AccountDeletionBlockedException.CODE_PREFLIGHT_FAILED))
    }

    @Test
    @DisplayName("삭제 요청 상태 조회는 job 상태와 지원 참조값을 반환한다")
    fun deletionStatusReturnsDurableState() {
        every { authUseCase.getAccountDeletionStatus(USER_ID) } returns AccountDeletionStatusResponse(
            state = "DELETION_REQUESTED",
            status = "IN_PROGRESS",
            jobId = 17L,
            requestedAt = null,
            updatedAt = null,
            completedAt = null,
            lastErrorCode = null,
            supportReference = "request:17",
            retryable = false,
        )

        mockMvc.perform(get("$PATH/deletion-status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.state").value("DELETION_REQUESTED"))
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.jobId").value(17))

        verify(exactly = 1) { authUseCase.getAccountDeletionStatus(USER_ID) }
    }
}
