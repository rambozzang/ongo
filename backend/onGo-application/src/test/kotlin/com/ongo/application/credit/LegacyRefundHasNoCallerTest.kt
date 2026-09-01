package com.ongo.application.credit

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * 출처 불명 환불(`CreditService.refundCredit`)을 **운영 코드가 부르지 않는지** 정적으로 고정한다.
 *
 * ## 왜 런타임 예외만으로는 부족한가
 *
 * 그 메서드는 이제 호출 즉시 [UnsupportedOperationException] 을 던지고
 * `@Deprecated(level = ERROR)` 로 Kotlin 호출부를 컴파일 단계에서 막는다. 하지만:
 *
 * - `@Suppress("DEPRECATION_ERROR")` 한 줄이면 컴파일 방어가 뚫린다.
 * - 뚫은 뒤 `runCatching { }` 으로 감싸면 런타임 예외도 조용히 사라진다.
 *
 * 두 방어를 동시에 우회하는 것은 어렵지 않고, 우회한 코드는 리뷰에서 눈에 잘 띄지 않는다.
 * 그래서 **소스에 그 호출이 나타나는 것 자체**를 여기서 막는다. 우회하려면 이 테스트를
 * 먼저 지워야 하고, 그건 리뷰에서 보인다.
 *
 * ## 왜 이 메서드가 위험한가
 *
 * 차감이 어디에서 나갔는지 모른 채 전액을 무료분에 얹었다. 구매 패키지에서 나간 크레딧이
 * **월말에 사라지는 무료 크레딧으로 바뀌고**, `free_monthly` 한도에 걸린 몫은 그대로
 * 증발한다. 고객이 돈 주고 산 자산이 줄어든다.
 *
 * 대체 수단은 [CreditService.refundAllocation] 이다. 차감 시점의 객체를 가질 수 없는
 * 경로는 분해를 DB 에 저장하고 [CreditAllocation.restored] 로 복원한다.
 */
class LegacyRefundHasNoCallerTest {

    /**
     * 테스트 작업 디렉터리는 모듈 루트(`onGo-application`)다. 형제 모듈까지 보려면
     * 한 단계 올라가야 한다.
     */
    private val backendRoot = File("..").canonicalFile

    private fun productionSources(): List<File> =
        backendRoot.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("onGo-") }
            .flatMap { module ->
                File(module, "src/main/kotlin").walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .toList()
            }

    /** 이 테스트가 실제로 소스를 읽고 있는지부터 확인한다. 0 개면 아무것도 검사하지 않는다. */
    @Test
    @DisplayName("운영 소스를 실제로 스캔한다")
    fun scansProductionSources() {
        val sources = productionSources()

        assertTrue(sources.size > 100, "운영 소스를 찾지 못했다(${sources.size}개). 경로가 틀렸다: $backendRoot")
        assertTrue(
            sources.any { it.name == "CreditService.kt" },
            "CreditService.kt 를 스캔 대상에서 놓쳤다",
        )
    }

    @Test
    @DisplayName("운영 코드에 출처 불명 환불 호출이 없다")
    fun noProductionCallerOfLegacyRefund() {
        val callers = productionSources().filter { file ->
            // 선언부(`fun refundCredit(`)와 KDoc 언급은 호출이 아니다. 실제 호출 형태만 본다.
            Regex("""(?<!fun )\brefundCredit\s*\(""").containsMatchIn(
                file.readLines()
                    .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
                    .joinToString("\n"),
            ) && file.name != "CreditService.kt"
        }

        assertTrue(
            callers.isEmpty(),
            buildString {
                appendLine("출처 불명 환불을 부르는 운영 코드가 생겼다. 구매 크레딧이 무료 크레딧으로 바뀐다.")
                appendLine("refundAllocation(CreditAllocation) 으로 바꾸세요.")
                callers.forEach { appendLine("  - ${it.relativeTo(backendRoot)}") }
            },
        )
    }

    /**
     * 살아 있는 경로는 **계속 쓰여야 한다.** 이 테스트가 "아무도 환불하지 않는다"를
     * 고정하는 것으로 오해되면 안 된다 — 환불은 여전히 일어나야 하고, 다만
     * 출처를 보존하는 API 로 일어나야 한다.
     */
    @Test
    @DisplayName("운영 코드가 출처 보존 환불 API 를 실제로 쓴다")
    fun productionUsesTheAllocationAwareApi() {
        val users = productionSources().filter { file ->
            file.name != "CreditService.kt" && file.readText().contains("refundAllocation(")
        }

        assertTrue(
            users.isNotEmpty(),
            "refundAllocation 을 쓰는 운영 코드가 없다 — 환불 경로가 통째로 사라졌을 수 있다",
        )
    }
}
