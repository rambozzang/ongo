package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.accountdeletion.UserFkKey
import com.ongo.domain.accountdeletion.UserFkScanner
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

/**
 * `pg_constraint` 를 읽어 `users` 참조 외래키를 조사한다.
 *
 * `information_schema` 를 쓰지 않는다. 다중 컬럼 외래키에서 행이 중복돼 개수가 틀어진다.
 */
@Repository
class UserFkJooqScanner(
    private val dsl: DSLContext,
) : UserFkScanner {

    override fun actualUserFks(): List<UserFkKey> =
        dsl.fetch(
            """
            SELECT
                n.nspname AS schema_name,
                c.conname AS constraint_name,
                t.relname AS table_name,
                (SELECT string_agg(a.attname, ',' ORDER BY k.ord)
                   FROM unnest(c.conkey) WITH ORDINALITY k(attnum, ord)
                   JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum) AS local_cols,
                (SELECT string_agg(a.attname, ',' ORDER BY k.ord)
                   FROM unnest(c.confkey) WITH ORDINALITY k(attnum, ord)
                   JOIN pg_attribute a ON a.attrelid = c.confrelid AND a.attnum = k.attnum) AS ref_cols
            FROM pg_constraint c
            JOIN pg_class t     ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE c.contype = 'f'
              AND c.confrelid = 'public.users'::regclass
            """.trimIndent()
        ).map {
            UserFkKey(
                schema = it.get("schema_name", String::class.java),
                constraintName = it.get("constraint_name", String::class.java),
                table = it.get("table_name", String::class.java),
                localColumns = it.get("local_cols", String::class.java).split(","),
                referencedColumns = it.get("ref_cols", String::class.java).split(","),
            )
        }

    override fun countRowsFor(key: UserFkKey, userId: Long): Long {
        // 다중 컬럼 외래키는 어느 컬럼이 사용자인지 단정할 수 없다. 세지 않고 막는 편이 안전하다.
        // 호출자(preflight)는 0 이 아니면 차단하므로, 여기서 보수적으로 1 을 돌려준다.
        if (key.localColumns.size != 1) return 1

        // 식별자는 pg_constraint 에서 온 값이지만 문자열로 이어 붙이지 않고
        // jOOQ 의 이름 렌더링을 거친다. 인용·이스케이프를 직접 다루지 않기 위해서다.
        val table = DSL.table(DSL.name(key.schema, key.table))
        val column = DSL.field(DSL.name(key.localColumns.single()), Long::class.java)

        return dsl.selectCount()
            .from(table)
            .where(column.eq(userId))
            .fetchOne(0, Long::class.java) ?: 0
    }
}
