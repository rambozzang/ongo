<template>
  <div class="max-w-6xl mx-auto space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-900">{{ t('audience.title') }}</h1>
    </div>

    <!-- 탭 -->
    <div class="border-b border-gray-200">
      <nav class="flex gap-6">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          :class="[
            'pb-3 text-sm font-medium border-b-2 transition-colors',
            activeTab === tab.key
              ? 'border-indigo-600 text-indigo-600'
              : 'border-transparent text-gray-500 hover:text-gray-700',
          ]"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- 팬 프로필 탭 -->
    <div v-if="activeTab === 'profiles'">
      <div class="mb-4 flex gap-3">
        <select v-model="sortBy" class="px-3 py-2 border rounded-lg text-sm" @change="loadProfiles">
          <option value="engagement_score">{{ t('audience.sort.engagementScore') }}</option>
          <option value="total_interactions">{{ t('audience.sort.totalInteractions') }}</option>
          <option value="last_seen_at">{{ t('audience.sort.lastSeenAt') }}</option>
        </select>
      </div>

      <div v-if="store.loading" class="text-center py-12 text-gray-400">
        {{ t('audience.loading') }}
      </div>

      <div v-else-if="store.profiles.length === 0" class="text-center py-12 text-gray-400">
        {{ t('audience.emptyProfiles') }}
      </div>

      <div v-else class="bg-white rounded-lg border overflow-hidden">
        <table class="w-full text-sm">
          <thead class="bg-gray-50 border-b">
            <tr>
              <th class="text-left px-4 py-3 font-medium text-gray-500">{{ t('audience.table.profile') }}</th>
              <th class="text-left px-4 py-3 font-medium text-gray-500">{{ t('audience.table.platform') }}</th>
              <th class="text-left px-4 py-3 font-medium text-gray-500">{{ t('audience.table.engagement') }}</th>
              <th class="text-left px-4 py-3 font-medium text-gray-500">{{ t('audience.table.tags') }}</th>
              <th class="text-right px-4 py-3 font-medium text-gray-500">{{ t('audience.table.interactions') }}</th>
              <th class="text-right px-4 py-3 font-medium text-gray-500">{{ t('audience.table.lastActivity') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr v-for="profile in store.profiles" :key="profile.id" class="hover:bg-gray-50">
              <td class="px-4 py-3">
                <div class="flex items-center gap-3">
                  <img
                    v-if="profile.avatarUrl"
                    :src="profile.avatarUrl"
                    :alt="profile.authorName"
                    class="w-8 h-8 rounded-full object-cover"
                  />
                  <div
                    v-else
                    class="w-8 h-8 rounded-full bg-indigo-100 text-indigo-600 flex items-center justify-center text-xs font-bold"
                  >
                    {{ profile.authorName.charAt(0) }}
                  </div>
                  <span class="font-medium text-gray-900">{{ profile.authorName }}</span>
                </div>
              </td>
              <td class="px-4 py-3 text-gray-600">{{ profile.platform }}</td>
              <td class="px-4 py-3">
                <div class="flex items-center gap-2">
                  <div class="w-16 h-2 bg-gray-200 rounded-full overflow-hidden">
                    <div
                      class="h-full rounded-full"
                      :class="scoreColor(profile.engagementScore)"
                      :style="{ width: `${Math.min(profile.engagementScore, 100)}%` }"
                    />
                  </div>
                  <span class="text-gray-700 text-xs">{{ profile.engagementScore }}</span>
                </div>
              </td>
              <td class="px-4 py-3">
                <div class="flex flex-wrap gap-1">
                  <span
                    v-for="tag in profile.tags"
                    :key="tag"
                    class="px-2 py-0.5 bg-gray-100 text-gray-600 rounded text-xs"
                  >
                    {{ tag }}
                  </span>
                </div>
              </td>
              <td class="px-4 py-3 text-right text-gray-700">{{ profile.totalInteractions.toLocaleString() }}</td>
              <td class="px-4 py-3 text-right text-gray-500 text-xs">{{ formatDate(profile.lastSeenAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="store.totalProfiles > 20" class="flex justify-center mt-4">
        <div class="flex gap-2">
          <button
            :disabled="currentPage === 0"
            class="px-3 py-1.5 text-sm border rounded-lg hover:bg-gray-50 disabled:opacity-50"
            @click="changePage(currentPage - 1)"
          >
            {{ t('audience.pagination.previous') }}
          </button>
          <span class="px-3 py-1.5 text-sm text-gray-600">
            {{ currentPage + 1 }} / {{ Math.ceil(store.totalProfiles / 20) }}
          </span>
          <button
            :disabled="(currentPage + 1) * 20 >= store.totalProfiles"
            class="px-3 py-1.5 text-sm border rounded-lg hover:bg-gray-50 disabled:opacity-50"
            @click="changePage(currentPage + 1)"
          >
            {{ t('audience.pagination.next') }}
          </button>
        </div>
      </div>
    </div>

    <!-- 세그먼트 탭 -->
    <div v-if="activeTab === 'segments'">
      <div class="mb-4 flex justify-end">
        <button
          class="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm hover:bg-indigo-700"
          @click="showSegmentModal = true"
        >
          {{ t('audience.segment.add') }}
        </button>
      </div>

      <div v-if="store.loading" class="text-center py-12 text-gray-400">
        {{ t('audience.loading') }}
      </div>

      <div v-else-if="store.segments.length === 0" class="text-center py-12 text-gray-400">
        {{ t('audience.segment.empty') }}
      </div>

      <div v-else class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div
          v-for="segment in store.segments"
          :key="segment.id"
          class="bg-white rounded-lg border p-5 hover:shadow-md transition-shadow"
        >
          <div class="flex items-start justify-between mb-2">
            <h3 class="font-semibold text-gray-900">{{ segment.name }}</h3>
            <button
              class="text-gray-400 hover:text-red-500 transition-colors"
              @click="handleDeleteSegment(segment.id)"
            >
              <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
          <p v-if="segment.description" class="text-sm text-gray-500 mb-3">{{ segment.description }}</p>
          <div class="flex items-center justify-between text-sm">
            <span class="text-gray-600">
              <span class="font-medium text-indigo-600">{{ segment.memberCount.toLocaleString() }}</span>{{ t('audience.members') }}
            </span>
            <span v-if="segment.autoUpdate" class="px-2 py-0.5 bg-green-100 text-green-700 rounded text-xs">
              {{ t('audience.autoUpdate') }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 세그먼트 추가 모달 -->
    <div
      v-if="showSegmentModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      @click.self="closeModal"
    >
      <div class="bg-white rounded-xl shadow-xl w-full max-w-md p-6">
        <h2 class="text-lg font-bold text-gray-900 mb-4">{{ t('audience.segment.modal.title') }}</h2>
        <form @submit.prevent="handleCreateSegment">
          <div class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('audience.segment.modal.name') }}</label>
              <input
                v-model="segmentForm.name"
                type="text"
                required
                class="w-full px-3 py-2 border rounded-lg text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                :placeholder="t('audience.segment.modal.namePlaceholder')"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('audience.segment.modal.description') }}</label>
              <textarea
                v-model="segmentForm.description"
                rows="2"
                class="w-full px-3 py-2 border rounded-lg text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                :placeholder="t('audience.segment.modal.descriptionPlaceholder')"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('audience.segment.modal.conditions') }}</label>
              <input
                v-model="segmentForm.conditions"
                type="text"
                class="w-full px-3 py-2 border rounded-lg text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                :placeholder="t('audience.segment.modal.conditionsPlaceholder')"
              />
            </div>
            <div class="flex items-center gap-2">
              <input
                id="autoUpdate"
                v-model="segmentForm.autoUpdate"
                type="checkbox"
                class="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
              />
              <label for="autoUpdate" class="text-sm text-gray-700">{{ t('audience.segment.modal.autoUpdate') }}</label>
            </div>
          </div>
          <div class="mt-6 flex justify-end gap-3">
            <button
              type="button"
              class="px-4 py-2 text-sm text-gray-700 border rounded-lg hover:bg-gray-50"
              @click="closeModal"
            >
              {{ t('audience.segment.modal.cancel') }}
            </button>
            <button
              type="submit"
              :disabled="!segmentForm.name || creating"
              class="px-4 py-2 text-sm bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-50"
            >
              {{ creating ? t('audience.segment.modal.creating') : t('audience.segment.modal.add') }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAudienceStore } from '@/stores/audience'

const { t } = useI18n({ useScope: 'global' })
const store = useAudienceStore()

const activeTab = ref('profiles')
const sortBy = ref('engagement_score')
const currentPage = ref(0)
const showSegmentModal = ref(false)
const creating = ref(false)

const segmentForm = reactive({
  name: '',
  description: '',
  conditions: '',
  autoUpdate: false,
})

const tabs = [
  { key: 'profiles', label: t('audience.tabs.profiles') },
  { key: 'segments', label: t('audience.tabs.segments') },
]

function scoreColor(score: number): string {
  if (score >= 80) return 'bg-green-500'
  if (score >= 50) return 'bg-yellow-500'
  return 'bg-gray-400'
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

async function loadProfiles() {
  currentPage.value = 0
  await store.loadProfiles(sortBy.value, 0, 20)
}

async function changePage(page: number) {
  currentPage.value = page
  await store.loadProfiles(sortBy.value, page, 20)
}

async function handleCreateSegment() {
  creating.value = true
  try {
    await store.createSegment({
      name: segmentForm.name,
      description: segmentForm.description || undefined,
      conditions: segmentForm.conditions || undefined,
      autoUpdate: segmentForm.autoUpdate,
    })
    closeModal()
  } catch (e) {
    console.error(t('audience.segment.createFailed'), e)
  } finally {
    creating.value = false
  }
}

async function handleDeleteSegment(id: number) {
  if (!confirm(t('audience.segment.confirmDelete'))) return
  try {
    await store.deleteSegment(id)
  } catch (e) {
    console.error(t('audience.segment.deleteFailed'), e)
  }
}

function closeModal() {
  showSegmentModal.value = false
  segmentForm.name = ''
  segmentForm.description = ''
  segmentForm.conditions = ''
  segmentForm.autoUpdate = false
}

onMounted(async () => {
  await Promise.all([store.loadProfiles(), store.loadSegments()])
})
</script>
