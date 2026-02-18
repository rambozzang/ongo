package com.ongo.application.ai

import com.ongo.common.enums.AiProvider
import org.springframework.ai.chat.client.ChatClient

interface ChatClientRegistry {
    fun getClient(provider: AiProvider): ChatClient
    fun isProviderAvailable(provider: AiProvider): Boolean
}
