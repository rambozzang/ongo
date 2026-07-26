package com.ongo.infrastructure.external.platform

import java.io.BufferedOutputStream
import java.io.File
import java.nio.file.Files

internal class TempFileChunkBuffer(prefix: String) {
    private val file = Files.createTempFile("ongo-$prefix-", ".upload").toFile()
    private var output: BufferedOutputStream? = file.outputStream().buffered()
    private var written = 0L

    @Synchronized
    fun write(chunk: ByteArray, offset: Long) {
        check(offset == written) { "업로드 청크 순서가 올바르지 않습니다: expected=$written, actual=$offset" }
        output?.write(chunk) ?: error("이미 완료된 업로드 버퍼입니다")
        written += chunk.size
    }

    @Synchronized
    fun finish(): File {
        output?.flush()
        output?.close()
        output = null
        return file
    }

    fun cleanup() {
        runCatching { output?.close() }
        output = null
        Files.deleteIfExists(file.toPath())
    }
}
