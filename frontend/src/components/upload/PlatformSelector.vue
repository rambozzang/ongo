<template>
  <div class="space-y-6">
    <!-- Platform toggle cards -->
    <div>
      <label class="mb-3 block text-sm font-medium text-gray-700 dark:text-gray-300">게시할 플랫폼 선택</label>
      <div class="grid gap-3 tablet:grid-cols-2 lg:grid-cols-4">
        <div
          v-for="platform in ALL_PLATFORMS"
          :key="platform"
          class="relative rounded-xl border-2 p-4 transition-all"
          :class="
            isConnected(platform)
              ? isSelected(platform)
                ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                : 'cursor-pointer border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
              : 'cursor-default border-gray-100 dark:border-gray-800 bg-gray-50 dark:bg-gray-900 opacity-60'
          "
          @click="togglePlatform(platform)"
        >
          <div class="flex items-center gap-3">
            <div
              class="flex h-10 w-10 items-center justify-center rounded-lg"
              :style="{ backgroundColor: PLATFORM_CONFIG[platform].color + '1A' }"
            >
              <span class="text-lg font-bold" :style="{ color: PLATFORM_CONFIG[platform].color }">
                {{ PLATFORM_CONFIG[platform].label[0] }}
              </span>
            </div>
            <div class="flex-1">
              <p class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ PLATFORM_CONFIG[platform].label }}</p>
              <p v-if="isConnected(platform)" class="text-xs text-green-600">연동됨</p>
              <router-link
                v-else
                to="/channels"
                class="text-xs text-primary-600 hover:text-primary-700"
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

    <!-- Per-platform metadata tabs -->
    <div v-if="selectedPlatforms.length > 0">
      <div class="mb-4 border-b border-gray-200 dark:border-gray-700">
        <nav class="flex gap-0">
          <button
            v-for="platform in selectedPlatforms"
            :key="platform"
            class="border-b-2 px-4 py-2.5 text-sm font-medium transition-colors"
            :class="
              activeTab === platform
                ? 'border-primary-500 text-primary-600'
                : 'border-transparent text-gray-500 dark:text-gray-400 hover:border-gray-300 dark:hover:border-gray-600 hover:text-gray-700 dark:hover:text-gray-300'
            "
            @click="activeTab = platform"
          >
            {{ PLATFORM_CONFIG[platform].label }}
            <span
              v-if="isOverLimit(platform, 'title')"
              class="ml-1 text-xs text-red-500"
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
            <label class="text-sm font-medium text-gray-700 dark:text-gray-300">
              {{ PLATFORM_CONFIG[activeTab].label }} 제목
            </label>
            <span
              class="text-xs"
              :class="isOverLimit(activeTab, 'title') ? 'text-red-500' : 'text-gray-400 dark:text-gray-500'"
            >
              {{ platformMeta[activeTab].title.length }}/{{ PLATFORM_LIMITS[activeTab].title }}
            </span>
          </div>
          <input
            v-model="platformMeta[activeTab].title"
            type="text"
            class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 px-3 py-2.5 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            @input="emitUpdate"
          />
          <p
            v-if="isOverLimit(activeTab, 'title')"
            class="mt-1 text-xs text-red-500"
          >
            {{ PLATFORM_CONFIG[activeTab].label }}은(는) 제목 {{ PLATFORM_LIMITS[activeTab].title }}자까지 허용합니다
          </p>
        </div>

        <!-- Description -->
        <div>
          <div class="mb-1.5 flex items-center justify-between">
            <label class="text-sm font-medium text-gray-700 dark:text-gray-300">
              {{ PLATFORM_CONFIG[activeTab].label }} 설명
            </label>
            <span
              class="text-xs"
              :class="isOverLimit(activeTab, 'description') ? 'text-red-500' : 'text-gray-400 dark:text-gray-500'"
            >
              {{ platformMeta[activeTab].description.length }}/{{ PLATFORM_LIMITS[activeTab].description }}
            </span>
          </div>
          <textarea
            v-model="platformMeta[activeTab].description"
            rows="3"
            class="w-full resize-none rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 px-3 py-2.5 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            @input="emitUpdate"
          />
        </div>

        <!-- Tags -->
        <div>
          <div class="mb-1.5 flex items-center justify-between">
            <label class="text-sm font-medium text-gray-700 dark:text-gray-300">
              {{ PLATFORM_CONFIG[activeTab].label }} 태그
            </label>
            <span class="text-xs text-gray-400 dark:text-gray-500">
              {{ platformMeta[activeTab].tags.length }}개
            </span>
          </div>
          <div class="flex flex-wrap gap-1.5">
            <span
              v-for="(tag, i) in platformMeta[activeTab].tags"
              :key="i"
              class="inline-flex items-center gap-1 rounded-full bg-gray-100 dark:bg-gray-800 px-2.5 py-0.5 text-xs text-gray-700 dark:text-gray-300"
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
              class="min-w-[100px] flex-1 border-none bg-transparent text-gray-900 dark:text-gray-100 p-0 text-xs focus:outline-none focus:ring-0"
              placeholder="태그 추가 (Enter)"
              @keydown.enter.prevent="addPlatformTag(activeTab)"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- Publish options -->
    <div v-if="selectedPlatforms.length > 0" class="rounded-xl border border-gray-200 dark:border-gray-700 p-5">
      <label class="mb-3 block text-sm font-medium text-gray-700 dark:text-gray-300">게시 설정</label>

      <div class="flex flex-col gap-4 tablet:flex-row tablet:items-end">
        <div class="flex gap-3">
          <button
            class="rounded-lg px-5 py-2.5 text-sm font-medium transition-colors"
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
            class="inline-flex items-center gap-1.5 rounded-lg px-5 py-2.5 text-sm font-medium transition-colors"
            :class="
              scheduleMode
                ? 'bg-primary-600 text-white'
                : 'border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700'
            "
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
            class="rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            :min="todayStr"
            @change="emitSchedule"
          />
          <input
            v-model="scheduleTime"
            type="time"
            class="rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            @change="emitSchedule"
          />
          <button
            v-if="aiScheduleSuggestions.length > 0"
            class="inline-flex items-center gap-1 rounded-lg border border-primary-300 dark:border-primary-800 bg-primary-50 dark:bg-primary-900/20 px-3 py-2 text-xs font-medium text-primary-700 dark:text-primary-400 hover:bg-primary-100 dark:hover:bg-primary-900/30"
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
        <p class="mb-2 text-xs font-medium text-primary-800 dark:text-primary-400">AI 추천 게시 시간</p>
        <div class="space-y-1.5">
          <button
            v-for="(sug, i) in aiScheduleSuggestions"
            :key="i"
            class="flex w-full items-center justify-between rounded-lg bg-white dark:bg-gray-800 px-3 py-2 text-left text-sm hover:bg-primary-100 dark:hover:bg-primary-900/30"
            @click="applySuggestion(sug)"
          >
            <span class="font-medium text-gray-900 dark:text-gray-100">{{ sug.dayOfWeek }} {{ sug.time }}</span>
            <span class="text-xs text-primary-600">{{ sug.reason }}</span>
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
import type { Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'
import type { Visibility, PlatformPublishConfig } from '@/types/video'
import type { ScheduleSuggestion } from '@/types/ai'

const ALL_PLATFORMS: Platform[] = ['YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'NAVER_CLIP']

const PLATFORM_LIMITS: Record<Platform, { title: number; description: number }> = {
  YOUTUBE: { title: 100, description: 5000 },
  TIKTOK: { title: 150, description: 2200 },
  INSTAGRAM: { title: 100, description: 2200 },
  NAVER_CLIP: { title: 100, description: 2000 },
}

const props = defineProps<{
  connectedPlatforms: Platform[]
  baseTitle: string
  baseDescription: string
  baseTags: string[]
  baseVisibility: Visibility
  scheduledAt?: string
  aiScheduleSuggestions: ScheduleSuggestion[]
}>()

const emit = defineEmits<{
  'update:platforms': [configs: PlatformPublishConfig[]]
  'update:scheduledAt': [value: string | undefined]
}>()

const selectedPlatforms = ref<Platform[]>([])
const activeTab = ref<Platform | null>(null)
const scheduleMode = ref(false)
const scheduleDate = ref('')
const scheduleTime = ref('')
const showSuggestions = ref(false)
const platformTagInput = ref('')

const todayStr = computed(() => {
  const d = new Date()
  return d.toISOString().split('T')[0]
})

const platformMeta = reactive<Record<Platform, { title: string; description: string; tags: string[] }>>({
  YOUTUBE: { title: '', description: '', tags: [] },
  TIKTOK: { title: '', description: '', tags: [] },
  INSTAGRAM: { title: '', description: '', tags: [] },
  NAVER_CLIP: { title: '', description: '', tags: [] },
})

// Sync base metadata when it changes
watch(
  () => [props.baseTitle, props.baseDescription, props.baseTags] as const,
  ([title, desc, tags]) => {
    for (const p of ALL_PLATFORMS) {
      if (!platformMeta[p].title || platformMeta[p].title === '') {
        platformMeta[p].title = title
      }
      if (!platformMeta[p].description || platformMeta[p].description === '') {
        platformMeta[p].description = desc
      }
      if (platformMeta[p].tags.length === 0) {
        platformMeta[p].tags = [...tags]
      }
    }
  },
  { immediate: true },
)

function isConnected(platform: Platform): boolean {
  return props.connectedPlatforms.includes(platform)
}

function isSelected(platform: Platform): boolean {
  return selectedPlatforms.value.includes(platform)
}

function togglePlatform(platform: Platform) {
  if (!isConnected(platform)) return

  const idx = selectedPlatforms.value.indexOf(platform)
  if (idx >= 0) {
    selectedPlatforms.value.splice(idx, 1)
    if (activeTab.value === platform) {
      activeTab.value = selectedPlatforms.value[0] ?? null
    }
  } else {
    // Init from base
    platformMeta[platform].title = props.baseTitle
    platformMeta[platform].description = props.baseDescription
    platformMeta[platform].tags = [...props.baseTags]
    selectedPlatforms.value.push(platform)
    if (!activeTab.value) activeTab.value = platform
  }
  emitUpdate()
}

function isOverLimit(platform: Platform, field: 'title' | 'description'): boolean {
  return platformMeta[platform][field].length > PLATFORM_LIMITS[platform][field]
}

function removePlatformTag(platform: Platform, index: number) {
  platformMeta[platform].tags.splice(index, 1)
  emitUpdate()
}

function addPlatformTag(platform: Platform) {
  const tag = platformTagInput.value.trim()
  if (tag && !platformMeta[platform].tags.includes(tag)) {
    platformMeta[platform].tags.push(tag)
    emitUpdate()
  }
  platformTagInput.value = ''
}

function emitUpdate() {
  const configs: PlatformPublishConfig[] = selectedPlatforms.value.map((p) => ({
    platform: p,
    title: platformMeta[p].title,
    description: platformMeta[p].description,
    tags: platformMeta[p].tags,
    visibility: props.baseVisibility,
  }))
  emit('update:platforms', configs)
}

function emitSchedule() {
  if (scheduleDate.value && scheduleTime.value) {
    emit('update:scheduledAt', `${scheduleDate.value}T${scheduleTime.value}:00`)
  }
}

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
  scheduleDate.value = target.toISOString().split('T')[0]
  scheduleTime.value = sug.time
  showSuggestions.value = false
  emitSchedule()
}
</script>
