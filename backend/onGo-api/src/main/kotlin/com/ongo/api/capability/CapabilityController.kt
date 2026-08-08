package com.ongo.api.capability

import com.ongo.application.capability.CapabilityUseCase
import com.ongo.common.ResData
import com.ongo.common.annotation.RequiresPermission
import com.ongo.common.enums.Permission
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "기능 capability", description = "현재 배포에서 실제로 사용할 수 있는 기능 목록")
@RestController
@RequestMapping("/api/v1/capabilities")
class CapabilityController(
    private val capabilityUseCase: CapabilityUseCase,
) {
    @Operation(summary = "활성 기능 목록 조회")
    @RequiresPermission(Permission.VIDEO_READ)
    @GetMapping
    fun list() = ResData(data = capabilityUseCase.list())
}
