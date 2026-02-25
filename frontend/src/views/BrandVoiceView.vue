<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ $t('brandVoice.title') }}</h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ $t('brandVoice.description') }}</p>
      </div>
      <div class="flex items-center gap-3">
        <div
          class="flex items-center gap-2 rounded-lg border px-4 py-2 text-sm"
          :class="isLow ? 'border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-900/20' : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800'"
        >
          <SparklesIcon class="h-4 w-4" :class="isLow ? 'text-red-500' : 'text-primary-600'" />
          <span class="text-gray-600 dark:text-gray-300">{{ $t('brandVoice.creditsRemaining') }}</span>
          <span class="font-bold" :class="isLow ? 'text-red-600' : 'text-primary-600 dark:text-primary-400'">
            {{ balance.toLocaleString() }}
          </span>
        </div>
      </div>
    </div>

    <PageGuide :title="$t('brandVoice.pageGuideTitle')" :items="($tm('brandVoice.pageGuide') as string[])" />

    <!-- Tab Navigation -->
    <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
      <nav class="-mb-px flex gap-6">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="border-b-2 px-1 py-3 text-sm font-medium transition-colors"
          :class="activeTab === tab.key
            ? 'border-primary-500 text-primary-600 dark:text-primary-400'
            : 'border-transparent text-gray-500 dark:text-gray-400 hover:border-gray-300 dark:hover:border-gray-600 hover:text-gray-700 dark:hover:text-gray-300'"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- Tab Content -->
    <div class="mt-6">
      <!-- Tab 1: Profile Management -->
      <div v-show="activeTab === 'profiles'">
        <!-- Header with create button -->
        <div class="mb-4 flex items-center justify-between">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('brandVoice.profiles.title') }}
          </h2>
          <button
            class="btn-primary inline-flex items-center gap-2"
            @click="showTrainModal = true"
          >
            <PlusIcon class="h-4 w-4" />
            {{ $t('brandVoice.profiles.createButton') }}
          </button>
        </div>

        <!-- Loading -->
        <div v-if="store.loading" class="flex items-center justify-center py-12">
          <div class="h-8 w-8 animate-spin rounded-full border-2 border-primary-500 border-t-transparent" />
        </div>

        <!-- Profile Grid -->
        <div
          v-else-if="store.profiles.length > 0"
          class="grid gap-4 tablet:grid-cols-2 desktop:grid-cols-3"
        >
          <VoiceProfileCard
            v-for="profile in store.profiles"
            :key="profile.id"
            :profile="profile"
            @select="handleSelectProfile"
            @delete="handleDeleteProfile"
          />
        </div>

        <!-- Empty State -->
        <div
          v-else
          class="rounded-xl border border-dashed border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-900 px-6 py-12 text-center"
        >
          <UserCircleIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-500" />
          <h3 class="mb-1 text-sm font-medium text-gray-900 dark:text-gray-100">
            {{ $t('brandVoice.profiles.emptyTitle') }}
          </h3>
          <p class="mb-4 text-sm text-gray-500 dark:text-gray-400">
            {{ $t('brandVoice.profiles.emptyDescription') }}
          </p>
          <button
            class="btn-primary inline-flex items-center gap-2"
            @click="showTrainModal = true"
          >
            <PlusIcon class="h-4 w-4" />
            {{ $t('brandVoice.profiles.createButton') }}
          </button>
        </div>
      </div>

      <!-- Tab 2: Text Generation -->
      <div v-show="activeTab === 'generate'">
        <div class="grid gap-6 desktop:grid-cols-2">
          <!-- Input Panel -->
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
            <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('brandVoice.generate.title') }}
            </h2>

            <div class="space-y-4">
              <!-- Profile Select -->
              <div>
                <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  {{ $t('brandVoice.generate.profileLabel') }}
                </label>
                <select
                  v-model="generateForm.profileId"
                  class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                  :disabled="store.generating"
                >
                  <option :value="null" disabled>
                    {{ store.readyProfiles.length === 0 ? $t('brandVoice.generate.noProfiles') : $t('brandVoice.generate.profileSelect') }}
                  </option>
                  <option
                    v-for="profile in store.readyProfiles"
                    :key="profile.id"
                    :value="profile.id"
                  >
                    {{ profile.name }} ({{ $t(`brandVoice.tones.${profile.tone}`) }})
                  </option>
                </select>
              </div>

              <!-- Prompt -->
              <div>
                <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  {{ $t('brandVoice.generate.promptLabel') }}
                </label>
                <textarea
                  v-model="generateForm.prompt"
                  rows="4"
                  class="w-full resize-y rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                  :placeholder="$t('brandVoice.generate.promptPlaceholder')"
                  :disabled="store.generating"
                />
              </div>

              <!-- Platform -->
              <div>
                <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  {{ $t('brandVoice.generate.platformLabel') }}
                </label>
                <select
                  v-model="generateForm.platform"
                  class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                  :disabled="store.generating"
                >
                  <option value="" disabled>{{ $t('brandVoice.generate.platformSelect') }}</option>
                  <option v-for="p in platformOptions" :key="p.value" :value="p.value">
                    {{ $t(`brandVoice.platforms.${p.value}`) }}
                  </option>
                </select>
              </div>

              <!-- Options Row -->
              <div class="grid gap-4 tablet:grid-cols-2">
                <div>
                  <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                    {{ $t('brandVoice.generate.maxLengthLabel') }}
                  </label>
                  <input
                    v-model.number="generateForm.maxLength"
                    type="number"
                    min="0"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                    :placeholder="$t('brandVoice.generate.maxLengthPlaceholder')"
                    :disabled="store.generating"
                  />
                </div>
                <div class="flex items-end pb-2">
                  <label class="flex items-center gap-2 text-sm font-medium text-gray-700 dark:text-gray-300">
                    <input
                      v-model="generateForm.includeHashtags"
                      type="checkbox"
                      class="rounded border-gray-300 dark:border-gray-600 text-primary-600 focus:ring-primary-500"
                      :disabled="store.generating"
                    />
                    {{ $t('brandVoice.generate.includeHashtags') }}
                  </label>
                </div>
              </div>

              <!-- Generate Button -->
              <div class="flex justify-end pt-2">
                <button
                  class="btn-primary inline-flex items-center gap-2"
                  :disabled="!canGenerate || store.generating"
                  @click="handleGenerate"
                >
                  <SparklesIcon class="h-4 w-4" />
                  {{ store.generating ? $t('brandVoice.generate.generating') : $t('brandVoice.generate.generateButton') }}
                </button>
              </div>
            </div>
          </div>

          <!-- Result Panel -->
          <div>
            <!-- Result -->
            <div
              v-if="store.generatedResult"
              class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm"
            >
              <div class="mb-3 flex items-center justify-between">
                <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
                  {{ $t('brandVoice.generate.resultTitle') }}
                </h3>
                <button
                  class="inline-flex items-center gap-1 rounded-lg border border-gray-200 dark:border-gray-700 px-3 py-1.5 text-xs text-gray-600 dark:text-gray-300 transition-colors hover:bg-gray-50 dark:hover:bg-gray-800"
                  @click="copyGeneratedText"
                >
                  <ClipboardDocumentIcon class="h-3.5 w-3.5" />
                  {{ $t('brandVoice.generate.copyButton') }}
                </button>
              </div>

              <p class="mb-4 whitespace-pre-wrap rounded-lg bg-gray-50 dark:bg-gray-800 p-3 text-sm leading-relaxed text-gray-700 dark:text-gray-300">
                {{ store.generatedResult.text }}
              </p>

              <!-- Hashtags -->
              <div v-if="store.generatedResult.hashtags.length > 0" class="mb-4">
                <p class="mb-1 text-xs font-medium text-gray-500 dark:text-gray-400">
                  {{ $t('brandVoice.generate.hashtags') }}
                </p>
                <div class="flex flex-wrap gap-1">
                  <span
                    v-for="tag in store.generatedResult.hashtags"
                    :key="tag"
                    class="rounded-full bg-blue-50 dark:bg-blue-900/20 px-2 py-0.5 text-xs font-medium text-blue-700 dark:text-blue-400"
                  >
                    {{ tag }}
                  </span>
                </div>
              </div>

              <!-- Confidence + Credits -->
              <div class="flex flex-wrap items-center gap-4 border-t border-gray-100 dark:border-gray-800 pt-3 text-xs text-gray-500 dark:text-gray-400">
                <span>
                  {{ $t('brandVoice.generate.confidenceScore') }}:
                  <strong class="text-primary-600 dark:text-primary-400">{{ Math.round(store.generatedResult.confidenceScore * 100) }}%</strong>
                </span>
                <span>
                  {{ $t('brandVoice.generate.creditsUsed') }}:
                  <strong>{{ store.generatedResult.creditsUsed }}</strong>
                </span>
                <span>
                  {{ $t('brandVoice.generate.creditsRemainingLabel') }}:
                  <strong>{{ store.generatedResult.creditsRemaining }}</strong>
                </span>
              </div>

              <!-- Regenerate -->
              <div class="mt-3 flex justify-end">
                <button
                  class="btn-primary inline-flex items-center gap-2 text-xs"
                  :disabled="store.generating"
                  @click="handleGenerate"
                >
                  <ArrowPathIcon class="h-3.5 w-3.5" />
                  {{ $t('brandVoice.generate.regenerateButton') }}
                </button>
              </div>
            </div>

            <!-- Empty State -->
            <div
              v-else
              class="rounded-xl border border-dashed border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-900 px-6 py-12 text-center"
            >
              <DocumentTextIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-500" />
              <h3 class="mb-1 text-sm font-medium text-gray-900 dark:text-gray-100">
                {{ $t('brandVoice.generate.emptyTitle') }}
              </h3>
              <p class="text-sm text-gray-500 dark:text-gray-400">
                {{ $t('brandVoice.generate.emptyDescription') }}
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Tab 3: Voice Analysis -->
      <div v-show="activeTab === 'analysis'">
        <div class="grid gap-6 desktop:grid-cols-2">
          <!-- Input Panel -->
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
            <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('brandVoice.analysis.title') }}
            </h2>

            <div class="space-y-4">
              <div>
                <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  {{ $t('brandVoice.analysis.inputLabel') }}
                </label>
                <textarea
                  v-model="analysisText"
                  rows="8"
                  class="w-full resize-y rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                  :placeholder="$t('brandVoice.analysis.inputPlaceholder')"
                  :disabled="store.analyzing"
                />
              </div>

              <div class="flex justify-end">
                <button
                  class="btn-primary inline-flex items-center gap-2"
                  :disabled="!analysisText.trim() || store.analyzing"
                  @click="handleAnalyze"
                >
                  <MagnifyingGlassIcon class="h-4 w-4" />
                  {{ store.analyzing ? $t('brandVoice.analysis.analyzing') : $t('brandVoice.analysis.analyzeButton') }}
                </button>
              </div>
            </div>
          </div>

          <!-- Result Panel -->
          <div>
            <div v-if="store.analysis">
              <div class="mb-3 flex items-center justify-between">
                <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
                  {{ $t('brandVoice.analysis.resultTitle') }}
                </h3>
                <button
                  class="inline-flex items-center gap-1 rounded-lg border border-gray-200 dark:border-gray-700 px-3 py-1.5 text-xs text-gray-600 dark:text-gray-300 transition-colors hover:bg-gray-50 dark:hover:bg-gray-800"
                  :disabled="store.analyzing"
                  @click="handleAnalyze"
                >
                  <ArrowPathIcon class="h-3.5 w-3.5" />
                  {{ $t('brandVoice.analysis.reAnalyze') }}
                </button>
              </div>
              <VoiceAnalysisResult :analysis="store.analysis" />
            </div>

            <!-- Empty State -->
            <div
              v-else
              class="rounded-xl border border-dashed border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-900 px-6 py-12 text-center"
            >
              <ChartBarIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-500" />
              <h3 class="mb-1 text-sm font-medium text-gray-900 dark:text-gray-100">
                {{ $t('brandVoice.analysis.emptyTitle') }}
              </h3>
              <p class="text-sm text-gray-500 dark:text-gray-400">
                {{ $t('brandVoice.analysis.emptyDescription') }}
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Train Voice Modal -->
    <TrainVoiceModal
      :show="showTrainModal"
      @close="showTrainModal = false"
      @submit="handleTrainSubmit"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  SparklesIcon,
  PlusIcon,
  UserCircleIcon,
  DocumentTextIcon,
  ChartBarIcon,
  ClipboardDocumentIcon,
  ArrowPathIcon,
  MagnifyingGlassIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import VoiceProfileCard from '@/components/brandvoice/VoiceProfileCard.vue'
