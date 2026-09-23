package com.ongo.infrastructure.external.platform

import java.io.BufferedOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * 업로드 전체를 로컬 임시 디스크에 쌓는 전송기(YouTube·TikTok)의 사전 여유 공간 검사.
 *
 * 10GB 원본을 쌓다가 디스크가 차면 그 전송뿐 아니라 같은 서버의 쇼츠 렌더·로그까지 함께 실패한다.
 * 외부 세션을 열기 전에 막는다. 동시 전송끼리 공간을 예약하지는 않는다 — 동시 수는 업로드 세마포어가 제한한다.
 */
class TempDiskSpaceGuard(
    private val reservedFreeBytes: Long = DEFAULT_RESERVED_FREE_BYTES,
    private val usableSpace: (Path) -> Long = { directory -> Files.getFileStore(directory).usableSpace },
) {
    init {
        require(reservedFreeBytes >= 0) { "reservedFreeBytes must not be negative" }
    }

    fun requireCapacity(directory: Path, incomingBytes: Long) {
        require(incomingBytes >= 0) { "incomingBytes must not be negative" }
        val requiredBytes = try {
            Math.addExact(incomingBytes, reservedFreeBytes)
        } catch (e: ArithmeticException) {
            throw IllegalStateException("임시 업로드 파일의 필요 디스크 공간을 계산할 수 없습니다", e)
        }
        val availableBytes = try {
            usableSpace(directory)
        } catch (e: Exception) {
            throw IllegalStateException("임시 디스크 여유 공간을 확인할 수 없어 업로드를 시작할 수 없습니다", e)
        }
        if (availableBytes < requiredBytes) {
            throw IllegalStateException(
                "임시 디스크 공간이 부족합니다. 업로드 전 필요 공간은 파일 ${incomingBytes}바이트와 " +
                    "여유분 ${reservedFreeBytes}바이트(총 ${requiredBytes}바이트)이며, " +
                    "현재 사용 가능 공간은 ${availableBytes}바이트입니다.",
            )
        }
    }

    companion object {
        /** 파일을 쌓은 뒤에도 로그·OS·다른 작업이 쓸 여유분. */
        const val DEFAULT_RESERVED_FREE_BYTES = 512L * 1024 * 1024
    }
}

internal class TempFileChunkBuffer(
    prefix: String,
    private val diskSpaceGuard: TempDiskSpaceGuard = TempDiskSpaceGuard(),
) {
    private val file = Files.createTempFile("ongo-$prefix-", ".upload").toFile()
    private var output: BufferedOutputStream? = file.outputStream().buffered()
    private var written = 0L

    fun ensureCapacity(totalBytes: Long) {
        try {
            diskSpaceGuard.requireCapacity(file.toPath().parent, totalBytes)
        } catch (e: Exception) {
            // 세션을 열기 전에 실패하면 호출부가 이 임시 파일을 넘겨받지 못한다 — 여기서 지운다.
            runCatching { cleanup() }
            throw e
        }
    }

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
