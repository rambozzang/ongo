import { ref, computed } from 'vue'
import { teamApi } from '@/api/team'

export type PermissionStatus = 'GRANTED' | 'DENIED' | 'INHERITED'

export interface MemberPermissions {
  memberId: number
  memberName: string | null
  memberEmail: string
  role: string
  permissions: Record<string, PermissionStatus>
}

const cachedPermissions = ref<Record<string, PermissionStatus>>({})
const permissionsLoaded = ref(false)
const loading = ref(false)

export const PERMISSION_CATEGORIES: Record<string, { label: string; permissions: string[] }> = {
  VIDEO: {
    label: '영상',
    permissions: ['VIDEO_CREATE', 'VIDEO_READ', 'VIDEO_UPDATE', 'VIDEO_DELETE', 'VIDEO_PUBLISH'],
  },
  SCHEDULE: {
    label: '예약',
    permissions: ['SCHEDULE_CREATE', 'SCHEDULE_READ', 'SCHEDULE_UPDATE', 'SCHEDULE_DELETE'],
  },
  ANALYTICS: {
    label: '분석',
    permissions: ['ANALYTICS_READ', 'ANALYTICS_EXPORT'],
  },
  AI: {
    label: 'AI',
    permissions: ['AI_USE', 'AI_BATCH'],
  },
  TEAM: {
    label: '팀',
    permissions: ['TEAM_MANAGE', 'TEAM_INVITE', 'TEAM_REMOVE'],
  },
  APPROVAL: {
    label: '승인',
    permissions: ['APPROVAL_CREATE', 'APPROVAL_APPROVE', 'APPROVAL_REJECT'],
  },
  SETTINGS: {
    label: '설정',
    permissions: ['SETTINGS_READ', 'SETTINGS_UPDATE'],
  },
  BILLING: {
    label: '결제',
    permissions: ['BILLING_READ', 'BILLING_MANAGE'],
  },
  AUTOMATION: {
    label: '자동화',
    permissions: ['AUTOMATION_CREATE', 'AUTOMATION_UPDATE', 'AUTOMATION_DELETE'],
  },
}

export function usePermission() {
  function hasPermission(permission: string): boolean {
    const status = cachedPermissions.value[permission]
    return status === 'GRANTED' || status === 'INHERITED'
  }

  function checkPermissions(permissions: string[]): Record<string, boolean> {
    return permissions.reduce(
      (acc, perm) => {
        acc[perm] = hasPermission(perm)
        return acc
      },
      {} as Record<string, boolean>,
    )
  }

  async function loadPermissions(memberId: number) {
    loading.value = true
    try {
      const result = await teamApi.getMemberPermissions(memberId)
      cachedPermissions.value = result.permissions
      permissionsLoaded.value = true
    } catch {
      // keep current state
    } finally {
      loading.value = false
    }
  }

  function setPermissions(permissions: Record<string, PermissionStatus>) {
    cachedPermissions.value = permissions
    permissionsLoaded.value = true
  }

  const isLoaded = computed(() => permissionsLoaded.value)

  return {
    hasPermission,
    checkPermissions,
    loadPermissions,
    setPermissions,
    cachedPermissions,
    isLoaded,
    loading,
  }
}
