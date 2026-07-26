<template>
  <div class="relative">
    <PageHeader :title="t('admin.title')" :description="t('admin.description')" />

    <PageGuide :title="t('admin.pageGuideTitle')" :items="(tm('admin.pageGuide') as string[])" />

    <!-- Search -->
    <div class="mb-6 flex items-center gap-4">
      <div class="relative flex-1 max-w-md">
        <MagnifyingGlassIcon class="absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400" />
        <input
          v-model="searchQuery"
          type="text"
          :placeholder="t('admin.searchPlaceholder')"
          class="input-field pl-10"
          @input="debouncedSearch"
        />
      </div>
    </div>

    <!-- Users Table -->
    <div class="overflow-hidden rounded-lg border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800">
      <!-- 스켈레톤일 때만 여백을 준다 (실제 테이블은 컨테이너에 붙어야 divide-y가 끝까지 이어짐) -->
      <AsyncState
        :loading="loading"
        skeleton="table"
        :skeleton-count="6"
        :class="{ 'p-4': loading }"
      >
        <table class="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
          <thead class="bg-gray-50 dark:bg-gray-900">
            <tr>
              <th class="px-4 py-3 text-left text-caption uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('admin.userName') }}</th>
              <th class="px-4 py-3 text-left text-caption uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('admin.email') }}</th>
              <th class="px-4 py-3 text-left text-caption uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('admin.plan') }}</th>
              <th class="px-4 py-3 text-left text-caption uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('admin.storage') }}</th>
              <th class="px-4 py-3 text-left text-caption uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('admin.joinDate') }}</th>
              <th class="px-4 py-3 text-left text-caption uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('admin.actions') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-200 dark:divide-gray-700">
            <tr
              v-for="user in users"
              :key="user.id"
              class="cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700/50"
              role="button"
              tabindex="0"
              @click="openUserDetail(user)"
              @keydown.enter="openUserDetail(user)"
            >
              <td class="whitespace-nowrap px-4 py-3 text-body font-medium text-gray-900 dark:text-gray-100">
                {{ user.name }}
                <span v-if="user.role === 'ADMIN'" class="ml-1 inline-flex items-center rounded bg-primary-100 px-1.5 py-0.5 text-caption text-primary-700 dark:bg-primary-900/30 dark:text-primary-400">Admin</span>
              </td>
              <td class="whitespace-nowrap px-4 py-3 text-body text-gray-500 dark:text-gray-400">{{ user.email }}</td>
              <td class="whitespace-nowrap px-4 py-3 text-body">
                <span class="inline-flex items-center rounded-full px-2 py-0.5 text-caption" :class="planBadgeClass(user.planType)">
                  {{ user.planType }}
                </span>
              </td>
              <td class="px-4 py-3 text-body">
                <div class="flex items-center gap-2">
                  <div class="h-2 w-24 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-600">
                    <div
                      class="h-full rounded-full transition-all"
                      :class="storageBarClass(user.storageUsedBytes, user.storageLimitBytes)"
                      :style="{ width: storagePercent(user.storageUsedBytes, user.storageLimitBytes) + '%' }"
                    />
                  </div>
                  <span class="text-body-xs text-gray-500 dark:text-gray-400">
                    {{ formatBytes(user.storageUsedBytes) }} / {{ formatBytes(user.storageLimitBytes) }}
                  </span>
                </div>
              </td>
              <td class="whitespace-nowrap px-4 py-3 text-body text-gray-500 dark:text-gray-400">
                {{ user.createdAt ? new Date(user.createdAt).toLocaleDateString() : '-' }}
              </td>
              <td class="whitespace-nowrap px-4 py-3 text-body">
                <button
                  class="rounded px-2 py-1 text-body text-primary-600 hover:bg-primary-50 dark:text-primary-400 dark:hover:bg-primary-900/30"
                  @click.stop="openQuotaModal(user)"
                >
                  {{ t('admin.adjustQuota') }}
                </button>
              </td>
            </tr>
            <tr v-if="users.length === 0">
              <td colspan="6" class="px-4 py-8 text-center text-body text-gray-500 dark:text-gray-400">
                {{ t('empty.noResults') }}
              </td>
            </tr>
          </tbody>
        </table>
      </AsyncState>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="flex items-center justify-between border-t border-gray-200 px-4 py-3 dark:border-gray-700">
        <p class="text-body text-gray-500 dark:text-gray-400">
          {{ t('admin.totalUsers', { count: totalElements }) }}
        </p>
        <div class="flex gap-1">
          <button
            v-for="p in visiblePages"
            :key="p"
            class="rounded px-3 py-1 text-body"
            :class="p === currentPage ? 'bg-primary-600 text-white' : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700'"
            @click="goToPage(p)"
          >
            {{ p + 1 }}
          </button>
        </div>
      </div>
    </div>

    <!-- User Detail Drawer -->
    <Teleport to="body">
      <Transition name="drawer">
        <div v-if="showDetailDrawer" class="fixed inset-0 z-50 flex justify-end" role="dialog" aria-modal="true" :aria-label="t('admin.userDetail')" @click.self="closeDetailDrawer">
          <div class="fixed inset-0 bg-black/40" @click="closeDetailDrawer" />
          <div class="relative z-10 flex h-full w-full max-w-2xl flex-col overflow-y-auto bg-white shadow-xl dark:bg-gray-800">
            <!-- Drawer Header -->
            <div class="sticky top-0 z-10 flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4 dark:border-gray-700 dark:bg-gray-800">
              <div>
                <h2 class="text-title font-semibold text-gray-900 dark:text-gray-100">{{ t('admin.userDetail') }}</h2>
                <p class="text-body text-gray-500 dark:text-gray-400">{{ detailUser?.name }} ({{ detailUser?.email }})</p>
              </div>
              <button class="rounded-lg p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300" @click="closeDetailDrawer">
                <XMarkIcon class="h-5 w-5" />
              </button>
            </div>

            <AsyncState
              :loading="detailLoading"
              skeleton="none"
              full-page
              class="flex flex-1 flex-col justify-center"
            >
              <div class="flex-1 space-y-6 p-6">
                <!-- User Info Card -->
                <div class="rounded-lg border border-gray-200 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900/50">
                  <div class="grid grid-cols-2 gap-4 text-body">
                    <div>
                      <span class="text-gray-500 dark:text-gray-400">{{ t('admin.role') }}</span>
                      <div class="mt-1 flex items-center gap-2">
                        <span class="font-medium text-gray-900 dark:text-gray-100">{{ detailData?.role }}</span>
                        <span v-if="detailData?.role === 'ADMIN'" class="rounded bg-primary-100 px-1.5 py-0.5 text-caption text-primary-700 dark:bg-primary-900/30 dark:text-primary-400">Admin</span>
                      </div>
                    </div>
                    <div>
                      <span class="text-gray-500 dark:text-gray-400">{{ t('admin.plan') }}</span>
                      <div class="mt-1">
                        <span class="inline-flex items-center rounded-full px-2 py-0.5 text-caption" :class="planBadgeClass(detailData?.planType ?? '')">
                          {{ detailData?.planType }}
                        </span>
                      </div>
                    </div>
                    <div>
                      <span class="text-gray-500 dark:text-gray-400">{{ t('admin.videoCount') }}</span>
                      <p class="mt-1 font-medium text-gray-900 dark:text-gray-100">{{ detailData?.videoCount ?? 0 }}</p>
                    </div>
                    <div>
                      <span class="text-gray-500 dark:text-gray-400">{{ t('admin.storage') }}</span>
                      <p class="mt-1 font-medium text-gray-900 dark:text-gray-100">
                        {{ formatBytes(detailData?.storageUsedBytes ?? 0) }} / {{ formatBytes(detailData?.storageLimitBytes ?? 0) }}
                      </p>
                    </div>
                    <div>
                      <span class="text-gray-500 dark:text-gray-400">{{ t('admin.joinDate') }}</span>
                      <p class="mt-1 font-medium text-gray-900 dark:text-gray-100">{{ detailData?.createdAt ? new Date(detailData.createdAt).toLocaleDateString() : '-' }}</p>
                    </div>
                    <div v-if="detailData?.storageQuotaOverride">
                      <span class="text-gray-500 dark:text-gray-400">{{ t('admin.quotaOverride') }}</span>
                      <p class="mt-1 font-medium text-warning-strong">{{ formatBytes(detailData.storageQuotaOverride) }}</p>
                    </div>
                  </div>
                </div>

                <!-- Action Buttons -->
                <div class="flex flex-wrap gap-2">
                  <button
                    v-if="detailData && detailData.role !== 'ADMIN'"
                    class="btn-secondary"
                    :disabled="actionLoading"
                    @click="toggleRole"
                  >
                    {{ detailData.role === 'USER' ? t('admin.promoteAdmin') : t('admin.demoteUser') }}
                  </button>
                  <button
                    v-if="detailData && detailData.role !== 'ADMIN'"
                    class="rounded-lg px-3 py-1.5 text-body font-medium"
                    :class="subscriptionDetail?.status === 'SUSPENDED'
                      ? 'border border-success text-success-strong hover:bg-success-subtle'
                      : 'border border-error text-error-strong hover:bg-error-subtle'"
                    :disabled="actionLoading"
                    @click="toggleActivation"
                  >
                    {{ subscriptionDetail?.status === 'SUSPENDED' ? t('admin.activate') : t('admin.deactivate') }}
                  </button>
                  <button
                    class="rounded-lg border border-gray-300 px-3 py-1.5 text-body font-medium text-primary-600 hover:bg-primary-50 dark:border-gray-600 dark:text-primary-400 dark:hover:bg-primary-900/30"
                    @click="openQuotaModalFromDetail"
                  >
                    {{ t('admin.adjustQuota') }}
                  </button>
                </div>

                <!-- Tabs -->
                <OTabs v-model="activeTab" :tabs="detailTabs" />

                <!-- Videos Tab -->
                <AsyncState
                  v-if="activeTab === 'videos'"
                  :loading="videosLoading"
                  :empty="userVideos.length === 0"
                  skeleton="list"
                  :skeleton-count="3"
                  :empty-icon="FilmIcon"
                  :empty-title="t('admin.noVideos')"
                  empty-variant="compact"
                >
                  <div class="space-y-3">
                    <div
                      v-for="video in userVideos"
                      :key="video.id"
                      class="rounded-lg border border-gray-200 p-3 dark:border-gray-700"
                    >
                      <div class="flex items-start justify-between">
                        <div>
                          <p class="font-medium text-gray-900 dark:text-gray-100">{{ video.title }}</p>
                          <div class="mt-1 flex items-center gap-3 text-body-xs text-gray-500 dark:text-gray-400">
                            <span :class="statusBadgeClass(video.status)">{{ video.status }}</span>
                            <span>{{ video.mediaType }}</span>
                            <span v-if="video.fileSizeBytes">{{ formatBytes(video.fileSizeBytes) }}</span>
                            <span>{{ video.createdAt ? new Date(video.createdAt).toLocaleDateString() : '' }}</span>
                          </div>
                        </div>
                      </div>
                      <!-- Platform uploads -->
                      <div v-if="video.platforms.length > 0" class="mt-2 flex flex-wrap gap-2">
                        <div
                          v-for="(upload, idx) in video.platforms"
                          :key="idx"
                          class="flex items-center gap-1 rounded-full px-2 py-0.5 text-body-xs"
                          :class="platformUploadClass(upload.status)"
                        >
                          <span class="font-medium">{{ upload.platform }}</span>
                          <span>{{ upload.status }}</span>
                        </div>
                      </div>
                    </div>

                    <!-- Video Pagination -->
                    <div v-if="videosTotalPages > 1" class="flex items-center justify-between pt-2">
                      <span class="text-body-xs text-gray-500 dark:text-gray-400">{{ t('admin.totalVideos', { count: videosTotalElements }) }}</span>
                      <div class="flex gap-1">
                        <button
                          v-for="p in videosVisiblePages"
                          :key="p"
                          class="rounded px-2 py-0.5 text-body-xs"
                          :class="p === videosPage ? 'bg-primary-600 text-white' : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700'"
                          @click="loadUserVideos(p)"
                        >
                          {{ p + 1 }}
                        </button>
                      </div>
                    </div>
                  </div>
                </AsyncState>

                <!-- Channels Tab -->
                <AsyncState
                  v-if="activeTab === 'channels'"
                  :loading="channelsLoading"
                  :empty="userChannels.length === 0"
                  skeleton="list"
                  :skeleton-count="3"
                  :empty-icon="LinkIcon"
                  :empty-title="t('admin.noChannels')"
                  empty-variant="compact"
                >
                  <div class="space-y-3">
                    <div
                      v-for="channel in userChannels"
                      :key="channel.id"
                      class="flex items-center justify-between rounded-lg border border-gray-200 p-3 dark:border-gray-700"
                    >
                      <div>
                        <div class="flex items-center gap-2">
                          <span class="rounded bg-gray-100 px-1.5 py-0.5 text-caption text-gray-700 dark:bg-gray-700 dark:text-gray-300">{{ channel.platform }}</span>
                          <p class="font-medium text-gray-900 dark:text-gray-100">{{ channel.channelName }}</p>
                        </div>
                        <div class="mt-1 flex items-center gap-3 text-body-xs text-gray-500 dark:text-gray-400">
                          <span>{{ t('admin.subscribers') }}: {{ channel.subscriberCount.toLocaleString() }}</span>
                          <span :class="channel.status === 'CONNECTED' ? 'text-success-strong' : 'text-warning-strong'">
                            {{ channel.status }}
                          </span>
                          <span v-if="channel.connectedAt">{{ t('admin.connected') }}: {{ new Date(channel.connectedAt).toLocaleDateString() }}</span>
                        </div>
                      </div>
                      <a
                        v-if="channel.channelUrl"
                        :href="channel.channelUrl"
                        target="_blank"
                        class="text-body text-primary-600 hover:underline dark:text-primary-400"
                      >
                        {{ t('admin.openChannel') }}
                      </a>
                    </div>
                  </div>
                </AsyncState>

                <!-- Subscription Tab -->
                <AsyncState
                  v-if="activeTab === 'subscription'"
                  :loading="subscriptionLoading"
                  :empty="!subscriptionDetail"
                  skeleton="list"
                  :skeleton-count="3"
                  :empty-icon="CreditCardIcon"
                  :empty-title="t('admin.noSubscription')"
                  empty-variant="compact"
                >
                  <div v-if="subscriptionDetail" class="rounded-lg border border-gray-200 p-4 dark:border-gray-700">
                    <div class="grid grid-cols-2 gap-4 text-body">
                      <div>
                        <span class="text-gray-500 dark:text-gray-400">{{ t('admin.subPlan') }}</span>
                        <p class="mt-1 font-medium text-gray-900 dark:text-gray-100">
                          <span class="inline-flex items-center rounded-full px-2 py-0.5 text-caption" :class="planBadgeClass(subscriptionDetail.planType)">
                            {{ subscriptionDetail.planType }}
                          </span>
                        </p>
                      </div>
                      <div>
                        <span class="text-gray-500 dark:text-gray-400">{{ t('admin.subStatus') }}</span>
                        <p class="mt-1">
                          <span class="inline-flex items-center rounded-full px-2 py-0.5 text-caption" :class="subStatusClass(subscriptionDetail.status)">
                            {{ subscriptionDetail.status }}
                          </span>
                        </p>
                      </div>
                      <div>
                        <span class="text-gray-500 dark:text-gray-400">{{ t('admin.subPrice') }}</span>
                        <p class="mt-1 font-medium text-gray-900 dark:text-gray-100">{{ subscriptionDetail.price.toLocaleString() }}원 / {{ subscriptionDetail.billingCycle }}</p>
                      </div>
                      <div>
                        <span class="text-gray-500 dark:text-gray-400">{{ t('admin.subPeriod') }}</span>
                        <p class="mt-1 font-medium text-gray-900 dark:text-gray-100">
                          {{ subscriptionDetail.currentPeriodStart ? new Date(subscriptionDetail.currentPeriodStart).toLocaleDateString() : '-' }}
                          ~
                          {{ subscriptionDetail.currentPeriodEnd ? new Date(subscriptionDetail.currentPeriodEnd).toLocaleDateString() : '-' }}
                        </p>
                      </div>
                      <div v-if="subscriptionDetail.nextBillingDate">
                        <span class="text-gray-500 dark:text-gray-400">{{ t('admin.subNextBilling') }}</span>
                        <p class="mt-1 font-medium text-gray-900 dark:text-gray-100">{{ new Date(subscriptionDetail.nextBillingDate).toLocaleDateString() }}</p>
                      </div>
                      <div v-if="subscriptionDetail.pendingPlanType">
                        <span class="text-gray-500 dark:text-gray-400">{{ t('admin.subPendingPlan') }}</span>
                        <p class="mt-1 font-medium text-warning-strong">{{ subscriptionDetail.pendingPlanType }}</p>
                      </div>
                      <div v-if="subscriptionDetail.cancelledAt">
                        <span class="text-gray-500 dark:text-gray-400">{{ t('admin.subCancelledAt') }}</span>
                        <p class="mt-1 font-medium text-error-strong">{{ new Date(subscriptionDetail.cancelledAt).toLocaleDateString() }}</p>
                      </div>
                      <div v-if="subscriptionDetail.storageQuotaOverride">
                        <span class="text-gray-500 dark:text-gray-400">{{ t('admin.quotaOverride') }}</span>
                        <p class="mt-1 font-medium text-warning-strong">{{ formatBytes(subscriptionDetail.storageQuotaOverride) }}</p>
                      </div>
                    </div>
                  </div>
                </AsyncState>
              </div>
            </AsyncState>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- Storage Quota Modal -->
    <BaseModal v-model="showQuotaModal" :title="t('admin.quotaModalTitle')">
      <p class="text-body text-gray-500 dark:text-gray-400">
        {{ selectedUser?.name }} ({{ selectedUser?.email }})
      </p>

      <div class="mt-4 space-y-4">
        <!-- Current usage -->
        <div>
          <label class="text-body font-medium text-gray-700 dark:text-gray-300">{{ t('admin.currentUsage') }}</label>
          <p class="text-body text-gray-500 dark:text-gray-400">
            {{ formatBytes(selectedUser?.storageUsedBytes ?? 0) }} / {{ formatBytes(selectedUser?.storageLimitBytes ?? 0) }}
          </p>
        </div>

        <!-- Quota input -->
        <div>
          <label class="text-body font-medium text-gray-700 dark:text-gray-300">{{ t('admin.newQuotaGB') }}</label>
          <div class="mt-1 flex items-center gap-2">
            <input
              v-model.number="quotaInputGB"
              type="number"
              min="0"
              step="1"
              class="input"
            />
            <span class="text-body text-gray-500 dark:text-gray-400">GB</span>
          </div>
        </div>

        <!-- Reset to plan default -->
        <button
          class="text-body text-primary-600 underline hover:text-primary-700 dark:text-primary-400"
          @click="resetToDefault"
        >
          {{ t('admin.resetToDefault') }}
        </button>
      </div>

      <template #footer>
        <button class="btn-secondary" @click="closeQuotaModal">{{ t('action.cancel') }}</button>
        <button class="btn-primary" :disabled="saving" @click="saveQuota">
          {{ saving ? t('action.loading') : t('action.save') }}
        </button>
      </template>
    </BaseModal>

    <!-- Confirm Dialog -->
    <BaseModal v-model="showConfirm" :title="confirmTitle" max-width="sm">
      <p class="text-body text-gray-600 dark:text-gray-400">{{ confirmMessage }}</p>
      <template #footer>
        <button class="btn-secondary" @click="cancelConfirm">{{ t('action.cancel') }}</button>
        <button
          :class="confirmDanger ? 'btn-danger' : 'btn-primary'"
          :disabled="actionLoading"
          @click="executeConfirm"
        >
          {{ actionLoading ? t('action.loading') : t('action.confirm') }}
        </button>
      </template>
    </BaseModal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import {
  MagnifyingGlassIcon,
  XMarkIcon,
  FilmIcon,
  LinkIcon,
  CreditCardIcon,
} from '@heroicons/vue/24/outline'
import OTabs from '@/components/ui/OTabs.vue'
import AsyncState from '@/components/common/AsyncState.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { useLocale } from '@/composables/useLocale'
import { adminApi } from '@/api/admin'
import type {
  AdminUserListItem,
  AdminUserDetail,
  AdminVideoItem,
  AdminChannelItem,
  AdminSubscriptionDetail,
} from '@/types/admin'

