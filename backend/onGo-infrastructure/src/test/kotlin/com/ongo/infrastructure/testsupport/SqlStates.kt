package com.ongo.infrastructure.testsupport

import java.sql.SQLException

/**
 * 제약 위반을 단언할 때 쓰는 SQLState 유틸.
 *
 * 벤더 예외 클래스(`org.postgresql.util.PSQLException`)를 직접 단언하지 않는다.
 * 스프링 계층 + SQLState 로 계약을 두어 Boot/JDBC 드라이버 패치에 덜 흔들리게 한다.
 *
 * 예외 타입만 넓게(`Exception::class`) 단언하면 SQL 문법 오류나 연결 실패도 통과시킨다.
 * "막혔다"와 "다른 이유로 실패했다"를 구분하려면 SQLState 까지 봐야 한다.
 */
object SqlStates {

    /** 23505 unique_violation */
    const val UNIQUE_VIOLATION = "23505"

    /** 23503 foreign_key_violation */
    const val FOREIGN_KEY_VIOLATION = "23503"

    /** 원인 체인을 훑어 첫 SQLState 를 찾는다. 벤더 예외 클래스에 직접 의존하지 않기 위함이다. */
    fun of(throwable: Throwable): String? {
        var cause: Throwable? = throwable
        while (cause != null) {
            (cause as? SQLException)?.sqlState?.let { return it }
            if (cause.cause === cause) return null
            cause = cause.cause
        }
        return null
    }
}
