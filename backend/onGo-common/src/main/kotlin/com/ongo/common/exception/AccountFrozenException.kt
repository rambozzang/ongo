package com.ongo.common.exception

/**
 * 삭제 요청으로 동결된 계정에 쓰기를 시도했을 때.
 *
 * 동결 중 허용되는 것은 삭제 요청·토큰 갱신·로그아웃·상태 조회뿐이다.
 * 토큰 갱신이 허용된다고 해서 **쓰기가 재개되는 것은 아니다** — 갱신은 세션을 잇는 것이지
 * 게이트를 푸는 것이 아니다.
 */
class AccountFrozenException(
    message: String = "계정 삭제가 요청되어 변경할 수 없습니다.",
) : RuntimeException(message) {

    companion object {
        const val CODE = "ACCOUNT_FROZEN"
    }
}