const { t, tm } = useLocale()

// --- User List ---
const users = ref<AdminUserListItem[]>([])
const loading = ref(false)
const searchQuery = ref('')
const currentPage = ref(0)
const totalElements = ref(0)
const totalPages = ref(0)
const pageSize = 20

let searchTimeout: ReturnType<typeof setTimeout> | null = null

function debouncedSearch() {
  if (searchTimeout) clearTimeout(searchTimeout)
  searchTimeout = setTimeout(() => {
    currentPage.value = 0
    fetchUsers()
  }, 300)
}

async function fetchUsers() {
  loading.value = true
  try {
    const result = await adminApi.getUsers(
      searchQuery.value || undefined,
      currentPage.value,
      pageSize,
    )
    users.value = result.content
    totalElements.value = result.totalElements
    totalPages.value = result.totalPages
  } catch {
    // error handled silently
  } finally {
    loading.value = false
  }
}

function goToPage(page: number) {
  currentPage.value = page
  fetchUsers()
}

const visiblePages = computed(() => {
  const pages: number[] = []
  const start = Math.max(0, currentPage.value - 2)
  const end = Math.min(totalPages.value - 1, currentPage.value + 2)
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
})

// --- Quota Modal ---
const showQuotaModal = ref(false)
const selectedUser = ref<AdminUserListItem | null>(null)
const quotaInputGB = ref<number | null>(null)
const useDefault = ref(false)
const saving = ref(false)

