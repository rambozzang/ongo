package com.ongo.infrastructure.accountdeletion

import com.ongo.common.exception.AccountFrozenException
import com.ongo.domain.accountdeletion.AccountDeletionJobRepository
import com.ongo.domain.accountdeletion.SystemWritePathRegistry
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.accountdeletion.WriteOrigin
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * `users.deletion_state` 를 읽어 쓰기 허용 여부를 판정한다.
 *
 * 상태를 읽지 못하면 **막는다.** 조회 실패나 사용자 부재를 통과시키면, 하필 삭제가
 * 진행 중인 계정에 데이터가 들어오는 창이 열린다. 정상 사용자가 일시적 DB 오류로
 * 쓰기를 거부당하는 쪽이 낫다.
 */
@Component
class UserWriteGuardImpl(
    private val repository: AccountDeletionJobRepository,
) : UserWriteGuard {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun requireWritable(userId: Long, origin: WriteOrigin, systemPath: String?) {
        if (origin == WriteOrigin.SYSTEM_RECONCILIATION) {
            // 우회는 등록된 경로만 할 수 있다. 미등록 우회를 통과시키면 동결이 무의미해진다.
            // 여기서도 fail-closed 다 — 근거 없는 우회는 예외로 막는다.
            require(systemPath != null && SystemWritePathRegistry.isRegistered(systemPath)) {
                "등록되지 않은 시스템 경로가 동결 우회를 시도했다: $systemPath. " +
                    "SystemWritePathRegistry 에 근거와 함께 등록해라"
            }
            // 우회는 흔한 일이 아니어야 한다. 남겨서 추적 가능하게 한다.
            log.info(
                "동결 우회(시스템 정합성): path={} userId={} 근거={}",
                systemPath, userId, SystemWritePathRegistry.rationaleFor(systemPath),
            )
            return
        }

        val state = try {
            repository.findDeletionState(userId)
        } catch (e: Exception) {
            log.error("계정 상태 조회 실패. 쓰기를 막는다. userId={}", userId, e)
            throw AccountFrozenException("계정 상태를 확인할 수 없어 요청을 처리하지 못했습니다.")
        }

        if (state == null) {
            // 사용자가 없거나 상태가 비어 있다. 어느 쪽이든 판정 근거가 없으므로 막는다.
            log.warn("계정 상태를 찾을 수 없다. 쓰기를 막는다. userId={}", userId)
            throw AccountFrozenException("계정 상태를 확인할 수 없어 요청을 처리하지 못했습니다.")
        }

        if (!state.allowsWrites()) {
            throw AccountFrozenException()
        }
    }
}
