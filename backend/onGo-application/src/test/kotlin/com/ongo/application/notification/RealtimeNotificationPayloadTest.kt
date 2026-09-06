package com.ongo.application.notification

import com.ongo.application.credit.LowCreditAlertEvent
import com.ongo.application.credit.LowCreditAlertEventListener
import com.ongo.domain.notification.Notification
import com.ongo.domain.notification.NotificationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 실시간 알림이 **읽을 수 있는 문구를 싣고 나가는지** 고정한다.
 *
 * ## 무엇이 깨져 있었나
 *
 * 프런트는 `payload.title` 이 없으면 `message.type` 으로 폴백하고 본문은 빈 문자열로
 * 둔다(`frontend/src/composables/useWebSocket.ts` 의 `handleMessage`). 그런데 서버는
 * 숫자·식별자만 실어 보냈다.
 *
 * 결과는 토스트에 원문 enum 이 그대로 뜨는 것이다 — **"CREDIT_LOW"**, 본문은 비어 있다.
 * 정작 사람이 읽을 문구("AI 크레딧 부족 / 잔여 크레딧이 …")는 바로 옆에서 DB 에만
 * 저장된다. 알림센터를 열어야 보이는 것을 실시간 알림이라 부를 수는 없다.
 *
 * ## 왜 DB 행을 기준으로 단언하는가
 *
 * 문구를 테스트에 복사해 두면 서버 문구가 바뀔 때 **테스트만 지나가고 실시간 알림은
 * 옛 문구로 남는다.** 저장한 알림과 보낸 페이로드가 같은 출처인지를 본다.
 */
class RealtimeNotificationPayloadTest {

    private val notificationRepository = mockk<NotificationRepository>()
    private val websocket = mockk<WebSocketNotificationService>(relaxed = true)

    private val listener = LowCreditAlertEventListener(notificationRepository, websocket)

    /** 저장된 알림 행을 붙잡아 페이로드와 대조한다. */
    private fun capturedNotification(): Notification {
        val saved = slot<Notification>()
        verify { notificationRepository.save(capture(saved)) }
        return saved.captured
    }

    private fun capturedPayload(): Map<*, *> {
        val payload = slot<Any>()
        verify { websocket.sendToUser(7L, "CREDIT_LOW", capture(payload)) }
        return payload.captured as Map<*, *>
    }

    /**
     * **핵심 회귀.** 문구가 빠지면 사용자는 "CREDIT_LOW" 라는 토스트를 본다.
     */
    @Test
    @DisplayName("크레딧 부족 실시간 알림이 저장된 문구를 그대로 싣는다")
    fun lowCreditPayloadCarriesReadableText() {
        every { notificationRepository.save(any()) } answers { firstArg() }

        listener.handleLowCreditAlert(LowCreditAlertEvent(userId = 7L, balance = 4, freeMonthly = 30))

        val notification = capturedNotification()
        val payload = capturedPayload()

        // 저장한 문구와 보낸 문구가 같은 출처여야 한다 — 문구를 여기 복사해 두지 않는다.
        assertEquals(notification.title, payload["title"], "실시간 알림에 제목이 없어 enum 이름이 노출된다")
        assertEquals(notification.message, payload["message"], "실시간 알림 본문이 비어 있다")
    }

    /** 예전 클라이언트가 쓰던 숫자 필드를 없애면 그쪽 화면이 조용히 깨진다. */
    @Test
    @DisplayName("기존 숫자 필드를 함께 유지한다")
    fun lowCreditPayloadKeepsLegacyFields() {
        every { notificationRepository.save(any()) } answers { firstArg() }

        listener.handleLowCreditAlert(LowCreditAlertEvent(userId = 7L, balance = 4, freeMonthly = 30))

        val payload = capturedPayload()
        assertEquals(4, payload["balance"])
        assertEquals(30, payload["freeMonthly"])
    }

    /**
     * 문구가 비어 있지 않은지도 본다. 빈 문자열을 실어 보내면 위 두 단언은 통과하지만
     * 사용자가 보는 것은 여전히 빈 토스트다.
     */
    @Test
    @DisplayName("실린 문구가 비어 있지 않다")
    fun lowCreditPayloadTextIsNotBlank() {
        every { notificationRepository.save(any()) } answers { firstArg() }

        listener.handleLowCreditAlert(LowCreditAlertEvent(userId = 7L, balance = 4, freeMonthly = 30))

        val payload = capturedPayload()
        assertEquals(false, (payload["title"] as? String).isNullOrBlank(), "제목이 비어 있다")
        assertEquals(false, (payload["message"] as? String).isNullOrBlank(), "본문이 비어 있다")
    }
}