function openQuotaModal(user: AdminUserListItem) {
  selectedUser.value = user
  quotaInputGB.value = Math.round(user.storageLimitBytes / (1024 * 1024 * 1024))
  useDefault.value = false
  showQuotaModal.value = true
}

function openQuotaModalFromDetail() {
  if (!detailUser.value) return
  // Create an AdminUserListItem from detail data for the modal
  const user: AdminUserListItem = {
    id: detailData.value!.id,
    name: detailData.value!.name,
    email: detailData.value!.email,
    role: detailData.value!.role,
    planType: detailData.value!.planType,
    storageUsedBytes: detailData.value!.storageUsedBytes,
    storageLimitBytes: detailData.value!.storageLimitBytes,
    createdAt: detailData.value!.createdAt,
  }
  openQuotaModal(user)
}

function closeQuotaModal() {
  showQuotaModal.value = false
  selectedUser.value = null
}

function resetToDefault() {
  useDefault.value = true
  quotaInputGB.value = null
}

async function saveQuota() {
  if (!selectedUser.value) return
  saving.value = true
  try {
    const limitBytes = useDefault.value || quotaInputGB.value === null
      ? null
      : quotaInputGB.value * 1024 * 1024 * 1024
    await adminApi.updateStorageQuota(selectedUser.value.id, { limitBytes })
    closeQuotaModal()
    await fetchUsers()
    // Refresh detail if open
    if (showDetailDrawer.value && detailUser.value) {
      await loadDetailData(detailUser.value.id)
    }
  } catch {
    // error handled silently
  } finally {
    saving.value = false
  }
}

