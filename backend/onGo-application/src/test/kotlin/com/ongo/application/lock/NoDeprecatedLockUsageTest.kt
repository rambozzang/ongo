package com.ongo.application.lock

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 운영 코드가 락 누수 경로를 다시 쓰지 않게 막는다.
 *
 * `tryLock`/`releaseLock` 은 획득과 해제가 **다른 커넥션에서** 일어난다.
 * PostgreSQL 자문 락은 세션 범위라 다른 커넥션에서 해제해도 풀리지 않는다.
 * 그러면 락이 영원히 잡혀 해당 스케줄러가 다시는 돌지 않는다.
 *
 * 두 메서드에 `@Deprecated` 를 달아뒀지만 경고는 무시할 수 있다. 스케줄러마다 개별
 * 테스트를 두는 방법도 있는데, 그러면 **테스트를 안 만든 새 스케줄러가 그대로 빠져나간다.**
 * 그래서 소스를 훑어 한 번에 막는다.
 *
 * 실제로 이 경로를 쓰던 스케줄러가 5개 있었고 전부 `withLock` 으로 옮겼다.
 */
class NoDeprecatedLockUsageTest {

    private val deprecated = listOf("distributedLockPort.tryLock", "distributedLockPort.releaseLock")

    /** 모듈 루트를 찾는다. 테스트 작업 디렉터리는 모듈 디렉터리다. */
    private fun mainSourceRoots(): List<File> {
        val backend = generateSequence(File(".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").exists() }
            ?: File("..").absoluteFile

        return backend.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("onGo-") }
            ?.map { File(it, "src/main/kotlin") }
            ?.filter { it.exists() }
            ?: emptyList()
    }

    @Test
    @DisplayName("운영 코드에 tryLock/releaseLock 이 남아 있으면 안 된다")
    fun noProductionCodeUsesLeakingLockApi() {
        val roots = mainSourceRoots()

        // 소스를 못 찾으면 이 테스트는 아무것도 검증하지 않은 채 통과한다.
        assertTrue(roots.isNotEmpty()) { "main 소스 루트를 찾지 못했다. 검사가 헛돌고 있다" }

        val offenders = roots
            .flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" } }
            .filter { f -> deprecated.any { f.readText().contains(it) } }
            .map { it.name }
            .sorted()

        assertTrue(offenders.isEmpty()) {
            buildString {
                append("락 누수 경로를 쓰는 파일이 ${offenders.size}개 있다.\n")
                offenders.forEach { append("  - $it\n") }
                append("\nwithLock(lockId) { ... } 을 써라. ")
                append("획득과 해제가 같은 커넥션에서 일어나야 자문 락이 실제로 풀린다.")
            }
        }
    }
}
