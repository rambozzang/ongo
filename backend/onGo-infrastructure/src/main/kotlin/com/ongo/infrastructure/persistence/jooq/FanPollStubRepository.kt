package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.fanpoll.*
import org.springframework.stereotype.Repository

@Repository
class FanPollStubRepository : FanPollRepository {
    override fun findByUserId(userId: Long): List<FanPoll> = emptyList()
    override fun findById(id: Long): FanPoll? = null
    override fun findByUserIdAndStatus(userId: Long, status: String): List<FanPoll> = emptyList()
    override fun save(poll: FanPoll): FanPoll = poll.copy(id = 1)
    override fun updateStatus(id: Long, status: String) {}
    override fun deleteById(id: Long) {}
}

@Repository
class PollOptionStubRepository : PollOptionRepository {
    override fun findByPollId(pollId: Long): List<PollOption> = emptyList()
    override fun saveAll(options: List<PollOption>): List<PollOption> = options
    override fun deleteByPollId(pollId: Long) {}
}