// --- User Detail Drawer ---
const showDetailDrawer = ref(false)
const detailLoading = ref(false)
const detailUser = ref<AdminUserListItem | null>(null)
const detailData = ref<AdminUserDetail | null>(null)
const activeTab = ref<'videos' | 'channels' | 'subscription'>('videos')

// Videos
const userVideos = ref<AdminVideoItem[]>([])
const videosLoading = ref(false)
const videosPage = ref(0)
const videosTotalElements = ref(0)
const videosTotalPages = ref(0)

// Channels
const userChannels = ref<AdminChannelItem[]>([])
const channelsLoading = ref(false)

// Subscription
const subscriptionDetail = ref<AdminSubscriptionDetail | null>(null)
const subscriptionLoading = ref(false)

// Actions
const actionLoading = ref(false)

const detailTabs = computed(() => [
  { key: 'videos' as const, label: t('admin.tabVideos') },
  { key: 'channels' as const, label: t('admin.tabChannels') },
  { key: 'subscription' as const, label: t('admin.tabSubscription') },
])

async function openUserDetail(user: AdminUserListItem) {
  detailUser.value = user
  showDetailDrawer.value = true
  activeTab.value = 'videos'
  await loadDetailData(user.id)
}

async function loadDetailData(userId: number) {
  detailLoading.value = true
  try {
    const [detail] = await Promise.all([
      adminApi.getUserDetail(userId),
      loadUserVideos(0, userId),
      loadUserChannels(userId),
      loadUserSubscription(userId),
    ])
    detailData.value = detail
  } catch {
    // error handled silently
  } finally {
    detailLoading.value = false
  }
}

