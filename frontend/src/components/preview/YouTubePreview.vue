<template>
  <div class="youtube-preview">
    <div class="mb-3 flex items-center justify-between">
      <span class="text-sm font-medium text-gray-700 dark:text-gray-300">이렇게 보여요</span>
      <div class="flex gap-1 rounded-lg bg-gray-100 p-0.5 dark:bg-gray-800">
        <button
          @click="viewMode = 'feed'"
          :class="[
            'rounded-md px-2.5 py-1 text-xs font-medium transition-colors',
            viewMode === 'feed'
              ? 'bg-white text-gray-900 shadow-sm dark:bg-gray-700 dark:text-gray-100'
              : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200',
          ]"
        >
          피드
        </button>
        <button
          @click="viewMode = 'search'"
          :class="[
            'rounded-md px-2.5 py-1 text-xs font-medium transition-colors',
            viewMode === 'search'
              ? 'bg-white text-gray-900 shadow-sm dark:bg-gray-700 dark:text-gray-100'
              : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200',
          ]"
        >
          검색결과
        </button>
      </div>
    </div>

    <!-- Feed Card Layout (기본) -->
    <div v-if="viewMode === 'feed'" class="overflow-hidden rounded-lg bg-white shadow-lg dark:bg-gray-900">
      <!-- Thumbnail -->
      <div class="relative aspect-video bg-black">
        <img
          v-if="thumbnail"
          :src="thumbnail"
          alt="썸네일"
          class="h-full w-full object-cover"
        />
        <div
          v-else
          class="flex h-full w-full items-center justify-center bg-gradient-to-br from-gray-800 to-gray-900"
        >
          <svg class="h-16 w-16 text-red-600 opacity-80" viewBox="0 0 68 48" fill="currentColor">
            <path d="M66.52 7.74c-.78-2.93-2.49-5.41-5.42-6.19C55.79.13 34 0 34 0S12.21.13 6.9 1.55C3.97 2.33 2.27 4.81 1.48 7.74.06 13.05 0 24 0 24s.06 10.95 1.48 16.26c.78 2.93 2.49 5.41 5.42 6.19C12.21 47.87 34 48 34 48s21.79-.13 27.1-1.55c2.93-.78 4.64-3.26 5.42-6.19C67.94 34.95 68 24 68 24s-.06-10.95-1.48-16.26z" fill="#FF0000"/>
            <path d="M45 24L27 14v20" fill="white"/>
          </svg>
        </div>
        <!-- Duration badge -->
        <div class="absolute bottom-2 right-2 rounded bg-black/80 px-1.5 py-0.5 text-xs font-medium text-white">
          0:00
        </div>
      </div>

      <!-- Video Info -->
      <div class="p-3">
        <div class="flex gap-3">
          <!-- Channel Avatar -->
          <div class="mt-0.5 flex-shrink-0">
            <div class="flex h-9 w-9 items-center justify-center rounded-full bg-red-600">
              <span class="text-sm font-bold text-white">{{ channelInitial }}</span>
            </div>
          </div>

          <div class="min-w-0 flex-1">
            <!-- Title -->
            <h3
              class="mb-1 line-clamp-2 text-sm font-medium leading-5 text-gray-900 dark:text-gray-100"
              :title="title"
            >
              {{ displayTitle || '영상 제목을 입력하세요' }}
            </h3>

            <!-- Title Character Indicator -->
            <div class="mb-1 flex items-center gap-1.5">
              <span :class="['text-[11px]', titleCountColor]">
                {{ titleLength }}/100자
              </span>
              <span
                v-if="titleLength > 100"
                class="rounded bg-red-100 px-1.5 py-0.5 text-[10px] font-semibold text-red-600 dark:bg-red-900/40 dark:text-red-400"
              >
                100자 초과
              </span>
            </div>

            <!-- Channel & Meta -->
            <div class="text-xs text-gray-600 dark:text-gray-400">
              <span>{{ channelName || '채널명' }}</span>
              <span class="mx-1">·</span>
              <span>조회수 0회</span>
              <span class="mx-1">·</span>
              <span>방금 전</span>
            </div>
          </div>

          <!-- More button -->
          <div class="flex-shrink-0">
            <svg class="h-5 w-5 text-gray-500 dark:text-gray-400" fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/>
            </svg>
          </div>
        </div>
      </div>

      <!-- Description Section -->
      <div v-if="description" class="border-t border-gray-100 px-3 pb-3 dark:border-gray-800">
        <div class="mt-3">
          <p class="line-clamp-3 text-xs leading-5 text-gray-700 dark:text-gray-300 whitespace-pre-line">{{ description }}</p>
          <button class="mt-1 text-xs font-medium text-gray-600 dark:text-gray-400">더보기</button>
        </div>
      </div>

      <!-- Tags -->
      <div v-if="tags && tags.length > 0" class="border-t border-gray-100 px-3 pb-3 pt-2 dark:border-gray-800">
        <div class="flex flex-wrap gap-1.5">
          <span
            v-for="(tag, idx) in tags.slice(0, 5)"
            :key="idx"
            class="text-xs font-medium text-blue-600 dark:text-blue-400"
          >
            #{{ tag }}
          </span>
          <span
            v-if="tags.length > 5"
            class="rounded-full bg-gray-100 px-2 py-0.5 text-[11px] font-medium text-gray-500 dark:bg-gray-800 dark:text-gray-400"
          >
            +{{ tags.length - 5 }}
          </span>
        </div>
      </div>

      <!-- Action Bar -->
      <div class="flex items-center justify-between border-t border-gray-100 px-1 py-1 dark:border-gray-800">
        <button class="flex flex-1 items-center justify-center gap-1.5 rounded-full py-2 text-gray-700 dark:text-gray-300">
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M14 9V5a3 3 0 00-3-3l-4 9v11h11.28a2 2 0 002-1.7l1.38-9a2 2 0 00-2-2.3H14z"/>
          </svg>
          <span class="text-xs font-medium">좋아요</span>
        </button>
        <button class="flex flex-1 items-center justify-center gap-1.5 rounded-full py-2 text-gray-700 dark:text-gray-300">
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M10 15V19a3 3 0 003 3l4-9V2H5.72a2 2 0 00-2 1.7l-1.38 9a2 2 0 002 2.3H10z"/>
          </svg>
        </button>
        <button class="flex flex-1 items-center justify-center gap-1.5 rounded-full py-2 text-gray-700 dark:text-gray-300">
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z"/>
          </svg>
          <span class="text-xs font-medium">공유</span>
        </button>
        <button class="flex flex-1 items-center justify-center gap-1.5 rounded-full py-2 text-gray-700 dark:text-gray-300">
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z"/>
          </svg>
          <span class="text-xs font-medium">저장</span>
        </button>
      </div>
    </div>

    <!-- Search Result Card Layout -->
    <div v-else class="overflow-hidden rounded-lg bg-white shadow-lg dark:bg-gray-900">
      <div class="flex gap-3 p-3">
        <!-- Thumbnail (left) -->
        <div class="relative w-[168px] flex-shrink-0">
          <div class="aspect-video overflow-hidden rounded-lg bg-black">
            <img
              v-if="thumbnail"
              :src="thumbnail"
              alt="썸네일"
              class="h-full w-full object-cover"
            />
            <div
              v-else
              class="flex h-full w-full items-center justify-center bg-gradient-to-br from-gray-800 to-gray-900"
            >
              <svg class="h-10 w-10 text-red-600 opacity-80" viewBox="0 0 68 48" fill="currentColor">
                <path d="M66.52 7.74c-.78-2.93-2.49-5.41-5.42-6.19C55.79.13 34 0 34 0S12.21.13 6.9 1.55C3.97 2.33 2.27 4.81 1.48 7.74.06 13.05 0 24 0 24s.06 10.95 1.48 16.26c.78 2.93 2.49 5.41 5.42 6.19C12.21 47.87 34 48 34 48s21.79-.13 27.1-1.55c2.93-.78 4.64-3.26 5.42-6.19C67.94 34.95 68 24 68 24s-.06-10.95-1.48-16.26z" fill="#FF0000"/>
                <path d="M45 24L27 14v20" fill="white"/>
              </svg>
            </div>
            <!-- Duration badge -->
            <div class="absolute bottom-1 right-1 rounded bg-black/80 px-1 py-0.5 text-[10px] font-medium text-white">
              0:00
            </div>
          </div>
        </div>

        <!-- Info (right) -->
        <div class="min-w-0 flex-1">
          <h3
            class="mb-1 line-clamp-2 text-sm font-medium leading-5 text-gray-900 dark:text-gray-100"
            :title="title"
          >
            {{ displayTitle || '영상 제목을 입력하세요' }}
          </h3>

          <!-- Title Character Indicator -->
          <div class="mb-1.5 flex items-center gap-1.5">
            <span :class="['text-[11px]', titleCountColor]">
              {{ titleLength }}/100자
            </span>
            <span
              v-if="titleLength > 100"
              class="rounded bg-red-100 px-1.5 py-0.5 text-[10px] font-semibold text-red-600 dark:bg-red-900/40 dark:text-red-400"
            >
              100자 초과
            </span>
          </div>

          <!-- Channel & Meta -->
          <div class="mb-1 flex items-center gap-1.5 text-xs text-gray-600 dark:text-gray-400">
            <div class="flex h-5 w-5 items-center justify-center rounded-full bg-red-600">
              <span class="text-[9px] font-bold text-white">{{ channelInitial }}</span>
            </div>
            <span>{{ channelName || '채널명' }}</span>
          </div>
          <div class="text-xs text-gray-500 dark:text-gray-500">
            조회수 0회 · 방금 전
          </div>

          <!-- Description snippet -->
          <p
            v-if="description"
            class="mt-1 line-clamp-1 text-[11px] text-gray-500 dark:text-gray-500"
          >
            {{ description }}
          </p>

          <!-- Tags (inline) -->
          <div v-if="tags && tags.length > 0" class="mt-1.5 flex flex-wrap gap-1">
            <span
              v-for="(tag, idx) in tags.slice(0, 3)"
              :key="idx"
              class="text-[11px] font-medium text-blue-600 dark:text-blue-400"
            >
              #{{ tag }}
            </span>
            <span
              v-if="tags.length > 3"
              class="text-[11px] text-gray-400"
            >
              +{{ tags.length - 3 }}
            </span>
          </div>
        </div>

        <!-- More button -->
        <div class="flex-shrink-0">
          <svg class="h-5 w-5 text-gray-500 dark:text-gray-400" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/>
          </svg>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

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

const viewMode = ref<'feed' | 'search'>('feed')

const titleLength = computed(() => props.title.length)

const displayTitle = computed(() => {
  if (props.title.length > 100) {
    return props.title.slice(0, 100) + '...'
  }
  return props.title
})

const titleCountColor = computed(() => {
  if (titleLength.value > 100) return 'text-red-500 font-semibold'
  if (titleLength.value >= 80) return 'text-yellow-500'
  return 'text-green-500'
})

const channelInitial = computed(() => {
  const name = props.channelName || '채'
  return name.charAt(0).toUpperCase()
})
</script>

<style scoped>
.youtube-preview {
  @apply w-full;
}
</style>
