<template>
  <div class="instagram-preview">
    <div class="mb-3 text-sm font-medium text-gray-700 dark:text-gray-300">
      {{ t('preview.howItLooks') }}
    </div>

    <!-- Phone Frame -->
    <div class="mx-auto max-w-[360px]">
      <div class="rounded-[2rem] border-4 border-gray-800 bg-black shadow-2xl dark:border-gray-700">
        <!-- Phone Screen -->
        <div class="relative aspect-[9/16] overflow-hidden rounded-[1.5rem] bg-gradient-to-br from-gray-900 to-black">
          <!-- Video Area with Thumbnail -->
          <div class="absolute inset-0">
            <img
              v-if="thumbnail"
              :src="thumbnail"
              alt="썸네일"
              class="h-full w-full object-cover"
            />
            <div
              v-else
              class="flex h-full w-full items-center justify-center"
            >
              <svg class="h-16 w-16 text-gray-600" fill="currentColor" viewBox="0 0 24 24">
                <path d="M10 16.5l6-4.5-6-4.5v9zM12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z" />
              </svg>
            </div>
          </div>

          <!-- Overlay Gradient -->
          <div class="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-black/20" />

          <!-- Top Header -->
          <div class="absolute left-0 right-0 top-0 flex items-center justify-between px-4 py-3">
            <div class="flex items-center gap-2">
              <!-- Instagram Reels icon -->
              <svg class="h-5 w-5 text-white" fill="currentColor" viewBox="0 0 24 24">
                <path d="M12 2.982c2.937 0 3.285.011 4.445.064a6.087 6.087 0 0 1 2.042.379 3.408 3.408 0 0 1 1.265.823 3.408 3.408 0 0 1 .823 1.265 6.087 6.087 0 0 1 .379 2.042c.053 1.16.064 1.508.064 4.445s-.011 3.285-.064 4.445a6.087 6.087 0 0 1-.379 2.042 3.643 3.643 0 0 1-2.088 2.088 6.087 6.087 0 0 1-2.042.379c-1.16.053-1.508.064-4.445.064s-3.285-.011-4.445-.064a6.087 6.087 0 0 1-2.042-.379 3.408 3.408 0 0 1-1.265-.823 3.408 3.408 0 0 1-.823-1.265 6.087 6.087 0 0 1-.379-2.042c-.053-1.16-.064-1.508-.064-4.445s.011-3.285.064-4.445a6.087 6.087 0 0 1 .379-2.042 3.408 3.408 0 0 1 .823-1.265 3.408 3.408 0 0 1 1.265-.823 6.087 6.087 0 0 1 2.042-.379c1.16-.053 1.508-.064 4.445-.064M12 1c-2.987 0-3.362.013-4.535.066a8.074 8.074 0 0 0-2.67.511 5.392 5.392 0 0 0-1.949 1.27 5.392 5.392 0 0 0-1.27 1.949 8.074 8.074 0 0 0-.51 2.67C1.012 8.638 1 9.013 1 12s.013 3.362.066 4.535a8.074 8.074 0 0 0 .511 2.67 5.392 5.392 0 0 0 1.27 1.949 5.392 5.392 0 0 0 1.949 1.27 8.074 8.074 0 0 0 2.67.51C8.638 22.988 9.013 23 12 23s3.362-.013 4.535-.066a8.074 8.074 0 0 0 2.67-.511 5.625 5.625 0 0 0 3.219-3.219 8.074 8.074 0 0 0 .51-2.67C22.988 15.362 23 14.987 23 12s-.013-3.362-.066-4.535a8.074 8.074 0 0 0-.511-2.67 5.392 5.392 0 0 0-1.27-1.949 5.392 5.392 0 0 0-1.949-1.27 8.074 8.074 0 0 0-2.67-.51C15.362 1.012 14.987 1 12 1z" />
              </svg>
              <span class="text-sm font-semibold text-white">릴스</span>
            </div>
            <svg class="h-6 w-6 text-white" fill="currentColor" viewBox="0 0 24 24">
              <path d="M2 6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6zm2 0v12h16V6H4z" />
            </svg>
          </div>

          <!-- Right Side Action Buttons -->
          <div class="absolute bottom-28 right-3 flex flex-col items-center gap-5">
            <!-- Heart -->
            <div class="flex flex-col items-center gap-1">
              <svg class="h-7 w-7 text-white drop-shadow" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
              </svg>
              <span class="text-[11px] font-semibold text-white drop-shadow">1,234</span>
            </div>

            <!-- Comment -->
            <div class="flex flex-col items-center gap-1">
              <svg class="h-7 w-7 text-white drop-shadow" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
              </svg>
              <span class="text-[11px] font-semibold text-white drop-shadow">56</span>
            </div>

            <!-- Share -->
            <div class="flex flex-col items-center gap-1">
              <svg class="h-7 w-7 text-white drop-shadow" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 19V5m0 0l-4 4m4-4l4 4" />
              </svg>
            </div>

            <!-- Save / Bookmark -->
            <div class="flex flex-col items-center gap-1">
              <svg class="h-7 w-7 text-white drop-shadow" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
              </svg>
            </div>

            <!-- More (three dots) -->
            <div class="flex flex-col items-center gap-1">
              <svg class="h-7 w-7 text-white drop-shadow" fill="currentColor" viewBox="0 0 24 24">
                <circle cx="12" cy="5" r="2" />
                <circle cx="12" cy="12" r="2" />
                <circle cx="12" cy="19" r="2" />
              </svg>
            </div>

            <!-- Profile circle with border -->
            <div class="mt-1 h-8 w-8 overflow-hidden rounded-lg border-2 border-white bg-gray-700">
              <div class="flex h-full w-full items-center justify-center">
                <svg class="h-4 w-4 text-white" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
                </svg>
              </div>
            </div>
          </div>

          <!-- Bottom Info Overlay -->
          <div class="absolute bottom-0 left-0 right-14 px-4 pb-4">
            <!-- Channel Name + Follow -->
            <div class="mb-2 flex items-center gap-2">
              <div class="flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-tr from-yellow-400 via-red-500 to-purple-600 p-0.5">
                <div class="flex h-full w-full items-center justify-center rounded-full bg-black">
                  <svg class="h-4 w-4 text-white" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
                  </svg>
                </div>
              </div>
              <span class="text-sm font-semibold text-white drop-shadow">
                {{ channelName || t('preview.username') }}
              </span>
              <span class="text-white/60">·</span>
              <button class="rounded-lg border border-white/80 px-3 py-0.5 text-xs font-semibold text-white">
                팔로우
              </button>
            </div>

            <!-- Caption (description + hashtags merged) -->
            <div class="relative">
              <div
                v-if="!captionExpanded"
                class="text-sm leading-relaxed text-white drop-shadow"
              >
                <span class="line-clamp-2">{{ displayCaption }}</span>
                <button
                  v-if="shouldTruncateCaption"
                  class="ml-1 text-gray-300"
                  @click="captionExpanded = true"
                >... 더 보기</button>
              </div>
              <div
                v-else
                class="max-h-32 overflow-y-auto text-sm leading-relaxed text-white drop-shadow"
              >
                <span class="whitespace-pre-wrap">{{ displayCaption }}</span>
                <button
                  class="ml-1 text-gray-300"
                  @click="captionExpanded = false"
                >접기</button>
              </div>
            </div>

            <!-- Character Count Indicator -->
            <div class="mt-1.5">
              <span
                class="text-[10px] font-medium"
                :class="captionLength > 2200 ? 'text-red-400' : 'text-white/60'"
              >
                {{ captionLength.toLocaleString() }}{{ t('preview.charCount', { max: '2,200' }) }}
              </span>
              <span
                v-if="captionLength > 2200"
                class="ml-1 text-[10px] text-red-400"
              >{{ t('preview.exceeded') }}</span>
            </div>
          </div>

          <!-- Bottom nav bar -->
          <div class="absolute bottom-0 left-0 right-0 flex items-center justify-around border-t border-white/10 bg-black/50 px-2 py-2 backdrop-blur-sm">
            <svg class="h-6 w-6 text-white/60" fill="currentColor" viewBox="0 0 24 24">
              <path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z" />
            </svg>
            <svg class="h-6 w-6 text-white/60" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <svg class="h-7 w-7 text-white/60" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
            <svg class="h-6 w-6 text-white" fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 2.982c2.937 0 3.285.011 4.445.064a6.087 6.087 0 0 1 2.042.379 3.408 3.408 0 0 1 1.265.823 3.408 3.408 0 0 1 .823 1.265 6.087 6.087 0 0 1 .379 2.042c.053 1.16.064 1.508.064 4.445s-.011 3.285-.064 4.445a6.087 6.087 0 0 1-.379 2.042 3.643 3.643 0 0 1-2.088 2.088 6.087 6.087 0 0 1-2.042.379c-1.16.053-1.508.064-4.445.064s-3.285-.011-4.445-.064a6.087 6.087 0 0 1-2.042-.379 3.408 3.408 0 0 1-1.265-.823 3.408 3.408 0 0 1-.823-1.265 6.087 6.087 0 0 1-.379-2.042c-.053-1.16-.064-1.508-.064-4.445s.011-3.285.064-4.445a6.087 6.087 0 0 1 .379-2.042 3.408 3.408 0 0 1 .823-1.265 3.408 3.408 0 0 1 1.265-.823 6.087 6.087 0 0 1 2.042-.379c1.16-.053 1.508-.064 4.445-.064M12 1c-2.987 0-3.362.013-4.535.066a8.074 8.074 0 0 0-2.67.511 5.392 5.392 0 0 0-1.949 1.27 5.392 5.392 0 0 0-1.27 1.949 8.074 8.074 0 0 0-.51 2.67C1.012 8.638 1 9.013 1 12s.013 3.362.066 4.535a8.074 8.074 0 0 0 .511 2.67 5.392 5.392 0 0 0 1.27 1.949 5.392 5.392 0 0 0 1.949 1.27 8.074 8.074 0 0 0 2.67.51C8.638 22.988 9.013 23 12 23s3.362-.013 4.535-.066a8.074 8.074 0 0 0 2.67-.511 5.625 5.625 0 0 0 3.219-3.219 8.074 8.074 0 0 0 .51-2.67C22.988 15.362 23 14.987 23 12s-.013-3.362-.066-4.535a8.074 8.074 0 0 0-.511-2.67 5.392 5.392 0 0 0-1.27-1.949 5.392 5.392 0 0 0-1.949-1.27 8.074 8.074 0 0 0-2.67-.51C15.362 1.012 14.987 1 12 1z" />
            </svg>
            <div class="h-6 w-6 rounded-full border border-white/60 bg-gray-700" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

