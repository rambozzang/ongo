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

export interface CreditPackage {
  name: string
  credits: number
  price: number
  pricePerCredit: number
  validDays: number
}

export const CREDIT_PACKAGES: CreditPackage[] = [
  { name: '스타터 팩', credits: 500, price: 4900, pricePerCredit: 9.8, validDays: 30 },
  { name: '베이직 팩', credits: 1200, price: 9900, pricePerCredit: 8.3, validDays: 60 },
  { name: '프로 팩', credits: 3000, price: 19900, pricePerCredit: 6.6, validDays: 90 },
  { name: '비즈니스 팩', credits: 10000, price: 49900, pricePerCredit: 5.0, validDays: 180 },
]

export interface PurchaseCreditRequest {
  packageType: string
  paymentMethod: string
}
