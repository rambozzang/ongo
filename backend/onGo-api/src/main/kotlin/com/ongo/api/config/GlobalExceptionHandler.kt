package com.ongo.api.config

import com.ongo.common.ResData
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.RateLimitExceededException
import com.ongo.common.exception.TokenExpiredException
import com.ongo.common.exception.UnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(e: UnauthorizedException): ResponseEntity<ResData<Nothing>> {
        log.warn("Unauthorized: {}", e.message)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ResData(success = false, error = e.message))
    }

    @ExceptionHandler(TokenExpiredException::class)
    fun handleTokenExpired(e: TokenExpiredException): ResponseEntity<ResData<Nothing>> {
        log.warn("Token expired: {}", e.message)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ResData(success = false, error = e.message))
    }

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(e: ForbiddenException): ResponseEntity<ResData<Nothing>> {
        log.warn("Forbidden: {}", e.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ResData(success = false, error = e.message))
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(e: NotFoundException): ResponseEntity<ResData<Nothing>> {
        log.warn("Not found: {}", e.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ResData(success = false, error = e.message))
    }

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimitExceeded(e: RateLimitExceededException): ResponseEntity<ResData<Nothing>> {
        log.warn("Rate limit exceeded: {}", e.message)
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ResData(success = false, error = e.message ?: "요청이 너무 많습니다"))
    }

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(e: BusinessException): ResponseEntity<ResData<Nothing>> {
        log.warn("Business exception [{}]: {}", e.code, e.message)
        return ResponseEntity.badRequest()
            .body(ResData(success = false, error = e.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ResData<Nothing>> {
        val errors = e.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation error: {}", errors)
        return ResponseEntity.badRequest()
            .body(ResData(success = false, error = errors))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ResData<Nothing>> {
        log.warn("Illegal argument: {}", e.message)
        return ResponseEntity.badRequest()
            .body(ResData(success = false, error = e.message ?: "잘못된 요청입니다"))
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<ResData<Nothing>> {
        log.warn("Illegal state: {}", e.message)
        return ResponseEntity.badRequest()
            .body(ResData(success = false, error = e.message ?: "잘못된 상태입니다"))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(e: Exception): ResponseEntity<ResData<Nothing>> {
        log.error("Unexpected error", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ResData(success = false, error = "서버 오류가 발생했습니다"))
    }
}
