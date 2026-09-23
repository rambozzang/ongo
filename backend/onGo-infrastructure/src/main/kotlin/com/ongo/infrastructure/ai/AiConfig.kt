package com.ongo.infrastructure.ai

import com.ongo.application.ai.economics.AiPriceBook
import com.ongo.application.ai.economics.AiUnitEconomics
import com.ongo.common.enums.AiProvider
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.google.genai.GoogleGenAiChatModel
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class AiConfig {

    @Bean
    @Primary
    @Qualifier("anthropicChatClient")
    fun anthropicChatClient(anthropicChatModel: AnthropicChatModel, economics: AiUnitEconomics): ChatClient =
        guarded(ChatClient.builder(anthropicChatModel), AiProvider.CLAUDE, economics)

    @Bean
    @Qualifier("openaiChatClient")
    fun openaiChatClient(openAiChatModel: OpenAiChatModel, economics: AiUnitEconomics): ChatClient =
        guarded(ChatClient.builder(openAiChatModel), AiProvider.OPENAI, economics)

    @Bean
    @Qualifier("geminiChatClient")
    @ConditionalOnProperty(prefix = "spring.ai.google.genai", name = ["enabled"], havingValue = "true")
    fun geminiChatClient(geminiChatModel: GoogleGenAiChatModel, economics: AiUnitEconomics): ChatClient =
        guarded(ChatClient.builder(geminiChatModel), AiProvider.GEMINI, economics)

    // --- DashScope (Alibaba Cloud Model Studio) ---

    /**
     * **모든 채팅 클라이언트는 원가 가로채기를 거친다.** 크레딧 예산 없이 부르거나 예산을 넘는 출력을
     * 요청하는 길을 여기서 닫는다([AiCostGuardAdvisor]).
     */
    private fun guarded(builder: ChatClient.Builder, provider: AiProvider, economics: AiUnitEconomics): ChatClient =
        builder.defaultAdvisors(AiCostGuardAdvisor(provider, economics)).build()

    private fun dashScopeChatClient(
        apiKey: String,
        baseUrl: String,
        modelName: String,
        provider: AiProvider,
        economics: AiUnitEconomics,
    ): ChatClient {
        val options = OpenAiChatOptions.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .model(modelName)
            .temperature(0.7)
            .maxTokens(AiCostGuardAdvisor.DEFAULT_MAX_OUTPUT_TOKENS)
            /*
             * **생각 모드를 끈다.** qwen3.5-plus 는 기본으로 켜져 있어, 제목·해시태그 같은 요청에도 보이지 않는
             * 추론 토큰이 수천 개씩 **출력 요금**으로 청구됐다. 우리 기능은 형식이 정해진 생성이라 추론이 필요 없다.
             * 단가표(AiPriceBook)는 꺼진 상태를 전제로 한다. 기본이 꺼진 모델(kimi 등)에는 영향이 없다.
             */
            .extraBody(mapOf("enable_thinking" to false))
            .build()
        val chatModel = OpenAiChatModel.builder()
            .options(options)
            .build()
        return guarded(ChatClient.builder(chatModel), provider, economics)
    }

    @Bean
    @Qualifier("qwenChatClient")
    fun qwenChatClient(
        @Value("\${dashscope.api-key}") apiKey: String,
        @Value("\${dashscope.base-url}") baseUrl: String,
        economics: AiUnitEconomics,
    ): ChatClient = dashScopeChatClient(apiKey, baseUrl, AiPriceBook.llm(AiProvider.QWEN).model, AiProvider.QWEN, economics)

    @Bean
    @Qualifier("kimiChatClient")
    fun kimiChatClient(
        @Value("\${dashscope.api-key}") apiKey: String,
        @Value("\${dashscope.base-url}") baseUrl: String,
        economics: AiUnitEconomics,
    ): ChatClient = dashScopeChatClient(apiKey, baseUrl, AiPriceBook.llm(AiProvider.KIMI).model, AiProvider.KIMI, economics)

    @Bean
    @Qualifier("glmChatClient")
    fun glmChatClient(
        @Value("\${dashscope.api-key}") apiKey: String,
        @Value("\${dashscope.base-url}") baseUrl: String,
        economics: AiUnitEconomics,
    ): ChatClient = dashScopeChatClient(apiKey, baseUrl, AiPriceBook.llm(AiProvider.GLM).model, AiProvider.GLM, economics)

    @Bean
    @Qualifier("minimaxChatClient")
    fun minimaxChatClient(
        @Value("\${dashscope.api-key}") apiKey: String,
        @Value("\${dashscope.base-url}") baseUrl: String,
        economics: AiUnitEconomics,
    ): ChatClient = dashScopeChatClient(apiKey, baseUrl, AiPriceBook.llm(AiProvider.MINIMAX).model, AiProvider.MINIMAX, economics)
}
