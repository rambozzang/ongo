import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { PermissionStatus } from '@/composables/usePermission'

export interface TeamMemberResponse {
  id: number
  memberEmail: string
  memberName: string | null
  role: string
  status: string
  invitedAt: string | null
  joinedAt: string | null
  createdAt: string | null
}

export interface InviteMemberRequest {
  email: string
  role: string
}

export interface UpdateRoleRequest {
  role: string
}

export interface MemberPermissionsResponse {
  memberId: number
  memberName: string | null
  memberEmail: string
  role: string
  permissions: Record<string, PermissionStatus>
}

export interface TeamPermissionsResponse {
  members: MemberPermissionsResponse[]
}

export interface UpdatePermissionsRequest {
  permissions: Record<string, boolean>
}

export interface PermissionCatalogResponse {
  permissions: Array<{ name: string; category: string; description: string }>
  roleDefaults: Record<string, string[]>
}

export const teamApi = {
  listMembers() {
    return apiClient.get<ResData<TeamMemberResponse[]>>('/team/members').then(unwrapResponse)
  },

  inviteMember(request: InviteMemberRequest) {
    return apiClient.post<ResData<TeamMemberResponse>>('/team/invite', request).then(unwrapResponse)
  },

  updateRole(id: number, request: UpdateRoleRequest) {
    return apiClient.put<ResData<TeamMemberResponse>>(`/team/members/${id}/role`, request).then(unwrapResponse)
  },

  removeMember(id: number) {
    return apiClient.delete<ResData<void>>(`/team/members/${id}`).then(unwrapResponse)
  },

  getTeamPermissions() {
    return apiClient.get<ResData<TeamPermissionsResponse>>('/team/permissions').then(unwrapResponse)
  },

  getMemberPermissions(memberId: number) {
    return apiClient
      .get<ResData<MemberPermissionsResponse>>(`/team/members/${memberId}/permissions`)
      .then(unwrapResponse)
  },

  updateMemberPermissions(memberId: number, request: UpdatePermissionsRequest) {
    return apiClient
      .put<ResData<MemberPermissionsResponse>>(`/team/members/${memberId}/permissions`, request)
      .then(unwrapResponse)
  },

  getPermissionCatalog() {
    return apiClient
      .get<ResData<PermissionCatalogResponse>>('/team/permissions/catalog')
      .then(unwrapResponse)
  },
}
