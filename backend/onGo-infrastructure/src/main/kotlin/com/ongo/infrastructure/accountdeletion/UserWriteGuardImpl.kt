package com.ongo.infrastructure.accountdeletion

import com.ongo.common.exception.AccountFrozenException
import com.ongo.domain.accountdeletion.AccountDeletionJobRepository
import com.ongo.domain.accountdeletion.UserWriteGuard
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

    override fun requireWritable(userId: Long) {
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
