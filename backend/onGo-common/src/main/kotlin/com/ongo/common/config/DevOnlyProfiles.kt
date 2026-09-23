package com.ongo.common.config

/**
 * 개발 전용 기능(인증 없는 관리자 로그인 등)을 켜는 **유일한** 프로필 식.
 *
 * `"dev", "local"` 만 보던 때는 운영 인스턴스에 dev 프로필이 **하나 섞이는 것만으로**
 * 누구나 ADMIN 토큰을 받았다(2026-08-08 운영에서 실제 확인). prod 가 함께 켜져 있으면
 * 무조건 끈다 — 프로필 조합이 틀려도 관리자 우회는 열리지 않는다.
 *
 * 컨트롤러 등록(`@Profile`)과 보안 공개 경로가 **같은 상수**를 써야 한다. 둘이 갈라지면
 * "컨트롤러는 없는데 경로는 공개" 또는 그 반대가 된다.
 */
object DevOnlyProfiles {
    const val EXPRESSION = "(dev | local) & !prod"

    /** 운영 기동 검증기가 거부하는 프로필. prod 와 함께 켜져 있으면 기동하지 않는다. */
    val FORBIDDEN_WITH_PROD = setOf("dev", "local")
}
