package com.ongo.api.auth

import com.ongo.common.config.DevOnlyProfiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Profiles
import org.springframework.mock.env.MockEnvironment

/**
 * 인증 없는 관리자 로그인이 **어떤 프로필 조합에서 뜨는지** 고정한다.
 *
 * 2026-08-08 운영에서 dev 프로필이 섞여 `/auth/dev-login` 이 누구에게나 ADMIN 토큰을 줬다.
 * 컨트롤러의 실제 `@Profile` 값을 읽어 판정하므로, 누가 애노테이션을 `"dev", "local"` 로
 * 되돌리면 여기서 깨진다.
 */
class DevAuthControllerProfileTest {

    private val controllerProfiles: Array<String> =
        DevAuthController::class.java.getAnnotation(Profile::class.java).value

    private fun registeredUnder(vararg active: String): Boolean =
        MockEnvironment().apply { setActiveProfiles(*active) }
            .acceptsProfiles(Profiles.of(*controllerProfiles))

    @Test
    @DisplayName("컨트롤러와 보안 공개 경로가 같은 프로필 식을 쓴다")
    fun usesSharedExpression() {
        assertEquals(listOf(DevOnlyProfiles.EXPRESSION), controllerProfiles.toList())
    }

    @Test
    @DisplayName("개발·로컬 단독이면 뜬다")
    fun registeredForDevAndLocal() {
        assertTrue(registeredUnder("dev"))
        assertTrue(registeredUnder("local"))
    }

    @Test
    @DisplayName("prod 가 함께 켜져 있으면 어떤 조합이든 뜨지 않는다")
    fun neverRegisteredWithProd() {
        assertFalse(registeredUnder("prod"))
        assertFalse(registeredUnder("prod", "dev"), "운영에 dev 가 섞이면 관리자 우회가 열린다")
        assertFalse(registeredUnder("dev", "prod"))
        assertFalse(registeredUnder("prod", "local"))
        assertFalse(registeredUnder())
    }
}
