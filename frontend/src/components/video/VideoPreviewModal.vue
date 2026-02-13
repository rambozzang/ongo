<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-opacity duration-200"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition-opacity duration-200"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="modelValue"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/90 p-4"
        role="dialog"
        aria-modal="true"
        :aria-label="`영상 미리보기: ${video.title}`"
        @click="handleBackdropClick"
      >
        <Transition
          enter-active-class="transition-all duration-200"
          enter-from-class="opacity-0 scale-95"
          enter-to-class="opacity-100 scale-100"
          leave-active-class="transition-all duration-200"
          leave-from-class="opacity-100 scale-100"
          leave-to-class="opacity-0 scale-95"
        >
          <div
            v-if="modelValue"
            class="relative w-full max-w-6xl"
            @click.stop
          >
            <!-- Close Button -->
            <button
              class="absolute -top-12 right-0 rounded-full bg-white/10 p-2 text-white backdrop-blur-sm transition-colors hover:bg-white/20"
              aria-label="미리보기 닫기"
              @click="closeModal"
            >
              <XMarkIcon class="h-6 w-6" />
            </button>

            <!-- Video Player -->
            <VideoPlayer
              :src="video.fileUrl"
              :poster="video.thumbnailUrl || undefined"
              class="mb-4"
            />

            <!-- Video Info -->
            <div class="rounded-lg bg-gray-900/50 p-4 backdrop-blur-sm">
              <h2 class="mb-3 text-xl font-bold text-white">{{ video.title }}</h2>

              <!-- Platform Badges -->
              <div v-if="video.uploads.length > 0" class="flex flex-wrap gap-2">
                <div
                  v-for="upload in video.uploads"
                  :key="upload.id"
                  class="flex items-center gap-1.5"
                >
                  <PlatformBadge :platform="upload.platform" />
                  <StatusBadge :status="upload.status" />
                </div>
              </div>

              <!-- Description -->
              <p
                v-if="video.description"
                class="mt-3 text-sm text-gray-300 line-clamp-3"
              >
                {{ video.description }}
              </p>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { XMarkIcon } from '@heroicons/vue/24/solid'
import VideoPlayer from './VideoPlayer.vue'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import type { Video } from '@/types/video'

const props = defineProps<{
  modelValue: boolean
  video: Video
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

function closeModal() {
  emit('update:modelValue', false)
}

function handleBackdropClick() {
  closeModal()
}

function handleEscape(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.modelValue) {
    closeModal()
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleEscape)
  // Prevent body scroll when modal is open
  if (props.modelValue) {
    document.body.style.overflow = 'hidden'
  }
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleEscape)
  document.body.style.overflow = ''
})
</script>
