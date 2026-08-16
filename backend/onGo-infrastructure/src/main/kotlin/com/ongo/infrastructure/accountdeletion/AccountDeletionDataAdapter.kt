package com.ongo.infrastructure.accountdeletion

import com.ongo.domain.accountdeletion.AccountDeletionDataPort
import com.ongo.domain.accountdeletion.AccountDeletionJobRepository
import com.ongo.domain.accountdeletion.FkPolicy
import com.ongo.domain.accountdeletion.UserFkPolicy
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Tables.USERS
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 정책 엔진이 승인한 직접 사용자 소유 row를 PostgreSQL에서 정리한다.
 *
 * 테이블명을 코드에 다시 하드코딩하지 않고, 이미 스키마에서 읽어 정책으로 승인된
 * `UserFkPolicy`만 허용한다. 정책 목록에 없는 FK가 있거나 사용자 관계 컬럼이 들어오면
 * 호출 자체를 거부한다.
 */
@Component
class AccountDeletionDataAdapter(
    private val dsl: DSLContext,
    private val jobs: AccountDeletionJobRepository,
) : AccountDeletionDataPort {

    @Transactional
    override fun deleteUserDataAndComplete(
        jobId: Long,
        userId: Long,
        policies: List<UserFkPolicy>,
    ) {
        require(policies.all { it.policy == FkPolicy.DELETE && it.key.localColumns == listOf("user_id") }) {
            "계정 삭제 DB 단계에 승인되지 않은 외래키 정책이 포함되어 있다"
        }

        orderedPolicies(policies).forEach { policy ->
            val table = DSL.table(DSL.name(policy.key.schema, policy.key.table))
            val userColumn = DSL.field(DSL.name(policy.key.localColumns.single()), Long::class.java)
            dsl.deleteFrom(table)
                .where(userColumn.eq(userId))
                .execute()
        }

        // jobs 는 users 를 참조하지 않으므로 users 삭제 후에도 감사 기록을 남길 수 있다.
        dsl.deleteFrom(USERS)
            .where(ID.eq(userId))
            .execute()

        check(jobs.markCompleted(jobId) != null) {
            "계정 삭제 완료 job 을 찾을 수 없다: $jobId"
        }
    }

    /**
     * 정책 테이블 사이에 직접 FK가 있는 경우 자식부터 지운다.
     * 현재 마이그레이션은 대부분 CASCADE지만, 삭제 순서를 DB 옵션에만 기대면 새 NO ACTION
     * 자식이 추가될 때 런타임 장애로 발견된다. 그래프가 순환이면 안전하게 중단한다.
     */
    private fun orderedPolicies(policies: List<UserFkPolicy>): List<UserFkPolicy> {
        val byTable = policies.associateBy { "${it.key.schema}.${it.key.table}" }
        val edges = byTable.keys.associateWith { mutableSetOf<String>() }.toMutableMap()
        val indegree = byTable.keys.associateWith { 0 }.toMutableMap()

        dsl.fetch(
            """
            SELECT child_ns.nspname AS child_schema,
                   child.relname AS child_table,
                   parent_ns.nspname AS parent_schema,
                   parent.relname AS parent_table
              FROM pg_constraint c
              JOIN pg_class child ON child.oid = c.conrelid
              JOIN pg_namespace child_ns ON child_ns.oid = child.relnamespace
              JOIN pg_class parent ON parent.oid = c.confrelid
              JOIN pg_namespace parent_ns ON parent_ns.oid = parent.relnamespace
             WHERE c.contype = 'f'
               AND child_ns.nspname = 'public'
               AND parent_ns.nspname = 'public'
            """.trimIndent(),
        ).forEach { record ->
            val child = "${record.get("child_schema", String::class.java)}.${record.get("child_table", String::class.java)}"
            val parent = "${record.get("parent_schema", String::class.java)}.${record.get("parent_table", String::class.java)}"
            if (child in byTable && parent in byTable && child != parent && edges.getValue(child).add(parent)) {
                indegree[parent] = indegree.getValue(parent) + 1
            }
        }

        val ready = java.util.PriorityQueue<String>().apply {
            indegree.filterValues { it == 0 }.keys.forEach(::add)
        }
        val ordered = mutableListOf<String>()
        while (ready.isNotEmpty()) {
            val current = ready.remove()
            ordered += current
            edges.getValue(current).forEach { parent ->
                val next = indegree.getValue(parent) - 1
                indegree[parent] = next
                if (next == 0) ready.add(parent)
            }
        }

        check(ordered.size == byTable.size) {
            "계정 삭제 대상 테이블의 FK 그래프가 순환한다"
        }
        return ordered.map { byTable.getValue(it) }
    }
}
