package com.ongo.application.fanpoll

import com.ongo.application.fanpoll.dto.*
import com.ongo.domain.fanpoll.FanPollRepository
import com.ongo.domain.fanpoll.PollOptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class FanPollUseCase(
    private val pollRepo: FanPollRepository,
    private val optionRepo: PollOptionRepository,
) {
    fun getPolls(userId: Long, status: String?): List<FanPollResponse> {
        val polls = if (status != null) pollRepo.findByUserIdAndStatus(userId, status)
        else pollRepo.findByUserId(userId)
        return polls.map { poll ->
            val options = optionRepo.findByPollId(poll.id)
            FanPollResponse.from(poll, options)
        }
    }

    fun getPoll(id: Long): FanPollResponse? {
        val poll = pollRepo.findById(id) ?: return null
        val options = optionRepo.findByPollId(id)
        return FanPollResponse.from(poll, options)
    }

    @Transactional
    fun closePoll(id: Long) = pollRepo.updateStatus(id, "CLOSED")

    @Transactional
    fun deletePoll(id: Long) {
        optionRepo.deleteByPollId(id)
        pollRepo.deleteById(id)
    }

    fun getSummary(userId: Long): FanPollSummaryResponse {
        val polls = pollRepo.findByUserId(userId)
        val active = polls.filter { it.status == "ACTIVE" }
        return FanPollSummaryResponse(
            totalPolls = polls.size,
            activePolls = active.size,
            totalVotes = polls.sumOf { it.totalVotes.toLong() },
            avgParticipation = if (polls.isNotEmpty()) polls.sumOf { it.totalVotes } / polls.size.toDouble() else 0.0,
            mostPopularPoll = polls.maxByOrNull { it.totalVotes }?.title,
        )
    }
}
