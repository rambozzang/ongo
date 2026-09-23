package com.ongo.infrastructure.ai

import com.ongo.application.ai.economics.AiSpendContext
import com.ongo.application.ai.economics.AiUnitEconomics
import com.ongo.common.enums.AiProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.CallAdvisor
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt

/**
 * 모든 채팅 호출의 원가 집행. **제공자에게 요청이 나가기 전에** 막아야 하는 것들을 고정한다.
 */
class AiCostGuardAdvisorTest {

    private val economics = AiUnitEconomics()
    private val advisor = AiCostGuardAdvisor(AiProvider.QWEN, economics)

    /** 다음 단계(= 실제 모델)가 불렸는지, 무엇을 받았는지 기록하는 가짜 체인. */
    private class RecordingChain(private val usage: DefaultUsage?) : CallAdvisorChain {
        var received: ChatClientRequest? = null
        override fun nextCall(request: ChatClientRequest): ChatClientResponse {
            received = request
            val metadata = ChatResponseMetadata.builder().apply { usage?.let { usage(it) } }.build()
            return ChatClientResponse(ChatResponse(listOf(Generation(AssistantMessage("ok"))), metadata), emptyMap())
        }
        override fun getCallAdvisors(): List<CallAdvisor> = emptyList()
        override fun copy(after: CallAdvisor): CallAdvisorChain = this
    }

    private fun request(text: String) = ChatClientRequest(Prompt(UserMessage(text)), emptyMap())

    @Test
    @DisplayName("원가 예산 없이 부르면 모델에 요청을 보내지 않고 거부한다")
    fun rejectsWithoutBudget() {
        val chain = RecordingChain(DefaultUsage(10, 10))

        assertThrows(AiBudgetMissingException::class.java) { advisor.adviseCall(request("안녕"), chain) }
        assertNull(chain.received, "예산 없이 모델을 불렀다 — 크레딧 없는 유료 호출")
    }

    @Test
    @DisplayName("남은 예산으로 살 수 있는 만큼만 출력을 허락한다")
    fun capsOutputToBudget() {
        val chain = RecordingChain(DefaultUsage(100, 100))
        val budget = economics.budgetKrw(2)

        AiSpendContext.withBudget(budget, "test") { advisor.adviseCall(request("짧은 요청"), chain) }

        val maxTokens = chain.received!!.prompt().options!!.maxTokens!!
        val input = economics.estimateInputTokens("짧은 요청".toByteArray().size.toLong())
        assertEquals(economics.affordableOutputTokens(AiProvider.QWEN, input, budget).toInt(), maxTokens)
        assertTrue(economics.llmCostKrw(AiProvider.QWEN, input, maxTokens.toLong()) <= budget, "허락한 최악 원가가 예산을 넘는다")
    }

    @Test
    @DisplayName("예산이 넉넉해도 기본 상한(4096)을 넘겨 허락하지 않는다")
    fun neverExceedsDefaultCap() {
        val chain = RecordingChain(DefaultUsage(100, 100))

        AiSpendContext.withBudget(economics.budgetKrw(1_000), "test") { advisor.adviseCall(request("요청"), chain) }

        assertEquals(AiCostGuardAdvisor.DEFAULT_MAX_OUTPUT_TOKENS, chain.received!!.prompt().options!!.maxTokens)
    }

    /** 입력이 너무 커서 쓸 만한 답을 살 수 없으면 제공자 요금이 나가기 전에 멈춘다. */
    @Test
    @DisplayName("입력만으로 예산이 차면 모델을 부르지 않는다")
    fun refusesWhenInputAlonePaysOut() {
        val chain = RecordingChain(DefaultUsage(10, 10))
        val huge = "가".repeat(20_000)

        assertThrows(AiBudgetExceededException::class.java) {
            AiSpendContext.withBudget(economics.budgetKrw(2), "test") { advisor.adviseCall(request(huge), chain) }
        }
        assertNull(chain.received)
    }

    @Test
    @DisplayName("실제 사용량으로 예산을 깎아, 한 예산 안의 두 번째 호출은 남은 만큼만 쓴다")
    fun recordsMeasuredUsage() {
        val chain = RecordingChain(DefaultUsage(1_000, 500))
        val limit = economics.budgetKrw(10)
        var spentAfterFirst = 0.0
        var secondMax = 0

        AiSpendContext.withBudget(limit, "test") {
            advisor.adviseCall(request("첫 요청"), chain)
            spentAfterFirst = AiSpendContext.current()!!.spentKrw
            advisor.adviseCall(request("두 번째"), chain)
            secondMax = chain.received!!.prompt().options!!.maxTokens!!
        }

        assertEquals(economics.llmCostKrw(AiProvider.QWEN, 1_000, 500), spentAfterFirst, 1e-9)
        val input = economics.estimateInputTokens("두 번째".toByteArray().size.toLong())
        assertEquals(economics.affordableOutputTokens(AiProvider.QWEN, input, limit - spentAfterFirst).coerceAtMost(4096).toInt(), secondMax)
    }

    /** 사용량을 보고하지 않은 응답(0/0)은 공짜가 아니라 모르는 것이다 — 허락한 최악값으로 깎는다. */
    @Test
    @DisplayName("사용량 보고가 없으면 최악값으로 깎는다")
    fun unknownUsageCountsAsWorstCase() {
        val chain = RecordingChain(DefaultUsage(0, 0))
        var spent = 0.0

        AiSpendContext.withBudget(economics.budgetKrw(3), "test") {
            advisor.adviseCall(request("요청"), chain)
            spent = AiSpendContext.current()!!.spentKrw
        }

        val maxTokens = chain.received!!.prompt().options!!.maxTokens!!.toLong()
        val input = economics.estimateInputTokens("요청".toByteArray().size.toLong())
        assertEquals(economics.llmCostKrw(AiProvider.QWEN, input, maxTokens), spent, 1e-9)
    }

    @Test
    @DisplayName("안쪽 예산은 바깥 예산의 남은 양을 넘지 못하고, 쓴 만큼 바깥에 반영된다")
    fun nestedBudgetsNeverWiden() {
        AiSpendContext.withBudget(5.0, "outer") {
            AiSpendContext.withBudget(100.0, "inner") {
                assertEquals(5.0, AiSpendContext.current()!!.limitKrw)
                AiSpendContext.current()!!.record(2.0)
            }
            assertEquals(2.0, AiSpendContext.current()!!.spentKrw)
        }
        assertNull(AiSpendContext.current())
    }
}
