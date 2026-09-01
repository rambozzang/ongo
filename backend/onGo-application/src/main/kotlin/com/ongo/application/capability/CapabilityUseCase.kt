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
     * 런타임 의존성. 인프라 어댑터 없이 이 모듈만 테스트할 수 있도록 nullable 이다.
     *
     * **null 은 "판단 불가" 이지 "사용 가능" 이 아니다.** 예전에는 null 이면 활성으로
     * 떨어뜨리고 "단위 테스트뿐" 이라고 주석에 적어 두었는데, 그것을 강제하는 장치가 없었다.
     * 빈 등록이 조건부로 빠지거나 프로필이 잘못 잡히면 프런트는 AI·UGC·결제를 정상으로
     * 표시하고, 사용자는 클릭한 뒤에야 실패를 본다. 유료 기능에서 그 순서는 최악이다.
     *
     * 이제 null 은 각 기능을 **비활성**으로 만든다. 잘못된 배포는 기능이 잠기는 쪽으로
     * 틀려야 한다.
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
        val readiness = portOneReadiness ?: return MISSING_DEPENDENCY_PAYMENT
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
        val registry = chatClientRegistry ?: return MISSING_DEPENDENCY_AI
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
        val renderer = videoRenderer ?: return MISSING_DEPENDENCY_SHORTS
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
        /*
         * 의존성이 없을 때의 문구.
         *
         * 어느 빈이 빠졌는지 쓰지 않는다. 사용자가 할 수 있는 일이 없고, 배포 구성을
         * 알려 줄 이유도 없다. 이유가 있는 비활성(키 미설정·ffmpeg 없음)과 문구를 맞춰
         * 둔 것도 의도다 — 사용자에게는 같은 사실이다.
         */
        private val MISSING_DEPENDENCY_AI = RuntimeStatus(
            enabled = false,
            reason = "AI 기능을 지금 사용할 수 없습니다. 관리자에게 문의해 주세요.",
        )
        private val MISSING_DEPENDENCY_SHORTS = RuntimeStatus(
            enabled = false,
            reason = "영상 렌더링을 지금 사용할 수 없습니다. 관리자에게 문의해 주세요.",
        )
        private val MISSING_DEPENDENCY_PAYMENT = RuntimeStatus(
            enabled = false,
            reason = "온라인 결제를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도하거나 고객지원에 문의해 주세요.",
        )

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
            AppCapability("competitors"),
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
