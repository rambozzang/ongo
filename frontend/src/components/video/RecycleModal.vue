<template>
  <Teleport to="body">
    <div
      v-if="modelValue"
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="recycle-modal-title"
      @keydown.escape="cancel"
    >
      <div class="fixed inset-0 bg-black/50" aria-hidden="true" @click="cancel" />
      <div class="glass-elevated relative w-full max-w-2xl overflow-hidden rounded-xl">
        <!-- Header -->
        <div class="border-b border-gray-200 dark:border-gray-700 px-6 py-4">
          <h3 id="recycle-modal-title" class="text-lg font-semibold text-gray-900 dark:text-gray-100">콘텐츠 재활용</h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            기존 영상의 메타데이터를 활용하여 다시 게시합니다
          </p>
        </div>

        <!-- Content -->
        <div class="max-h-[calc(100vh-200px)] overflow-y-auto p-6">
          <!-- Original Video Info -->
          <div class="mb-6 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50 p-4">
            <p class="mb-2 text-xs font-medium text-gray-500 dark:text-gray-400">원본 영상</p>
            <div class="flex gap-3">
              <div class="h-16 w-28 flex-shrink-0 overflow-hidden rounded bg-gray-100 dark:bg-gray-800">
                <img
                  v-if="video.thumbnailUrl"
                  :src="video.thumbnailUrl"
                  :alt="video.title"
                  class="h-full w-full object-cover"
                />
                <div v-else class="flex h-full w-full items-center justify-center">
                  <VideoCameraIcon class="h-6 w-6 text-gray-300 dark:text-gray-600" />
                </div>
              </div>
              <div class="min-w-0 flex-1">
                <p class="line-clamp-1 text-sm font-medium text-gray-900 dark:text-gray-100">
                  {{ video.title }}
                </p>
                <div class="mt-1 flex flex-wrap items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
                  <span>{{ formatDate(video.createdAt) }}</span>
                  <span v-if="video.uploads.length > 0">&middot;</span>
                  <div class="flex flex-wrap gap-1">
                    <PlatformBadge
                      v-for="upload in video.uploads"
                      :key="`${upload.platform}#${upload.channelId ?? 'default'}`"
                      :platform="upload.platform"
                      class="scale-90"
                    />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Form -->
          <form class="space-y-4" @submit.prevent="handleSubmit">
            <!-- Title -->
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                제목 *
              </label>
              <input
                v-model="formData.title"
                type="text"
                class="input w-full"
                placeholder="영상 제목 입력"
                required
              />
            </div>

            <!-- Description -->
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                설명
              </label>
              <textarea
                v-model="formData.description"
                rows="4"
                class="input w-full resize-none"
                placeholder="영상 설명 입력"
              />
            </div>

            <!-- Tags -->
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                태그
              </label>
              <input
                v-model="tagInput"
                type="text"
                class="input w-full"
                placeholder="태그 입력 후 Enter (예: 브이로그, 여행, 일상)"
                @keydown.enter.prevent="addTag"
              />
              <div v-if="formData.tags.length > 0" class="mt-2 flex flex-wrap gap-1.5">
                <span
                  v-for="(tag, idx) in formData.tags"
                  :key="idx"
                  class="inline-flex items-center gap-1 rounded-full bg-gray-100 dark:bg-gray-800 px-2.5 py-1 text-xs font-medium text-gray-700 dark:text-gray-300"
                >
                  #{{ tag }}
                  <button
                    type="button"
                    class="text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300"
                    :aria-label="`태그 '${tag}' 삭제`"
                    @click="removeTag(idx)"
                  >
                    <XMarkIcon class="h-3 w-3" />
                  </button>
                </span>
              </div>
            </div>

            <!-- Category -->
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                카테고리
              </label>
              <select v-model="formData.category" class="input w-full">
                <option value="">카테고리 선택</option>
                <option value="교육">교육</option>
                <option value="엔터테인먼트">엔터테인먼트</option>
                <option value="음악">음악</option>
                <option value="브이로그">브이로그</option>
                <option value="게임">게임</option>
                <option value="스포츠">스포츠</option>
                <option value="뉴스">뉴스</option>
                <option value="기타">기타</option>
              </select>
            </div>

            <!-- Target Platforms -->
            <div>
              <label class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">
                업로드 플랫폼 *
              </label>
              <p v-if="channelsLoading" class="mb-2 text-xs text-gray-500 dark:text-gray-400">
                연결된 게시 계정을 불러오는 중...
              </p>
              <p v-if="channelsError" role="alert" class="mb-2 rounded border border-error-subtle bg-error-subtle px-3 py-2 text-xs text-error-strong">
                {{ channelsError }}
              </p>
              <p v-else-if="!channelsLoading && availablePlatforms.length === 0" class="mb-2 rounded border border-dashed border-gray-300 px-3 py-2 text-xs text-gray-500 dark:border-gray-700 dark:text-gray-400">
                먼저 채널 관리에서 게시 계정을 연결해주세요.
              </p>
              <div class="grid grid-cols-2 gap-3">
                <label
                  v-for="platform in availablePlatforms"
                  :key="platform.key"
                  class="flex cursor-pointer items-center gap-2 rounded-lg border border-gray-200 dark:border-gray-700 px-3 py-2.5 transition-colors hover:bg-gray-50 dark:hover:bg-gray-800"
                  :class="{
                    'bg-primary-50 dark:bg-primary-900/20 border-primary-300 dark:border-primary-700':
                    formData.platforms.includes(platform.key),
                  }"
                >
                  <input
                    v-model="formData.platforms"
                    type="checkbox"
                    :value="platform.key"
                    class="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                  />
                  <span class="text-sm font-medium text-gray-700 dark:text-gray-300">
                    <span>
                      {{ platform.label }}
                      <span v-if="platform.channelName" class="ml-1 text-xs text-gray-500">({{ platform.channelName }})</span>
                    </span>
                  </span>
                </label>
              </div>
            </div>

            <!-- Scheduled Publish -->
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                예약 게시
              </label>
              <input
                v-model="formData.scheduledAt"
                type="datetime-local"
                class="input w-full"
              />
              <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
              비워두면 즉시 게시됩니다
              </p>
            </div>
          </form>
        </div>

        <!-- Footer -->
        <div class="border-t border-gray-200 dark:border-gray-700 px-6 py-4">
          <div class="flex justify-end gap-3">
            <button type="button" class="btn-secondary" @click="cancel">취소</button>
            <button
              type="button"
              class="btn-primary"
              :disabled="!isFormValid || recycleStore.loading"
              @click="handleSubmit"
            >
              <ArrowPathRoundedSquareIcon v-if="!recycleStore.loading" class="mr-1.5 h-4 w-4" />
              <span v-if="recycleStore.loading" class="mr-2 inline-block h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
              {{ recycleStore.loading ? '재게시 중...' : '재게시' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import {
  VideoCameraIcon,
  XMarkIcon,
  ArrowPathRoundedSquareIcon,
} from '@heroicons/vue/24/outline'
import type { Video } from '@/types/video'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import { useRecycleStore } from '@/stores/recycle'
import { useNotification } from '@/composables/useNotification'
import { channelApi } from '@/api/channel'
import type { Channel, Platform } from '@/types/channel'

interface Props {
  modelValue: boolean
  video: Video
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
}>()

const recycleStore = useRecycleStore()
const { success } = useNotification()

const formData = ref({
  title: '',
  description: '',
  tags: [] as string[],
  category: '',
  platforms: [] as string[],
  scheduledAt: '',
})

const tagInput = ref('')
const connectedChannels = ref<Channel[]>([])
const channelsLoading = ref(false)
const channelsError = ref('')

const PLATFORM_LABELS: Record<Platform, string> = {
  YOUTUBE: 'YouTube', TIKTOK: 'TikTok', INSTAGRAM: 'Instagram', NAVER_CLIP: 'Naver Clip',
  TWITTER: 'X (Twitter)', FACEBOOK: 'Facebook', THREADS: 'Threads', PINTEREST: 'Pinterest',
  LINKEDIN: 'LinkedIn', WORDPRESS: 'WordPress', TUMBLR: 'Tumblr', VIMEO: 'Vimeo', DAILYMOTION: 'Dailymotion',
}

const availablePlatforms = computed(() => connectedChannels.value
  .filter((channel) => channel.tokenStatus !== 'EXPIRED' && channel.tokenStatus !== 'DISCONNECTED')
  .map((channel) => ({
    key: `${channel.platform}#${channel.id}`,
    label: PLATFORM_LABELS[channel.platform],
    channelName: channel.channelName,
  })))

const isFormValid = computed(() => {
  return formData.value.title.trim() !== '' && formData.value.platforms.length > 0
})

function formatDate(dateStr: string): string {
  const d = new Date(dateStr)
  return d.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })
}

