package com.ongo.application.capability

import org.springframework.stereotype.Service

/**
 * 서버가 실제로 노출한 기능의 단일 목록이다.
 * 프론트 메뉴는 이 계약을 기준으로만 기능을 노출할 수 있어, 죽은 WIP 메뉴가
 * 배포 설정에 따라 다시 나타나는 일을 막는다.
 */
@Service
class CapabilityUseCase {
    fun list(): List<AppCapability> = ACTIVE_CAPABILITIES

    companion object {
        private val ACTIVE_CAPABILITIES = listOf(
            AppCapability("today"),
            AppCapability("compose"),
            AppCapability("videos"),
            AppCapability("ai"),
            AppCapability("templates"),
            AppCapability("brandkit"),
            AppCapability("assets"),
            AppCapability("keyword-research"),
            AppCapability("trends"),
            AppCapability("calendar-v2"),
            AppCapability("automation"),
            AppCapability("channels-v2"),
            AppCapability("performance"),
            AppCapability("revenue"),
            AppCapability("ab-tests"),
            AppCapability("inbox-v2"),
            AppCapability("audience"),
            AppCapability("channel-audit"),
            AppCapability("brand-deals"),
            AppCapability("linkbio"),
            AppCapability("ugc/campaigns"),
            AppCapability("creator/campaigns"),
            AppCapability("team"),
        )
    }
}

data class AppCapability(
    val key: String,
    val enabled: Boolean = true,
)
