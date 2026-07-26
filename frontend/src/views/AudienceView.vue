<template>
  <div class="relative">
    <PageHeader :title="$t('audience.title')" :description="$t('audience.description')" />

    <PageGuide :title="$t('audience.pageGuideTitle')" :items="($tm('audience.pageGuide') as string[])" />

    <!-- 탭 -->
    <OTabs v-model="activeTab" :tabs="tabs" class="mb-6" />

    <!-- 팬 프로필 탭 -->
    <div v-if="activeTab === 'profiles'">
      <div class="mb-4 flex gap-3">
        <select v-model="sortBy" class="input-field" @change="loadProfiles">
          <option value="engagement_score">{{ $t('audience.sort.engagementScore') }}</option>
          <option value="total_interactions">{{ $t('audience.sort.totalInteractions') }}</option>
          <option value="last_seen_at">{{ $t('audience.sort.lastSeenAt') }}</option>
        </select>
      </div>

      <div v-if="store.loading" class="text-center py-12 text-gray-400">
        {{ $t('audience.loading') }}
      </div>

      <div v-else-if="store.profiles.length === 0" class="text-center py-12 text-gray-400">
        {{ $t('audience.emptyProfiles') }}
      </div>

      <div v-else class="card overflow-hidden !p-0">
        <table class="w-full text-body">
          <thead class="bg-gray-50 dark:bg-gray-900">
            <tr>
              <th class="text-left text-body-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400 px-4 py-3">{{ $t('audience.table.profile') }}</th>
              <th class="text-left text-body-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400 px-4 py-3">{{ $t('audience.table.platform') }}</th>
              <th class="text-left text-body-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400 px-4 py-3">{{ $t('audience.table.engagement') }}</th>
              <th class="text-left text-body-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400 px-4 py-3">{{ $t('audience.table.tags') }}</th>
              <th class="text-right text-body-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400 px-4 py-3">{{ $t('audience.table.interactions') }}</th>
              <th class="text-right text-body-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400 px-4 py-3">{{ $t('audience.table.lastActivity') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-200 dark:divide-gray-700">
            <tr v-for="profile in store.profiles" :key="profile.id" class="hover:bg-gray-50 dark:hover:bg-gray-700/50">
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
                    class="w-8 h-8 rounded-full bg-primary-100 text-primary-600 flex items-center justify-center text-body-xs font-bold"
                  >
                    {{ profile.authorName.charAt(0) }}
                  </div>
                  <span class="font-medium text-gray-900 dark:text-gray-100">{{ profile.authorName }}</span>
                </div>
              </td>
              <td class="px-4 py-3 text-gray-600 dark:text-gray-400">{{ profile.platform }}</td>
              <td class="px-4 py-3">
                <div class="flex items-center gap-2">
                  <div class="w-16 h-2 bg-gray-200 dark:bg-gray-600 rounded-full overflow-hidden">
                    <div
                      class="h-full rounded-full"
                      :class="scoreColor(profile.engagementScore)"
                      :style="{ width: `${Math.min(profile.engagementScore, 100)}%` }"
                    />
                  </div>
                  <span class="text-gray-700 dark:text-gray-300 text-body-xs">{{ profile.engagementScore }}</span>
                </div>
              </td>
              <td class="px-4 py-3">
                <div class="flex flex-wrap gap-1">
                  <span
                    v-for="tag in profile.tags"
                    :key="tag"
                    class="px-2 py-0.5 bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 rounded text-body-xs"
                  >
                    {{ tag }}
                  </span>
                </div>
              </td>
              <td class="px-4 py-3 text-right text-gray-700 dark:text-gray-300">{{ profile.totalInteractions.toLocaleString() }}</td>
              <td class="px-4 py-3 text-right text-gray-500 dark:text-gray-400 text-body-xs">{{ formatDate(profile.lastSeenAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="store.totalProfiles > 20" class="flex justify-center mt-4">
        <div class="flex gap-2">
          <button
            :disabled="currentPage === 0"
            class="btn-secondary text-body"
            @click="changePage(currentPage - 1)"
          >
            {{ $t('audience.pagination.previous') }}
          </button>
          <span class="px-3 py-1.5 text-body text-gray-600 dark:text-gray-400">
            {{ currentPage + 1 }} / {{ Math.ceil(store.totalProfiles / 20) }}
          </span>
          <button
            :disabled="(currentPage + 1) * 20 >= store.totalProfiles"
            class="btn-secondary text-body"
            @click="changePage(currentPage + 1)"
          >
            {{ $t('audience.pagination.next') }}
          </button>
        </div>
      </div>
    </div>

    <!-- 세그먼트 탭 -->
    <div v-if="activeTab === 'segments'">
      <div class="mb-4 flex justify-end">
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="showSegmentModal = true"
        >
          {{ $t('audience.segment.add') }}
        </button>
      </div>

      <div v-if="store.loading" class="text-center py-12 text-gray-400">
        {{ $t('audience.loading') }}
      </div>

      <div v-else-if="store.segments.length === 0" class="text-center py-12 text-gray-400">
        {{ $t('audience.segment.empty') }}
      </div>

      <div v-else class="page-grid page-grid--cards">
        <div
          v-for="segment in store.segments"
          :key="segment.id"
          class="card"
        >
          <div class="flex items-start justify-between mb-2">
            <h3 class="font-semibold text-gray-900 dark:text-gray-100">{{ segment.name }}</h3>
            <button
              class="text-gray-400 hover:text-error-strong transition-colors"
              @click="handleDeleteSegment(segment.id)"
            >
              <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
          <p v-if="segment.description" class="text-body text-gray-500 dark:text-gray-400 mb-3">{{ segment.description }}</p>
          <div class="flex items-center justify-between text-body">
            <span class="text-gray-600 dark:text-gray-400">
              <span class="font-medium text-primary-600 dark:text-primary-400">{{ segment.memberCount.toLocaleString() }}</span>{{ $t('audience.members') }}
            </span>
            <span v-if="segment.autoUpdate" class="px-2 py-0.5 bg-success-subtle text-success-strong rounded text-body-xs">
              {{ $t('audience.autoUpdate') }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 세그먼트 추가 모달 -->
    <BaseModal v-model="showSegmentModal" :title="$t('audience.segment.modal.title')">
      <form @submit.prevent="handleCreateSegment">
        <div class="space-y-4">
          <div>
            <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('audience.segment.modal.name') }}</label>
            <input
              v-model="segmentForm.name"
              type="text"
              required
              class="input-field"
              :placeholder="$t('audience.segment.modal.namePlaceholder')"
            />
          </div>
          <div>
            <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('audience.segment.modal.description') }}</label>
            <textarea
              v-model="segmentForm.description"
              rows="2"
              class="input-field"
              :placeholder="$t('audience.segment.modal.descriptionPlaceholder')"
            />
          </div>
          <div>
            <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('audience.segment.modal.conditions') }}</label>
            <input
              v-model="segmentForm.conditions"
              type="text"
              class="input-field"
              :placeholder="$t('audience.segment.modal.conditionsPlaceholder')"
            />
          </div>
          <div class="flex items-center gap-2">
            <input
              id="autoUpdate"
              v-model="segmentForm.autoUpdate"
              type="checkbox"
              class="rounded border-gray-300 dark:border-gray-600 text-primary-600 focus:ring-primary-500"
            />
            <label for="autoUpdate" class="text-body text-gray-700 dark:text-gray-300">{{ $t('audience.segment.modal.autoUpdate') }}</label>
          </div>
        </div>
        <div class="mt-6 flex justify-end gap-3">
          <button
            type="button"
            class="btn-secondary"
            @click="closeModal"
          >
            {{ $t('audience.segment.modal.cancel') }}
          </button>
          <button
            type="submit"
            :disabled="!segmentForm.name || creating"
            class="btn-primary"
          >
            {{ creating ? $t('audience.segment.modal.creating') : $t('audience.segment.modal.add') }}
          </button>
        </div>
      </form>
    </BaseModal>

    <!-- 세그먼트 삭제 확인 -->
    <ConfirmModal
      v-model="showDeleteModal"
      :title="$t('audience.segment.deleteTitle')"
      :message="$t('audience.segment.confirmDelete')"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="confirmDeleteSegment"
      @cancel="deleteTargetId = null"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAudienceStore } from '@/stores/audience'
import OTabs from '@/components/ui/OTabs.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'

const { t } = useI18n({ useScope: 'global' })
const store = useAudienceStore()

const activeTab = ref('profiles')
const sortBy = ref('engagement_score')
const currentPage = ref(0)
const showSegmentModal = ref(false)
const creating = ref(false)
const showDeleteModal = ref(false)
const deleteTargetId = ref<number | null>(null)

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
  if (score >= 80) return 'bg-success'
  if (score >= 50) return 'bg-warning'
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

function handleDeleteSegment(id: number) {
  deleteTargetId.value = id
  showDeleteModal.value = true
}

async function confirmDeleteSegment() {
  const id = deleteTargetId.value
  deleteTargetId.value = null
  if (id === null) return
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
