# Alibaba Cloud Model Studio AI 모델 통합 설계

## 개요

Alibaba Cloud Model Studio (百炼) Coding Plan을 통해 4개의 AI 모델을 추가합니다.
하나의 API 키(`sk-sp-xxxxx`)로 qwen3.5-plus, kimi-k2.5, glm-5, MiniMax-M2.5를 모두 사용합니다.

기존 AI provider(Claude, Gemini, OpenAI)는 유지하고, 새로운 4개를 추가하여 총 7개 provider를 지원합니다.

## API 정보

- **Base URL**: `https://coding-intl.dashscope.aliyuncs.com/v1` (OpenAI-compatible)
- **인증**: `Authorization: Bearer sk-sp-xxxxx`
- **프로토콜**: OpenAI Chat Completions API 호환

## 모델 목록

| Provider | Model ID | Vision | 설명 |
|----------|----------|--------|------|
| QWEN | qwen3.5-plus | O | Alibaba 최신 모델, 균형 잡힌 성능 |
| KIMI | kimi-k2.5 | O | Moonshot AI 최신 모델 |
| GLM | glm-5 | X | Zhipu AI 최신 모델 |
| MINIMAX | minimax-m2.5 | X | MiniMax 최신 모델 |

## 접근법

**Spring AI OpenAiChatModel 다중 인스턴스화**

Spring AI의 `OpenAiApi.builder()`로 동일한 base-url/api-key를 가진 API 인스턴스를 생성하고,
`OpenAiChatModel`을 모델별로 4개 생성하여 각각 ChatClient Bean으로 등록합니다.
기존 `ChatClientRegistry` 패턴에 자연스럽게 통합됩니다.

## 수정 파일

### 1. AiProvider enum 확장
**파일**: `onGo-common/.../enums/AiProvider.kt`

QWEN, KIMI, GLM, MINIMAX 4개 값 추가.

### 2. application.yml 설정 추가
**파일**: `onGo-api/.../resources/application.yml`

```yaml
dashscope:
  api-key: ${DASHSCOPE_API_KEY:dummy-dashscope-key}
  base-url: https://coding-intl.dashscope.aliyuncs.com/v1
```

### 3. AiConfig Bean 추가
**파일**: `onGo-infrastructure/.../ai/AiConfig.kt`

4개의 ChatClient Bean 등록 (qwenChatClient, kimiChatClient, glmChatClient, minimaxChatClient).
각각 OpenAiApi.builder()로 생성, model명만 다르게 설정.

### 4. ChatClientRegistryImpl 확장
**파일**: `onGo-infrastructure/.../ai/ChatClientRegistryImpl.kt`

4개 새 provider를 clients Map에 등록. fallback은 기존대로 CLAUDE.

### 5. 프론트엔드 설정 페이지
**파일**: `frontend/src/views/SettingsView.vue`

AI Provider 선택지에 Qwen 3.5, Kimi K2.5, GLM-5, MiniMax M2.5 추가.

### 6. i18n 메시지
**파일**: `frontend/src/locales/ko.json`, `frontend/src/locales/en.json`

새 provider 관련 설명 메시지 추가.

## 변경하지 않는 파일

- `ChatClientRegistry.kt` (인터페이스) - 변경 불필요
- `ChatClientResolver.kt` - AiProvider 기반 동적 선택이므로 변경 불필요
- `CreditService` / `AiFeature` - 크레딧 시스템은 provider에 무관하게 동작
- DB 마이그레이션 - user_settings의 default_ai_provider는 문자열 저장이므로 불필요
