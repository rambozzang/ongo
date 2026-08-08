package com.ongo.common.enums

enum class ScheduleStatus {
    SCHEDULED,
    PROCESSING,
    PUBLISHED,
    /** 일부 플랫폼만 게시되었거나 게시 결과 확인이 필요한 상태 */
    PARTIALLY_PUBLISHED,
    /** 외부 호출 결과를 확인할 수 없어 재전송하면 안 되는 상태 */
    UNCONFIRMED,
    FAILED,
    CANCELLED,
}
