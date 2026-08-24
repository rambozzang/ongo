package com.ongo.application.capability

import com.ongo.application.ai.ChatClientRegistry
import com.ongo.common.enums.AiProvider
import com.ongo.domain.ugc.shorts.VideoRenderer
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * 서버가 실제로 노출한 기능의 단일 목록이다.
 * 프론트 메뉴는 이 계약을 기준으로만 기능을 노출할 수 있어, 죽은 WIP 메뉴가
 * 배포 설정에 따라 다시 나타나는 일을 막는다.
 */
@Service
class CapabilityUseCase(
    /** Comma-separated keys to hide for a staged/disabled deployment. */
    @param:Value("\${capabilities.disabled:}")
    private val disabledCapabilities: String = "",
    /**
     * Nullable only so the application module can be tested without loading the
     * infrastructure adapters. In the running API these beans are present and
     * capability state is therefore fail-closed on missing runtime dependencies.
     */
    private val chatClientRegistry: ChatClientRegistry? = null,
    private val videoRenderer: VideoRenderer? = null,
    private val portOneReadiness: com.ongo.application.portone.PortOneReadiness? = null,
) {
    fun list(): List<AppCapability> {
        val disabled = disabledCapabilities
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
        return ACTIVE_CAPABILITIES.map { capability ->
            val runtime = runtimeStatus(capability.key)
            val manuallyDisabled = capability.key in disabled
            capability.copy(
                enabled = runtime.enabled && !manuallyDisabled,
                reason = when {
                    manuallyDisabled -> "이 기능은 현재 운영 설정에서 비활성화되어 있습니다."
                    runtime.enabled -> null
                    else -> runtime.reason
                },
            )
        }
    }

    private fun runtimeStatus(key: String): RuntimeStatus = when (key) {
        "ai" -> aiStatus()
        "ugc/shorts/runs" -> shortsStatus()
        "payment" -> paymentStatus()
        else -> RuntimeStatus(enabled = true)
    }

    /**
     * 온라인 결제 가능 여부.
     *
     * `subscription` 이 아니라 별도 키인 이유: `subscription` 을 끄면 구독 화면 자체가
     * 메뉴에서 사라져 사용자가 자기 플랜과 크레딧 잔액도 볼 수 없게 된다. 막아야 하는 것은
     * **결제 시작**이지 결제 정보 조회가 아니다.
     *
     * 이유 문구에 어느 설정이 빠졌는지 쓰지 않는다. 사용자가 할 수 있는 일이 없고,
     * 설정 상태를 알려줄 이유도 없다.
     */
    private fun paymentStatus(): RuntimeStatus {
        // null 은 인프라 빈 없이 도는 모듈 단위 테스트뿐이다. aiStatus 와 같은 관례를 따른다.
        val readiness = portOneReadiness ?: return RuntimeStatus(enabled = true)
        return if (readiness.isReady()) {
            RuntimeStatus(enabled = true)
        } else {
            RuntimeStatus(
                enabled = false,
                reason = "온라인 결제를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도하거나 고객지원에 문의해 주세요.",
            )
        }
    }

    private fun aiStatus(): RuntimeStatus {
        // A null registry means this is an isolated application-module test. The
        // infrastructure bean is mandatory in the running API, so do not make
        // unit construction imply that production has an AI provider.
        val registry = chatClientRegistry ?: return RuntimeStatus(enabled = true)
        val available = runCatching {
            AiProvider.entries.any(registry::isProviderAvailable)
        }.getOrDefault(false)
        return if (available) {
            RuntimeStatus(enabled = true)
        } else {
            RuntimeStatus(
                enabled = false,
                reason = "사용 가능한 AI 제공자 API 키가 설정되지 않았습니다.",
            )
        }
    }

    private fun shortsStatus(): RuntimeStatus {
        // See aiStatus(): null is only for lightweight tests, while the API bean
        // receives the real ffmpeg-backed renderer.
        val renderer = videoRenderer ?: return RuntimeStatus(enabled = true)
        return runCatching { renderer.checkAvailability() }
            .fold(
                onSuccess = { availability ->
                    RuntimeStatus(
                        enabled = availability.available,
                        reason = availability.reason
                            ?: "영상 렌더링을 지금 사용할 수 없습니다. 관리자에게 문의해 주세요.",
                    )
                },
                onFailure = {
                    RuntimeStatus(
                        enabled = false,
                        reason = "영상 렌더링을 지금 사용할 수 없습니다. 관리자에게 문의해 주세요.",
                    )
                },
            )
    }

    companion object {
        private val ACTIVE_CAPABILITIES = listOf(
            AppCapability("today"),
            AppCapability("compose"),
            AppCapability("videos"),
            AppCapability("ai"),
            AppCapability("templates"),
            AppCapability("brandkit"),
            AppCapability("assets"),
            AppCapability("subtitle-editor"),
            AppCapability("recycling"),
            AppCapability("calendar-v2"),
            AppCapability("automation"),
            AppCapability("channels-v2"),
            AppCapability("performance"),
            AppCapability("revenue"),
            AppCapability("ab-tests"),
            AppCapability("analytics/compare"),
            AppCapability("goals"),
            AppCapability("inbox-v2"),
            AppCapability("audience"),
            AppCapability("notifications"),
            AppCapability("channel-audit"),
            AppCapability("brand-deals"),
            AppCapability("linkbio"),
            AppCapability("ugc/campaigns"),
            AppCapability("creator/campaigns"),
            AppCapability("ugc/shorts/prompts"),
            AppCapability("ugc/shorts/templates"),
            AppCapability("ugc/shorts/runs"),
            AppCapability("team"),
            AppCapability("webhooks"),
            AppCapability("activity-log"),
            AppCapability("manual"),
            AppCapability("subscription"),
            // 메뉴 항목이 아니라 결제 시작 가능 여부 신호다. 구독 화면은 계속 보여야 한다.
            AppCapability("payment"),
            AppCapability("settings-v2"),
            AppCapability("admin"),
        )
    }
}

data class AppCapability(
    val key: String,
    val enabled: Boolean = true,
    /** User-safe reason shown when the server deliberately disables a feature. */
    val reason: String? = null,
)

private data class RuntimeStatus(
    val enabled: Boolean,
    val reason: String? = null,
)
