package com.ongo.application.common

import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 올려둔 오브젝트를 **최대 한 번** 지운다.
 *
 * 업로드가 성공한 뒤 DB 저장이나 커밋이 실패하면 스토리지에는 파일이 남는데 그걸 가리키는
 * 행은 없다. 아무도 찾지 못하는 채로 매달 과금되는 고아다.
 *
 * 정리 경로가 둘이라 멱등성이 필요하다 — 메서드 안에서 던진 예외는 catch 도 타고 롤백
 * 콜백도 탄다. 그래서 호출 횟수가 아니라 **실제 삭제 횟수**를 보장한다. 두 번 지우면
 * 같은 키를 방금 다시 올린 다른 시도의 결과물을 지울 위험이 생긴다.
 *
 * 삭제 실패는 삼킨다. 정리하다 난 오류가 원래 실패 사유를 덮으면 사용자는 진짜 원인을
 * 볼 수 없고, 남은 오브젝트는 저장소 lifecycle 이 걷어간다.
 */
class StorageObjectCleanup(
    private val fileStoragePort: FileStoragePort,
    val key: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val deleted = AtomicBoolean(false)

    fun deleteOnce() {
        if (deleted.compareAndSet(false, true)) {
            runCatching { fileStoragePort.deleteByKey(key) }
                .onFailure { log.error("고아 오브젝트 정리 실패 [key={}]", key, it) }
        }
    }

    /**
     * 트랜잭션이 커밋되지 않으면 지우도록 등록한다.
     *
     * 커밋은 `@Transactional` 메서드가 **반환된 뒤** 프록시에서 일어난다. 그래서 커밋 실패는
     * 메서드 안의 try/catch 로 잡을 수 없고, 이 콜백이 유일한 방어선이다.
     *
     * 동기화가 없는 호출(트랜잭션 밖에서 직접 부르는 경우)에서는 등록할 곳이 없어 아무 것도
     * 하지 않는다. 그때는 호출부의 catch 가 정리를 맡으며, 멱등이라 어느 쪽이든 한 번이다.
     */
    fun deleteIfTransactionRollsBack() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        log.warn("트랜잭션이 커밋되지 않아 오브젝트를 정리한다 [key={}]", key)
                        deleteOnce()
                    }
                }
            },
        )
    }
}
