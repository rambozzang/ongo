<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('fanPoll.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('fanPoll.description') }}
        </p>
      </div>
      <button
        class="btn-primary inline-flex items-center gap-2"
        @click="showCreateForm = !showCreateForm"
      >
        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4" />
        </svg>
        {{ $t('fanPoll.createPoll') }}
      </button>
    </div>

    <PageGuide
      :title="$t('fanPoll.pageGuideTitle')"
      :items="($tm('fanPoll.pageGuide') as string[])"
    />

    <!-- Loading -->
    <div v-if="store.loading" class="flex items-center justify-center py-20">
      <LoadingSpinner size="lg" />
    </div>

    <template v-else>
      <!-- Summary Stats -->
      <div v-if="store.summary" class="mb-6 grid grid-cols-1 gap-4 tablet:grid-cols-3 desktop:grid-cols-5">
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('fanPoll.totalPolls') }}
          </p>
          <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary.totalPolls }}
          </p>
        </div>
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('fanPoll.activePolls') }}
          </p>
          <p class="mt-1 text-xl font-bold text-green-600 dark:text-green-400">
            {{ store.summary.activePolls }}
          </p>
        </div>
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('fanPoll.closedPolls') }}
          </p>
          <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary.closedPolls }}
          </p>
        </div>
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('fanPoll.totalVotes') }}
          </p>
          <p class="mt-1 text-xl font-bold text-primary-600 dark:text-primary-400">
            {{ store.summary.totalVotes.toLocaleString('ko-KR') }}
          </p>
        </div>
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('fanPoll.avgVotesPerPoll') }}
          </p>
          <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary.avgVotesPerPoll.toLocaleString('ko-KR') }}
          </p>
        </div>
      </div>

      <!-- Create Poll Form -->
      <Transition
        enter-active-class="transition duration-200 ease-out"
        enter-from-class="-translate-y-2 opacity-0"
        enter-to-class="translate-y-0 opacity-100"
        leave-active-class="transition duration-150 ease-in"
        leave-from-class="translate-y-0 opacity-100"
        leave-to-class="-translate-y-2 opacity-0"
      >
        <div
          v-if="showCreateForm"
          class="mb-6 rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <h2 class="mb-4 text-base font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('fanPoll.newPoll') }}
          </h2>
          <form class="space-y-4" @submit.prevent="handleCreatePoll">
            <div class="grid grid-cols-1 gap-4 tablet:grid-cols-2">
              <div>
                <label class="mb-1 block text-xs font-medium text-gray-700 dark:text-gray-300">
                  {{ $t('fanPoll.pollTitle') }}
                </label>
                <input
                  v-model="newPoll.title"
                  type="text"
                  required
                  :placeholder="$t('fanPoll.pollTitlePlaceholder')"
                  class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
                />
              </div>
              <div>
                <label class="mb-1 block text-xs font-medium text-gray-700 dark:text-gray-300">
                  {{ $t('fanPoll.pollType') }}
                </label>
                <select
                  v-model="newPoll.type"
                  class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-700 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300"
                >
                  <option value="SINGLE_CHOICE">{{ $t('fanPoll.typeSingle') }}</option>
                  <option value="MULTIPLE_CHOICE">{{ $t('fanPoll.typeMultiple') }}</option>
                  <option value="RANKING">{{ $t('fanPoll.typeRanking') }}</option>
                </select>
              </div>
            </div>

            <div>
              <label class="mb-1 block text-xs font-medium text-gray-700 dark:text-gray-300">
                {{ $t('fanPoll.pollDescription') }}
              </label>
              <textarea
                v-model="newPoll.description"
                required
                rows="2"
                :placeholder="$t('fanPoll.pollDescriptionPlaceholder')"
                class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
              />
            </div>

            <!-- Options -->
            <div>
              <label class="mb-1 block text-xs font-medium text-gray-700 dark:text-gray-300">
                {{ $t('fanPoll.pollOptions') }}
              </label>
              <div class="space-y-2">
                <div
                  v-for="(option, index) in newPoll.options"
                  :key="index"
                  class="flex items-center gap-2"
                >
                  <input
                    v-model="newPoll.options[index]"
                    type="text"
                    required
                    :placeholder="$t('fanPoll.optionPlaceholder', { n: index + 1 })"
                    class="flex-1 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
                  />
                  <button
                    v-if="newPoll.options.length > 2"
                    type="button"
                    class="rounded p-1.5 text-gray-400 transition-colors hover:bg-red-50 hover:text-red-500 dark:hover:bg-red-900/20 dark:hover:text-red-400"
                    @click="removeOption(index)"
                  >
                    <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
              </div>
              <button
                type="button"
                class="mt-2 text-xs font-medium text-primary-600 transition-colors hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
                @click="addOption"
              >
                + {{ $t('fanPoll.addOption') }}
              </button>
            </div>

            <div class="grid grid-cols-1 gap-4 tablet:grid-cols-2">
              <div>
                <label class="mb-1 block text-xs font-medium text-gray-700 dark:text-gray-300">
                  {{ $t('fanPoll.deadline') }}
                </label>
                <input
                  v-model="newPoll.deadline"
                  type="datetime-local"
                  required
                  class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
                />
              </div>
            </div>

            <div class="flex items-center justify-end gap-3">
              <button
                type="button"
                class="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-800"
                @click="showCreateForm = false"
              >
                {{ $t('fanPoll.cancel') }}
              </button>
              <button
                type="submit"
                :disabled="!isFormValid"
                class="rounded-lg bg-primary-600 px-5 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {{ $t('fanPoll.createPoll') }}
              </button>
            </div>
          </form>
        </div>
      </Transition>

      <!-- Status Filter -->
      <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
        <nav class="-mb-px flex space-x-6">
          <button
            v-for="tab in statusTabs"
            :key="tab.key"
            :class="[
              activeFilter === tab.key
                ? 'border-primary-500 text-primary-600 dark:border-primary-400 dark:text-primary-400'
                : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400',
              'whitespace-nowrap border-b-2 px-1 py-3 text-sm font-medium',
            ]"
            @click="activeFilter = tab.key"
          >
            {{ tab.label }} ({{ tab.count }})
          </button>
        </nav>
      </div>

      <!-- Poll Cards Grid -->
      <div v-if="filteredPolls.length > 0" class="mb-6 grid gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
        <PollCard
          v-for="poll in filteredPolls"
          :key="poll.id"
          :poll="poll"
          :selected="selectedPoll?.id === poll.id"
          @select="handleSelectPoll"
        />
      </div>

      <!-- Empty state -->
      <div
        v-else
        class="mb-6 flex flex-col items-center justify-center rounded-xl border border-dashed border-gray-300 py-16 dark:border-gray-600"
      >
        <svg class="mb-3 h-12 w-12 text-gray-400 dark:text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
        </svg>
        <p class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ $t('fanPoll.noPolls') }}</p>
        <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">{{ $t('fanPoll.noPollsDesc') }}</p>
      </div>

      <!-- Result Panel -->
      <div v-if="selectedPoll" class="mt-6">
        <div class="mb-4 flex items-center justify-between">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('fanPoll.resultPanel') }}
          </h2>
          <button
            class="text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300"
            @click="selectedPoll = null"
          >
            {{ $t('fanPoll.closePanel') }}
          </button>
        </div>
        <PollResultChart
          :poll="selectedPoll"
          @close="handleClosePoll"
          @delete="handleDeletePoll"
        />
      </div>
    </template>

    <!-- Toast -->
    <Transition
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="translate-y-2 opacity-0"
      enter-to-class="translate-y-0 opacity-100"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="translate-y-0 opacity-100"
      leave-to-class="translate-y-2 opacity-0"
    >
      <div
        v-if="showToast"
        class="fixed bottom-8 right-8 z-50 rounded-lg bg-gray-900 px-4 py-3 text-white shadow-lg dark:bg-gray-100 dark:text-gray-900"
      >
        {{ toastMessage }}
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useFanPollStore } from '@/stores/fanPoll'
import type { FanPoll, PollType } from '@/types/fanPoll'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PollCard from '@/components/fanpoll/PollCard.vue'
import PollResultChart from '@/components/fanpoll/PollResultChart.vue'

