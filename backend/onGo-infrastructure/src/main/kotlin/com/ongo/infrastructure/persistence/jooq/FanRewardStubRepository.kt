package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.fanreward.*
import org.springframework.stereotype.Repository

@Repository
class FanRewardStubRepository : FanRewardRepository {
    override fun findById(id: Long): FanReward? = null
    override fun findByUserId(userId: Long): List<FanReward> = emptyList()
    override fun findByCategory(userId: Long, category: String): List<FanReward> = emptyList()
    override fun save(reward: FanReward): FanReward = reward.copy(id = 1)
    override fun deleteById(id: Long) {}
}

@Repository
class FanActivityStubRepository : FanActivityRepository {
    override fun findByUserId(userId: Long): List<FanActivity> = emptyList()
    override fun save(activity: FanActivity): FanActivity = activity.copy(id = 1)
}
