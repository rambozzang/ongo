export type TeamRole = 'owner' | 'admin' | 'editor' | 'viewer'
export type InviteStatus = 'pending' | 'accepted' | 'expired'

export interface TeamMember {
  id: number
  name: string
  email: string
  avatar: string
  role: TeamRole
  joinedAt: string
  lastActiveAt: string
  isOnline: boolean
}

export interface TeamInvite {
  id: number
  email: string
  role: TeamRole
  status: InviteStatus
  invitedAt: string
  expiresAt: string
}

export interface TeamActivity {
  id: number
  memberId: number
  memberName: string
  action: string
  target: string
  createdAt: string
}
