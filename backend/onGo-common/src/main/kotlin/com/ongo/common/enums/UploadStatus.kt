package com.ongo.common.enums

enum class UploadStatus {
    DRAFT,
    UPLOADING,
    PROCESSING,
    REVIEW,
    PUBLISHED,
    FAILED,
    REJECTED,
    IMPORTING,
    IMPORT_FAILED,
    /** 외부 API 호출이 끊겨 게시 여부를 확인할 수 없는 상태 */
    UNCONFIRMED,
    /** 여러 플랫폼 중 일부만 게시된 상태 */
    PARTIALLY_PUBLISHED,
    /** 사용자가 예약 게시를 취소해 외부 전송을 시작하지 않은 상태 */
    CANCELLED,
}
