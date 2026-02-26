<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  PlusIcon,
  XMarkIcon,
  ArrowPathIcon,
  TagIcon,
  QueueListIcon,
} from '@heroicons/vue/24/outline'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PlaylistCard from '@/components/playlistmanager/PlaylistCard.vue'
import PlatformFilter from '@/components/playlistmanager/PlatformFilter.vue'
import PlaylistStats from '@/components/playlistmanager/PlaylistStats.vue'
import { usePlaylistManagerStore } from '@/stores/playlistManager'
import { storeToRefs } from 'pinia'
import type { CreatePlaylistRequest } from '@/types/playlistManager'

const store = usePlaylistManagerStore()
const { playlists, summary, isLoading } = storeToRefs(store)

/* ---- State ---- */
const showCreateModal = ref(false)
const selectedPlatform = ref('ALL')
const isSyncingAll = ref(false)

/* ---- Form ---- */
const form = ref<CreatePlaylistRequest>({
  title: '',
  description: '',
  platform: 'YOUTUBE',
  visibility: 'PUBLIC',
  tags: [],
})
const tagInput = ref('')

/* ---- Computed ---- */
const filteredPlaylists = computed(() => {
  if (selectedPlatform.value === 'ALL') return playlists.value
  return playlists.value.filter((p) => p.platform === selectedPlatform.value)
})

/* ---- Helpers ---- */
/* ---- Handlers ---- */
function openCreate() {
  form.value = {
    title: '',
    description: '',
    platform: 'YOUTUBE',
    visibility: 'PUBLIC',
    tags: [],
  }
  tagInput.value = ''
  showCreateModal.value = true
}

async function handleCreate() {
  if (!form.value.title.trim()) return
  await store.createPlaylist({ ...form.value })
  showCreateModal.value = false
}

function handleCardClick(_id: number) {
  // 향후 상세 뷰 연결
}

function handleDelete(id: number) {
  if (confirm('이 재생목록을 삭제하시겠습니까?')) {
    store.deletePlaylist(id)
  }
}

async function handleSync(id: number) {
  await store.syncPlaylist(id)
}

async function handleSyncAll() {
  isSyncingAll.value = true
  try {
    const promises = playlists.value
      .filter((p) => !p.isSynced)
      .map((p) => store.syncPlaylist(p.id))
    await Promise.all(promises)
  } finally {
    isSyncingAll.value = false
  }
}

function addTag() {
  const tag = tagInput.value.trim()
  if (tag && !form.value.tags?.includes(tag)) {
    form.value.tags = [...(form.value.tags ?? []), tag]
  }
  tagInput.value = ''
}

function removeTag(tag: string) {
  form.value.tags = form.value.tags?.filter((t) => t !== tag)
}

