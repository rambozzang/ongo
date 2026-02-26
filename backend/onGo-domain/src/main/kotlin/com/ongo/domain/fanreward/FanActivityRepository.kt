package com.ongo.domain.fanreward

interface FanActivityRepository {
    fun findByUserId(userId: Long): List<FanActivity>
    fun save(activity: FanActivity): FanActivity
}
