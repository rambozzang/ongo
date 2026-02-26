package com.ongo.application.fanpoll.dto

import com.ongo.domain.fanpoll.FanPoll
import com.ongo.domain.fanpoll.PollOption
import java.math.BigDecimal

data class FanPollResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val type: String,
    val status: String,
    val options: List<PollOptionResponse>,
    val totalVotes: Int,
    val startDate: String?,
    val endDate: String?,
    val createdAt: String,
) {
    companion object {
        fun from(e: FanPoll, options: List<PollOption> = emptyList()) = FanPollResponse(
            id = e.id, title = e.title, description = e.description,
            type = e.type, status = e.status,
            options = options.map { PollOptionResponse.from(it) },
            totalVotes = e.totalVotes,
            startDate = e.startDate?.toString(), endDate = e.endDate?.toString(),
            createdAt = e.createdAt.toString(),
        )
    }
}

data class PollOptionResponse(
    val id: Long,
    val text: String,
    val votes: Int,
    val percentage: BigDecimal,
) {
    companion object {
        fun from(e: PollOption) = PollOptionResponse(
            id = e.id, text = e.text, votes = e.votes, percentage = e.percentage,
        )
    }
}

data class FanPollSummaryResponse(
    val totalPolls: Int,
    val activePolls: Int,
    val totalVotes: Long,
    val avgParticipation: Double,
    val mostPopularPoll: String?,
)

data class CreatePollRequest(
    val title: String,
    val description: String? = null,
    val type: String = "SINGLE",
    val options: List<String>,
    val endDate: String? = null,
)