const { t } = useI18n({ useScope: 'global' })
const store = useFanPollStore()

const selectedPoll = ref<FanPoll | null>(null)
const showCreateForm = ref(false)
const activeFilter = ref<'all' | 'active' | 'closed' | 'scheduled' | 'draft'>('all')
const showToast = ref(false)
const toastMessage = ref('')

// Create form
const newPoll = ref<{
  title: string
  description: string
  type: PollType
  options: string[]
  deadline: string
}>({
  title: '',
  description: '',
  type: 'SINGLE_CHOICE',
  options: ['', ''],
  deadline: '',
})

const isFormValid = computed(() =>
  newPoll.value.title.trim() !== '' &&
  newPoll.value.description.trim() !== '' &&
  newPoll.value.options.every((o) => o.trim() !== '') &&
  newPoll.value.deadline !== '',
)

const statusTabs = computed(() => [
  { key: 'all' as const, label: t('fanPoll.filterAll'), count: store.polls.length },
  { key: 'active' as const, label: t('fanPoll.filterActive'), count: store.activePolls.length },
  { key: 'closed' as const, label: t('fanPoll.filterClosed'), count: store.closedPolls.length },
  { key: 'scheduled' as const, label: t('fanPoll.filterScheduled'), count: store.scheduledPolls.length },
  { key: 'draft' as const, label: t('fanPoll.filterDraft'), count: store.draftPolls.length },
])

