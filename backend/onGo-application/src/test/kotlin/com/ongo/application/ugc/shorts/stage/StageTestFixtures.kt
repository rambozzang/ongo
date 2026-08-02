package com.ongo.application.ugc.shorts.stage

import com.ongo.application.ai.ChatClientResolver
import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.ShortsClip
import io.mockk.every
import io.mockk.mockk
import org.springframework.ai.chat.client.ChatClient

/** ChatClient 플루언트 체인(resolve → prompt → system → user → call → entity)이 [result]를 반환하도록 스텁한다. */
fun <T : Any> stubChatClientEntity(resolver: ChatClientResolver, type: Class<T>, result: T) {
    val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
    val callSpec = mockk<ChatClient.CallResponseSpec>()
    val chatClient = mockk<ChatClient>()
    every { resolver.resolve(any()) } returns chatClient
    every { chatClient.prompt() } returns requestSpec
    every { requestSpec.system(any<String>()) } returns requestSpec
    every { requestSpec.user(any<String>()) } returns requestSpec
    every { requestSpec.call() } returns callSpec
    every { callSpec.entity(type) } returns result
}

/** 테스트용 실행 컨텍스트. 필요한 필드만 골라 채운다. */
fun stageContext(
    clips: List<ShortsClip> = emptyList(),
    hooks: Map<Long, List<com.ongo.domain.ugc.shorts.ClipHook>> = emptyMap(),
    transcriptText: String? = "전사 전문",
    transcriptSegments: List<TranscriptSegmentMs> = emptyList(),
    template: com.ongo.domain.ugc.shorts.ShortsTemplate? = null,
    schedule: ScheduleParams? = null,
    cropJson: String? = null,
) = ShortsStageContext(
    run = PipelineRun(id = 1, workspaceId = 10, userId = 1, sourceVideoId = 5),
    userId = 1,
    workspaceId = 10,
    sourceVideoTitle = "원본 영상",
    sourceFileUrl = "https://cdn.example.com/source.mp4",
    transcriptText = transcriptText,
    transcriptSegments = transcriptSegments,
    clips = clips,
    hooks = hooks,
    cropJson = cropJson,
    template = template,
    schedule = schedule,
)
