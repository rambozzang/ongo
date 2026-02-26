package com.ongo.domain.fanpoll

interface FanPollRepository {
    fun findByUserId(userId: Long): List<FanPoll>
    fun findById(id: Long): FanPoll?
    fun findByUserIdAndStatus(userId: Long, status: String): List<FanPoll>
    fun save(poll: FanPoll): FanPoll
    fun updateStatus(id: Long, status: String)
    fun deleteById(id: Long)
}
