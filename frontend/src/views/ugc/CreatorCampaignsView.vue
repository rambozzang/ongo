<template>
  <div>
    <PageHeader :title="$t('ugc.myCampaigns')" :description="$t('ugc.myCampaignsDescription')" />

    <div v-if="loading" class="py-16 text-center text-sm text-gray-400">{{ $t('action.loading') }}</div>

    <div v-else-if="items.length === 0" class="card py-16 text-center text-sm text-gray-500 dark:text-gray-400">
      {{ $t('ugc.noMyApplications') }}
    </div>

    <div v-else class="space-y-3">
      <div v-for="item in items" :key="item.application.id" class="card flex items-center justify-between gap-4">
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <span class="truncate font-semibold text-gray-900 dark:text-gray-100">{{ item.campaignName }}</span>
            <span :class="['rounded-full px-2 py-0.5 text-xs font-medium', appStatusClass(item.application.status)]">
              {{ $t(`ugc.appStatus.${item.application.status}`) }}
            </span>
          </div>
          <div class="mt-1 flex flex-wrap gap-x-4 text-xs text-gray-400">
            <span>{{ $t('ugc.campaignStatus') }}: {{ $t(`ugc.status.${item.campaignStatus}`) }}</span>
            <span>{{ $t('ugc.period') }}: {{ formatPeriod(item.startAt, item.endAt) }}</span>
            <span>{{ $t('ugc.appliedAt') }}: {{ item.application.createdAt?.slice(0, 10) }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="!loading && totalPages > 1" class="mt-6 flex items-center justify-center gap-3">
      <button class="btn-secondary" :disabled="page === 0" @click="changePage(page - 1)">{{ $t('ugc.prev') }}</button>
      <span class="text-sm text-gray-500 dark:text-gray-400">{{ page + 1 }} / {{ totalPages }}</span>
      <button class="btn-secondary" :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">{{ $t('ugc.next') }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useNotificationStore } from '@/stores/notification'
import { ugcParticipationApi, type MyApplicationResponse, type ApplicationStatus } from '@/api/ugcParticipation'
import PageHeader from '@/components/common/PageHeader.vue'

const { t } = useI18n({ useScope: 'global' })
const notify = useNotificationStore()

const items = ref<MyApplicationResponse[]>([])
const totalElements = ref(0)
const page = ref(0)
const size = ref(20)
const loading = ref(true)

const totalPages = computed(() => Math.max(1, Math.ceil(totalElements.value / size.value)))

function appStatusClass(status: ApplicationStatus): string {
  switch (status) {
    case 'ACCEPTED':
      return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300'
    case 'REJECTED':
      return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300'
    case 'APPLIED':
      return 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
    default:
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
  }
}

function formatPeriod(startAt: string | null, endAt: string | null): string {
  if (!startAt || !endAt) return '-'
  return `${startAt.slice(0, 10)} ~ ${endAt.slice(0, 10)}`
}

async function fetchMine() {
  loading.value = true
  try {
    const res = await ugcParticipationApi.myApplications({ page: page.value, size: size.value })
    items.value = res.items
    totalElements.value = res.totalElements
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.loadFailed'))
  } finally {
    loading.value = false
  }
}

function changePage(next: number) {
  page.value = next
  fetchMine()
}

onMounted(fetchMine)
</script>
