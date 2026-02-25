<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ $t('liveDashboard.title') }}
          </h1>
          <!-- Connection status indicator -->
          <span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium"
            :class="isConnected
              ? 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400'
              : 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-400'"
          >
            <span
              class="inline-block h-2 w-2 rounded-full"
              :class="isConnected ? 'bg-green-500 animate-pulse' : 'bg-red-500'"
            />
            {{ isConnected ? $t('liveDashboard.connected') : $t('liveDashboard.disconnected') }}
          </span>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('liveDashboard.description') }}
        </p>
      </div>

      <div class="flex items-center gap-4">
        <!-- Last updated -->
        <span v-if="lastUpdated" class="text-xs text-gray-400 dark:text-gray-500">
          {{ $t('liveDashboard.lastUpdated', { time: lastUpdatedFormatted }) }}
        </span>

        <!-- Auto-refresh toggle -->
        <label class="inline-flex cursor-pointer items-center gap-2">
          <span class="text-sm text-gray-600 dark:text-gray-400">
            {{ $t('liveDashboard.autoRefresh') }}
          </span>
          <button
            type="button"
            role="switch"
            :aria-checked="autoRefreshEnabled"
            class="relative inline-flex h-6 w-11 flex-shrink-0 rounded-full border-2 border-transparent transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900"
            :class="autoRefreshEnabled ? 'bg-primary-600' : 'bg-gray-200 dark:bg-gray-700'"
            @click="toggleAutoRefresh"
          >
            <span
              class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200"
              :class="autoRefreshEnabled ? 'translate-x-5' : 'translate-x-0'"
            />
          </button>
        </label>

        <!-- Manual refresh button -->
        <button
          :disabled="loading"
          class="btn-secondary inline-flex items-center gap-1.5 text-sm disabled:opacity-50"
          @click="handleRefresh"
        >
          <ArrowPathIcon class="h-4 w-4" :class="{ 'animate-spin': loading }" />
          {{ $t('liveDashboard.refresh') }}
        </button>
      </div>
    </div>

    <!-- Page Guide -->
    <PageGuide
      :title="$t('liveDashboard.pageGuideTitle')"
      :items="($tm('liveDashboard.pageGuide') as string[])"
    />

    <!-- Loading skeleton -->
    <div v-if="loading && metrics.length === 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
      <div
        v-for="i in 6"
        :key="i"
        class="h-48 animate-pulse rounded-xl border border-gray-200 bg-gray-100 dark:border-gray-700 dark:bg-gray-800"
      />
    </div>

    <template v-else>
      <!-- Metrics Grid -->
      <div class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
        <LiveMetricCard
          v-for="metric in metrics"
          :key="metric.type"
          :metric="metric"
        />
      </div>

      <!-- Alerts Section -->
      <div class="mt-8">
        <div class="mb-4 flex items-center justify-between">
          <div class="flex items-center gap-2">
            <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('liveDashboard.alerts') }}
            </h2>
            <span
              v-if="unreadAlerts.length > 0"
              class="inline-flex h-5 min-w-[1.25rem] items-center justify-center rounded-full bg-red-500 px-1.5 text-xs font-medium text-white"
            >
              {{ unreadAlerts.length }}
            </span>
          </div>
        </div>

        <div v-if="alerts.length === 0" class="rounded-xl border border-gray-200 bg-white p-8 text-center dark:border-gray-700 dark:bg-gray-900">
          <BellSlashIcon class="mx-auto h-10 w-10 text-gray-300 dark:text-gray-600" />
          <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">
            {{ $t('liveDashboard.noAlerts') }}
          </p>
        </div>

        <div v-else class="space-y-2">
          <LiveAlertItem
            v-for="alert in alerts"
            :key="alert.id"
            :alert="alert"
            @mark-read="handleMarkRead"
          />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import { ArrowPathIcon, BellSlashIcon } from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import LiveMetricCard from '@/components/livedashboard/LiveMetricCard.vue'
import LiveAlertItem from '@/components/livedashboard/LiveAlertItem.vue'
import { useLiveDashboardStore } from '@/stores/liveDashboard'

const store = useLiveDashboardStore()
const { state, loading, metrics, alerts, unreadAlerts, isConnected } = storeToRefs(store)

const autoRefreshEnabled = ref(false)

const lastUpdated = computed(() => state.value?.lastUpdated ?? null)

const lastUpdatedFormatted = computed(() => {
  if (!lastUpdated.value) return ''
  const date = new Date(lastUpdated.value)
  return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' })
})

function toggleAutoRefresh() {
  autoRefreshEnabled.value = !autoRefreshEnabled.value
  if (autoRefreshEnabled.value) {
    store.startAutoRefresh(30000)
  } else {
    store.stopAutoRefresh()
  }
}

async function handleRefresh() {
  await store.fetchState()
}

async function handleMarkRead(alertId: number) {
  await store.markAlertRead(alertId)
}

onMounted(() => {
  store.fetchState()
})

onUnmounted(() => {
  store.stopAutoRefresh()
})
</script>
