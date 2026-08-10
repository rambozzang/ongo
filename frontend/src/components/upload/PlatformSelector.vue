<template>
  <div class="space-y-6">
    <!-- Platform toggle cards -->
    <div>
      <label class="mb-3 block text-body font-medium text-gray-700 dark:text-gray-300">게시할 플랫폼 선택</label>
      <div class="grid gap-3 tablet:grid-cols-2 desktop:grid-cols-4">
        <div
          v-for="platform in ALL_PLATFORMS"
          :key="platform"
          role="button"
          :tabindex="canSelect(platform) ? 0 : -1"
          :aria-disabled="!canSelect(platform)"
          :aria-pressed="isSelected(platform)"
          class="relative rounded-xl border-2 p-4 transition-all"
          :class="
            isConnected(platform)
              ? getChannel(platform)?.tokenStatus === 'EXPIRED'
                ? 'cursor-not-allowed border-error bg-error-subtle opacity-70'
                : !canUploadMedia(platform)
                  ? 'cursor-not-allowed border-warning bg-warning-subtle opacity-70'
                  : isSelected(platform)
                    ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                    : 'cursor-pointer border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
              : 'cursor-default border-gray-100 dark:border-gray-800 bg-gray-50 dark:bg-gray-900 opacity-60'
          "
          @click="togglePlatform(platform)"
          @keydown.enter.prevent="togglePlatform(platform)"
          @keydown.space.prevent="togglePlatform(platform)"
        >
          <div class="flex items-center gap-3">
            <div
              class="flex h-10 w-10 items-center justify-center rounded-lg"
              :style="{ backgroundColor: PLATFORM_CONFIG[platform].color + '1A' }"
            >
              <span class="text-title font-bold" :style="{ color: PLATFORM_CONFIG[platform].color }">
                {{ PLATFORM_CONFIG[platform].label[0] }}
              </span>
            </div>
            <div class="flex-1">
              <p class="text-body font-medium text-gray-900 dark:text-gray-100">{{ PLATFORM_CONFIG[platform].label }}</p>
              <p v-if="getChannel(platform)?.tokenStatus === 'EXPIRED'" class="text-body-xs text-error-strong">
                토큰 만료 — 재연결 필요
              </p>
              <p v-else-if="getChannel(platform)?.tokenStatus === 'EXPIRING_SOON'" class="text-body-xs text-warning-strong">
                토큰 만료 임박
              </p>
              <p v-else-if="isConnected(platform) && getCapability(platform)?.cloudVideoUpload && !getCapability(platform)?.directVideoUpload && mediaType === 'VIDEO'" class="text-body-xs text-info-strong">
                클라우드 경유 업로드
              </p>
              <p v-else-if="isConnected(platform) && !canUploadMedia(platform)" class="text-body-xs text-warning-strong">
                {{ getCapability(platform)?.unavailableReason || `${mediaType === 'IMAGE' ? '이미지' : '영상'} 업로드 미지원` }}
              </p>
              <p v-else-if="isConnected(platform)" class="text-body-xs text-success-strong">연동됨</p>
              <router-link
                v-else
                to="/channels?connect=1"
                class="text-body-xs text-primary-600 hover:text-primary-700"
                @click.stop
              >
                연동하기
              </router-link>
            </div>
            <div
              v-if="isConnected(platform)"
              class="flex h-6 w-11 items-center rounded-full p-0.5 transition-colors"
              :class="isSelected(platform) ? 'bg-primary-600' : 'bg-gray-300 dark:bg-gray-600'"
            >
              <div
                class="h-5 w-5 rounded-full bg-white shadow transition-transform"
                :class="isSelected(platform) ? 'translate-x-5' : 'translate-x-0'"
              />
            </div>
          </div>
        </div>
      </div>
    </div>

    <div
      v-if="selectedPlatforms.length > 0"
      class="rounded-lg border p-4"
      :class="validationIssues.length > 0 ? 'border-error bg-error-subtle' : 'border-success bg-success-subtle'"
      :role="validationIssues.length > 0 ? 'alert' : 'status'"
    >
      <p class="text-body font-semibold" :class="validationIssues.length > 0 ? 'text-error-strong' : 'text-success-strong'">
        {{ validationIssues.length > 0 ? `게시 전 ${validationIssues.length}개 항목을 확인해주세요` : `${selectedPlatforms.length}개 플랫폼에 게시할 준비가 됐습니다` }}
      </p>
      <ul v-if="validationIssues.length > 0" class="mt-2 list-disc space-y-1 pl-5 text-body text-error-strong">
        <li v-for="issue in validationIssues" :key="issue">{{ issue }}</li>
      </ul>
    </div>

    <!-- Per-platform metadata tabs -->
    <div v-if="selectedPlatforms.length > 0">
      <div class="mb-4 border-b border-gray-200 dark:border-gray-700">
        <nav class="flex gap-0">
          <button
            v-for="platform in selectedPlatforms"
            :key="platform"
            class="border-b-2 px-4 py-2.5 text-body font-medium transition-colors"
            :class="
              activeTab === platform
                ? 'border-primary-500 text-primary-600'
                : 'border-transparent text-gray-500 dark:text-gray-400 hover:border-gray-300 dark:hover:border-gray-600 hover:text-gray-700 dark:hover:text-gray-300'
            "
            @click="activeTab = platform"
          >
            {{ PLATFORM_CONFIG[platform].label }}
            <span
              v-if="PLATFORM_LIMITS[platform] && isOverLimit(platform, 'title')"
              class="ml-1 text-body-xs text-error-strong"
              title="제목이 플랫폼 제한을 초과합니다"
            >!</span>
          </button>
        </nav>
      </div>

      <!-- Per-platform editor -->
      <div v-if="activeTab" class="space-y-4 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
        <!-- Title -->
        <div>
          <div class="mb-1.5 flex items-center justify-between">
            <label class="text-body font-medium text-gray-700 dark:text-gray-300">
              {{ PLATFORM_CONFIG[activeTab].label }} 제목
            </label>
            <span
              class="text-body-xs"
              :class="isOverLimit(activeTab, 'title') ? 'text-error-strong' : 'text-gray-400 dark:text-gray-500'"
            >
              {{ platformMeta[activeTab]?.title.length || 0 }}/{{ PLATFORM_LIMITS[activeTab]?.title || 0 }}
            </span>
          </div>
          <input
            v-model="platformMeta[activeTab]!.title"
            type="text"
            class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 px-3 py-2.5 text-body focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            @input="emitUpdate"
          />
          <p
            v-if="PLATFORM_LIMITS[activeTab] && isOverLimit(activeTab, 'title')"
            class="mt-1 text-body-xs text-error-strong"
          >
            {{ PLATFORM_CONFIG[activeTab].label }}은(는) 제목 {{ PLATFORM_LIMITS[activeTab]?.title }}자까지 허용합니다
          </p>
        </div>

        <!-- Description -->
        <div>
          <div class="mb-1.5 flex items-center justify-between">
            <label class="text-body font-medium text-gray-700 dark:text-gray-300">
              {{ PLATFORM_CONFIG[activeTab].label }} 설명
            </label>
            <span
              class="text-body-xs"
              :class="isOverLimit(activeTab, 'description') ? 'text-error-strong' : 'text-gray-400 dark:text-gray-500'"
            >
              {{ platformMeta[activeTab]?.description.length || 0 }}/{{ PLATFORM_LIMITS[activeTab]?.description || 0 }}
            </span>
          </div>
          <textarea
            v-if="(PLATFORM_LIMITS[activeTab]?.description || 0) > 0"
            v-model="platformMeta[activeTab]!.description"
            rows="3"
            class="w-full resize-none rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 px-3 py-2.5 text-body focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            @input="emitUpdate"
          />
          <p v-else class="rounded-lg bg-gray-50 px-3 py-2 text-body-xs text-gray-500 dark:bg-gray-800 dark:text-gray-400">
            이 플랫폼은 별도 설명 필드를 제공하지 않아 제목과 태그로 게시 문구를 구성합니다.
          </p>
        </div>

        <!-- Tags -->
        <div>
          <div class="mb-1.5 flex items-center justify-between">
            <label class="text-body font-medium text-gray-700 dark:text-gray-300">
              {{ PLATFORM_CONFIG[activeTab].label }} 태그
            </label>
            <span class="text-body-xs text-gray-400 dark:text-gray-500">
              {{ platformMeta[activeTab]?.tags.length || 0 }}개
            </span>
          </div>
          <div class="flex flex-wrap gap-1.5">
            <span
              v-for="(tag, i) in platformMeta[activeTab]?.tags || []"
              :key="i"
              class="inline-flex items-center gap-1 rounded-full bg-gray-100 dark:bg-gray-800 px-2.5 py-0.5 text-body-xs text-gray-700 dark:text-gray-300"
            >
              #{{ tag }}
              <button
                type="button"
                class="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300"
                @click="removePlatformTag(activeTab, i)"
              >
                <XMarkIcon class="h-3 w-3" />
              </button>
            </span>
            <input
              v-model="platformTagInput"
              type="text"
              class="min-w-[100px] flex-1 border-none bg-transparent text-gray-900 dark:text-gray-100 p-0 text-body-xs focus:outline-none focus:ring-0"
              placeholder="태그 추가 (Enter)"
              @keydown.enter.prevent="addPlatformTag(activeTab)"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- Publish options -->
    <div v-if="selectedPlatforms.length > 0" class="rounded-xl border border-gray-200 dark:border-gray-700 p-5">
      <label class="mb-3 block text-body font-medium text-gray-700 dark:text-gray-300">게시 설정</label>

      <div class="flex flex-col gap-4 tablet:flex-row tablet:items-end">
        <div class="flex gap-3">
          <button
            class="rounded-lg px-5 py-2.5 text-body font-medium transition-colors"
            :class="
              !scheduleMode
                ? 'bg-primary-600 text-white'
                : 'border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700'
            "
            @click="scheduleMode = false; emit('update:scheduledAt', undefined)"
          >
            즉시 게시
          </button>
          <button
            class="inline-flex items-center gap-1.5 rounded-lg px-5 py-2.5 text-body font-medium transition-colors"
            :class="
              scheduleMode
                ? 'bg-primary-600 text-white'
                : 'border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700'
            "
            :disabled="!allSelectedSupportScheduling"
            :title="!allSelectedSupportScheduling ? '선택한 플랫폼 중 API 예약 게시를 지원하지 않는 플랫폼이 있습니다' : undefined"
            @click="scheduleMode = true"
          >
            <CalendarDaysIcon class="h-4 w-4" />
            예약 게시
          </button>
        </div>

        <div v-if="scheduleMode" class="flex items-center gap-2">
          <input
            v-model="scheduleDate"
            type="date"
            class="rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 px-3 py-2 text-body focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            :min="todayStr"
            @change="emitSchedule"
          />
          <input
            v-model="scheduleTime"
            type="time"
            class="rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 px-3 py-2 text-body focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            @change="emitSchedule"
          />
          <button
            v-if="aiScheduleSuggestions.length > 0"
            class="inline-flex items-center gap-1 rounded-lg border border-primary-300 dark:border-primary-800 bg-primary-50 dark:bg-primary-900/20 px-3 py-2 text-caption text-primary-700 dark:text-primary-400 hover:bg-primary-100 dark:hover:bg-primary-900/30"
            @click="showSuggestions = !showSuggestions"
          >
            <SparklesIcon class="h-3.5 w-3.5" />
            AI 추천
          </button>
        </div>
      </div>

      <!-- AI schedule suggestions -->
      <div
        v-if="scheduleMode && showSuggestions && aiScheduleSuggestions.length > 0"
        class="mt-3 rounded-lg bg-primary-50 dark:bg-primary-900/20 p-3"
      >
        <p class="mb-2 text-caption text-primary-800 dark:text-primary-400">AI 추천 게시 시간</p>
        <div class="space-y-1.5">
          <button
            v-for="(sug, i) in aiScheduleSuggestions"
            :key="i"
            class="flex w-full items-center justify-between rounded-lg bg-white dark:bg-gray-800 px-3 py-2 text-left text-body hover:bg-primary-100 dark:hover:bg-primary-900/30"
            @click="applySuggestion(sug)"
          >
            <span class="font-medium text-gray-900 dark:text-gray-100">{{ sug.dayOfWeek }} {{ sug.time }}</span>
            <span class="text-body-xs text-primary-600">{{ sug.reason }}</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { XMarkIcon, CalendarDaysIcon } from '@heroicons/vue/24/outline'
