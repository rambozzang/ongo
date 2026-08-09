import { defineStore } from 'pinia'
import type { TeamMember, TeamInvite, TeamIncomingInvite, TeamActivity, TeamRole } from '@/types/team'
import { teamApi } from '@/api/team'
import type { TeamInvitationResponse, TeamMemberResponse } from '@/api/team'

interface TeamState {
  teamName: string
  members: TeamMember[]
  invites: TeamInvite[]
  incomingInvites: TeamIncomingInvite[]
  activities: TeamActivity[]
  loading: boolean
  error: string | null
}

function mapApiMember(m: TeamMemberResponse): TeamMember {
  return {
    id: m.id,
    name: m.memberName ?? m.memberEmail,
    email: m.memberEmail,
    avatar: '#6366F1',
    role: (m.role?.toLowerCase() ?? 'viewer') as TeamRole,
    joinedAt: m.joinedAt ?? m.createdAt ?? new Date().toISOString(),
    lastActiveAt: m.joinedAt ?? new Date().toISOString(),
    isOnline: false,
  }
}

function mapApiInvite(m: TeamMemberResponse): TeamInvite {
  const invitedAt = m.invitedAt ?? m.createdAt ?? new Date().toISOString()
  const normalizedStatus = m.status.toLowerCase()
  return {
    id: m.id,
    email: m.memberEmail,
    role: (m.role?.toLowerCase() ?? 'viewer') as TeamRole,
    status: normalizedStatus === 'expired' ? 'expired' : normalizedStatus === 'accepted' || normalizedStatus === 'joined' ? 'accepted' : 'pending',
    invitedAt,
    expiresAt: m.expiresAt ?? new Date(new Date(invitedAt).getTime() + 7 * 24 * 60 * 60 * 1000).toISOString(),
  }
}

function mapIncomingInvite(m: TeamInvitationResponse): TeamIncomingInvite {
  const invitedAt = m.invitedAt ?? new Date().toISOString()
  const normalizedStatus = m.status.toLowerCase()
  return {
    id: m.id,
    workspaceId: m.workspaceId,
    workspaceName: m.workspaceName,
    ownerId: m.ownerId,
    role: (m.role?.toLowerCase() ?? 'viewer') as TeamRole,
    status: normalizedStatus === 'expired' ? 'expired' : 'pending',
    invitedAt,
    expiresAt: m.expiresAt ?? new Date(new Date(invitedAt).getTime() + 7 * 24 * 60 * 60 * 1000).toISOString(),
  }
}

const emptyState = (): TeamState => ({
  teamName: '내 팀',
  members: [],
  invites: [],
  incomingInvites: [],
  activities: [],
  loading: false,
  error: null,
})

export const useTeamStore = defineStore('team', {
  state: (): TeamState => emptyState(),

  getters: {
    onlineMembers: (state): TeamMember[] => {
      return state.members.filter((m) => m.isOnline)
    },

    membersByRole: (state) => {
      return (role: TeamRole): TeamMember[] => {
        return state.members.filter((m) => m.role === role)
      }
    },

    pendingInvites: (state): TeamInvite[] => {
      return state.invites.filter((i) => i.status === 'pending')
    },
  },

  actions: {
    async fetchMembers() {
      this.loading = true
      this.error = null
      try {
        const [entries, incoming] = await Promise.all([
          teamApi.listMembers(),
          teamApi.listIncomingInvitations(),
        ])
        this.members = entries
          .filter((entry) => ['JOINED', 'ACCEPTED'].includes(entry.status.toUpperCase()))
          .map(mapApiMember)
        this.invites = entries
          .filter((entry) => !['JOINED', 'ACCEPTED'].includes(entry.status.toUpperCase()))
          .map(mapApiInvite)
        this.incomingInvites = incoming.map(mapIncomingInvite)
      } catch (error) {
        this.error = error instanceof Error ? error.message : '팀 정보를 불러오지 못했습니다'
      } finally {
        this.loading = false
      }
    },

    async acceptInvitation(invitationId: number) {
      await teamApi.acceptInvitation(invitationId)
      this.incomingInvites = this.incomingInvites.filter((invite) => invite.id !== invitationId)
    },

    async declineInvitation(invitationId: number) {
      await teamApi.declineInvitation(invitationId)
      this.incomingInvites = this.incomingInvites.filter((invite) => invite.id !== invitationId)
    },

    async inviteMember(email: string, role: TeamRole) {
      const result = await teamApi.inviteMember({ email, role: role.toUpperCase() })
      this.invites.unshift(mapApiInvite(result))
    },

    async removeMember(memberId: number) {
      await teamApi.removeMember(memberId)
      this.members = this.members.filter((m) => m.id !== memberId)
    },

    async updateRole(memberId: number, newRole: TeamRole) {
      const member = this.members.find((m) => m.id === memberId)
      if (!member) return

      await teamApi.updateRole(memberId, { role: newRole.toUpperCase() })
      member.role = newRole
    },

    async cancelInvite(inviteId: number) {
      await teamApi.removeMember(inviteId)
      this.invites = this.invites.filter((i) => i.id !== inviteId)
    },

    async resendInvite(inviteId: number) {
      const result = await teamApi.resendInvite(inviteId)
      const index = this.invites.findIndex((i) => i.id === inviteId)
      if (index !== -1) this.invites[index] = mapApiInvite(result)
    },
  },
})
