package com.ongo.application.ai

import com.ongo.application.ai.result.MetaGenerationResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.Platform
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 메타 생성이 한도표 규칙·내 성과 근거를 프롬프트에 넣고, 결과를 한도 안으로 맞춰 돌려주는지 고정한다. */
class GenerateMetaUseCaseTest {

    private val resolver = mockk<ChatClientResolver>()
    private val credits = mockk<CreditService>()
    private val rateLimiter = mockk<AiRateLimiter>()
    private val grounding = mockk<MetaPerformanceGrounding>()
    private val client = mockk<ChatClient>()
    private val system = slot<String>()
    private val user = slot<String>()

    private fun useCase(withGrounding: Boolean = true) =
        GenerateMetaUseCase(resolver, credits, rateLimiter, if (withGrounding) grounding else null)

    private fun stub(result: MetaGenerationResult) {
        every { rateLimiter.checkRateLimit(7) } just runs
        every { resolver.resolve(7) } returns client
        every { client.prompt().system(capture(system)).user(capture(user)).call().entity(MetaGenerationResult::class.java) } returns result
        every { credits.withCredits(7, AiFeature.META_GENERATION, any<() -> MetaGenerationResult>()) } answers { lastArg<() -> MetaGenerationResult>().invoke() }
    }

    @Test
    fun `내 성과 근거와 대상 플랫폼 규칙을 프롬프트에 넣고 근거 사용 여부를 돌려준다`() {
        every { grounding.forUser(7) } returns MetaPerformanceGrounding.Grounding("<user_input>잘 된 영상 목록</user_input>", true, 5, 3, null)
        stub(MetaGenerationResult(listOf(MetaGenerationResult.PlatformMetaResult("TIKTOK", listOf("제목"), "설명", listOf("#a", "a", "b")))))

        val outcome = useCase().executeWithGrounding(7, "대본", listOf(Platform.TIKTOK), "친근", "요리")

        assertTrue(user.captured.contains("잘 된 영상 목록"), user.captured)
        assertTrue(system.captured.contains("- TIKTOK: 제목"), system.captured)
        assertTrue(!system.captured.contains("150자"), system.captured)
        assertTrue(outcome.grounding.used)
        assertEquals(listOf("a", "b"), outcome.result.platforms.single().hashtags)
    }

    @Test
    fun `근거 구성 요소가 없으면 근거 없이 생성하고 프롬프트에 빈 자리만 남는다`() {
        stub(MetaGenerationResult(listOf(MetaGenerationResult.PlatformMetaResult("YOUTUBE", listOf("제목"), "설명", emptyList()))))

        val outcome = useCase(withGrounding = false).executeWithGrounding(7, "대본", listOf(Platform.YOUTUBE), "친근", "요리")

        assertTrue(!user.captured.contains("{performance}"), user.captured)
        assertTrue(!outcome.grounding.used)
        assertEquals("YOUTUBE", outcome.result.platforms.single().platform)
    }
}
