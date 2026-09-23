package com.ongo.infrastructure.external.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Path

class TempDiskSpaceGuardTest {
    @Test
    fun `requires complete staged file plus reserve before upload can start`() {
        val guard = TempDiskSpaceGuard(reservedFreeBytes = 512, usableSpace = { 1_511 })

        val error = assertThrows(IllegalStateException::class.java) {
            guard.requireCapacity(Path.of("/tmp"), incomingBytes = 1_000)
        }

        assertThat(error.message)
            .contains("임시 디스크 공간이 부족합니다")
            .contains("필요 공간")
            .contains("사용 가능 공간은 1511바이트")
    }

    @Test
    fun `accepts exactly required free space`() {
        val guard = TempDiskSpaceGuard(reservedFreeBytes = 512, usableSpace = { 1_512 })

        guard.requireCapacity(Path.of("/tmp"), incomingBytes = 1_000)
    }
}