const filteredPolls = computed(() => {
  if (activeFilter.value === 'active') return store.activePolls
  if (activeFilter.value === 'closed') return store.closedPolls
  if (activeFilter.value === 'scheduled') return store.scheduledPolls
  if (activeFilter.value === 'draft') return store.draftPolls
  return store.polls
})

function addOption() {
  newPoll.value.options.push('')
}

function removeOption(index: number) {
  newPoll.value.options.splice(index, 1)
}

let toastTimeout: ReturnType<typeof setTimeout> | null = null

function showToastMessage(message: string) {
  toastMessage.value = message
  showToast.value = true
  if (toastTimeout) clearTimeout(toastTimeout)
  toastTimeout = setTimeout(() => {
    showToast.value = false
  }, 3000)
}

function handleSelectPoll(id: number) {
  const poll = store.polls.find((p) => p.id === id)
  if (poll) {
    selectedPoll.value = selectedPoll.value?.id === id ? null : poll
  }
}

async function handleCreatePoll() {
  if (!isFormValid.value) return
  await store.createPoll({
    title: newPoll.value.title,
    description: newPoll.value.description,
    type: newPoll.value.type,
    options: newPoll.value.options,
    deadline: new Date(newPoll.value.deadline).toISOString(),
    allowMultiple: newPoll.value.type === 'MULTIPLE_CHOICE',
  })
  newPoll.value = { title: '', description: '', type: 'SINGLE_CHOICE', options: ['', ''], deadline: '' }
  showCreateForm.value = false
  showToastMessage(t('fanPoll.createSuccess'))
}

async function handleClosePoll(id: number) {
  if (!confirm(t('fanPoll.confirmClose'))) return
  await store.closePoll(id)
  if (selectedPoll.value?.id === id) {
    const updated = store.polls.find((p) => p.id === id)
    if (updated) selectedPoll.value = updated
  }
  showToastMessage(t('fanPoll.closeSuccess'))
}

async function handleDeletePoll(id: number) {
  if (!confirm(t('fanPoll.confirmDelete'))) return
  await store.deletePoll(id)
  if (selectedPoll.value?.id === id) {
    selectedPoll.value = null
  }
  showToastMessage(t('fanPoll.deleteSuccess'))
}

onMounted(() => {
  store.fetchSummary()
  store.fetchPolls()
})
</script>
