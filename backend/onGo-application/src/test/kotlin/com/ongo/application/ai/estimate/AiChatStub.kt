package com.ongo.application.ai.estimate

import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.ai.chat.client.ChatClient

/** `.entity(type)` 로 [result] 를 돌려주는 채팅 체인. 사용자 프롬프트를 잡아 둔다. */
fun <T : Any> stubChatEntity(resolver: ChatClientResolver, userId: Long, type: Class<T>, result: T?): CapturingSlot<String> {
    val prompt = slot<String>()
    val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
    val callSpec = mockk<ChatClient.CallResponseSpec>()
    val chatClient = mockk<ChatClient>()
    every { resolver.resolve(userId) } returns chatClient
    every { chatClient.prompt() } returns requestSpec
    every { requestSpec.system(any<String>()) } returns requestSpec
    every { requestSpec.user(capture(prompt)) } returns requestSpec
    every { requestSpec.call() } returns callSpec
    every { callSpec.entity(type) } returns result
    return prompt
}

/** 모델 호출이 실패하는 체인. */
fun stubChatFailure(resolver: ChatClientResolver, userId: Long, error: Exception) {
    val chatClient = mockk<ChatClient>()
    every { resolver.resolve(userId) } returns chatClient
    every { chatClient.prompt() } throws error
}

/** 차감 경로가 블록을 실제로 실행하게 한다(차감·환불 자체는 CreditService 시험이 본다). */
fun passThroughCredits(creditService: CreditService, userId: Long, feature: AiFeature) {
    every { creditService.withCredits(userId, feature, any<() -> Any>()) } answers { thirdArg<() -> Any>()() }
}
