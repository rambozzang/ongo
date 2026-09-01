package com.ongo.application.translation

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.credit.CreditAllocation
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.credit.CreditService
import com.ongo.application.translation.dto.*
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.translation.TranslationCreditAllocation
import com.ongo.domain.translation.TranslationRepository
import com.ongo.domain.translation.VideoTranslation
import com.ongo.domain.video.VideoRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class TranslationUseCase(
    private val translationRepository: TranslationRepository,
    private val videoRepository: VideoRepository,
    private val creditService: CreditService,
    private val chatClientResolver: ChatClientResolver,
    private val rateLimiter: AiRateLimiter,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 정산의 커밋 경계. 표식과 환불이 **한 트랜잭션**이어야 한다.
     *
     * `REQUIRES_NEW` 인 이유: 이 정산은 virtual thread(트랜잭션 없음)와 복구 스케줄러
     * 양쪽에서 불린다. 호출자가 향후 트랜잭션을 열더라도 그 경계에 끌려들어가면
     * 환불 커밋 시점이 달라진다.
     */
    private val settleTx = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }
    private val objectMapper = jacksonObjectMapper()

    companion object {
        const val CREDIT_PER_LANGUAGE = 3

        /**
         * 이 시간보다 오래 `claimed_at` 이 갱신되지 않으면 워커가 죽은 것으로 본다.
         *
         * 짧으면 살아 있는 번역을 중복 실행해 LLM 을 두 번 태우고, 길면 고객이 그만큼
         * 오래 기다린다. 번역은 보통 수 초라 10 분이면 정상 실행과 겹치지 않는다.
         */
        const val STALE_AFTER_MINUTES = 10L

        /** 실행 시도 상한. 넘으면 재실행 대신 환불한다 — 멈춘 채로 두지 않는다. */
        const val MAX_ATTEMPTS = 3

        /** 한 복구 주기에 되살릴 최대 건수. 한 번에 몰아 LLM 을 폭주시키지 않는다. */
        const val RECOVERY_BATCH_LIMIT = 50

        /** 진행 중 상태. 이 상태의 언어는 다시 청구하지 않는다. */
        const val STATUS_TRANSLATING = "TRANSLATING"
        val SUPPORTED_LANGUAGES = listOf("ko", "en", "ja", "zh", "es", "fr", "de", "pt")
    }

    fun getTranslations(videoId: Long): List<TranslationResponse> =
        translationRepository.findByVideoId(videoId).map { it.toResponse() }

    /**
     * ## 이미 진행 중인 언어는 다시 청구하지 않는다
     *
     * `video_translations` 는 `(video_id, language)` 유니크(`uq_video_translations_video_language`,
     * V62)라 언어당 행은 하나다. 예전에는 그 행이 `TRANSLATING` 이어도 상태를 되돌리고
     * 재실행했다. 같은 언어를 두 번 요청하면 **두 번 차감**되고 두 스레드가 같은 행을
     * 갱신했는데, 실패 시 환불은 한 번뿐이었다.
     *
     * 이제 실제로 새로 시작하는 언어만 세어 그만큼만 차감한다. 진행 중인 언어는 현재
     * 상태를 그대로 응답에 담는다 — 요청은 성공이고, 결과는 이미 만들어지는 중이다.
     *
     * ## 스레드는 커밋 뒤에 띄운다
     *
     * 이 메서드는 `@Transactional` 이다. 예전에는 커밋 전에 virtual thread 를 띄워, 그
     * 스레드가 **아직 커밋되지 않은 행**을 갱신하려 했다(0행 갱신 또는 락 대기).
     * `afterCommit` 으로 미뤄 그 창을 없앤다. 트랜잭션이 없으면(단위 테스트 등) 즉시
     * 실행한다.
     *
     * 신규 INSERT 경합은 유니크 제약이 판정한다. 동시에 같은 언어를 넣으면 한쪽이 예외로
     * 끝나고, 이 메서드가 트랜잭션이므로 **차감도 함께 롤백**된다.
     */
    @Transactional
    fun requestTranslation(userId: Long, videoId: Long, request: TranslateRequest): List<TranslationResponse> {
        val video = videoRepository.findById(videoId) ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) throw BusinessException("FORBIDDEN", "해당 영상에 대한 권한이 없습니다")

        /*
         * **중복을 먼저 없앤다.**
         *
         * 예전에는 `["en","en"]` 을 받으면 비용을 2 배로 계산해 6 크레딧을 차감한 뒤,
         * 두 번째 INSERT 가 `uq_video_translations_video_language` 에 걸려 트랜잭션이
         * 통째로 롤백됐다. 사용자는 이유를 알 수 없는 500 을 받았다.
         *
         * 차감·레이트리밋·저장이 전부 **고유 언어 수** 기준이어야 한다.
         */
        val validLangs = request.languages.filter { it in SUPPORTED_LANGUAGES }.distinct()
        if (validLangs.isEmpty()) throw BusinessException("INVALID_LANGUAGE", "지원 언어: $SUPPORTED_LANGUAGES")

        val existingByLang = validLangs.associateWith { translationRepository.findByVideoIdAndLanguage(videoId, it) }
        val startingLangs = validLangs.filter { existingByLang[it]?.status != STATUS_TRANSLATING }

        /*
         * ## 레이트리밋 단위: **언어당 1 토큰**
         *
         * [startingLangs] 는 이 요청이 실제로 띄울 `translateAsync` 개수이고, 각 스레드는
         * LLM 을 정확히 1 회 부른다. [AiRateLimiter] 의 토큰 1 개는 **LLM 요청 1 회**를
         * 뜻하므로(다른 유료 경로가 전부 그렇게 쓴다) 여기서도 호출 수만큼 쓴다.
         *
         * 요청당 1 토큰으로 하면 8 개 언어를 요청해 **분당 80 회**를 태울 수 있다. 같은
         * 시간에 메타 생성 사용자는 10 회로 묶여 있다 — 제한이 의미를 잃는다.
         * 지원 언어가 8 개라 한 요청의 최대 소모는 8 토큰이고, 한도 10 을 넘지 않는다.
         *
         * `tryConsume(n)` 은 원자적이라 모자라면 하나도 쓰지 않는다.
         *
         * **차감·저장보다 먼저** 부른다. 뒤에 두면 거절된 요청이 이미 크레딧을 깎고
         * `TRANSLATING` 행을 남긴 뒤다. 이 메서드가 `@Transactional` 이므로 여기서
         * 던지면 아무것도 쓰이지 않은 채 롤백된다.
         *
         * 이미 `TRANSLATING` 인 언어는 [startingLangs] 에서 빠진다 — 새 LLM 호출이 없으니
         * 토큰도 쓰지 않는다. 모두 진행 중이면 limiter 를 아예 부르지 않는다.
         *
         * [translateAsync] 안에서 다시 부르지 않는다. 그러면 같은 호출을 두 번 센다.
         */
        if (startingLangs.isNotEmpty()) {
            rateLimiter.checkRateLimit(userId, startingLangs.size)
        }

        val totalCost = startingLangs.size * CREDIT_PER_LANGUAGE
        /*
         * 차감 영수증. 언어별 실패는 여기서 [CREDIT_PER_LANGUAGE] 만큼씩 떼어내 되돌린다.
         *
         * 한 번에 합산 차감하므로 무료분과 여러 구매 패키지에 걸쳐 있을 수 있다. 금액만
         * 들고 환불하면 구매분이 만료되는 무료분으로 바뀌거나 사라진다
         * ([com.ongo.application.credit.CreditAllocation] 참고).
         *
         * 언어마다 별도 스레드에서 떼어내므로 영수증은 그 계산을 동기화한다. 전부 실패해도
         * 총합이 차감액을 넘지 않는다.
         */
        val allocation = if (totalCost > 0) {
            creditService.validateAndDeduct(userId, totalCost, "TRANSLATION")
        } else {
            CreditAllocation.empty(userId, "TRANSLATION")
        }

        /*
         * 요청 영수증을 **언어별로 쪼갠다.**
         *
         * 작업 단위는 언어 행이다. 전체 영수증을 모든 행에 복사하면 세 행이 같은 몫을
         * 환불해 없던 크레딧이 생긴다. 여기서 언어당 [CREDIT_PER_LANGUAGE] 만큼 떼어내
         * 그 행이 자기 몫만 소유하게 한다.
         *
         * 이 분해를 DB 에 남기는 것이 핵심이다. 클로저에만 두면 재시작과 함께 사라져,
         * 나중에 환불할 때 출처를 몰라 구매분이 무료분으로 바뀐다.
         */
        val perLanguage = startingLangs.associateWith {
            allocation.takeForRefund(CREDIT_PER_LANGUAGE)?.let { taken ->
                TranslationCreditAllocation(
                    // 환불 대상을 스냅샷이 직접 들고 간다. 복구 시점에 원본 영상이 없어도
                    // 누구에게 돌려줄지 알 수 있어야 한다.
                    userId = userId,
                    freeAmount = taken.freeAmount,
                    purchasedAmounts = taken.purchasedPortions.associate { p -> p.purchasedCreditId to p.amount },
                )
            }
        }

        val pending = mutableListOf<() -> Unit>()
        val responses = validLangs.map { lang ->
            val existing = existingByLang[lang]
            when {
                // 이미 돌고 있다. 재차감도 재실행도 하지 않는다.
                // 그 행이 멈춰 있다면 복구 스캐너([recoverStalledTranslations])가 집는다.
                existing != null && existing.status == STATUS_TRANSLATING -> existing.toResponse()

                existing != null -> {
                    val existingId = existing.id!!
                    translationRepository.update(existingId, status = STATUS_TRANSLATING, title = null, description = null, tags = null, subtitleContent = null)
                    // 재시도 행은 새 차감의 출처로 갈아끼운다. 이전 시도의 분해는 이미 정산됐다.
                    translationRepository.replaceCreditAllocation(existingId, perLanguage[lang])
                    pending += { runTranslation(existingId, videoId, userId, lang, video.title, video.description) }
                    translationRepository.findById(existingId)!!.toResponse()
                }

                else -> {
                    val saved = translationRepository.save(
                        VideoTranslation(
                            videoId = videoId,
                            language = lang,
                            status = STATUS_TRANSLATING,
                            creditAllocation = perLanguage[lang],
                        ),
                    )
                    val savedId = saved.id!!
                    pending += { runTranslation(savedId, videoId, userId, lang, video.title, video.description) }
                    saved.toResponse()
                }
            }
        }

        runAfterCommit { pending.forEach { it() } }
        return responses
    }

    /**
     * 커밋 뒤에 실행한다. 활성 트랜잭션이 없으면 즉시 실행한다.
     *
     * 비동기 작업이 커밋 전에 시작하면 아직 보이지 않는 행을 읽거나 갱신하려 한다.
     */
    private fun runAfterCommit(block: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            block()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = block()
            },
        )
    }

    /**
     * 번역 한 건을 실행한다. **원자적 claim 을 이긴 경우에만** 모델을 부른다.
     *
     * 요청 직후 실행과 복구 스캐너가 같은 진입점을 쓴다. claim 이 없으면 복구 tick 두 개가
     * 같은 행을 통과해 LLM 을 두 번 태우고, 사용자 재시도까지 겹치면 세 번이 된다.
     */
    private fun runTranslation(
        translationId: Long,
        videoId: Long,
        userId: Long,
        language: String,
        title: String,
        description: String?,
    ) {
        Thread.startVirtualThread {
            val now = LocalDateTime.now()
            val claimed = translationRepository.claimForTranslation(
                translationId,
                now,
                now.minusMinutes(STALE_AFTER_MINUTES),
            )
            if (claimed == null) {
                // 다른 워커가 살아서 잡고 있거나 이미 끝난 행이다. 아무것도 하지 않는다.
                log.debug("번역 행을 선점하지 못했다. 건너뛴다: translationId={}", translationId)
                return@startVirtualThread
            }

            /*
             * **상한을 넘으면 전달을 포기하고 돌려준다.**
             *
             * 상한이 없으면 죽는 입력 하나가 LLM 호출을 무한히 태운다. 반대로 상한에서
             * 그냥 멈추면 고객은 크레딧만 잃고 영원히 TRANSLATING 을 본다.
             * 전달하거나 환불하거나 둘 중 하나이며, 멈춘 상태로 두지 않는다.
             */
            if (claimed.attempts > MAX_ATTEMPTS) {
                log.error(
                    "번역 재시도 상한 초과. 환불하고 종료한다: translationId={} attempts={}",
                    translationId, claimed.attempts,
                )
                failAndRefund(translationId, userId, "재시도 한도를 초과했습니다")
                return@startVirtualThread
            }

            try {
                val langName = mapOf("ko" to "한국어", "en" to "English", "ja" to "日本語", "zh" to "中文", "es" to "Español", "fr" to "Français", "de" to "Deutsch", "pt" to "Português")[language] ?: language
                val prompt = buildString {
                    appendLine("다음 영상 메타데이터를 $langName($language)로 번역해주세요.")
                    appendLine("제목: $title")
                    if (!description.isNullOrBlank()) appendLine("설명: $description")
                    appendLine()
                    appendLine("JSON 형식으로 응답:")
                    appendLine("""{"title": "번역된 제목", "description": "번역된 설명"}""")
                }

                val response = chatClientResolver.resolve(userId).prompt()
                    .system("당신은 다국어 콘텐츠 번역 전문가입니다. 플랫폼에 최적화된 자연스러운 번역을 제공합니다.")
                    .user(prompt)
                    .call()
                    .content() ?: ""

                val jsonStart = response.indexOf("{")
                val jsonEnd = response.lastIndexOf("}") + 1
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    val json = objectMapper.readTree(response.substring(jsonStart, jsonEnd))
                    translationRepository.update(
                        translationId,
                        title = json.get("title")?.asText(),
                        description = json.get("description")?.asText(),
                        tags = null, subtitleContent = null,
                        status = "COMPLETED",
                    )
                } else {
                    /*
                     * 모델이 응답했지만 JSON 이 아니다. **사용자는 결과를 받지 못했다.**
                     *
                     * 예전에는 이 분기가 catch 밖의 정상 흐름이라 상태만 FAILED 로 적고
                     * 환불하지 않았다. 돈은 나가고 크레딧도 사라졌다. 결과가 없으면 실패이고,
                     * 실패는 환불한다 — 예외로 끝난 경우와 같은 대우다.
                     */
                    failAndRefund(translationId, userId, "AI 응답을 파싱할 수 없습니다")
                }
            } catch (e: Exception) {
                log.error("번역 실패: translationId={}", translationId, e)
                failAndRefund(translationId, userId, e.message)
            }
        }
    }

    /**
     * 실패한 번역을 닫고 **그 행에 저장된 출처로** 크레딧을 돌려준다.
     *
     * ## 표식이 먼저다 (예전과 반대)
     *
     * 예전에는 "돈을 먼저 돌려주고 상태를 적는다"였다. 인메모리 영수증이 중복 환불을
     * 막아 준다는 전제였는데, **재시작하면 그 카운터가 사라진다.** 복구 tick 과 사용자
     * 재시도가 같은 행을 두 번 환불할 수 있었다.
     *
     * 이제 [TranslationRepository.settleFailure] 의 조건부 갱신
     * (`WHERE status = 'TRANSLATING'`)이 승자를 정하고 **이긴 쪽만** 돌려준다. DB 가
     * 판정하므로 재시작을 견딘다.
     *
     * 표식과 환불은 **한 트랜잭션**이다. 환불이 실패하면 표식도 함께 롤백되어 다음
     * 복구 tick 이 다시 시도할 수 있다. 표식만 남으면 자동 재시도가 영구히 막힌다.
     */
    private fun failAndRefund(translationId: Long, userIdHint: Long?, reason: String?) {
        runCatching {
            settleTx.execute {
                val row = translationRepository.findById(translationId)
                if (row == null) {
                    log.error("번역 행이 사라져 정산하지 못했다: translationId={}", translationId)
                    return@execute
                }

                val won = translationRepository.settleFailure(translationId, "FAILED")
                if (!won) {
                    log.info("이미 정산된 번역이라 환불하지 않는다: translationId={}", translationId)
                    return@execute
                }

                val snapshot = row.creditAllocation
                if (snapshot == null) {
                    /*
                     * **V109 이전에 만들어진 행이다. 자동 환불하지 않는다.**
                     *
                     * 출처를 모르는 채 전액을 무료분으로 돌려주면 구매분이 월말에 사라지는
                     * 무료분이 되거나 free_monthly 한도에 걸려 증발한다. 그것이 이 스냅샷이
                     * 막으려는 손실이므로 여기서 다시 만들지 않는다.
                     *
                     * 상태는 확정한다 — 미정산으로 두면 복구 스캐너가 영원히 다시 집는다.
                     */
                    /*
                     * 출처를 모르면 **환불 대상도 확정할 수 없다.**
                     *
                     * 복구 경로는 원본 영상이 삭제된 뒤에도 실행되므로 `videos.user_id` 로
                     * 소유자를 되짚을 수 없다. 추측한 대상에게 크레딧을 넣는 것은 출처를
                     * 모른 채 무료분으로 돌려주는 것보다 나쁘다 — 남의 계정에 돈이 간다.
                     */
                    log.error(
                        "CRITICAL 수기 정산 필요: 차감 출처 분해가 없어 자동 환불을 하지 않았다. " +
                            "translationId={} userIdHint={} language={} amount={} reason={}",
                        translationId, userIdHint, row.language, CREDIT_PER_LANGUAGE, reason,
                    )
                    return@execute
                }

                /*
                 * 환불 대상은 **스냅샷이 들고 온 userId** 다. 호출부가 넘긴 값을 쓰면
                 * 안 된다 — 복구 경로가 실제로 `videoId` 를 userId 자리에 넘기고 있었고,
                 * 그대로면 존재하지 않거나 남의 계정으로 크레딧이 들어간다.
                 */
                creditService.refundAllocation(
                    CreditAllocation.restored(
                        userId = snapshot.userId,
                        featureName = "TRANSLATION_FAILED",
                        freeAmount = snapshot.freeAmount,
                        purchasedAmounts = snapshot.purchasedAmounts,
                    ),
                )
            }
        }.onFailure { error ->
            /*
             * 트랜잭션이 통째로 롤백됐다. 상태도 크레딧도 그대로이므로 다음 복구 tick 이
             * 다시 시도한다. 그 사실을 남겨 두어야 반복 실패를 운영이 알아챌 수 있다.
             */
            log.error(
                "번역 정산 실패. 다음 복구 주기에 재시도한다. translationId={} userIdHint={} reason={}",
                translationId, userIdHint, reason, error,
            )
        }
    }

    /**
     * 멈춘 번역을 되살린다.
     *
     * 프로세스가 죽으면 `TRANSLATING` 행은 아무도 집지 않는다. 예전에는 이 행이 **영원히**
     * 그 상태로 남았다 — 재요청도 이미 `TRANSLATING` 인 언어를 건너뛰므로, 사용자는 다시
     * 눌러도 멈춘 행만 돌려받았다. 크레딧은 나갔고 결과는 영영 오지 않았다.
     *
     * 재실행은 [TranslationRepository.claimForTranslation] 의 원자적 선점을 이긴 경우에만
     * 일어난다. 상한을 넘으면 재실행 대신 저장된 출처로 환불한다.
     */
    @Scheduled(
        fixedDelayString = "\${translation.recovery-delay-ms:60000}",
        initialDelayString = "\${translation.recovery-initial-delay-ms:15000}",
    )
    fun recoverStalledTranslations() {
        val stalled = runCatching {
            translationRepository.findStalled(LocalDateTime.now().minusMinutes(STALE_AFTER_MINUTES), RECOVERY_BATCH_LIMIT)
        }.getOrElse { error ->
            log.error("멈춘 번역 조회에 실패했다", error)
            return
        }
        if (stalled.isEmpty()) return

        log.info("멈춘 번역 {}건을 재개한다", stalled.size)
        for (row in stalled) {
            val id = row.id ?: continue
            val video = runCatching { videoRepository.findById(row.videoId) }.getOrNull()
            if (video == null) {
                // 원본이 사라졌다. 재실행할 대상이 없으므로 정산으로 닫는다.
                // 환불 대상은 저장된 스냅샷이 안다. 여기서 videoId 를 userId 자리에
                // 넘기면 남의 계정으로 크레딧이 들어간다 — 실제로 그랬다.
                log.warn("원본 영상이 없어 번역을 재개할 수 없다: translationId={} videoId={}", id, row.videoId)
                failAndRefund(id, row.creditAllocation?.userId, "원본 영상을 찾을 수 없습니다")
                continue
            }
            runTranslation(id, row.videoId, video.userId, row.language, video.title, video.description)
        }
    }

    @Transactional
    fun updateTranslation(userId: Long, translationId: Long, request: UpdateTranslationRequest): TranslationResponse {
        val translation = translationRepository.findById(translationId) ?: throw NotFoundException("번역", translationId)
        val video = videoRepository.findById(translation.videoId) ?: throw NotFoundException("영상", translation.videoId)
        if (video.userId != userId) throw BusinessException("FORBIDDEN", "해당 번역에 대한 권한이 없습니다")

        val tagsJson = request.tags?.let { objectMapper.writeValueAsString(it) }
        translationRepository.update(translationId, request.title, request.description, tagsJson, request.subtitleContent, null)
        return translationRepository.findById(translationId)!!.toResponse()
    }

    @Transactional
    fun deleteTranslation(userId: Long, translationId: Long) {
        val translation = translationRepository.findById(translationId) ?: throw NotFoundException("번역", translationId)
        val video = videoRepository.findById(translation.videoId) ?: throw NotFoundException("영상", translation.videoId)
        if (video.userId != userId) throw BusinessException("FORBIDDEN", "해당 번역에 대한 권한이 없습니다")
        translationRepository.delete(translationId)
    }

    private fun VideoTranslation.toResponse(): TranslationResponse {
        val tagList = try { objectMapper.readValue(tags ?: "[]", List::class.java) as List<String> } catch (_: Exception) { emptyList() }
        return TranslationResponse(
            id = id!!,
            videoId = videoId,
            language = language,
            title = title,
            description = description,
            tags = tagList,
            subtitleContent = subtitleContent,
            status = status,
            createdAt = createdAt?.toString(),
        )
    }
}
