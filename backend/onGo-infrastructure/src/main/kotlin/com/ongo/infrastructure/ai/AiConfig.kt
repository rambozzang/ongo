package com.ongo.infrastructure.ai

import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.beans.factory.annotation.Qualifier
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
}
