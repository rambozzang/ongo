# Alibaba Cloud Model Studio AI 모델 통합 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Alibaba Cloud Model Studio의 Coding Plan API를 통해 qwen3.5-plus, kimi-k2.5, glm-5, MiniMax-M2.5 4개 AI 모델을 기존 시스템에 추가한다.

**Architecture:** Spring AI의 `OpenAiApi.builder()`로 OpenAI-compatible 엔드포인트를 가리키는 4개의 ChatModel/ChatClient Bean을 생성하고, 기존 `ChatClientRegistry` 패턴에 통합한다. 하나의 DashScope API 키로 4개 모델을 모두 호출한다.

**Tech Stack:** Spring AI 1.0.0-M5 (OpenAiChatModel), Kotlin, Vue.js 3, i18n

---

### Task 1: AiProvider enum 확장

**Files:**
- Modify: `backend/onGo-common/src/main/kotlin/com/ongo/common/enums/AiProvider.kt`

**Step 1: AiProvider에 4개 값 추가**

```kotlin
enum class AiProvider(val displayName: String) {
    CLAUDE("Claude"),
    GEMINI("Gemini"),
    OPENAI("OpenAI"),
    QWEN("Qwen 3.5"),
    KIMI("Kimi K2.5"),
    GLM("GLM-5"),
    MINIMAX("MiniMax M2.5"),
    ;

    companion object {
        fun fromString(value: String?): AiProvider =
            try {
                valueOf(value?.uppercase() ?: "CLAUDE")
            } catch (_: IllegalArgumentException) {
                CLAUDE
            }
    }
}
```

**Step 2: 빌드 확인**

Run: `cd backend && ./gradlew :onGo-common:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: 커밋**

```bash
git add backend/onGo-common/src/main/kotlin/com/ongo/common/enums/AiProvider.kt
git commit -m "feat: AiProvider enum에 QWEN, KIMI, GLM, MINIMAX 추가"
```

---

### Task 2: application.yml에 DashScope 설정 추가

**Files:**
- Modify: `backend/onGo-api/src/main/resources/application.yml:56` (spring.ai 블록 뒤에 추가)

**Step 1: dashscope 설정 추가**

`application.yml`에서 `spring.ai` 블록(라인 36-56) 바로 뒤, `spring.security` 블록(라인 58) 앞에 추가:

```yaml
  # DashScope (Alibaba Cloud Model Studio) - Qwen, Kimi, GLM, MiniMax
  dashscope:
    api-key: ${DASHSCOPE_API_KEY:dummy-dashscope-key}
    base-url: https://coding-intl.dashscope.aliyuncs.com/v1
```

주의: `spring:` 하위가 아닌 루트 레벨에 `dashscope:` 추가.

**Step 2: 커밋**

```bash
git add backend/onGo-api/src/main/resources/application.yml
git commit -m "feat: DashScope API 설정 추가 (Alibaba Cloud Model Studio)"
```

---

### Task 3: AiConfig에 DashScope ChatClient Bean 4개 추가

**Files:**
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/ai/AiConfig.kt`

**Step 1: AiConfig 수정**

기존 코드 뒤에 DashScope 모델용 Bean 4개를 추가한다. `OpenAiApi.builder()`로 DashScope 엔드포인트를 가리키는 API 인스턴스를 생성하고, 모델명만 다르게 설정한다:

```kotlin
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
        val api = OpenAiApi.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .build()
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
```

**Step 2: 빌드 확인**

Run: `cd backend && ./gradlew :onGo-infrastructure:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: 커밋**

```bash
git add backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/ai/AiConfig.kt
git commit -m "feat: DashScope 4개 모델 ChatClient Bean 등록"
```

---

### Task 4: ChatClientRegistryImpl에 4개 provider 등록

**Files:**
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/ai/ChatClientRegistryImpl.kt`

**Step 1: ChatClientRegistryImpl 수정**

```kotlin
package com.ongo.infrastructure.ai

import com.ongo.application.ai.ChatClientRegistry
import com.ongo.common.enums.AiProvider
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
class ChatClientRegistryImpl(
    @Qualifier("anthropicChatClient") private val anthropicChatClient: ChatClient,
    @Qualifier("openaiChatClient") private val openaiChatClient: ChatClient,
    @Autowired(required = false) @Qualifier("geminiChatClient") private val geminiChatClient: ChatClient?,
    @Qualifier("qwenChatClient") private val qwenChatClient: ChatClient,
    @Qualifier("kimiChatClient") private val kimiChatClient: ChatClient,
    @Qualifier("glmChatClient") private val glmChatClient: ChatClient,
    @Qualifier("minimaxChatClient") private val minimaxChatClient: ChatClient,
) : ChatClientRegistry {

    private val clients: Map<AiProvider, ChatClient> = buildMap {
        put(AiProvider.CLAUDE, anthropicChatClient)
        put(AiProvider.OPENAI, openaiChatClient)
        if (geminiChatClient != null) {
            put(AiProvider.GEMINI, geminiChatClient)
        }
        put(AiProvider.QWEN, qwenChatClient)
        put(AiProvider.KIMI, kimiChatClient)
        put(AiProvider.GLM, glmChatClient)
        put(AiProvider.MINIMAX, minimaxChatClient)
    }

    override fun getClient(provider: AiProvider): ChatClient =
        clients[provider]
            ?: clients[AiProvider.CLAUDE]
            ?: error("Anthropic ChatClient is required but not available")

    override fun isProviderAvailable(provider: AiProvider): Boolean =
        clients.containsKey(provider)
}
```

