package com.ongo.infrastructure.ai

import com.ongo.application.ai.economics.AiSpendContext
import com.ongo.application.ai.economics.AiUnitEconomics
import com.ongo.common.enums.AiProvider
import com.ongo.common.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.CallAdvisor
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain
import org.springframework.ai.chat.metadata.Usage
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.core.Ordered

/**
 * **모든 채팅 모델 호출이 받은 크레딧의 원가 예산 안에서만 실행되게 한다.** "무조건 수익" 의 집행 지점이다.
 *
 * 1. 예산([AiSpendContext])이 열려 있지 않으면 거부한다 — 크레딧 없이 유료 모델을 부르는 길이 없다.
 * 2. 입력 토큰을 **많게** 추정하고, 남은 예산으로 살 수 있는 만큼만 출력(`maxTokens`)을 허락한다.
 *    출력은 정확히 막히므로 한 호출의 원가는 예산을 넘지 못한다.
 * 3. 예산으로는 쓸 만한 답(최소 [MIN_USEFUL_OUTPUT_TOKENS])을 살 수 없으면 **부르지 않는다.**
 *    제공자 요금이 나가기 전에 멈추므로, 호출부의 크레딧 환불과 함께 사용자·회사 모두 손해가 없다.
 * 4. 끝나면 제공자가 보고한 실제 사용량으로 예산을 깎는다. 보고가 없으면 최악값(추정 입력 + 허락한 출력)으로 깎는다.
 *
 * 추정·허용 계산은 [AiUnitEconomics] 에 있다 — "모든 기능이 이익인가" 를 검사하는 테스트와 같은 계산을 쓴다.
 */
class AiCostGuardAdvisor(
    private val provider: AiProvider,
    private val economics: AiUnitEconomics,
    private val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
) : CallAdvisor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getName(): String = "AiCostGuardAdvisor"

    /** 모델 호출 바로 앞에서 돈다. 다른 가로채기가 덧붙인 내용까지 입력으로 센다. */
    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE - 1_000

    override fun adviseCall(request: ChatClientRequest, chain: CallAdvisorChain): ChatClientResponse {
        val budget = AiSpendContext.current()
            ?: throw AiBudgetMissingException(provider).also {
                log.error("원가 예산 없이 AI 호출이 시도됐다 — 크레딧을 받지 않는 경로다. provider={}", provider, it)
            }

        val plan = plan(request.prompt(), budget.remainingKrw)
        val guarded = request.mutate().prompt(withMaxTokens(request.prompt(), plan.maxOutputTokens)).build()
        val response = chain.nextCall(guarded)

        val cost = measuredCostKrw(response.chatResponse()?.metadata?.usage) ?: plan.worstCaseKrw
        budget.record(cost)
        if (budget.spentKrw > budget.limitKrw * OVERRUN_TOLERANCE) {
            // 입력 추정이 틀린 경우다. 출력은 막혀 있으므로 차이는 입력 추정 오차뿐이다 — 단가표를 다시 볼 신호.
            log.error(
                "AI 원가가 예산을 넘었다. label={} provider={} spent={}원 limit={}원",
                budget.label, provider, "%.2f".format(budget.spentKrw), "%.2f".format(budget.limitKrw),
            )
        }
        return response
    }

    internal data class Plan(val estimatedInputTokens: Long, val maxOutputTokens: Int, val worstCaseKrw: Double)

    internal fun plan(prompt: Prompt, remainingKrw: Double): Plan {
        val bytes = prompt.instructions.sumOf { (it.text ?: "").toByteArray(Charsets.UTF_8).size.toLong() }
        val inputTokens = economics.estimateInputTokens(bytes)
        val affordable = economics.affordableOutputTokens(provider, inputTokens, remainingKrw)
        val requested = (prompt.options?.maxTokens ?: maxOutputTokens).toLong()
        val allowed = minOf(affordable, requested, maxOutputTokens.toLong()).toInt()
        if (allowed < AiUnitEconomics.MIN_USEFUL_OUTPUT_TOKENS) {
            throw AiBudgetExceededException(provider, inputTokens, remainingKrw)
        }
        return Plan(inputTokens, allowed, economics.llmCostKrw(provider, inputTokens, allowed.toLong()))
    }

    private fun measuredCostKrw(usage: Usage?): Double? {
        val prompt = usage?.promptTokens ?: return null
        val completion = usage.completionTokens ?: return null
        // 사용량을 보고하지 않은 응답은 0/0 으로 온다(Spring AI EmptyUsage). 공짜가 아니라 모르는 것이다.
        if (prompt <= 0 && completion <= 0) return null
        return economics.llmCostKrw(provider, prompt.toLong(), completion.toLong())
    }

    companion object {
        const val DEFAULT_MAX_OUTPUT_TOKENS = AiUnitEconomics.AI_MAX_OUTPUT_TOKENS
        private const val OVERRUN_TOLERANCE = 1.0

        internal fun withMaxTokens(prompt: Prompt, maxTokens: Int): Prompt {
            val options = (prompt.options?.mutate() ?: ChatOptions.builder()).maxTokens(maxTokens).build()
            return prompt.mutate().chatOptions(options).build()
        }
    }
}

/** 크레딧을 받지 않은 경로에서 AI 를 부르려 했다. 코드 결함이므로 사용자에게는 일반 문구를 보인다. */
class AiBudgetMissingException(provider: AiProvider) : BusinessException(
    "AI_BUDGET_MISSING",
    "AI 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요. (${provider.name})",
)

/** 받은 크레딧으로 이 입력을 처리할 수 없다. 제공자를 부르기 전에 멈췄으므로 요금은 나가지 않았다. */
class AiBudgetExceededException(provider: AiProvider, inputTokens: Long, remainingKrw: Double) : BusinessException(
    "AI_INPUT_TOO_LARGE",
    "입력 내용이 너무 길어 이 기능으로 처리할 수 없습니다. 내용을 줄여 다시 시도해 주세요.",
) {
    val detail = "provider=${provider.name} inputTokens≈$inputTokens remaining=${"%.2f".format(remainingKrw)}원"
}
