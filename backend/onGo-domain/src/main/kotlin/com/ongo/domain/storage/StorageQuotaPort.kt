package com.ongo.domain.storage

interface StorageQuotaPort {
    fun calculateUserStorageBytes(userId: Long): Long
}
