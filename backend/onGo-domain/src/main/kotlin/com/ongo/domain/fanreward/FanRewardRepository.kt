package com.ongo.domain.fanreward

interface FanRewardRepository {
    fun findById(id: Long): FanReward?
    fun findByUserId(userId: Long): List<FanReward>
    fun findByCategory(userId: Long, category: String): List<FanReward>
    fun save(reward: FanReward): FanReward
    fun deleteById(id: Long)
}