interface Props {
  title?: string
  description?: string
  thumbnail?: string
  channelName?: string
  tags?: string[]
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  description: '',
  thumbnail: '',
  channelName: '',
  tags: () => [],
})

const captionExpanded = ref(false)

const hashtagsText = computed(() => {
  if (!props.tags || props.tags.length === 0) return ''
  return props.tags.map(tag => `#${tag}`).join(' ')
})

const displayCaption = computed(() => {
  const parts: string[] = []
  if (props.title) parts.push(props.title)
  if (props.description) parts.push(props.description)
  const text = parts.join('\n')
  if (hashtagsText.value) {
    return text ? `${text}\n\n${hashtagsText.value}` : hashtagsText.value
  }
  return text || t('preview.enterCaption')
})

const captionLength = computed(() => {
  const parts: string[] = []
  if (props.title) parts.push(props.title)
  if (props.description) parts.push(props.description)
  const text = parts.join('\n')
  const hashtags = hashtagsText.value
  if (text && hashtags) return (text + '\n\n' + hashtags).length
  return (text + hashtags).length
})

const shouldTruncateCaption = computed(() => {
  return displayCaption.value.length > 80 || displayCaption.value.includes('\n')
})
</script>

<style scoped>
.instagram-preview {
  @apply w-full;
}
</style>
