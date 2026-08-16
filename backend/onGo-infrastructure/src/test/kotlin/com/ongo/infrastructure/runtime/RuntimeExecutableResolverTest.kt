package com.ongo.infrastructure.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeExecutableResolverTest {

    @Test
    fun `explicit executable paths are preserved`() {
        assertEquals("/opt/tools/ffmpeg", RuntimeExecutableResolver.resolve("/opt/tools/ffmpeg"))
        assertEquals("custom-command", RuntimeExecutableResolver.resolve("custom-command"))
    }

    @Test
    fun `directoryOf returns the parent directory for an executable path`() {
        assertEquals("/data/ffmpeg/bin", RuntimeExecutableResolver.directoryOf("/data/ffmpeg/bin/ffmpeg"))
        assertTrue(RuntimeExecutableResolver.directoryOf("ffmpeg") == null)
    }
}
