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
          <h3 id="connect-channel-title" class="text-xl font-bold text-gray-900 dark:text-gray-100">새 채널 연결</h3>
          <p class="mt-2 text-sm text-gray-600 dark:text-gray-300">
            콘텐츠를 업로드할 플랫폼을 연결하세요.
          </p>
        </div>

        <!-- Platform Cards Grid -->
        <div class="grid grid-cols-1 gap-4 tablet:grid-cols-2">
          <div
            v-for="(config, platform) in PLATFORM_CONFIG"
            :key="platform"
            class="relative rounded-lg border-2 p-6 transition-all"
            :class="{
              'border-gray-200 dark:border-gray-700 hover:border-gray-400 dark:hover:border-gray-500 hover:shadow-md cursor-pointer': !isConnected(platform),
              'border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-700/30 cursor-not-allowed': isConnected(platform),
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
                :style="{ backgroundColor: config.color }"
              >
                {{ getPlatformInitial(platform) }}
              </div>
            </div>

            <!-- Platform Name -->
            <div class="text-center">
              <h4 class="text-lg font-bold text-gray-900 dark:text-gray-100">
                {{ config.label }}
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
                연결됨
              </button>
              <button
                v-else
                class="btn-primary w-full"
                :disabled="connectingPlatform === platform"
                @click.stop="handleConnect(platform)"
              >
                <template v-if="connectingPlatform === platform">
                  <ArrowPathIcon class="h-4 w-4 inline-block mr-1 animate-spin" />
                  연결 중...
                </template>
                <template v-else>
                  <LinkIcon class="h-4 w-4 inline-block mr-1" />
                  연결하기
                </template>
              </button>
            </div>
          </div>
        </div>

        <!-- Footer -->
        <div class="mt-6 flex justify-end border-t border-gray-100 dark:border-gray-700 pt-4">
          <button class="btn-secondary" @click="close">
            닫기
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { CheckIcon, LinkIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import type { Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'

const props = defineProps<{
  modelValue: boolean
  connectedPlatforms: Platform[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'connect': [platform: Platform]
}>()

const connectingPlatform = ref<Platform | null>(null)

function close() {
  emit('update:modelValue', false)
}

function isConnected(platform: Platform): boolean {
  return props.connectedPlatforms.includes(platform)
}

function getPlatformInitial(platform: Platform): string {
  const initials: Record<Platform, string> = {
    YOUTUBE: 'Y',
    TIKTOK: 'T',
    INSTAGRAM: 'I',
    NAVER_CLIP: 'N',
  }
  return initials[platform]
}

function getPlatformDescription(platform: Platform): string {
  const descriptions: Record<Platform, string> = {
    YOUTUBE: '세계 최대 동영상 플랫폼',
    TIKTOK: '숏폼 콘텐츠 플랫폼',
    INSTAGRAM: '사진 및 릴스 공유 플랫폼',
    NAVER_CLIP: '네이버 동영상 플랫폼',
  }
  return descriptions[platform]
}

function handleConnect(platform: Platform) {
  if (isConnected(platform) || connectingPlatform.value) return

  connectingPlatform.value = platform

  // Initiate real OAuth flow — redirect to backend authorization endpoint
  emit('connect', platform)
}
</script>
