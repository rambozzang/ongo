package com.ongo.common.util

import com.ongo.common.exception.BusinessException

/**
 * Safe enum parsing utilities to avoid raw valueOf() calls
 * that would throw unhandled IllegalArgumentException.
 */
inline fun <reified T : Enum<T>> safeValueOf(value: String): T? =
    enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }

inline fun <reified T : Enum<T>> safeValueOfOrThrow(value: String): T =
    safeValueOf<T>(value)
        ?: throw BusinessException(
            "INVALID_ENUM_VALUE",
            "유효하지 않은 값입니다: '$value'. 허용 값: ${enumValues<T>().joinToString { it.name }}"
        )