import TrainVoiceModal from '@/components/brandvoice/TrainVoiceModal.vue'
import VoiceAnalysisResult from '@/components/brandvoice/VoiceAnalysisResult.vue'
import { useBrandVoiceStore } from '@/stores/brandVoice'
import { useCredit } from '@/composables/useCredit'
import type { BrandVoiceProfile, TrainVoiceRequest } from '@/types/brandVoice'

// --- Stores & Composables ---
const { t } = useI18n({ useScope: 'global' })
const store = useBrandVoiceStore()
const { balance, isLow, checkAndUse, fetchBalance } = useCredit()

// --- Tabs ---
type TabKey = 'profiles' | 'generate' | 'analysis'
const activeTab = ref<TabKey>('profiles')

const tabs = computed(() => [
  { key: 'profiles' as TabKey, label: t('brandVoice.tabs.profiles') },
  { key: 'generate' as TabKey, label: t('brandVoice.tabs.generate') },
  { key: 'analysis' as TabKey, label: t('brandVoice.tabs.analysis') },
])

// --- Platforms ---
const platformOptions = [
  { value: 'YOUTUBE' },
  { value: 'TIKTOK' },
  { value: 'INSTAGRAM' },
  { value: 'NAVER_CLIP' },
]

// --- State ---
const showTrainModal = ref(false)

