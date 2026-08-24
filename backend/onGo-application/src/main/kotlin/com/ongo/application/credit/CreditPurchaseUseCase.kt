package com.ongo.application.credit

import com.ongo.common.enums.CreditPackage
import com.ongo.common.exception.BusinessException
import org.springframework.stereotype.Service

/**
 * 크레딧 패키지 조회 전용.
 *
 * ## 왜 여기서 구매를 처리하지 않는가
 *
 * 예전 `purchaseCredits` 는 PG 를 부르지 않은 채 PENDING 결제 행 하나를 만들고
 * `creditsAdded = 500`, `expiresAt = now + validDays` 를 돌려줬다. 컨트롤러는 그 위에
 * "크레딧 충전이 완료되었습니다"를 붙였다. **크레딧은 지급된 적이 없다.**
 *
 * 게다가 그 행은 완료될 수도 없었다. 지급 로직
 * [com.ongo.application.portone.PortOnePaymentService] `completeCredit` 은
 * `description` 을 `CREDIT|<패키지>` 형식으로 파싱하는데, 이 경로가 쓰던 형식은
 * `스타터 팩 (500 크레딧)` 이라 패키지를 꺼낼 수 없다. 결제 행만 남고 크레딧은 영원히
 * 지급되지 않는, 아무도 치우지 않는 고아 행이었다.
 *
 * ## 왜 통합하지 않고 막는가
 *
 * 정상 경로가 이미 끝까지 동작한다:
 * `CreditPurchaseModal` → `usePortOne().openCreditCheckout()`
 * → `POST /api/v1/portone/checkout/credit` → PortOne 브라우저 SDK
 * → `POST /api/v1/portone/payments/{id}/complete` → PG 재조회로 상태·금액·통화 검증
 * → `addPurchasedCredits`.
 *
 * 이 레거시 경로를 그 계약으로 바꾸면 같은 일을 하는 URL 이 두 개가 된다. 응답 형태도
 * 다르다 — 브라우저 SDK 는 storeId·channelKey 가 담긴 checkout intent 가 필요한데
 * 레거시 응답에는 그런 필드가 없다. 그리고 이 엔드포인트를 부르는 클라이언트는 **0개**다.
 * 그래서 통합이 아니라 차단이 더 작고 안전하다.
 *
 * ## 구조적으로 막는다
 *
 * 이 클래스는 이제 리포지터리를 **하나도 주입받지 않는다.** 결제 행을 만들 수단도,
 * 크레딧을 지급할 수단도 없다. 나중에 누가 예외를 지워도 여기서 부작용이 일어날 수
 * 없다 — 검사가 아니라 구조가 보장한다.
 */
@Service
class CreditPurchaseUseCase {

    /**
     * 레거시 구매 경로를 거절한다. 항상 던지므로 정상 반환이 없다.
     *
     * 결제 행을 만들지 않는다. 만들면 PG 에 대응하는 거래가 없는 PENDING 행이 쌓이고,
     * 그건 매출 대사에서 "결제 시도"로 잘못 세어진다.
     */
    fun rejectLegacyPurchase(): Nothing = throw BusinessException(
        "CREDIT_PURCHASE_PATH_UNSUPPORTED",
        "이 경로로는 크레딧을 구매할 수 없습니다. 구독·크레딧 화면의 결제 버튼을 이용해 주세요.",
    )

    fun getPackages(): List<CreditPackageInfo> {
        return CreditPackage.entries.map { pkg ->
            CreditPackageInfo(
                name = pkg.name,
                displayName = pkg.displayName,
                credits = pkg.credits,
                price = pkg.price,
                validDays = pkg.validDays,
                pricePerCredit = pkg.price.toDouble() / pkg.credits,
            )
        }
    }
}

data class CreditPackageInfo(
    val name: String,
    val displayName: String,
    val credits: Int,
    val price: Int,
    val validDays: Int,
    val pricePerCredit: Double,
)
