<script setup lang="ts">
import { ref, computed } from 'vue'
import { Menu, MenuButton, MenuItems, MenuItem } from '@headlessui/vue'
import { EllipsisVerticalIcon, UserMinusIcon } from '@heroicons/vue/24/outline'
import type { TeamMember, TeamRole } from '@/types/team'
import RoleBadge from './RoleBadge.vue'
import { useTeamStore } from '@/stores/team'

interface Props {
  member: TeamMember
  canManage: boolean
}

const props = defineProps<Props>()

const teamStore = useTeamStore()
const showDeleteConfirm = ref(false)

const initials = computed(() => {
  return props.member.name.slice(0, 2)
})

const relativeTime = computed(() => {
  const diff = Date.now() - new Date(props.member.lastActiveAt).getTime()
  const minutes = Math.floor(diff / (1000 * 60))
  const hours = Math.floor(diff / (1000 * 60 * 60))
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (props.member.isOnline) return '현재 온라인'
  if (minutes < 60) return `${minutes}분 전`
  if (hours < 24) return `${hours}시간 전`
  return `${days}일 전`
})

const handleRoleChange = (newRole: TeamRole) => {
  if (props.member.role === 'owner') return
  teamStore.updateRole(props.member.id, newRole)
}

const handleRemove = () => {
  teamStore.removeMember(props.member.id)
  showDeleteConfirm.value = false
}
</script>

<template>
  <div
    class="relative rounded-lg border border-gray-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md dark:border-gray-700 dark:bg-gray-800"
  >
    <div class="flex items-start justify-between">
      <div class="flex items-start space-x-3">
        <div class="relative">
          <div
            :style="{ backgroundColor: member.avatar }"
            class="flex h-12 w-12 items-center justify-center rounded-full text-white font-semibold"
          >
            {{ initials }}
          </div>
          <span
            v-if="member.isOnline"
            class="absolute bottom-0 right-0 block h-3 w-3 rounded-full bg-green-400 ring-2 ring-white dark:ring-gray-800"
          ></span>
          <span
            v-else
            class="absolute bottom-0 right-0 block h-3 w-3 rounded-full bg-gray-400 ring-2 ring-white dark:ring-gray-800"
          ></span>
        </div>

        <div class="flex-1 min-w-0">
          <h3 class="text-sm font-semibold text-gray-900 dark:text-white">
            {{ member.name }}
          </h3>
          <p class="text-sm text-gray-500 dark:text-gray-400 truncate">
            {{ member.email }}
          </p>
          <div class="mt-2 flex items-center space-x-2">
            <RoleBadge :role="member.role" />
          </div>
          <p class="mt-2 text-xs text-gray-500 dark:text-gray-400">
            {{ relativeTime }}
          </p>
        </div>
      </div>

      <Menu v-if="canManage && member.role !== 'owner'" as="div" class="relative">
        <MenuButton
          class="rounded-md p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
        >
          <EllipsisVerticalIcon class="h-5 w-5" />
        </MenuButton>

        <transition
          enter-active-class="transition ease-out duration-100"
          enter-from-class="transform opacity-0 scale-95"
          enter-to-class="transform opacity-100 scale-100"
          leave-active-class="transition ease-in duration-75"
          leave-from-class="transform opacity-100 scale-100"
          leave-to-class="transform opacity-0 scale-95"
        >
          <MenuItems
            class="absolute right-0 z-10 mt-2 w-48 origin-top-right rounded-md bg-white shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none dark:bg-gray-700 dark:ring-gray-600"
          >
            <div class="py-1">
              <div class="px-3 py-2 text-xs font-semibold text-gray-500 dark:text-gray-400">
                역할 변경
              </div>
              <MenuItem v-slot="{ active }">
                <button
                  @click="handleRoleChange('admin')"
                  :class="[
                    active ? 'bg-gray-100 dark:bg-gray-600' : '',
                    'block w-full px-4 py-2 text-left text-sm text-gray-700 dark:text-gray-200',
                  ]"
                >
                  관리자
                </button>
              </MenuItem>
              <MenuItem v-slot="{ active }">
                <button
                  @click="handleRoleChange('editor')"
                  :class="[
                    active ? 'bg-gray-100 dark:bg-gray-600' : '',
                    'block w-full px-4 py-2 text-left text-sm text-gray-700 dark:text-gray-200',
                  ]"
                >
                  에디터
                </button>
              </MenuItem>
              <MenuItem v-slot="{ active }">
                <button
                  @click="handleRoleChange('viewer')"
                  :class="[
                    active ? 'bg-gray-100 dark:bg-gray-600' : '',
                    'block w-full px-4 py-2 text-left text-sm text-gray-700 dark:text-gray-200',
                  ]"
                >
                  뷰어
                </button>
              </MenuItem>
              <hr class="my-1 border-gray-200 dark:border-gray-600" />
              <MenuItem v-slot="{ active }">
                <button
                  @click="showDeleteConfirm = true"
                  :class="[
                    active ? 'bg-red-50 dark:bg-red-900/20' : '',
                    'block w-full px-4 py-2 text-left text-sm text-red-600 dark:text-red-400',
                  ]"
                >
                  <UserMinusIcon class="inline-block h-4 w-4 mr-2" />
                  멤버 제거
                </button>
              </MenuItem>
            </div>
          </MenuItems>
        </transition>
      </Menu>
    </div>

    <!-- Delete Confirmation Modal -->
    <div
      v-if="showDeleteConfirm"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
      role="dialog"
      aria-modal="true"
      aria-label="멤버 제거 확인"
      @click.self="showDeleteConfirm = false"
    >
      <div
        class="mx-4 w-full max-w-md rounded-lg bg-white p-6 shadow-xl dark:bg-gray-800"
      >
        <h3 class="text-lg font-semibold text-gray-900 dark:text-white">
          멤버 제거
        </h3>
        <p class="mt-2 text-sm text-gray-600 dark:text-gray-400">
          정말로 <strong>{{ member.name }}</strong>님을 팀에서 제거하시겠습니까? 이
          작업은 되돌릴 수 없습니다.
        </p>
        <div class="mt-6 flex justify-end space-x-3">
          <button
            @click="showDeleteConfirm = false"
            class="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200 dark:hover:bg-gray-600"
          >
            취소
          </button>
          <button
            @click="handleRemove"
            class="rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 dark:bg-red-700 dark:hover:bg-red-600"
          >
            제거
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