function addTag() {
  const tag = tagInput.value.trim()
  if (tag && !formData.value.tags.includes(tag)) {
    formData.value.tags.push(tag)
    tagInput.value = ''
  }
}

function removeTag(index: number) {
  formData.value.tags.splice(index, 1)
}

async function handleSubmit() {
  if (!isFormValid.value) return

  await recycleStore.recycleVideo(props.video.id, {
    title: formData.value.title,
    description: formData.value.description,
    tags: formData.value.tags,
    category: formData.value.category,
    platforms: formData.value.platforms,
    scheduledAt: formData.value.scheduledAt || undefined,
  })

  success('콘텐츠가 성공적으로 재게시되었습니다')
  emit('confirm')
  emit('update:modelValue', false)
}

function cancel() {
  emit('update:modelValue', false)
}

async function loadChannels() {
  channelsLoading.value = true
  channelsError.value = ''
  try {
    const response = await channelApi.list()
    connectedChannels.value = response.channels
  } catch (error) {
    connectedChannels.value = []
    channelsError.value = error instanceof Error ? error.message : '연결된 채널을 불러오지 못했습니다.'
  } finally {
    channelsLoading.value = false
  }
}

// Initialize form data when video changes
watch(
  () => props.video,
  (video) => {
    if (video) {
      formData.value = {
        title: `재활용 - ${video.title}`,
        description: video.description || '',
        tags: [...video.tags],
        category: video.category || '',
        platforms: [],
        scheduledAt: '',
      }
    }
  },
  { immediate: true }
)

watch(
  () => props.modelValue,
  (open) => {
    if (open) void loadChannels()
  },
  { immediate: true },
)
</script>