**Step 2: 빌드 확인**

Run: `cd backend && ./gradlew :onGo-infrastructure:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: 커밋**

```bash
git add backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/ai/ChatClientRegistryImpl.kt
git commit -m "feat: ChatClientRegistry에 DashScope 4개 provider 등록"
```

---

### Task 5: 프론트엔드 i18n 메시지 추가

**Files:**
- Modify: `frontend/src/locales/ko/common.json:422-429` (aiProvider 섹션)
- Modify: `frontend/src/locales/en/common.json:422-429` (aiProvider 섹션)

**Step 1: 한국어 i18n 메시지 추가**

`ko/common.json`의 `aiProvider` 객체에 4개 키 추가 (기존 `openai` 뒤, `sttNote` 앞):

```json
"aiProvider": {
  "title": "기본 AI 제공자",
  "description": "AI 기능(메타 생성, 해시태그, 아이디어 등)에 사용할 AI 모델을 선택하세요.",
  "claude": "Anthropic의 Claude — 자연스러운 한국어 생성",
  "gemini": "Google의 Gemini — 다국어 지원 강점",
  "openai": "OpenAI GPT — 범용 AI 모델",
  "qwen": "Alibaba의 Qwen 3.5 — 균형 잡힌 성능, Vision 지원",
  "kimi": "Moonshot의 Kimi K2.5 — 강력한 추론, Vision 지원",
  "glm": "Zhipu의 GLM-5 — 중국어/한국어 강점",
  "minimax": "MiniMax M2.5 — 코딩 및 분석 특화",
  "sttNote": "* 음성 인식(STT)은 항상 OpenAI Whisper를 사용합니다."
}
```

**Step 2: 영어 i18n 메시지 추가**

`en/common.json`의 `aiProvider` 객체에 동일하게 4개 키 추가:

```json
"aiProvider": {
  "title": "Default AI Provider",
  "description": "Choose the AI model for AI features (meta generation, hashtags, ideas, etc.).",
  "claude": "Anthropic Claude — Natural Korean generation",
  "gemini": "Google Gemini — Strong multilingual support",
  "openai": "OpenAI GPT — General-purpose AI model",
  "qwen": "Alibaba Qwen 3.5 — Balanced performance, Vision support",
  "kimi": "Moonshot Kimi K2.5 — Strong reasoning, Vision support",
  "glm": "Zhipu GLM-5 — Chinese/Korean language strength",
  "minimax": "MiniMax M2.5 — Coding and analysis focused",
  "sttNote": "* Speech-to-text (STT) always uses OpenAI Whisper."
}
```

**Step 3: 커밋**

```bash
git add frontend/src/locales/ko/common.json frontend/src/locales/en/common.json
git commit -m "feat: AI Provider i18n 메시지 추가 (Qwen, Kimi, GLM, MiniMax)"
```

---

### Task 6: SettingsView.vue에 새 AI Provider 옵션 추가

**Files:**
- Modify: `frontend/src/views/SettingsView.vue:763-767` (aiProviderOptions)

**Step 1: aiProviderOptions에 4개 옵션 추가**

```typescript
const aiProviderOptions = computed(() => [
  { value: 'CLAUDE', label: 'Claude', description: t('settings.aiProvider.claude') },
  { value: 'GEMINI', label: 'Gemini', description: t('settings.aiProvider.gemini') },
  { value: 'OPENAI', label: 'OpenAI GPT', description: t('settings.aiProvider.openai') },
  { value: 'QWEN', label: 'Qwen 3.5', description: t('settings.aiProvider.qwen') },
  { value: 'KIMI', label: 'Kimi K2.5', description: t('settings.aiProvider.kimi') },
  { value: 'GLM', label: 'GLM-5', description: t('settings.aiProvider.glm') },
  { value: 'MINIMAX', label: 'MiniMax M2.5', description: t('settings.aiProvider.minimax') },
])
```

**Step 2: 프론트엔드 빌드 확인**

Run: `cd frontend && npm run build`
Expected: 빌드 성공, 에러 없음

**Step 3: 커밋**

```bash
git add frontend/src/views/SettingsView.vue
git commit -m "feat: 설정 페이지에 DashScope AI Provider 4개 옵션 추가"
```

---

### Task 7: 전체 빌드 및 검증

**Step 1: 백엔드 전체 빌드**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 2: 프론트엔드 전체 빌드**

Run: `cd frontend && npm run build`
Expected: 빌드 성공

**Step 3: 최종 커밋**

모든 변경사항이 이미 커밋되어 있으면 스킵. 아니면:

```bash
git add -A
git commit -m "feat: Alibaba Cloud Model Studio AI 모델 4개 통합 완료"
```
