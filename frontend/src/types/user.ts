import type { PlanType } from './subscription'

export interface User {
  id: number
  email: string
  name: string
  nickname: string
  profileImageUrl: string | null
  category: CreatorCategory | null
  planType: PlanType
  onboardingCompleted: boolean
  createdAt: string
  updatedAt: string
}

export interface UserProfile {
  nickname: string
  profileImageUrl: string | null
  category: CreatorCategory | null
}

export type CreatorCategory =
  | 'BEAUTY'
  | 'FOOD'
  | 'GAME'
  | 'DAILY'
  | 'EDUCATION'
  | 'IT'
  | 'TRAVEL'
  | 'MUSIC'
  | 'FASHION'
  | 'SPORTS'
  | 'OTHER'

export interface LoginRequest {
  code: string
  redirectUri: string
}

export interface AuthTokens {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  user: User
  isNewUser: boolean
}