async function loadUserVideos(page = 0, userId?: number) {
  const uid = userId ?? detailUser.value?.id
  if (!uid) return
  videosLoading.value = true
  videosPage.value = page
  try {
    const result = await adminApi.getUserVideos(uid, page, 10)
    userVideos.value = result.content
    videosTotalElements.value = result.totalElements
    videosTotalPages.value = result.totalPages
  } catch {
    userVideos.value = []
  } finally {
    videosLoading.value = false
  }
}

async function loadUserChannels(userId?: number) {
  const uid = userId ?? detailUser.value?.id
  if (!uid) return
  channelsLoading.value = true
  try {
    userChannels.value = await adminApi.getUserChannels(uid)
  } catch {
    userChannels.value = []
  } finally {
    channelsLoading.value = false
  }
}

async function loadUserSubscription(userId?: number) {
  const uid = userId ?? detailUser.value?.id
  if (!uid) return
  subscriptionLoading.value = true
  try {
    subscriptionDetail.value = await adminApi.getUserSubscription(uid)
  } catch {
    subscriptionDetail.value = null
  } finally {
    subscriptionLoading.value = false
  }
}

const videosVisiblePages = computed(() => {
  const pages: number[] = []
  const start = Math.max(0, videosPage.value - 2)
  const end = Math.min(videosTotalPages.value - 1, videosPage.value + 2)
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
})

