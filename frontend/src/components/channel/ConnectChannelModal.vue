<template>
  <Teleport to="body">
    <div
      v-if="modelValue"
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="connect-channel-title"
      @keydown.escape="close"
    >
      <!-- Backdrop -->
      <div class="fixed inset-0 bg-black/50" aria-hidden="true" @click="close" />

      <!-- Modal Content -->
      <div class="relative flex max-h-[calc(100vh-2rem)] w-full max-w-lg flex-col rounded-xl bg-white p-4 shadow-xl dark:bg-gray-800 tablet:p-5">
        <!-- Header -->
        <div class="mb-4 shrink-0">
          <h3 id="connect-channel-title" class="text-title font-bold text-gray-900 dark:text-gray-100">{{ $t('channels.connectModalTitle') }}</h3>
          <p class="mt-1 text-body text-gray-600 dark:text-gray-300">
            {{ $t('channels.connectModalDescription') }}
          </p>
          <p v-if="maxAllowed > 0" class="mt-1 text-body-xs" :class="isAtLimit ? 'text-error-strong font-medium' : 'text-gray-500 dark:text-gray-400'">
            {{ isAtLimit ? $t('channels.limitReached', { current: currentCount, max: maxAllowed }) : $t('channels.channelsConnected', { current: currentCount, max: maxAllowed }) }}
          </p>
        </div>

        <!-- Platform Cards Grid -->
        <div class="min-h-0 overflow-y-auto pr-1">
          <div v-if="!platformsLoading && !platformsError" class="grid grid-cols-2 gap-2.5">
          <div
            v-for="platform in availablePlatforms"
            :key="platform"
            class="relative rounded-lg border p-3 transition-all"
            :class="{
              'border-gray-200 dark:border-gray-700 hover:border-gray-400 dark:hover:border-gray-500 hover:shadow-md cursor-pointer': !isAtLimit,
              'border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-700/30 cursor-not-allowed': isAtLimit,
            }"
            @click="handleConnect(platform)"
          >
            <!-- Connected Checkmark -->
            <div
              v-if="isConnected(platform)"
              class="absolute top-3 right-3 flex h-6 w-6 items-center justify-center rounded-full bg-success"
            >
              <CheckIcon class="h-4 w-4 text-white" />
            </div>

            <!-- Platform Icon -->
              <div class="mb-2 flex items-center justify-center">
                <div
                class="flex h-10 w-10 items-center justify-center rounded-full text-title font-bold text-white shadow-lg"
                :style="{ backgroundColor: PLATFORM_CONFIG[platform].color }"
              >
                {{ getPlatformInitial(platform) }}
              </div>
            </div>

            <!-- Platform Name -->
            <div class="text-center">
              <h4 class="text-body font-bold text-gray-900 dark:text-gray-100">
                {{ PLATFORM_CONFIG[platform].label }}
              </h4>
              <p class="mt-1 text-[11px] leading-4 text-gray-600 dark:text-gray-400">
                {{ getPlatformDescription(platform) }}
              </p>
            </div>

            <!-- Action Button -->
            <div class="mt-2">
              <button
                class="btn-primary w-full"
                :disabled="connectingPlatform === platform || isAtLimit"
                @click.stop="handleConnect(platform)"
              >
                <template v-if="connectingPlatform === platform">
                  <ArrowPathIcon class="h-4 w-4 inline-block mr-1 animate-spin" />
                  {{ $t('channels.connecting') }}
                </template>
                <template v-else>
                  <LinkIcon class="h-4 w-4 inline-block mr-1" />
                  {{ isConnected(platform) ? '다른 계정 추가' : $t('channels.connect') }}
                </template>
              </button>
            </div>
          </div>
          </div>
          <div
            v-if="!platformsLoading && unavailablePlatforms.length"
            class="mt-3 rounded-lg border border-warning-subtle bg-warning-subtle px-3 py-2.5 text-[11px] text-warning-strong"
            role="status"
          >
            <p class="font-semibold">{{ $t('channels.platformsUnavailableTitle') }}</p>
            <p class="mt-1 leading-4">{{ $t('channels.platformsUnavailableDescription') }}</p>
            <p class="mt-1 font-medium">{{ unavailablePlatformLabels }}</p>
          </div>
          <div v-if="platformsLoading" class="py-8 text-center text-body text-gray-500 dark:text-gray-400">
            {{ $t('channels.loadingPlatforms') }}
          </div>
          <div v-else-if="platformsError" class="rounded-lg border border-error/30 bg-error-subtle px-3 py-3 text-body text-error-strong">
            <p>{{ platformsError }}</p>
            <button type="button" class="btn-secondary mt-2" @click="loadPlatforms">
              {{ $t('common.retry') }}
            </button>
          </div>
        </div>

        <!-- Footer -->
        <div class="mt-4 flex shrink-0 justify-end border-t border-gray-100 pt-3 dark:border-gray-700">
          <button class="btn-secondary" @click="close">
            {{ $t('channels.close') }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { CheckIcon, LinkIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import type { Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'
import { videoApi } from '@/api/video'

const props = defineProps<{
  modelValue: boolean
  connectedPlatforms: Platform[]
  maxAllowed: number
  currentCount: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'connect': [platform: Platform, addAsNew: boolean]
}>()

const connectingPlatform = ref<Platform | null>(null)
const availablePlatforms = ref<Platform[]>([])
const unavailablePlatforms = ref<Platform[]>([])
const platformsLoading = ref(false)
const platformsError = ref('')

const unavailablePlatformLabels = computed(() => unavailablePlatforms.value
  .map((platform) => PLATFORM_CONFIG[platform]?.label ?? platform)
  .join(', '))

const isAtLimit = computed(() => props.currentCount >= props.maxAllowed && props.maxAllowed > 0)

function close() {
  emit('update:modelValue', false)
}

function isConnected(platform: Platform): boolean {
  return props.connectedPlatforms.includes(platform)
}

function getPlatformInitial(platform: Platform): string {
  const initials: Partial<Record<Platform, string>> = {
    YOUTUBE: 'Y',
    TIKTOK: 'T',
    INSTAGRAM: 'I',
    NAVER_CLIP: 'N',
    TWITTER: 'X',
    FACEBOOK: 'F',
    THREADS: 'Th',
    PINTEREST: 'P',
    LINKEDIN: 'Li',
    WORDPRESS: 'W',
    DAILYMOTION: 'D',
    VIMEO: 'V',
    TUMBLR: 'Tu',
  }
  return initials[platform] || platform.charAt(0)
}

function getPlatformDescription(platform: Platform): string {
  const descriptions: Partial<Record<Platform, string>> = {
    YOUTUBE: '세계 최대 동영상 플랫폼',
    TIKTOK: '숏폼 콘텐츠 플랫폼',
    INSTAGRAM: '사진 및 릴스 공유 플랫폼',
    NAVER_CLIP: '네이버 동영상 플랫폼',
    TWITTER: 'X(트위터) 영상 게시',
    FACEBOOK: '페이스북 페이지 영상 업로드',
    THREADS: '스레드 영상 게시',
    PINTEREST: '보드에 동영상 Pin 게시',
    LINKEDIN: '프로필에 동영상 게시',
    WORDPRESS: '사이트에 동영상 글 게시',
    DAILYMOTION: '프로필에 동영상 게시',
    VIMEO: '동영상 업로드 및 관리',
    TUMBLR: '블로그에 네이티브 동영상 게시',
  }
  return descriptions[platform] || ''
}

function handleConnect(platform: Platform) {
  if (isAtLimit.value || connectingPlatform.value) return

  connectingPlatform.value = platform

  // Initiate real OAuth flow — redirect to backend authorization endpoint
  emit('connect', platform, isConnected(platform))
}

async function loadPlatforms() {
  if (platformsLoading.value) return
  platformsLoading.value = true
  platformsError.value = ''
  try {
    const capabilities = await videoApi.getUploadCapabilities()
    const uploadable = capabilities.filter((capability) => capability.directVideoUpload || capability.cloudVideoUpload)
    availablePlatforms.value = uploadable
      .filter((capability) => capability.configurationAvailable !== false)
      .map((capability) => capability.platform)
    unavailablePlatforms.value = uploadable
      .filter((capability) => capability.configurationAvailable === false)
      .map((capability) => capability.platform)
  } catch (error) {
    availablePlatforms.value = []
    unavailablePlatforms.value = []
    platformsError.value = error instanceof Error ? error.message : '플랫폼 목록을 불러오지 못했습니다.'
  } finally {
    platformsLoading.value = false
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      connectingPlatform.value = null
      void loadPlatforms()
    } else {
      connectingPlatform.value = null
    }
  },
  { immediate: true },
)
</script>
