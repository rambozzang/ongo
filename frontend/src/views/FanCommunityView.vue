<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  UsersIcon,
  PlusIcon,
  XMarkIcon,
} from '@heroicons/vue/24/outline'
import { useFanCommunityStore } from '@/stores/fanCommunity'
import PostCard from '@/components/fancommunity/PostCard.vue'
import PostTypeFilter from '@/components/fancommunity/PostTypeFilter.vue'
import CommunityStats from '@/components/fancommunity/CommunityStats.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import type { PostType, CommunityPost } from '@/types/fanCommunity'

const store = useFanCommunityStore()
const { posts, summary, isLoading } = storeToRefs(store)

// ─── Filter ──────────────────────────────────────
const selectedType = ref<string>('ALL')

const handleTypeChange = async (value: string) => {
  selectedType.value = value
  await store.fetchPosts(value === 'ALL' ? undefined : value)
}

const sortedPosts = computed(() => {
  const pinned = posts.value.filter((p) => p.isPinned)
  const unpinned = posts.value.filter((p) => !p.isPinned)
  return [...pinned, ...unpinned]
})

// ─── Post Detail ──────────────────────────────────
const handlePostClick = (_post: CommunityPost) => {
  // Detail view can be extended later
}

const handleLike = async (id: number) => {
  await store.likePost(id)
}

const handlePin = async (id: number) => {
  await store.pinPost(id)
}

// ─── New Post Modal ──────────────────────────────
const showNewPostModal = ref(false)
const newPostType = ref<PostType>('DISCUSSION')
const newPostTitle = ref('')
const newPostContent = ref('')
const newPostTags = ref('')
const newPostPinned = ref(false)

const typeOptions: { value: PostType; label: string }[] = [
  { value: 'ANNOUNCEMENT', label: '공지' },
  { value: 'DISCUSSION', label: '토론' },
  { value: 'POLL', label: '투표' },
  { value: 'QNA', label: 'Q&A' },
  { value: 'BEHIND_SCENES', label: '비하인드' },
  { value: 'FAN_ART', label: '팬아트' },
]

const openNewPostModal = () => {
  newPostType.value = 'DISCUSSION'
  newPostTitle.value = ''
  newPostContent.value = ''
  newPostTags.value = ''
  newPostPinned.value = false
  showNewPostModal.value = true
}

const closeNewPostModal = () => {
  showNewPostModal.value = false
}

const handleCreatePost = async () => {
  if (!newPostTitle.value.trim() || !newPostContent.value.trim()) return

  const tags = newPostTags.value
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean)

  await store.createPost({
    type: newPostType.value,
    title: newPostTitle.value.trim(),
    content: newPostContent.value.trim(),
    tags,
    isPinned: newPostPinned.value,
  })

  closeNewPostModal()
}

onMounted(() => {
  store.fetchPosts()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">팬 커뮤니티</h1>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          팬들과 소통하고 커뮤니티를 성장시키세요
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="openNewPostModal"
        >
          <PlusIcon class="h-5 w-5" />
          새 게시글
        </button>
      </div>
    </div>

    <!-- Community Stats -->
    <div class="mb-6">
      <CommunityStats :summary="summary" />
    </div>

    <!-- Post Type Filter -->
    <div class="mb-6">
      <PostTypeFilter :selected="selectedType" @update:selected="handleTypeChange" />
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <!-- Post List -->
    <div v-else-if="sortedPosts.length > 0" class="space-y-4">
      <PostCard
        v-for="post in sortedPosts"
        :key="post.id"
        :post="post"
        @click="handlePostClick"
        @like="handleLike"
        @pin="handlePin"
      />
    </div>

    <!-- Empty State -->
    <div
      v-else
      class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 py-16 text-center shadow-sm"
    >
      <UsersIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
      <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">게시글이 없습니다</h3>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
        첫 번째 게시글을 작성해보세요
      </p>
      <button
        class="btn-primary mt-4 inline-flex items-center gap-2"
        @click="openNewPostModal"
      >
        <PlusIcon class="h-5 w-5" />
        게시글 작성
      </button>
    </div>

    <!-- New Post Modal -->
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
          v-if="showNewPostModal"
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
          @click.self="closeNewPostModal"
        >
          <div class="w-full max-w-lg rounded-xl bg-white p-6 shadow-xl dark:bg-gray-900">
            <!-- Modal header -->
            <div class="mb-5 flex items-center justify-between">
              <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">새 게시글</h2>
              <button
                class="rounded-lg p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-800 dark:hover:text-gray-300"
                @click="closeNewPostModal"
              >
                <XMarkIcon class="h-5 w-5" />
              </button>
            </div>

            <!-- Form -->
            <div class="space-y-4">
              <!-- Post type -->
              <div>
                <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  게시글 타입
                </label>
                <select
                  v-model="newPostType"
                  class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
                >
                  <option
                    v-for="opt in typeOptions"
                    :key="opt.value"
                    :value="opt.value"
                  >
                    {{ opt.label }}
                  </option>
                </select>
              </div>

              <!-- Title -->
              <div>
                <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  제목
                </label>
                <input
                  v-model="newPostTitle"
                  type="text"
                  placeholder="게시글 제목을 입력하세요"
                  class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
                />
              </div>

              <!-- Content -->
              <div>
                <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  내용
                </label>
                <textarea
                  v-model="newPostContent"
                  rows="5"
                  placeholder="게시글 내용을 입력하세요"
                  class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
                />
              </div>

              <!-- Tags -->
              <div>
                <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  태그
                </label>
                <input
                  v-model="newPostTags"
                  type="text"
                  placeholder="쉼표로 구분 (예: 공지, 이벤트)"
                  class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
                />
              </div>

              <!-- Pin toggle -->
              <div class="flex items-center justify-between">
                <label class="text-sm font-medium text-gray-700 dark:text-gray-300">
                  게시글 고정
                </label>
                <label class="relative inline-flex cursor-pointer items-center">
                  <input v-model="newPostPinned" type="checkbox" class="peer sr-only" />
                  <div
                    class="h-5 w-9 rounded-full bg-gray-200 after:absolute after:left-[2px] after:top-[2px] after:h-4 after:w-4 after:rounded-full after:border after:border-gray-300 after:bg-white after:transition-all after:content-[''] peer-checked:bg-primary-600 peer-checked:after:translate-x-full peer-checked:after:border-white peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-primary-300 dark:bg-gray-700 dark:peer-focus:ring-primary-800"
                  />
                </label>
              </div>
            </div>

            <!-- Modal actions -->
            <div class="mt-6 flex items-center justify-end gap-3">
              <button
                class="rounded-lg px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
                @click="closeNewPostModal"
              >
                취소
              </button>
              <button
                class="btn-primary text-sm"
                :disabled="!newPostTitle.trim() || !newPostContent.trim()"
                @click="handleCreatePost"
              >
                게시글 작성
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>
