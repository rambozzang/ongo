package com.ongo.common.exception

/**
 * 계정 삭제가 정책 때문에 진행되지 않았을 때.
 *
 * **아무것도 지우지 않은 상태**에서만 던진다. 부분 삭제 후 이 예외가 나오면 안 된다.
 *
 * 사용자 응답에는 [code] 와 [message] 만 나간다. 어떤 테이블·컬럼이 막았는지는
 * [supportReference] 에 담아 **로그와 지원 조회용으로만** 쓴다. 스키마 구조를 응답으로
 * 흘리면 그 자체가 정보 노출이고, 테이블 이름이 바뀔 때 클라이언트가 깨진다.
 */
class AccountDeletionBlockedException(
    /** 클라이언트가 분기할 수 있는 안정적인 코드. 사람이 읽는 문구와 분리한다. */
    val code: String,
    message: String,
    /** 내부 진단용. 응답에 넣지 않는다. */
    val supportReference: String? = null,
) : RuntimeException(message) {

    companion object {
        /** 판단이 끝나지 않은 데이터를 사용자가 가지고 있다. */
        const val CODE_POLICY_REVIEW = "ACCOUNT_DELETION_BLOCKED_POLICY_REVIEW"

        /** 정책상 지울 수 있으나 처리 절차가 아직 없다. */
        const val CODE_NOT_READY = "ACCOUNT_DELETION_NOT_READY"

        /** 분류되지 않은 외래키가 있어 전역 차단됐다. */
        const val CODE_UNCLASSIFIED = "ACCOUNT_DELETION_BLOCKED_UNCLASSIFIED"

        /** 사전 점검 자체가 실패했다. fail-closed 로 막는다. */
        const val CODE_PREFLIGHT_FAILED = "ACCOUNT_DELETION_PREFLIGHT_FAILED"
    }
}
