export interface CreditBalance {
  totalBalance: number
  freeMonthly: number
  freeRemaining: number
  purchasedBalance: number
  freeResetDate: string
}

export interface PurchasedCredit {
  id: number
  packageName: string
  totalCredits: number
  remaining: number
  purchasedAt: string
  expiresAt: string
  status: 'ACTIVE' | 'EXPIRED' | 'EXHAUSTED'
}

export type CreditTransactionType = 'DEDUCT' | 'CHARGE' | 'FREE_RESET'

export interface CreditTransaction {
  id: number
  type: CreditTransactionType
  amount: number
  balanceAfter: number
  feature: string | null
  referenceId: number | null
  createdAt: string
}

/**
 * 서버 `GET /credits/packages` 응답. **가격·수량의 authoritative source 다.**
 *
 * `name` 이 `CreditPackage` enum 이름(결제에 보내는 식별자)이고, 사람이 읽을 이름은
 * `displayName` 이다. 화면 타입([CreditPackage])과 필드 이름이 다르므로 매핑해서 쓴다.
 */
export interface CreditPackageResponse {
  name: string
  displayName: string
  credits: number
  price: number
  validDays: number
  pricePerCredit: number
}

export interface CreditPackage {
  /**
   * 서버로 보내는 식별자. 백엔드 `CreditPackage` enum 이름과 일치해야 한다.
   * 표시명(`name`)을 보내면 Paddle 가격 ID 조회가 실패해 결제창이 열리지 않는다.
   */
  key: 'STARTER' | 'BASIC' | 'PRO' | 'BUSINESS'
  /** 화면 표시용 이름 */
  name: string
  credits: number
  price: number
  pricePerCredit: number
  validDays: number
}

/*
 * 패키지 목록 상수는 **일부러 두지 않는다.**
 *
 * 결제 금액은 서버가 `CreditPackage` enum 에서 계산한다(`CreditController.getPackages`).
 * 화면이 같은 숫자를 한 벌 더 들고 있으면 서버에서 가격을 바꾼 날 사용자가 본 금액과
 * 청구액이 갈리고, 그 차이는 결제창을 열기 전까지 아무 데도 드러나지 않는다.
 *
 * 그래서 `GET /credits/packages` 를 그대로 쓴다(`stores/credit.ts` 의 `fetchPackages`).
 * 조회에 실패하면 목록은 `null` 이고 화면은 살 수 있는 것이 없다고 말한다 — 오래된 숫자를
 * 대신 그리지 않는다. 이 규칙은 `planPricingContract.test.ts` 가 지킨다.
 */
