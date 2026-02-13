import { defineStore } from 'pinia'
import type { TeamMember, TeamInvite, TeamActivity, TeamRole } from '@/types/team'
import { teamApi } from '@/api/team'
import type { TeamMemberResponse } from '@/api/team'

interface TeamState {
  teamName: string
  members: TeamMember[]
  invites: TeamInvite[]
  activities: TeamActivity[]
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

const emptyState = (): TeamState => ({
  teamName: '',
  members: [],
  invites: [],
  activities: [],
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
      try {
        const members = await teamApi.listMembers()
        this.members = members.map(mapApiMember)
      } catch {
        // API failed — keep empty state
        this.members = []
      }
    },

    async inviteMember(email: string, role: TeamRole) {
      const result = await teamApi.inviteMember({ email, role: role.toUpperCase() })
      const member = mapApiMember(result)
      this.members.unshift(member)
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

    cancelInvite(inviteId: number) {
      this.invites = this.invites.filter((i) => i.id !== inviteId)
    },

    resendInvite(inviteId: number) {
      const invite = this.invites.find((i) => i.id === inviteId)
      if (!invite) return

      invite.status = 'pending'
      invite.invitedAt = new Date().toISOString()
      invite.expiresAt = new Date(Date.now() + 1000 * 60 * 60 * 24 * 7).toISOString()
    },
  },
})
