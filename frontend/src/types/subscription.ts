export type PlanType = 'FREE' | 'STARTER' | 'PRO' | 'BUSINESS'

export type SubscriptionStatus = 'ACTIVE' | 'CANCELLED' | 'PAST_DUE' | 'FREE' | 'TRIALING' | 'PAUSED'

export interface PlanFeatures {
  maxPlatforms: number
  monthlyUploads: number
  scheduleDays: number
  analyticsDays: number
  storageGB: number
  freeCredits: number
  maxTeamMembers: number
}

export interface Subscription {
  id?: number
  userId?: number
  planType: PlanType
  status: SubscriptionStatus
  price: number
  billingCycle: 'MONTHLY' | 'YEARLY'
  currentPeriodEnd: string | null
  nextBillingDate: string | null
  features: PlanFeatures | string[]
  paddleSubscriptionId?: string | null
  paddleCustomerId?: string | null
  trialEnd?: string | null
  pausedAt?: string | null
  resumeAt?: string | null
  pendingPlanType?: PlanType | null
  pendingBillingCycle?: 'MONTHLY' | 'YEARLY' | null
}

export interface ChangePlanResponse {
  subscription: Subscription
  proratedAmount: number | null
  effectiveDate: string
}

export interface Plan {
  type: PlanType
  name: string
  price: number
  yearlyPrice: number
  maxPlatforms: number
  maxUploadsPerMonth: number
  maxScheduleDays: number
  analyticsPeriodDays: number
  storageMb: number
  commentManagement: boolean
  teamMembers: number
  freeAiCredits: number
  support: string
}

/**
 * 로그인 전 가격 안내 전용 상수.
 *
 * **로그인 후 화면에서는 쓰지 않는다.** 가격과 한도는 서버가 결제 기준으로 삼는 값이고,
 * 인증된 `GET /subscriptions/plans` 가 그 값을 내려준다. 화면이 상수를 대신 그리면
 * 한쪽이 뒤처졌을 때 사용자가 본 금액과 청구액이 갈린다.
 *
 * `LoginView` 만 예외다 — 인증 전이라 그 API 를 부를 수 없다. 그 화면의 숫자가 서버와
 * 어긋나지 않도록 `planPricingContract.test.ts` 가 두 값을 맞춰 본다.
 */
export const PLANS: Plan[] = [
  {
    type: 'FREE',
    name: 'Free',
    price: 0,
    yearlyPrice: 0,
    maxPlatforms: 1,
    maxUploadsPerMonth: 5,
    maxScheduleDays: 0,
    analyticsPeriodDays: 7,
    storageMb: 1024,
    commentManagement: false,
    teamMembers: 0,
    freeAiCredits: 30,
    support: '커뮤니티',
  },
  {
    type: 'STARTER',
    name: 'Starter',
    price: 9900,
    yearlyPrice: 99000,
    maxPlatforms: 3,
    maxUploadsPerMonth: 30,
    maxScheduleDays: 7,
    analyticsPeriodDays: 30,
    storageMb: 10240,
    commentManagement: false,
    teamMembers: 0,
    freeAiCredits: 100,
    support: '이메일',
  },
  {
    type: 'PRO',
    name: 'Pro',
    price: 19900,
    yearlyPrice: 199000,
    maxPlatforms: 4,
    maxUploadsPerMonth: 100,
    maxScheduleDays: 30,
    analyticsPeriodDays: 365,
    storageMb: 51200,
    commentManagement: true,
    teamMembers: 2,
    freeAiCredits: 300,
    support: '우선 이메일',
  },
  {
    type: 'BUSINESS',
    name: 'Business',
    price: 49900,
    yearlyPrice: 499000,
    maxPlatforms: 4,
    maxUploadsPerMonth: -1,
    maxScheduleDays: 90,
    analyticsPeriodDays: -1,
    storageMb: 204800,
    commentManagement: true,
    teamMembers: 10,
    freeAiCredits: 1000,
    support: '전담 매니저',
  },
]

export interface Payment {
  id: number
  userId: number
  type: 'SUBSCRIPTION' | 'CREDIT'
  amount: number
  description: string
  /**
   * 서버 `PaymentStatus` 를 그대로 옮긴다.
   *
   * PENDING 이 빠져 있었는데, 체크아웃은 결제창을 열기 **전에** PENDING 행을 만들므로
   * 사용자가 결제를 취소하거나 브라우저를 닫으면 그 상태로 남아 목록에 그대로 나온다.
   * 타입에 없다고 값이 안 오는 것은 아니다.
   */
  status: 'PENDING' | 'COMPLETED' | 'FAILED' | 'REFUNDED'
  /**
   * 서버가 보내는 필드명은 `createdAt` 이다(`PaymentItem`).
   *
   * 예전에는 `paidAt` 으로 읽어서 항상 undefined 였고, 화면의 날짜 칸은 모든 행에서
   * "Invalid Date" 였다. 결제 시각이 아니라 **결제 행이 만들어진 시각**이라는 점도
   * 이름 그대로 두는 편이 정확하다 — PENDING 행에는 결제 시각이 아직 없다.
   */
  createdAt: string | null
  receiptUrl: string | null
}

export interface ChangePlanRequest {
  targetPlan: PlanType
  billingCycle?: 'MONTHLY' | 'YEARLY'
}

export interface PlanInfo {
  planType: PlanType
  price: number
  yearlyPrice: number
  features: PlanFeatures
  recommended: boolean
}

export interface Coupon {
  id: number
  code: string
  description: string | null
  discountType: string
  discountValue: number
  applicablePlans: string | null
  maxUses: number | null
  usedCount: number
  active: boolean
  validFrom: string
  validUntil: string | null
}

/*
 * CouponValidation 은 제거했다. 고객 쿠폰 엔드포인트가 항상 실패하므로 이 응답을 받는
 * 클라이언트가 없다. 남겨두면 "할인 응답 계약이 이미 있다"고 읽힌다.
 */

export interface UsageAlertConfig {
  id: number
  alertType: string
  thresholdPercent: number
  enabled: boolean
  lastAlertedAt: string | null
}