// Generate form
const generateForm = ref({
  profileId: null as number | null,
  prompt: '',
  platform: '',
  maxLength: undefined as number | undefined,
  includeHashtags: true,
})

// Analysis
const analysisText = ref('')

// --- Computed ---
const canGenerate = computed(() =>
  generateForm.value.profileId !== null &&
  generateForm.value.prompt.trim().length > 0 &&
  generateForm.value.platform !== '',
)

// --- Lifecycle ---
onMounted(() => {
  store.fetchProfiles()
  fetchBalance()
})

// --- Handlers ---
function handleSelectProfile(profile: BrandVoiceProfile) {
  store.selectedProfile = profile
  generateForm.value.profileId = profile.id
  activeTab.value = 'generate'
}

async function handleDeleteProfile(id: number) {
  await store.deleteProfile(id)
}

async function handleTrainSubmit(request: TrainVoiceRequest) {
  const canUse = await checkAndUse(5, t('brandVoice.train.title'))
  if (!canUse) return

  await store.trainVoice(request)
  showTrainModal.value = false
  await fetchBalance()
}

async function handleGenerate() {
  if (!canGenerate.value) return

  const canUse = await checkAndUse(3, t('brandVoice.generate.title'))
  if (!canUse) return

  await store.generateWithVoice({
    profileId: generateForm.value.profileId!,
    prompt: generateForm.value.prompt,
    platform: generateForm.value.platform,
    maxLength: generateForm.value.maxLength || undefined,
    includeHashtags: generateForm.value.includeHashtags,
  })
  await fetchBalance()
}

async function handleAnalyze() {
  if (!analysisText.value.trim()) return

  const canUse = await checkAndUse(2, t('brandVoice.analysis.title'))
  if (!canUse) return

  await store.analyzeText(analysisText.value)
  await fetchBalance()
}

async function copyGeneratedText() {
  if (!store.generatedResult) return
  try {
    await navigator.clipboard.writeText(store.generatedResult.text)
  } catch {
    // Clipboard API not available
  }
}
</script>
