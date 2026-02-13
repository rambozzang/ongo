<script setup lang="ts">
import { computed } from 'vue'
import {
  ArrowUpTrayIcon,
  CheckCircleIcon,
  PencilSquareIcon,
  TrashIcon,
  ClockIcon,
  SparklesIcon,
  LinkIcon,
  LinkSlashIcon,
  CogIcon,
  UserPlusIcon,
  UserMinusIcon,
  ArrowRightOnRectangleIcon,
  CreditCardIcon,
} from '@heroicons/vue/24/outline'
import type { ActivityLog, ActivityAction } from '@/types/activitylog'

const props = defineProps<{
  log: ActivityLog
  showIpAddress?: boolean
}>()

const emit = defineEmits<{
  clickEntity: [log: ActivityLog]
}>()

// Icon mapping by action type
const iconMap: Record<ActivityAction, any> = {
  upload: ArrowUpTrayIcon,
  publish: CheckCircleIcon,
  edit: PencilSquareIcon,
  delete: TrashIcon,
  schedule: ClockIcon,
  ai_generate: SparklesIcon,
  channel_connect: LinkIcon,
  channel_disconnect: LinkSlashIcon,
  settings_change: CogIcon,
  team_invite: UserPlusIcon,
  team_remove: UserMinusIcon,
  login: ArrowRightOnRectangleIcon,
  credit_purchase: CreditCardIcon,
}

const iconBgMap: Record<ActivityAction, string> = {
  upload: 'bg-blue-500',
  publish: 'bg-green-500',
  edit: 'bg-amber-500',
  delete: 'bg-red-500',
  schedule: 'bg-purple-500',
  ai_generate: 'bg-indigo-500',
  channel_connect: 'bg-teal-500',
  channel_disconnect: 'bg-orange-500',
  settings_change: 'bg-gray-500',
  team_invite: 'bg-cyan-500',
  team_remove: 'bg-rose-500',
  login: 'bg-slate-500',
  credit_purchase: 'bg-emerald-500',
}

const iconComponent = computed(() => iconMap[props.log.action])
const iconBgClass = computed(() => iconBgMap[props.log.action])

// Build Korean description text
const description = computed(() => {
  const name = props.log.userName
  const entity = props.log.entityName ? `'${props.log.entityName}'` : ''

  switch (props.log.action) {
    case 'upload':
      return `${name}님이 ${entity} 영상을 업로드했습니다`
    case 'publish':
      return `${name}님이 ${entity} 영상을 게시했습니다`
    case 'edit':
      return `${name}님이 ${entity} ${props.log.details?.field ? `${props.log.details.field}을(를)` : '정보를'} 수정했습니다`
    case 'delete':
      return `${name}님이 ${entity} 영상을 삭제했습니다`
    case 'schedule':
      return `${name}님이 ${entity} 예약 업로드를 설정했습니다`
    case 'ai_generate':
      return `${name}님이 ${entity} ${props.log.details?.type ?? 'AI 기능'}을 사용했습니다${props.log.details?.creditsUsed ? ` (${props.log.details.creditsUsed} 크레딧)` : ''}`
    case 'channel_connect':
      return `${name}님이 ${entity} 채널을 연결했습니다`
    case 'channel_disconnect':
      return `${name}님이 ${entity} 채널 연결을 해제했습니다`
    case 'settings_change':
      return `${name}님이 ${props.log.details?.setting ?? '설정'}을 변경했습니다`
    case 'team_invite':
      return `${name}님이 ${entity}님을 팀에 초대했습니다`
    case 'team_remove':
      return `${name}님이 ${entity}님을 팀에서 제거했습니다`
    case 'login':
      return `${name}님이 ${props.log.details?.provider ?? ''} 계정으로 로그인했습니다`
    case 'credit_purchase':
      return `${name}님이 크레딧 ${props.log.details?.amount ?? ''}개를 구매했습니다${props.log.details?.price ? ` (${props.log.details.price})` : ''}`
    default:
      return `${name}님이 활동을 수행했습니다`
  }
})

// Relative time formatting
const relativeTime = computed(() => {
  const now = new Date()
  const logDate = new Date(props.log.createdAt)
  const diffMs = now.getTime() - logDate.getTime()
  const diffSecs = Math.floor(diffMs / 1000)
  const diffMins = Math.floor(diffSecs / 60)
  const diffHours = Math.floor(diffMins / 60)
  const diffDays = Math.floor(diffHours / 24)

  if (diffSecs < 60) return '방금 전'
  if (diffMins < 60) return `${diffMins}분 전`
  if (diffHours < 24) return `${diffHours}시간 전`
  if (diffDays < 7) return `${diffDays}일 전`

  return `${logDate.getMonth() + 1}월 ${logDate.getDate()}일`
})

// User initials for avatar fallback
const userInitials = computed(() => {
  return props.log.userName.charAt(0)
})

// Platform badge from details
const platformBadge = computed(() => {
  const platform = props.log.details?.platform
  if (!platform) return null

  const labels: Record<string, string> = {
    YOUTUBE: 'YouTube',
    TIKTOK: 'TikTok',
    INSTAGRAM: 'Instagram',
    NAVER_CLIP: 'Naver Clip',
  }
  return labels[platform] ?? platform
})

const platformBadgeClass = computed(() => {
  const platform = props.log.details?.platform
  if (!platform) return ''

  const classes: Record<string, string> = {
    YOUTUBE: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
    TIKTOK: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
    INSTAGRAM: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    NAVER_CLIP: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  }
  return classes[platform] ?? ''
})

const hasEntityLink = computed(() => {
  return props.log.entityId && ['video', 'schedule', 'channel'].includes(props.log.entityType)
})

function handleEntityClick() {
  if (hasEntityLink.value) {
    emit('clickEntity', props.log)
  }
}
</script>

<template>
  <div
    class="group flex gap-3 rounded-lg px-3 py-3 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700/50"
  >
    <!-- User avatar -->
    <div class="flex-shrink-0">
      <div
        v-if="log.userAvatar"
        class="h-8 w-8 overflow-hidden rounded-full"
      >
        <img
          :src="log.userAvatar"
          :alt="log.userName"
          class="h-full w-full object-cover"
        />
      </div>
      <div
        v-else
        class="flex h-8 w-8 items-center justify-center rounded-full bg-primary-100 text-sm font-semibold text-primary-700 dark:bg-primary-900/30 dark:text-primary-400"
      >
        {{ userInitials }}
      </div>
    </div>

    <!-- Action icon -->
    <div
      class="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full"
      :class="iconBgClass"
    >
      <component :is="iconComponent" class="h-3.5 w-3.5 text-white" />
    </div>

    <!-- Content -->
    <div class="min-w-0 flex-1">
      <p class="text-sm text-gray-900 dark:text-gray-100">
        {{ description }}
        <button
          v-if="hasEntityLink"
          class="ml-1 inline-flex text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
          @click.stop="handleEntityClick"
        >
          <span class="text-xs underline">보기</span>
        </button>
      </p>

      <div class="mt-1 flex flex-wrap items-center gap-2">
        <span class="text-xs text-gray-500 dark:text-gray-400">{{ relativeTime }}</span>

        <span
          v-if="platformBadge"
          class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
          :class="platformBadgeClass"
        >
          {{ platformBadge }}
        </span>

        <span
          v-if="showIpAddress && log.ipAddress"
          class="text-[11px] text-gray-400 dark:text-gray-500"
        >
          {{ log.ipAddress }}
        </span>
      </div>
    </div>
  </div>
</template>
