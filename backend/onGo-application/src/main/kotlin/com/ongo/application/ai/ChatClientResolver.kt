package com.ongo.application.ai

import com.ongo.common.enums.AiProvider
import com.ongo.common.exception.BusinessException
import com.ongo.domain.settings.UserSettingsRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class ChatClientResolver(
    private val chatClientRegistry: ChatClientRegistry,
    private val userSettingsRepository: UserSettingsRepository,
) {

    private val log = LoggerFactory.getLogger(ChatClientResolver::class.java)

    fun resolve(userId: Long): ChatClient = chatClientRegistry.getClient(resolveProvider(userId))

    /**
     * [resolve] 가 실제로 고르는 제공자. 원가 원장처럼 "어느 제공자로 나갔는가" 를 적는 곳은
     * 규칙을 복사하지 말고 이것을 불러야 한다 — 선택 규칙이 바뀌면 기록이 따라 틀린다.
     */
    fun resolveProvider(userId: Long): AiProvider {
        val requested = if (userId == 0L) {
            null
        } else {
            userSettingsRepository.findByUserId(userId)?.defaultAiProvider
        }

        // 제공하지 않는 제공자(예전 기본값 CLAUDE 등)는 선택돼 있어도 쓰지 않는다 — AiProvider.OFFERED 참고.
        val provider = requested
            ?.takeIf { it in AiProvider.OFFERED }
            ?.takeIf(chatClientRegistry::isProviderAvailable)
            ?: AiProvider.OFFERED.firstOrNull(chatClientRegistry::isProviderAvailable)
            ?: throw BusinessException(
                "AI_PROVIDER_NOT_CONFIGURED",
                "사용 가능한 AI 제공자가 설정되지 않았습니다. 관리자에게 API 키를 설정해 주세요.",
            )

        // 예전 기본값(CLAUDE 등)을 가진 사용자는 매 호출마다 여기 걸린다 — 제공 목록 안의 선택이 막혔을 때만 남긴다.
        if (requested != null && requested in AiProvider.OFFERED && requested != provider) {
            log.warn("AI 제공자 {} 사용 불가, 설정된 {}으로 대체: userId={}", requested, provider, userId)
        }

        return provider
    }
}
