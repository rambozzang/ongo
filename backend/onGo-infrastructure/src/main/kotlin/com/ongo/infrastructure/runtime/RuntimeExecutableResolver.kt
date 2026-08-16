package com.ongo.infrastructure.runtime

import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolves optional media tools without assuming that the service user's PATH
 * contains deployment-managed binaries. An explicit configured path always
 * wins; the fallback list only applies to the plain command names.
 */
object RuntimeExecutableResolver {

    fun resolve(configured: String): String {
        if (configured !in setOf("ffmpeg", "yt-dlp")) return configured

        return candidates(configured)
            .firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }
            ?.toString()
            ?: configured
    }

    fun directoryOf(executable: String): String? {
        val path = runCatching { Path.of(executable) }.getOrNull() ?: return null
        return path.parent?.toString()
    }

    private fun candidates(command: String): List<Path> = when (command) {
        "ffmpeg" -> listOf(
            Path.of("/data/ffmpeg/bin/ffmpeg"),
            Path.of("/data/ffmpeg/current/bin/ffmpeg"),
            Path.of("/usr/local/bin/ffmpeg"),
        )
        "yt-dlp" -> listOf(
            Path.of("/data/yt-dlp/bin/yt-dlp"),
            Path.of("/data/yt-dlp/yt-dlp"),
            Path.of("/usr/local/bin/yt-dlp"),
        )
        else -> emptyList()
    }
}
