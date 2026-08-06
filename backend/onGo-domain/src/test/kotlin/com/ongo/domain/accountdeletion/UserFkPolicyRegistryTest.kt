package com.ongo.domain.accountdeletion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 레지스트리 자체의 불변식을 고정한다. DB 없이 돌아간다.
 *
 * 실제 스키마와의 대조는 `AccountDeletionPolicyGuardIT` 가 한다. 여기서는 레지스트리가
 * **스스로 일관된지**만 본다. 둘을 나눈 이유는 이 검사가 컨테이너 없이 빠르게 돌아야
 * 하고, 실패했을 때 원인이 "스키마가 바뀌었다"인지 "레지스트리가 깨졌다"인지 구분되기
 * 때문이다.
 */
class UserFkPolicyRegistryTest {

    @Test
    @DisplayName("키가 중복되면 안 된다 — associateBy 가 조용히 덮어쓴다")
    fun keysAreUnique() {
        val duplicates = UserFkPolicyRegistry.entries
            .groupBy { it.key }
            .filter { it.value.size > 1 }
            .map { "${it.key.constraintName} (${it.value.size}회)" }

        // 조회 맵을 associateBy 로 만들기 때문에 중복 키는 예외 없이 마지막 항목만 남는다.
        // 앞 항목의 정책이 조용히 무시되므로 REVIEW_BLOCK 이 DELETE 로 덮이는 사고가 가능하다.
        assertTrue(duplicates.isEmpty()) {
            "중복된 정책 키: ${duplicates.joinToString()}"
        }

        assertEquals(
            UserFkPolicyRegistry.entries.size,
            UserFkPolicyRegistry.entries.map { it.key }.toSet().size,
        ) { "키 집합 크기가 항목 수와 다르다" }
    }

    @Test
    @DisplayName("모든 항목을 키로 되찾을 수 있어야 한다")
    fun everyEntryIsFindableByItsKey() {
        val unfindable = UserFkPolicyRegistry.entries
            .filter { UserFkPolicyRegistry.find(it.key) == null }
            .map { it.key.constraintName }

        assertTrue(unfindable.isEmpty()) {
            "키로 조회되지 않는 항목: ${unfindable.joinToString()}"
        }
    }

    @Test
    @DisplayName("DELETE 는 user_id 단일 컬럼만 — 관계 참조는 소유가 아니다")
    fun deletableAreSingleUserIdColumn() {
        val violations = UserFkPolicyRegistry.deletable()
            .filterNot { it.key.localColumns == listOf("user_id") }
            .map { "${it.key.constraintName}(${it.key.localColumns.joinToString("+")})" }

        // reviewer_id, owner_id, requester_id 같은 컬럼은 관계를 가리킨다.
        // DELETE 로 올라가면 탈퇴자와 무관한 사용자의 행이 사라진다.
        assertTrue(violations.isEmpty()) {
            "DELETE 인데 user_id 단일 컬럼이 아닌 항목: ${violations.joinToString()}"
        }
    }

    @Test
    @DisplayName("정책과 행 연산이 어긋나면 안 된다")
    fun policyAndRowOperationAgree() {
        val mismatched = UserFkPolicyRegistry.entries.filter {
            when (it.policy) {
                FkPolicy.DELETE -> it.rowOperation != RowOperation.ROW_DELETE
                FkPolicy.REVIEW_BLOCK -> it.rowOperation != RowOperation.ROW_BLOCK
                FkPolicy.PRESERVE_ANONYMIZE -> it.rowOperation == RowOperation.ROW_DELETE
            }
        }.map { "${it.key.constraintName}: ${it.policy}/${it.rowOperation}" }

        assertTrue(mismatched.isEmpty()) {
            "정책과 행 연산이 어긋난 항목: ${mismatched.joinToString()}"
        }
    }

    @Test
    @DisplayName("근거는 소유성으로 적는다 — wip 상태를 근거로 쓰지 않는다")
    fun rationaleIsNotBasedOnWipStatus() {
        // "지금 wip 이라 행이 없다"는 현재 상태이지 정책 근거가 아니다. 기능이 켜지면 사라진다.
        // 근거가 상태에 기대면, 상태가 바뀌었을 때 정책이 조용히 틀린 것이 된다.
        val wipBased = UserFkPolicyRegistry.entries
            .filter { it.policy == FkPolicy.DELETE }
            .filter { it.rationale.contains("wip", ignoreCase = true) }
            .map { it.key.constraintName }

        assertTrue(wipBased.isEmpty()) {
            "DELETE 근거에 wip 상태가 들어간 항목: ${wipBased.joinToString()}. " +
                "누가 그 행을 소유하는지로 적어라"
        }
    }

    @Test
    @DisplayName("모든 항목에 근거가 있어야 한다")
    fun everyEntryHasRationale() {
        val missing = UserFkPolicyRegistry.entries
            .filter { it.rationale.isBlank() }
            .map { it.key.constraintName }

        assertTrue(missing.isEmpty()) {
            "근거 없는 항목: ${missing.joinToString()}"
        }
    }

    @Test
    @DisplayName("결정된 보존 정책이 없으므로 PRESERVE_ANONYMIZE 는 0건")
    fun noPreserveAnonymizeYet() {
        val preserve = UserFkPolicyRegistry.entries
            .filter { it.policy == FkPolicy.PRESERVE_ANONYMIZE }
            .map { it.key.constraintName }

        assertTrue(preserve.isEmpty()) {
            "PRESERVE_ANONYMIZE 항목: ${preserve.joinToString()}. " +
                "보존 정책 승인과 NOT NULL 해제 마이그레이션이 선행돼야 한다"
        }
    }
}
