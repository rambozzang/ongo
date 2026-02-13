package com.ongo.common

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
) {
    companion object {
        /**
         * 성공 응답을 생성합니다.
         */
        fun <T> success(data: T): ResponseEntity<ResData<T>> =
            ResponseEntity.ok(ResData(success = true, data = data))

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

