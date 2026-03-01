package com.ongo.infrastructure.ai

import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel
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
    fun anthropicChatClient(anthropicChatModel: AnthropicChatModel): ChatClient =
        ChatClient.builder(anthropicChatModel).build()

    @Bean
    @Qualifier("openaiChatClient")
    fun openaiChatClient(openAiChatModel: OpenAiChatModel): ChatClient =
        ChatClient.builder(openAiChatModel).build()

    @Bean
    @Qualifier("geminiChatClient")
    @ConditionalOnProperty(prefix = "spring.ai.vertex.ai.gemini", name = ["enabled"], havingValue = "true")
    fun geminiChatClient(geminiChatModel: VertexAiGeminiChatModel): ChatClient =
        ChatClient.builder(geminiChatModel).build()

    // --- DashScope (Alibaba Cloud Model Studio) ---

    private fun dashScopeChatClient(
        apiKey: String,
        baseUrl: String,
        modelName: String,
    ): ChatClient {
        val api = OpenAiApi(baseUrl, apiKey)
        val options = OpenAiChatOptions.builder()
            .model(modelName)
            .temperature(0.7)
            .maxTokens(4096)
            .build()
        val chatModel = OpenAiChatModel(api, options)
        return ChatClient.builder(chatModel).build()
    }

    @Bean
    @Qualifier("qwenChatClient")
    fun qwenChatClient(
        @Value("\${dashscope.api-key}") apiKey: String,
        @Value("\${dashscope.base-url}") baseUrl: String,
    ): ChatClient = dashScopeChatClient(apiKey, baseUrl, "qwen3.5-plus")

    @Bean
    @Qualifier("kimiChatClient")
    fun kimiChatClient(
        @Value("\${dashscope.api-key}") apiKey: String,
        @Value("\${dashscope.base-url}") baseUrl: String,
    ): ChatClient = dashScopeChatClient(apiKey, baseUrl, "kimi-k2.5")

    @Bean
    @Qualifier("glmChatClient")
    fun glmChatClient(
        @Value("\${dashscope.api-key}") apiKey: String,
        @Value("\${dashscope.base-url}") baseUrl: String,
    ): ChatClient = dashScopeChatClient(apiKey, baseUrl, "glm-5")

    @Bean
    @Qualifier("minimaxChatClient")
    fun minimaxChatClient(
        @Value("\${dashscope.api-key}") apiKey: String,
        @Value("\${dashscope.base-url}") baseUrl: String,
    ): ChatClient = dashScopeChatClient(apiKey, baseUrl, "minimax-m2.5")
}