function closeDetailDrawer() {
  showDetailDrawer.value = false
  detailUser.value = null
  detailData.value = null
  userVideos.value = []
  userChannels.value = []
  subscriptionDetail.value = null
}

// --- Confirm Dialog ---
const showConfirm = ref(false)
const confirmTitle = ref('')
const confirmMessage = ref('')
const confirmDanger = ref(false)
let confirmAction: (() => Promise<void>) | null = null

function cancelConfirm() {
  showConfirm.value = false
  confirmAction = null
}

async function executeConfirm() {
  if (confirmAction) {
    await confirmAction()
  }
  showConfirm.value = false
  confirmAction = null
}

function toggleRole() {
  if (!detailData.value) return
  const newRole = detailData.value.role === 'USER' ? 'ADMIN' : 'USER'
  confirmTitle.value = t('admin.confirmRoleChange')
  confirmMessage.value = t('admin.confirmRoleChangeMsg', { name: detailData.value.name, role: newRole })
  confirmDanger.value = newRole === 'ADMIN'
  confirmAction = async () => {
    actionLoading.value = true
    try {
      await adminApi.updateUserRole(detailData.value!.id, { role: newRole })
      await loadDetailData(detailData.value!.id)
      await fetchUsers()
    } catch {
      // error handled silently
    } finally {
      actionLoading.value = false
    }
  }
  showConfirm.value = true
}

