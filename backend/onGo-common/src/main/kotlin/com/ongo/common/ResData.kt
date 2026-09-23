package com.ongo.common

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.http.ResponseEntity

/**
 * API 응답 데이터를 담는 공통 클래스
 *
 * @param success 요청 성공 여부
 * @param message 응답 메시지 (주로 성공/알림 메시지)
 * @param data 실제 응답 데이터
 * @param error 에러 메시지 (성공 시 null)
 */
data class ResData<T>(
    var success: Boolean = true,
    val message: String? = null,
    var data: T? = null,
    val error: String? = null,
    /**
     * 분석 API 가 요금제 기간 한도로 조회 기간을 잘랐을 때만 실린다.
     * null 이면 **아예 내보내지 않는다** — 분석 전용 정보가 모든 API 응답에 `"periodLimit": null`
     * 로 새어 나오지 않게 한다.
     */
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val periodLimit: PeriodLimitMetadata? = null,
) {
    companion object {
        /**
         * 성공 응답을 생성합니다.
         */
        fun <T> success(data: T): ResponseEntity<ResData<T>> =
            ResponseEntity.ok(ResData(success = true, data = data))

        /** Success response with the applied analytics period. */
        fun <T> success(data: T, periodLimit: PeriodLimitMetadata): ResponseEntity<ResData<T>> =
            ResponseEntity.ok(ResData(success = true, data = data, periodLimit = periodLimit))

        /**
         * 성공 응답을 메시지와 함께 생성합니다.
         */
        fun <T> success(data: T, msg: String): ResponseEntity<ResData<T>> =
            ResponseEntity.ok(ResData(success = true, data = data, message = msg))

        /**
         * 실패 응답을 생성합니다. (비즈니스 로직 상의 실패)
         */
        fun fail(msg: String): ResponseEntity<ResData<Nothing>> =
            ResponseEntity.ok(ResData(success = false, error = msg))

        /**
         * 에러 응답을 생성합니다. (시스템 오류 등)
         */
        fun error(msg: String): ResponseEntity<ResData<Nothing>> =
            ResponseEntity.ok(ResData(success = false, error = msg))

        /**
         * 실패 응답을 데이터와 함께 생성합니다.
         */
        fun <T> fail(data: T, msg: String): ResponseEntity<ResData<T>> =
            ResponseEntity.ok(ResData(success = false, data = data, error = msg))
    }
}