onMounted(async () => {
  await Promise.all([store.fetchPlaylists(), store.fetchSummary()])
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          재생목록 관리
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          플랫폼별 재생목록을 관리하고 동기화하세요
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button
          @click="handleSyncAll"
          :disabled="isSyncingAll"
          class="btn-secondary inline-flex items-center gap-2"
        >
          <ArrowPathIcon class="w-5 h-5" :class="isSyncingAll ? 'animate-spin' : ''" />
          {{ isSyncingAll ? '동기화 중...' : '전체 동기화' }}
        </button>
        <button
          @click="openCreate"
          class="btn-primary inline-flex items-center gap-2"
        >
          <PlusIcon class="w-5 h-5" />
          새 재생목록
        </button>
      </div>
    </div>

    <!-- Stats -->
    <div class="mb-6">
      <PlaylistStats :summary="summary" />
    </div>

    <!-- Platform Filter -->
    <div class="mb-6">
      <PlatformFilter
        :selected="selectedPlatform"
        @update:selected="selectedPlatform = $event"
      />
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <!-- Playlist Grid -->
    <div v-else-if="filteredPlaylists.length > 0" class="grid grid-cols-1 tablet:grid-cols-2 desktop:grid-cols-3 gap-6">
      <PlaylistCard
        v-for="playlist in filteredPlaylists"
        :key="playlist.id"
        :playlist="playlist"
        @click="handleCardClick"
        @delete="handleDelete"
        @sync="handleSync"
      />
    </div>

    <!-- Empty State -->
    <div
      v-else
      class="flex flex-col items-center justify-center rounded-xl border border-dashed border-gray-300 dark:border-gray-600 py-16"
    >
      <QueueListIcon class="w-16 h-16 text-gray-400 dark:text-gray-500 mb-4" />
      <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
        재생목록이 없습니다
      </h3>
      <p class="text-sm text-gray-600 dark:text-gray-400 mb-6">
        {{ selectedPlatform === 'ALL'
          ? '새 재생목록을 만들어서 콘텐츠를 관리하세요'
          : '선택한 플랫폼의 재생목록이 없습니다'
        }}
      </p>
      <button @click="openCreate" class="btn-primary inline-flex items-center gap-2">
        <PlusIcon class="w-5 h-5" />
        첫 재생목록 만들기
      </button>
    </div>

    <!-- ============ Create Playlist Modal ============ -->
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
          v-if="showCreateModal"
          class="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4"
          @click="showCreateModal = false"
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
              v-if="showCreateModal"
              class="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-lg max-h-[90vh] overflow-hidden flex flex-col"
              @click.stop
            >
              <!-- Modal Header -->
              <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
                <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
                  새 재생목록 만들기
                </h2>
                <button
                  @click="showCreateModal = false"
                  class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                >
                  <XMarkIcon class="w-5 h-5" />
                </button>
              </div>

              <!-- Modal Body -->
              <div class="flex-1 overflow-y-auto px-6 py-4 space-y-4">
                <!-- Title -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    재생목록 제목 *
                  </label>
                  <input
                    v-model="form.title"
                    type="text"
                    placeholder="재생목록 이름을 입력하세요"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  />
                </div>

                <!-- Description -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    설명
                  </label>
                  <textarea
                    v-model="form.description"
                    rows="3"
                    placeholder="재생목록 설명을 입력하세요"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400 resize-none"
                  />
                </div>

                <!-- Platform -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    플랫폼
                  </label>
                  <select
                    v-model="form.platform"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  >
                    <option value="YOUTUBE">YouTube</option>
                    <option value="TIKTOK">TikTok</option>
                    <option value="INSTAGRAM">Instagram</option>
                    <option value="NAVER_CLIP">Naver Clip</option>
                  </select>
                </div>

                <!-- Visibility -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    공개 설정
                  </label>
                  <select
                    v-model="form.visibility"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  >
                    <option value="PUBLIC">공개</option>
                    <option value="UNLISTED">미등록</option>
                    <option value="PRIVATE">비공개</option>
                  </select>
                </div>

                <!-- Tags -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    태그
                  </label>
                  <div class="flex items-center gap-2">
                    <div class="relative flex-1">
                      <TagIcon class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                      <input
                        v-model="tagInput"
                        type="text"
                        placeholder="태그를 입력하고 Enter"
                        class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 pl-9 pr-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                        @keydown.enter.prevent="addTag"
                      />
                    </div>
                    <button
                      type="button"
                      @click="addTag"
                      class="btn-secondary px-3 py-2 text-sm"
                    >
                      추가
                    </button>
                  </div>
                  <div v-if="form.tags && form.tags.length > 0" class="flex flex-wrap gap-1.5 mt-2">
                    <span
                      v-for="tag in form.tags"
                      :key="tag"
                      class="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300"
                    >
                      #{{ tag }}
                      <button @click="removeTag(tag)" class="hover:text-primary-900 dark:hover:text-primary-100">
                        <XMarkIcon class="w-3 h-3" />
                      </button>
                    </span>
                  </div>
                </div>
              </div>

              <!-- Modal Footer -->
              <div class="px-6 py-4 border-t border-gray-200 dark:border-gray-700 flex items-center justify-end gap-3">
                <button
                  @click="showCreateModal = false"
                  class="btn-secondary"
                >
                  취소
                </button>
                <button
                  @click="handleCreate"
                  :disabled="!form.title.trim()"
                  class="btn-primary"
                >
                  만들기
                </button>
              </div>
            </div>
          </Transition>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>