function toggleActivation() {
  if (!detailData.value) return
  const isSuspended = subscriptionDetail.value?.status === 'SUSPENDED'

  confirmTitle.value = isSuspended ? t('admin.confirmActivate') : t('admin.confirmDeactivate')
  confirmMessage.value = isSuspended
    ? t('admin.confirmActivateMsg', { name: detailData.value.name })
    : t('admin.confirmDeactivateMsg', { name: detailData.value.name })
  confirmDanger.value = !isSuspended
  confirmAction = async () => {
    actionLoading.value = true
    try {
      if (isSuspended) {
        await adminApi.activateUser(detailData.value!.id)
      } else {
        await adminApi.deactivateUser(detailData.value!.id)
      }
      await loadDetailData(detailData.value!.id)
      await fetchUsers()
    } catch {
      // error handled silently
    } finally {
      actionLoading.value = false
    }
  }
  showConfirm.value = true
}

// --- Helpers ---
function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const gb = bytes / (1024 * 1024 * 1024)
  if (gb >= 1) return `${gb.toFixed(1)} GB`
  const mb = bytes / (1024 * 1024)
  if (mb >= 1) return `${mb.toFixed(0)} MB`
  const kb = bytes / 1024
  return `${kb.toFixed(0)} KB`
}

function storagePercent(used: number, limit: number): number {
  if (limit === 0) return 0
  return Math.min(100, (used / limit) * 100)
}

