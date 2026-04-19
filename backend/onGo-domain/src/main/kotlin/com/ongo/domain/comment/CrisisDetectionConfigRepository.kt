package com.ongo.domain.comment

interface CrisisDetectionConfigRepository {
    fun findByUserId(userId: Long): CrisisDetectionConfig?
    fun save(config: CrisisDetectionConfig): CrisisDetectionConfig
    fun update(config: CrisisDetectionConfig): CrisisDetectionConfig
}
