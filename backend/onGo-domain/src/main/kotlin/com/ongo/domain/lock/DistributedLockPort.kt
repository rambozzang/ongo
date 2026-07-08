package com.ongo.domain.lock

interface DistributedLockPort {
    fun tryLock(lockId: Long): Boolean
    fun releaseLock(lockId: Long)
}
