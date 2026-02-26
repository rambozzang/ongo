package com.ongo.domain.fanpoll

interface PollOptionRepository {
    fun findByPollId(pollId: Long): List<PollOption>
    fun saveAll(options: List<PollOption>): List<PollOption>
    fun deleteByPollId(pollId: Long)
}
