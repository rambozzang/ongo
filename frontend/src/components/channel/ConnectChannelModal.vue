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
      <div class="relative w-full max-w-2xl rounded-xl bg-white dark:bg-gray-800 p-6 shadow-xl">
        <!-- Header -->
        <div class="mb-6">
          <h3 id="connect-channel-title" class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ $t('channels.connectModalTitle') }}</h3>
          <p class="mt-2 text-sm text-gray-600 dark:text-gray-300">
            {{ $t('channels.connectModalDescription') }}
          </p>
          <p v-if="maxAllowed > 0" class="mt-1 text-xs" :class="isAtLimit ? 'text-red-500 dark:text-red-400 font-medium' : 'text-gray-500 dark:text-gray-400'">
            {{ isAtLimit ? $t('channels.limitReached', { current: currentCount, max: maxAllowed }) : $t('channels.channelsConnected', { current: currentCount, max: maxAllowed }) }}
          </p>
        </div>

        <!-- Platform Cards Grid -->
        <div class="grid grid-cols-1 gap-4 tablet:grid-cols-2">
          <div
            v-for="platform in SUPPORTED_PLATFORMS"
            :key="platform"
            class="relative rounded-lg border-2 p-6 transition-all"
            :class="{
              'border-gray-200 dark:border-gray-700 hover:border-gray-400 dark:hover:border-gray-500 hover:shadow-md cursor-pointer': !isConnected(platform) && !isAtLimit,
              'border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-700/30 cursor-not-allowed': isConnected(platform) || isAtLimit,
            }"
            @click="handleConnect(platform)"
          >
            <!-- Connected Checkmark -->
            <div
              v-if="isConnected(platform)"
              class="absolute top-3 right-3 flex h-6 w-6 items-center justify-center rounded-full bg-green-500"
            >
              <CheckIcon class="h-4 w-4 text-white" />
            </div>

            <!-- Platform Icon -->
            <div class="mb-4 flex items-center justify-center">
              <div
                class="flex h-16 w-16 items-center justify-center rounded-full text-2xl font-bold text-white shadow-lg"
                :style="{ backgroundColor: PLATFORM_CONFIG[platform].color }"
              >
                {{ getPlatformInitial(platform) }}
              </div>
            </div>

            <!-- Platform Name -->
            <div class="text-center">
              <h4 class="text-lg font-bold text-gray-900 dark:text-gray-100">
                {{ PLATFORM_CONFIG[platform].label }}
              </h4>
              <p class="mt-2 text-sm text-gray-600 dark:text-gray-400">
                {{ getPlatformDescription(platform) }}
              </p>
            </div>

            <!-- Action Button -->
            <div class="mt-4">
              <button
                v-if="isConnected(platform)"
                class="btn-secondary w-full cursor-not-allowed opacity-60"
                disabled
              >
                <CheckIcon class="h-4 w-4 inline-block mr-1" />
                {{ $t('channels.connected') }}
              </button>
              <button
                v-else
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
                  {{ $t('channels.connect') }}
                </template>
              </button>
            </div>
          </div>
        </div>

        <!-- Footer -->
        <div class="mt-6 flex justify-end border-t border-gray-100 dark:border-gray-700 pt-4">
          <button class="btn-secondary" @click="close">
            {{ $t('channels.close') }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { CheckIcon, LinkIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import { useI18n } from 'vue-i18n'
import type { Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'

const { t } = useI18n()

const SUPPORTED_PLATFORMS: Platform[] = ['YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'NAVER_CLIP', 'TWITTER', 'FACEBOOK', 'THREADS']

const props = defineProps<{
  modelValue: boolean
  connectedPlatforms: Platform[]
  maxAllowed: number
  currentCount: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'connect': [platform: Platform]
}>()

const connectingPlatform = ref<Platform | null>(null)

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
  }
  return descriptions[platform] || ''
}

function handleConnect(platform: Platform) {
  if (isConnected(platform) || isAtLimit.value || connectingPlatform.value) return

  connectingPlatform.value = platform

  // Initiate real OAuth flow — redirect to backend authorization endpoint
  emit('connect', platform)
}
</script>
