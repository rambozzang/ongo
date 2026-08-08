<template>
  <div class="relative min-h-full space-y-5 py-5 text-content">
    <template v-if="!bioPage">
      <div v-if="store.loadError" class="flex min-h-[400px] items-center justify-center">
        <div class="w-full max-w-md rounded-xl border border-error-subtle bg-error-subtle p-6 text-center text-body text-error-strong" role="alert">
          <p>{{ store.loadError }}</p>
          <button type="button" class="btn-primary mt-4" :disabled="store.loading" @click="store.fetchPage()">다시 시도</button>
        </div>
      </div>
      <div v-else class="flex min-h-[400px] items-center justify-center">
        <form class="w-full max-w-md rounded-xl border border-line bg-surface-card p-6" @submit.prevent="handleCreate">
          <h2 class="text-xl font-semibold text-gray-700 dark:text-gray-300">{{ $t('linkBioView.noPage') }}</h2>
          <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">{{ $t('linkBioView.noPageDescription') }}</p>
          <label class="mt-5 block text-sm font-medium">{{ $t('linkBioView.slug') }}
            <input v-model="newSlug" required pattern="[A-Za-z0-9-]+" class="input-field mt-1 w-full" placeholder="my-page" />
          </label>
          <label class="mt-3 block text-sm font-medium">{{ $t('linkBioView.displayName') }}
            <input v-model="newTitle" class="input-field mt-1 w-full" :placeholder="$t('linkBioView.displayName')" />
          </label>
          <p v-if="createError" class="mt-3 rounded-lg bg-error-subtle px-3 py-2 text-sm text-error-strong">{{ createError }}</p>
          <button type="submit" class="btn-primary mt-5 w-full" :disabled="creating">
            {{ creating ? $t('action.loading') : $t('linkBioView.createPage') }}
          </button>
        </form>
      </div>
    </template>
    <template v-else>
    <!-- Header -->
    <PageHeader :title="$t('linkBioView.title')" :description="$t('linkBioView.description')">
      <template #actions>
        <!-- Publish URL -->
        <div class="flex items-center gap-2 rounded-md border border-gray-300 bg-gray-50 px-3 py-2 dark:border-gray-600 dark:bg-gray-700">
          <span class="text-sm text-gray-600 dark:text-gray-400">{{ publishUrl }}</span>
          <button
            class="text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
            @click="copyLink"
          >
            <ClipboardDocumentIcon class="h-5 w-5" />
          </button>
        </div>
        <!-- Save Button -->
        <button
          :disabled="!isDirty"
          class="btn-primary inline-flex items-center gap-2"
          @click="handleSave"
        >
          <CheckIcon class="h-5 w-5" />
          {{ $t('linkBioView.save') }}
        </button>
      </template>
    </PageHeader>

    <PageGuide :title="$t('linkBioView.pageGuideTitle')" :items="($tm('linkBioView.pageGuide') as string[])" />

    <!-- Stats Bar -->
    <div class="grid gap-2.5 tablet:grid-cols-3 mb-6">
      <div class="rounded-[11px] border border-line bg-surface-card p-4">
        <div class="text-xs text-gray-500 dark:text-gray-400">{{ $t('linkBioView.totalViews') }}</div>
        <div class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
          {{ bioPage.totalViews.toLocaleString() }}
        </div>
      </div>
      <div class="rounded-[11px] border border-line bg-surface-card p-4">
        <div class="text-xs text-gray-500 dark:text-gray-400">{{ $t('linkBioView.totalClicks') }}</div>
        <div class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
          {{ totalClicks.toLocaleString() }}
        </div>
      </div>
      <div class="rounded-[11px] border border-line bg-surface-card p-4">
        <div class="text-xs text-gray-500 dark:text-gray-400">{{ $t('linkBioView.clickRate') }}</div>
        <div class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
          {{ clickRate }}%
        </div>
      </div>
    </div>

    <!-- Mobile Tabs -->
    <div class="mb-6 border-b border-gray-200 dark:border-gray-700 desktop:hidden">
      <div class="flex gap-4">
        <button
          :class="[
            'border-b-2 py-3 text-sm font-medium transition-colors',
            activeTab === 'editor'
              ? 'border-primary-600 text-primary-600 dark:border-primary-400 dark:text-primary-400'
              : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300',
          ]"
          @click="activeTab = 'editor'"
        >
          {{ $t('linkBioView.tabEditor') }}
        </button>
        <button
          :class="[
            'border-b-2 py-3 text-sm font-medium transition-colors',
            activeTab === 'preview'
              ? 'border-primary-600 text-primary-600 dark:border-primary-400 dark:text-primary-400'
              : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300',
          ]"
          @click="activeTab = 'preview'"
        >
          {{ $t('linkBioView.tabPreview') }}
        </button>
      </div>
    </div>

    <!-- Main Content -->
    <div>
      <div class="flex flex-col gap-8 desktop:flex-row">
        <!-- Editor Panel -->
        <div
          :class="[
            'flex-1',
            activeTab === 'preview' ? 'hidden desktop:block' : 'block',
          ]"
        >
          <BioEditor
            :page="bioPage"
            @update-profile="updateProfile"
            @change-theme="changeTheme"
            @update-colors="updateColors"
            @add-block="addBlock"
            @update-block="updateBlock"
            @delete-block="removeBlock"
            @toggle-visibility="toggleBlockVisibility"
            @reorder-block="reorderBlock"
          />
        </div>

        <!-- Preview Panel -->
        <div
          :class="[
            'desktop:w-[400px]',
            activeTab === 'editor' ? 'hidden desktop:block' : 'block',
          ]"
        >
          <div class="sticky top-8">
            <BioPreview :page="bioPage" />
          </div>
        </div>
      </div>
    </div>

    <!-- Toast Notification -->
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
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { ClipboardDocumentIcon, CheckIcon } from '@heroicons/vue/24/outline'
import { useLinkBioStore } from '@/stores/linkbio'
import BioEditor from '@/components/linkbio/BioEditor.vue'
import BioPreview from '@/components/linkbio/BioPreview.vue'
import type { ThemeStyle, BlockType, BioBlock, BioPage } from '@/types/linkbio'
import { linkBioApi } from '@/api/linkbio'

