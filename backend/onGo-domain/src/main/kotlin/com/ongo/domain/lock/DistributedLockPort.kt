package com.ongo.domain.lock

interface DistributedLockPort {

    /**
     * 락을 잡고 [block] 을 실행한 뒤 **반드시 해제한다.** 락을 못 잡으면 실행하지 않는다.
     *
     * @return 실행했으면 true, 다른 인스턴스가 락을 쥐고 있어 건너뛰었으면 false
     *
     * 신규 코드는 [tryLock]/[releaseLock] 대신 이걸 쓴다. 두 메서드를 따로 호출하면
     * 획득과 해제가 서로 다른 커넥션에서 일어날 수 있고, PostgreSQL advisory lock 은
     * 세션 락이라 그 경우 해제가 무시되고 락이 커넥션 풀에 남는다.
     */
    fun withLock(lockId: Long, block: () -> Unit): Boolean

    /**
     * 락만 획득한다.
     *
     * **주의: 이 방식은 락을 제대로 해제하지 못한다.** 획득한 커넥션이 풀로 반납된 뒤
     * [releaseLock] 이 다른 커넥션을 꺼내면 `pg_advisory_unlock` 이 false 를 반환하고,
     * 락은 반납된 커넥션이 계속 쥔다. 그 뒤 실행은 락을 못 잡아 배치가 조용히 스킵된다.
     * 남아 있는 사용처를 [withLock] 으로 옮기는 중이며 신규 사용은 금지한다.
     */
    @Deprecated("획득/해제가 다른 커넥션에서 일어나 락이 누수된다. withLock 을 사용할 것", ReplaceWith("withLock(lockId) { }"))
    fun tryLock(lockId: Long): Boolean

    @Deprecated("tryLock 과 짝을 이루는 누수 경로다. withLock 을 사용할 것", ReplaceWith("withLock(lockId) { }"))
    fun releaseLock(lockId: Long)
}