function storageBarClass(used: number, limit: number): string {
  const pct = storagePercent(used, limit)
  if (pct >= 90) return 'bg-error'
  if (pct >= 70) return 'bg-warning'
  return 'bg-primary-500'
}

function planBadgeClass(planType: string): string {
  switch (planType) {
    case 'BUSINESS': return 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
    case 'PRO': return 'bg-info-subtle text-info-strong'
    case 'STARTER': return 'bg-success-subtle text-success-strong'
    default: return 'bg-muted-subtle text-muted-strong'
  }
}

function statusBadgeClass(status: string): string {
  switch (status) {
    case 'PUBLISHED': return 'rounded-full bg-success-subtle px-2 py-0.5 text-success-strong'
    case 'FAILED':
    case 'REJECTED': return 'rounded-full bg-error-subtle px-2 py-0.5 text-error-strong'
    case 'PROCESSING':
    case 'UPLOADING': return 'rounded-full bg-warning-subtle px-2 py-0.5 text-warning-strong'
    default: return 'rounded-full bg-muted-subtle px-2 py-0.5 text-muted-strong'
  }
}

function platformUploadClass(status: string): string {
  switch (status) {
    case 'PUBLISHED': return 'bg-success-subtle text-success-strong'
    case 'FAILED':
    case 'REJECTED': return 'bg-error-subtle text-error-strong'
    case 'PROCESSING':
    case 'UPLOADING':
    case 'REVIEW': return 'bg-warning-subtle text-warning-strong'
    default: return 'bg-muted-subtle text-muted-strong'
  }
}

function subStatusClass(status: string): string {
  switch (status) {
    case 'ACTIVE': return 'bg-success-subtle text-success-strong'
    case 'SUSPENDED': return 'bg-error-subtle text-error-strong'
    case 'CANCELLED': return 'bg-muted-subtle text-muted-strong'
    case 'PAST_DUE': return 'bg-warning-subtle text-warning-strong'
    case 'FREE': return 'bg-info-subtle text-info-strong'
    default: return 'bg-muted-subtle text-muted-strong'
  }
}

onMounted(() => {
  fetchUsers()
})

onUnmounted(() => {
  if (searchTimeout) clearTimeout(searchTimeout)
})
</script>

<style scoped>
.drawer-enter-active,
.drawer-leave-active {
  transition: opacity 0.2s ease;
}
.drawer-enter-active > div:last-child,
.drawer-leave-active > div:last-child {
  transition: transform 0.3s ease;
}
.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}
.drawer-enter-from > div:last-child,
.drawer-leave-to > div:last-child {
  transform: translateX(100%);
}
</style>
