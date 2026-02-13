<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  ShieldCheckIcon,
  ArrowPathIcon,
  CheckIcon,
} from '@heroicons/vue/24/outline'
import { teamApi } from '@/api/team'
import type {
  MemberPermissionsResponse,
  TeamPermissionsResponse,
} from '@/api/team'
import type { PermissionStatus } from '@/composables/usePermission'
import { PERMISSION_CATEGORIES } from '@/composables/usePermission'

const loading = ref(true)
const saving = ref(false)
const saved = ref(false)
const teamPermissions = ref<TeamPermissionsResponse | null>(null)

// Track local changes: memberId -> { permName -> boolean }
const localChanges = ref<Record<number, Record<string, boolean>>>({})

const hasChanges = computed(() => {
  return Object.values(localChanges.value).some((perms) => Object.keys(perms).length > 0)
})

const categories = computed(() => {
  return Object.entries(PERMISSION_CATEGORIES).map(([key, config]) => ({
    key,
    label: config.label,
    permissions: config.permissions,
  }))
})

const rolePresets = [
  { key: 'OWNER', label: '소유자', color: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400' },
  { key: 'ADMIN', label: '관리자', color: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400' },
  { key: 'EDITOR', label: '에디터', color: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' },
  { key: 'VIEWER', label: '뷰어', color: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400' },
]

function getPermissionStatus(member: MemberPermissionsResponse, permission: string): PermissionStatus {
  // Check local changes first
  const memberChanges = localChanges.value[member.memberId]
  if (memberChanges && permission in memberChanges) {
    return memberChanges[permission] ? 'GRANTED' : 'DENIED'
  }
  return member.permissions[permission] ?? 'DENIED'
}

function isPermissionEnabled(member: MemberPermissionsResponse, permission: string): boolean {
  const status = getPermissionStatus(member, permission)
  return status === 'GRANTED' || status === 'INHERITED'
}

function togglePermission(member: MemberPermissionsResponse, permission: string) {
  if (member.role.toUpperCase() === 'OWNER') return // can't change owner permissions

  if (!localChanges.value[member.memberId]) {
    localChanges.value[member.memberId] = {}
  }

  const currentStatus = getPermissionStatus(member, permission)
  const isEnabled = currentStatus === 'GRANTED' || currentStatus === 'INHERITED'
  localChanges.value[member.memberId][permission] = !isEnabled
}

function getCellColor(member: MemberPermissionsResponse, permission: string): string {
  const status = getPermissionStatus(member, permission)
  switch (status) {
    case 'GRANTED':
      return 'bg-green-100 dark:bg-green-900/30'
    case 'INHERITED':
      return 'bg-green-50 dark:bg-green-900/10'
    case 'DENIED':
      return 'bg-red-50 dark:bg-red-900/10'
    default:
      return 'bg-gray-50 dark:bg-gray-800'
  }
}

function getStatusLabel(member: MemberPermissionsResponse, permission: string): string {
  const status = getPermissionStatus(member, permission)
  switch (status) {
    case 'GRANTED':
      return '허용 (커스텀)'
    case 'INHERITED':
      return '허용 (역할 기본)'
    case 'DENIED':
      return '거부'
    default:
      return '거부'
  }
}

async function saveChanges() {
  saving.value = true
  saved.value = false
  try {
    const promises = Object.entries(localChanges.value).map(([memberIdStr, perms]) => {
      if (Object.keys(perms).length === 0) return Promise.resolve()
      return teamApi.updateMemberPermissions(Number(memberIdStr), { permissions: perms })
    })
    await Promise.all(promises)
    localChanges.value = {}
    saved.value = true
    setTimeout(() => {
      saved.value = false
    }, 2000)
    // Reload
    await fetchPermissions()
  } catch {
    // handle error silently
  } finally {
    saving.value = false
  }
}

async function fetchPermissions() {
  loading.value = true
  try {
    teamPermissions.value = await teamApi.getTeamPermissions()
  } catch {
    teamPermissions.value = null
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchPermissions()
})
</script>

<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <ShieldCheckIcon class="h-6 w-6 text-indigo-600 dark:text-indigo-400" />
        <h2 class="text-lg font-semibold text-gray-900 dark:text-white">권한 매트릭스</h2>
      </div>
      <div class="flex items-center gap-3">
        <!-- Role preset legend -->
        <div class="hidden items-center gap-2 sm:flex">
          <span
            v-for="preset in rolePresets"
            :key="preset.key"
            :class="[preset.color, 'rounded-full px-2.5 py-0.5 text-xs font-medium']"
          >
            {{ preset.label }}
          </span>
        </div>
        <button
          v-if="hasChanges"
          @click="saveChanges"
          :disabled="saving"
          class="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50 dark:bg-indigo-500 dark:hover:bg-indigo-600"
        >
          <ArrowPathIcon v-if="saving" class="h-4 w-4 animate-spin" />
          <CheckIcon v-else-if="saved" class="h-4 w-4" />
          {{ saving ? '저장 중...' : saved ? '저장됨' : '변경사항 저장' }}
        </button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex items-center justify-center py-12">
      <ArrowPathIcon class="h-6 w-6 animate-spin text-gray-400" />
    </div>

    <!-- Matrix Table -->
    <div v-else-if="teamPermissions && teamPermissions.members.length > 0" class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-gray-200 dark:border-gray-700">
            <th
              class="sticky left-0 z-10 whitespace-nowrap bg-white px-4 py-3 text-left text-xs font-medium uppercase text-gray-500 dark:bg-gray-900 dark:text-gray-400"
            >
              권한
            </th>
            <th
              v-for="member in teamPermissions.members"
              :key="member.memberId"
              class="whitespace-nowrap px-3 py-3 text-center text-xs font-medium text-gray-500 dark:text-gray-400"
            >
              <div>{{ member.memberName || member.memberEmail.split('@')[0] }}</div>
              <span
                :class="[
                  rolePresets.find((r) => r.key === member.role.toUpperCase())?.color ??
                    'bg-gray-100 text-gray-600',
                  'mt-1 inline-block rounded-full px-2 py-0.5 text-[10px] font-medium',
                ]"
              >
                {{
                  rolePresets.find((r) => r.key === member.role.toUpperCase())?.label ??
                  member.role
                }}
              </span>
            </th>
          </tr>
        </thead>
        <tbody>
          <template v-for="category in categories" :key="category.key">
            <!-- Category header row -->
            <tr class="bg-gray-50 dark:bg-gray-800/50">
              <td
                :colspan="(teamPermissions?.members.length ?? 0) + 1"
                class="sticky left-0 z-10 px-4 py-2 text-xs font-semibold uppercase tracking-wider text-gray-600 dark:text-gray-300"
              >
                {{ category.label }}
              </td>
            </tr>
            <!-- Permission rows -->
            <tr
              v-for="perm in category.permissions"
              :key="perm"
              class="border-b border-gray-100 transition-colors hover:bg-gray-50 dark:border-gray-800 dark:hover:bg-gray-800/30"
            >
              <td
                class="sticky left-0 z-10 whitespace-nowrap bg-white px-4 py-2 text-xs text-gray-700 dark:bg-gray-900 dark:text-gray-300"
              >
                {{ perm.replace(/_/g, ' ') }}
              </td>
              <td
                v-for="member in teamPermissions.members"
                :key="`${member.memberId}-${perm}`"
                class="px-3 py-2 text-center"
              >
                <button
                  @click="togglePermission(member, perm)"
                  :disabled="member.role.toUpperCase() === 'OWNER'"
                  :class="[
                    getCellColor(member, perm),
                    'inline-flex h-7 w-7 items-center justify-center rounded-md border transition-all',
                    isPermissionEnabled(member, perm)
                      ? 'border-green-300 dark:border-green-700'
                      : 'border-gray-200 dark:border-gray-700',
                    member.role.toUpperCase() === 'OWNER'
                      ? 'cursor-not-allowed opacity-60'
                      : 'cursor-pointer hover:shadow-sm',
                  ]"
                  :title="getStatusLabel(member, perm)"
                >
                  <CheckIcon
                    v-if="isPermissionEnabled(member, perm)"
                    class="h-4 w-4 text-green-600 dark:text-green-400"
                  />
                  <span v-else class="h-1 w-3 rounded-full bg-gray-300 dark:bg-gray-600" />
                </button>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <!-- Empty State -->
    <div v-else class="py-12 text-center">
      <ShieldCheckIcon class="mx-auto h-12 w-12 text-gray-400 dark:text-gray-600" />
      <h3 class="mt-2 text-sm font-medium text-gray-900 dark:text-white">팀 멤버가 없습니다</h3>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
        팀 멤버를 초대하면 권한을 관리할 수 있습니다.
      </p>
    </div>
  </div>
</template>
