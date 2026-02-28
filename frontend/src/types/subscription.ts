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
  billingCycle: string
  currentPeriodEnd: string | null
  nextBillingDate: string | null
  features: PlanFeatures | string[]
  paddleSubscriptionId?: string | null
  paddleCustomerId?: string | null
  trialEnd?: string | null
  pausedAt?: string | null
  resumeAt?: string | null
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
  status: 'COMPLETED' | 'FAILED' | 'REFUNDED'
  paidAt: string
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

export interface CouponValidation {
  valid: boolean
  code: string
  discountType: string | null
  discountValue: number | null
  calculatedDiscount: number | null
  message: string | null
}

export interface UsageAlertConfig {
  id: number
  alertType: string
  thresholdPercent: number
  enabled: boolean
  lastAlertedAt: string | null
}