const { t } = useI18n({ useScope: 'global' })
const store = useLinkBioStore()
const { bioPage, isDirty, totalClicks, publishUrl } = storeToRefs(store)

const activeTab = ref<'editor' | 'preview'>('editor')
const showToast = ref(false)
const toastMessage = ref('')
const newSlug = ref('')
const newTitle = ref('')
const creating = ref(false)
const createError = ref('')

const clickRate = computed(() => {
  if (!bioPage.value || bioPage.value.totalViews === 0) return '0.0'
  return ((totalClicks.value / bioPage.value.totalViews) * 100).toFixed(1)
})

const updateProfile = (updates: Partial<Pick<BioPage, 'displayName' | 'bio'>>) => {
  store.updateProfile(updates)
}

const changeTheme = (theme: ThemeStyle) => {
  store.changeTheme(theme)
}

const updateColors = (colors: Partial<Pick<BioPage, 'backgroundColor' | 'textColor' | 'buttonColor' | 'buttonTextColor'>>) => {
  store.updateColors(colors)
}

const addBlock = (type: BlockType) => {
  store.addBlock(type)
}

const updateBlock = (blockId: number, updates: Partial<BioBlock>) => {
  store.updateBlock(blockId, updates)
}

const removeBlock = (blockId: number) => {
  store.removeBlock(blockId)
}

const toggleBlockVisibility = (blockId: number) => {
  store.toggleBlockVisibility(blockId)
}

const reorderBlock = (fromIndex: number, toIndex: number) => {
  store.reorderBlock(fromIndex, toIndex)
}

const handleSave = async () => {
  try {
    await store.savePage()
    showToastMessage(t('linkBioView.saved'))
  } catch {
    showToastMessage(t('linkBioView.saveFailed'))
  }
}

const handleCreate = async () => {
  creating.value = true
  createError.value = ''
  try {
    await linkBioApi.createPage({ slug: newSlug.value.trim(), title: newTitle.value.trim() || undefined })
    await store.fetchPage()
  } catch (cause) {
    createError.value = cause instanceof Error ? cause.message : t('linkBioView.createFailed')
  } finally {
    creating.value = false
  }
}

const copyLink = async () => {
  try {
    await navigator.clipboard.writeText(publishUrl.value)
    showToastMessage(t('linkBioView.linkCopied'))
  } catch {
    showToastMessage(t('linkBioView.linkCopyFailed'))
  }
}

let toastTimeout: ReturnType<typeof setTimeout> | null = null

const showToastMessage = (message: string) => {
  toastMessage.value = message
  showToast.value = true
  if (toastTimeout) clearTimeout(toastTimeout)
  toastTimeout = setTimeout(() => {
    showToast.value = false
  }, 3000)
}

onUnmounted(() => {
  if (toastTimeout) clearTimeout(toastTimeout)
})
</script>