import { SparklesIcon } from '@heroicons/vue/24/solid'
import type { Platform, Channel } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'
import type { Visibility, PlatformPublishConfig } from '@/types/video'
import type { MediaType, PlatformUploadCapability } from '@/types/video'
import type { ScheduleSuggestion } from '@/types/ai'

const ALL_PLATFORMS: Platform[] = ['YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'FACEBOOK', 'NAVER_CLIP', 'TWITTER', 'THREADS']

const PLATFORM_LIMITS: Partial<Record<Platform, { title: number; description: number }>> = {
  YOUTUBE: { title: 100, description: 5000 },
  TIKTOK: { title: 2200, description: 0 },
  INSTAGRAM: { title: 2200, description: 0 },
  FACEBOOK: { title: 255, description: 5000 },
  NAVER_CLIP: { title: 100, description: 1000 },
  TWITTER: { title: 280, description: 0 },
  THREADS: { title: 500, description: 0 },
}

const props = defineProps<{
  channels: Channel[]
  baseTitle: string
  baseDescription: string
  baseTags: string[]
  baseVisibility: Visibility
  scheduledAt?: string
  aiScheduleSuggestions: ScheduleSuggestion[]
  capabilities: PlatformUploadCapability[]
  file: File | null
  mediaType: MediaType
}>()

const emit = defineEmits<{
  'update:platforms': [configs: PlatformPublishConfig[]]
  'update:scheduledAt': [value: string | undefined]
  'update:valid': [value: boolean]
}>()

const selectedPlatforms = ref<Platform[]>([])
const activeTab = ref<Platform | null>(null)
const scheduleMode = ref(false)
const scheduleDate = ref('')
const scheduleTime = ref('')
const showSuggestions = ref(false)
const platformTagInput = ref('')

const todayStr = computed(() => {
  return formatLocalDate(new Date())
})

const platformMeta = reactive<Partial<Record<Platform, { title: string; description: string; tags: string[] }>>>({
  YOUTUBE: { title: '', description: '', tags: [] },
  TIKTOK: { title: '', description: '', tags: [] },
  INSTAGRAM: { title: '', description: '', tags: [] },
  FACEBOOK: { title: '', description: '', tags: [] },
  NAVER_CLIP: { title: '', description: '', tags: [] },
  TWITTER: { title: '', description: '', tags: [] },
  THREADS: { title: '', description: '', tags: [] },
})

const capabilityMap = computed(() => new Map(props.capabilities.map((item) => [item.platform, item])))

function getCapability(platform: Platform): PlatformUploadCapability | undefined {
  return capabilityMap.value.get(platform)
}

function canSelect(platform: Platform): boolean {
  const channel = getChannel(platform)
  if (!channel || channel.tokenStatus === 'EXPIRED') return false
  return canUploadMedia(platform)
}

function canUploadVideo(platform: Platform): boolean {
  const capability = getCapability(platform)
  return capability?.directVideoUpload === true || capability?.cloudVideoUpload === true
}

function supportsMediaType(platform: Platform): boolean {
  const accepted = getCapability(platform)?.acceptedMediaTypes
  // Older capability payloads predate the explicit field. Fail closed for
  // images while retaining the established video behavior during rollout.
  return accepted ? accepted.includes(props.mediaType) : props.mediaType === 'VIDEO'
}

function canUploadMedia(platform: Platform): boolean {
  return supportsMediaType(platform) && canUploadVideo(platform)
}

const allSelectedSupportScheduling = computed(() =>
  selectedPlatforms.value.length > 0 && selectedPlatforms.value.every((platform) => canUploadMedia(platform)),
)

const validationIssues = computed(() => {
  const issues: string[] = []
  for (const platform of selectedPlatforms.value) {
    const label = PLATFORM_CONFIG[platform].label
    const capability = getCapability(platform)
    const meta = platformMeta[platform]
    if (!canUploadMedia(platform)) {
      issues.push(`${label}: ${props.mediaType === 'IMAGE' ? '이미지' : '영상'} 업로드를 지원하지 않습니다.`)
      continue
    }
    if (!meta?.title.trim()) issues.push(`${label}: 제목이 필요합니다.`)
    if (props.file && capability) {
      const extension = props.file.name.split('.').pop()?.toLowerCase() || ''
      if (props.file.size > capability.maxFileSizeBytes) {
        issues.push(`${label}: 파일 크기가 최대 ${formatFileSize(capability.maxFileSizeBytes)}를 초과합니다.`)
      }
      if (!capability.acceptedExtensions.includes(extension)) {
        issues.push(`${label}: .${extension} 형식을 지원하지 않습니다.`)
      }
    }
    if (meta && capability && meta.title.length > capability.maxTitleLength) {
      issues.push(`${label}: 제목은 ${capability.maxTitleLength}자까지 입력할 수 있습니다.`)
    }
    if (meta && capability && capability.maxDescriptionLength > 0 && meta.description.length > capability.maxDescriptionLength) {
      issues.push(`${label}: 설명은 ${capability.maxDescriptionLength}자까지 입력할 수 있습니다.`)
    }
    if (meta && capability && meta.tags.length > capability.maxTagCount) {
      issues.push(`${label}: 태그는 ${capability.maxTagCount}개까지 입력할 수 있습니다.`)
    }
  }
  if (props.scheduledAt && new Date(props.scheduledAt).getTime() <= Date.now() + 5 * 60 * 1000) {
    issues.push('예약 시간은 현재보다 최소 5분 이후여야 합니다.')
  }
  return issues
})

function formatFileSize(bytes: number): string {
  return bytes >= 1024 ** 3 ? `${Math.round(bytes / 1024 ** 3)}GB` : `${Math.round(bytes / 1024 ** 2)}MB`
}

function formatLocalDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

watch(validationIssues, (issues) => emit('update:valid', selectedPlatforms.value.length > 0 && issues.length === 0), { immediate: true })

// Sync base metadata when it changes
watch(
  () => [props.baseTitle, props.baseDescription, props.baseTags] as const,
  ([title, desc, tags]) => {
    for (const p of ALL_PLATFORMS) {
      const meta = platformMeta[p]
      if (meta) {
        if (!meta.title || meta.title === '') {
          meta.title = title
        }
        if (!meta.description || meta.description === '') {
          meta.description = desc
        }
        if (meta.tags.length === 0) {
          meta.tags = [...tags]
        }
      }
    }
  },
  { immediate: true },
)

function getChannel(platform: Platform): Channel | undefined {
  return props.channels.find((c) => c.platform === platform)
}

function isConnected(platform: Platform): boolean {
  return !!getChannel(platform)
}

function isSelected(platform: Platform): boolean {
  return selectedPlatforms.value.includes(platform)
}

function togglePlatform(platform: Platform) {
  if (!canSelect(platform)) return

  const idx = selectedPlatforms.value.indexOf(platform)
  if (idx >= 0) {
    selectedPlatforms.value.splice(idx, 1)
    if (activeTab.value === platform) {
      activeTab.value = selectedPlatforms.value[0] ?? null
    }
  } else {
    // Init from base
    const meta = platformMeta[platform]
    if (meta) {
      meta.title = props.baseTitle
      meta.description = props.baseDescription
      meta.tags = [...props.baseTags]
    }
    selectedPlatforms.value.push(platform)
    if (!activeTab.value) activeTab.value = platform
  }
  emitUpdate()
}

function isOverLimit(platform: Platform, field: 'title' | 'description'): boolean {
  const limit = PLATFORM_LIMITS[platform]
  const meta = platformMeta[platform]
  if (!limit || !meta) return false
  return meta[field].length > limit[field]
}

function removePlatformTag(platform: Platform, index: number) {
  platformMeta[platform]?.tags.splice(index, 1)
  emitUpdate()
}

function addPlatformTag(platform: Platform) {
  const tag = platformTagInput.value.trim()
  const meta = platformMeta[platform]
  if (tag && meta && !meta.tags.includes(tag)) {
    meta.tags.push(tag)
    emitUpdate()
  }
  platformTagInput.value = ''
}

function emitUpdate() {
  const configs: PlatformPublishConfig[] = selectedPlatforms.value.map((p) => {
    const meta = platformMeta[p]
    return {
      platform: p,
      channelId: getChannel(p)?.id,
      title: meta?.title || '',
      description: meta?.description || '',
      tags: meta?.tags || [],
      visibility: props.baseVisibility,
      scheduledAt: props.scheduledAt,
    }
  })
  emit('update:platforms', configs)
}

function emitSchedule() {
  if (scheduleDate.value && scheduleTime.value) {
    emit('update:scheduledAt', `${scheduleDate.value}T${scheduleTime.value}:00`)
  }
}

watch(() => props.scheduledAt, emitUpdate)

function applySuggestion(sug: ScheduleSuggestion) {
  // Find the next occurrence of the suggested day
  const days = ['일요일', '월요일', '화요일', '수요일', '목요일', '금요일', '토요일']
  const targetDay = days.indexOf(sug.dayOfWeek)
  const today = new Date()
  const currentDay = today.getDay()
  let daysToAdd = targetDay - currentDay
  if (daysToAdd <= 0) daysToAdd += 7

  const target = new Date(today)
  target.setDate(target.getDate() + daysToAdd)
  scheduleDate.value = formatLocalDate(target)
  scheduleTime.value = sug.time
  showSuggestions.value = false
  emitSchedule()
}
</script>
