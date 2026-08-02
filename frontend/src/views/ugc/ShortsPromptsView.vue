<template>
  <div>
    <PageHeader :title="$t('ugc.shorts.prompts.title')" :description="$t('ugc.shorts.prompts.description')">
      <template #actions>
        <router-link to="/ugc/shorts/templates" class="btn-secondary inline-flex items-center gap-2">
          <Square2StackIcon class="h-5 w-5" />
          {{ $t('ugc.shorts.prompts.toTemplates') }}
        </router-link>
      </template>
    </PageHeader>

    <LoadingSpinner v-if="store.promptsLoading" full-page />

    <!-- 9단계 카드 목록 -->
    <div v-else class="space-y-3">
      <button
        v-for="p in store.prompts"
        :key="p.stage"
        class="card flex w-full items-center justify-between gap-4 text-left transition-colors hover:border-primary-300 dark:hover:border-primary-700"
        @click="openEditor(p)"
      >
        <div class="min-w-0 flex-1">
          <div class="flex flex-wrap items-center gap-2">
            <span class="font-semibold text-gray-900 dark:text-gray-100">{{ p.name }}</span>
            <span class="rounded-full bg-gray-100 px-2 py-0.5 text-caption text-gray-500 dark:bg-gray-700 dark:text-gray-300">
              {{ p.stage }}
            </span>
            <span
              :class="[
                'rounded-full px-2 py-0.5 text-caption',
                p.executable
                  ? 'bg-success-subtle text-success-strong'
                  : 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300',
              ]"
            >
              {{ p.executable ? $t('ugc.shorts.prompts.executable') : $t('ugc.shorts.prompts.referenceOnly') }}
            </span>
            <span v-if="p.customized" class="rounded-full bg-primary-100 px-2 py-0.5 text-caption text-primary-700 dark:bg-primary-900/30 dark:text-primary-300">
              {{ $t('ugc.shorts.prompts.customized') }}
            </span>
            <span v-if="isModified(p)" class="rounded-full bg-warning-subtle px-2 py-0.5 text-caption text-warning-strong">
              {{ $t('ugc.shorts.prompts.modified') }}
            </span>
          </div>
          <p v-if="p.description" class="mt-1 truncate text-body text-gray-500 dark:text-gray-400">
            {{ p.description }}
          </p>
          <p class="mt-1 truncate text-body-xs text-gray-400">{{ p.userPrompt }}</p>
          <div class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-body-xs text-gray-400">
            <span>{{ $t('ugc.shorts.prompts.revisionShort', { revision: p.revision }) }}</span>
            <span v-if="p.updatedAt">{{ $t('ugc.shorts.prompts.updatedAt') }}: {{ formatDate(p.updatedAt) }}</span>
          </div>
        </div>
        <ChevronRightIcon class="h-5 w-5 shrink-0 text-gray-300 dark:text-gray-600" />
      </button>
    </div>

    <!-- 편집 / 개정 이력 모달 -->
    <BaseModal v-model="editorOpen" :title="editModalTitle" max-width="xl">
      <OTabs v-model="editorTab" :tabs="editorTabs" :aria-label="editModalTitle" />

      <!-- 편집 탭 -->
      <div v-if="editorTab === 'edit' && editingPrompt" class="mt-4 space-y-4">
        <div>
          <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300" for="shorts-system-prompt">
            {{ $t('ugc.shorts.prompts.systemPrompt') }}
          </label>
          <textarea
            id="shorts-system-prompt"
            v-model="form.systemPrompt"
            class="input-field min-h-28 w-full"
            rows="5"
          />
          <p class="mt-1 text-body-xs text-gray-400">{{ $t('ugc.shorts.prompts.systemPromptHint') }}</p>
          <div v-if="editingPrompt.customized" class="mt-2 rounded-lg bg-gray-50 p-3 dark:bg-gray-800">
            <p class="mb-1 text-body-xs font-semibold text-gray-500 dark:text-gray-400">{{ $t('ugc.shorts.prompts.defaultLabel') }}</p>
            <p class="whitespace-pre-line text-body-xs text-gray-500 dark:text-gray-400">
              {{ editingPrompt.defaultSystemPrompt || '-' }}
            </p>
          </div>
        </div>
        <div>
          <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300" for="shorts-user-prompt">
            {{ $t('ugc.shorts.prompts.userPrompt') }}
          </label>
          <textarea
            id="shorts-user-prompt"
            v-model="form.userPrompt"
            class="input-field min-h-32 w-full"
            rows="6"
          />
          <div v-if="editingPrompt.customized" class="mt-2 rounded-lg bg-gray-50 p-3 dark:bg-gray-800">
            <p class="mb-1 text-body-xs font-semibold text-gray-500 dark:text-gray-400">{{ $t('ugc.shorts.prompts.defaultLabel') }}</p>
            <p class="whitespace-pre-line text-body-xs text-gray-500 dark:text-gray-400">
              {{ editingPrompt.defaultUserPrompt }}
            </p>
          </div>
        </div>
        <div>
          <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300" for="shorts-change-note">
            {{ $t('ugc.shorts.prompts.changeNote') }}
          </label>
          <input
            id="shorts-change-note"
            v-model="form.changeNote"
            type="text"
            class="input-field w-full"
            :placeholder="$t('ugc.shorts.prompts.changeNotePlaceholder')"
          />
        </div>
      </div>

      <!-- 개정 이력 탭 -->
      <div v-else class="mt-4">
        <LoadingSpinner v-if="store.revisionsLoading" />
        <p v-else-if="store.revisions.length === 0" class="py-8 text-center text-body text-gray-400">
          {{ $t('ugc.shorts.prompts.noRevisions') }}
        </p>
        <ul v-else class="space-y-3">
          <li
            v-for="rev in store.revisions"
            :key="rev.revision"
            class="rounded-xl border border-gray-200 p-4 dark:border-gray-700"
          >
            <div class="flex items-center justify-between gap-3">
              <div class="min-w-0">
                <p class="text-body font-semibold text-gray-900 dark:text-gray-100">
                  {{ $t('ugc.shorts.prompts.revisionShort', { revision: rev.revision }) }}
                  <span class="ml-2 text-body-xs font-normal text-gray-400">{{ formatDate(rev.createdAt) }}</span>
                </p>
                <p class="mt-0.5 text-body-xs text-gray-500 dark:text-gray-400">
                  {{ rev.changeNote || $t('ugc.shorts.prompts.noChangeNote') }}
                </p>
              </div>
              <button class="btn-secondary shrink-0" @click="askRestore(rev.revision)">
                {{ $t('ugc.shorts.prompts.restore') }}
              </button>
            </div>
            <p class="mt-2 line-clamp-2 whitespace-pre-line text-body-xs text-gray-500 dark:text-gray-400">
              {{ rev.userPrompt }}
            </p>
          </li>
        </ul>
      </div>

      <template #footer>
        <button
          v-if="editorTab === 'edit' && editingPrompt?.customized"
          class="btn-danger mr-auto"
          :disabled="saving"
          @click="resetConfirmOpen = true"
        >
          {{ $t('ugc.shorts.prompts.resetToDefault') }}
        </button>
        <button v-if="editorTab === 'edit'" class="btn-primary" :disabled="saving" @click="save">
          {{ saving ? $t('ugc.shorts.prompts.saving') : $t('ugc.shorts.prompts.save') }}
        </button>
      </template>
    </BaseModal>

    <!-- 기본값 복원 확인 -->
    <ConfirmModal
      v-model="resetConfirmOpen"
      danger
      :title="$t('ugc.shorts.prompts.resetConfirmTitle')"
      :message="$t('ugc.shorts.prompts.resetConfirmMessage')"
      @confirm="confirmReset"
    />

    <!-- 개정 롤백 확인 -->
    <ConfirmModal
      v-model="restoreConfirmOpen"
      :title="$t('ugc.shorts.prompts.restoreConfirmTitle')"
      :message="$t('ugc.shorts.prompts.restoreConfirmMessage', { revision: pendingRestoreRevision })"
      @confirm="confirmRestore"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUgcShortsStore } from '@/stores/ugcShorts'
import { useNotificationStore } from '@/stores/notification'
import type { ShortsPromptResponse } from '@/api/ugcShortsPrompt'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import OTabs from '@/components/ui/OTabs.vue'
import { ChevronRightIcon, Square2StackIcon } from '@heroicons/vue/24/outline'

const { t } = useI18n({ useScope: 'global' })
const store = useUgcShortsStore()
const notify = useNotificationStore()

const editorOpen = ref(false)
const editorTab = ref('edit')
const editingPrompt = ref<ShortsPromptResponse | null>(null)
const saving = ref(false)
const resetConfirmOpen = ref(false)
const restoreConfirmOpen = ref(false)
const pendingRestoreRevision = ref(0)

// 편집 폼 — 시스템/사용자 프롬프트와 변경 메모
const form = ref({
  systemPrompt: '' as string,
  userPrompt: '',
  changeNote: '',
})

const editModalTitle = computed(() =>
  editingPrompt.value
    ? `${t('ugc.shorts.prompts.title')} · ${editingPrompt.value.name}`
    : t('ugc.shorts.prompts.title'),
)

const editorTabs = computed(() => [
  { key: 'edit', label: t('ugc.shorts.prompts.editTab') },
  { key: 'history', label: t('ugc.shorts.prompts.historyTab') },
])

/** 현재 값이 시스템 기본값과 다른지 (카드의 "수정됨" 표시용) */
function isModified(p: ShortsPromptResponse): boolean {
  return (
    p.customized ||
    p.userPrompt !== p.defaultUserPrompt ||
    (p.systemPrompt ?? null) !== (p.defaultSystemPrompt ?? null)
  )
}

function formatDate(iso: string): string {
  return iso.slice(0, 16).replace('T', ' ')
}

function openEditor(p: ShortsPromptResponse) {
  editingPrompt.value = p
  form.value = {
    systemPrompt: p.systemPrompt ?? '',
    userPrompt: p.userPrompt,
    changeNote: '',
  }
  editorTab.value = 'edit'
  editorOpen.value = true
  store.revisions = []
  // 개정 이력은 미리 불러 두어 탭 전환 시 바로 보이게 한다
  store.fetchRevisions(p.stage).catch((e) => {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.prompts.revisionsLoadFailed'))
  })
}

async function save() {
  const target = editingPrompt.value
  if (!target) return
  if (!form.value.userPrompt.trim()) {
    notify.error(t('ugc.shorts.prompts.userPromptRequired'))
    return
  }
  saving.value = true
  try {
    const updated = await store.updatePrompt(target.stage, {
      systemPrompt: form.value.systemPrompt.trim() || null,
      userPrompt: form.value.userPrompt,
      changeNote: form.value.changeNote.trim() || null,
    })
    editingPrompt.value = updated
    form.value.changeNote = ''
    notify.success(t('ugc.shorts.prompts.saved'))
    store.fetchRevisions(target.stage).catch(() => undefined)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.prompts.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function confirmReset() {
  const target = editingPrompt.value
  if (!target) return
  saving.value = true
  try {
    const updated = await store.resetPrompt(target.stage)
    editingPrompt.value = updated
    form.value = {
      systemPrompt: updated.systemPrompt ?? '',
      userPrompt: updated.userPrompt,
      changeNote: '',
    }
    notify.success(t('ugc.shorts.prompts.resetDone'))
    store.fetchRevisions(target.stage).catch(() => undefined)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.prompts.resetFailed'))
  } finally {
    saving.value = false
  }
}

function askRestore(revision: number) {
  pendingRestoreRevision.value = revision
  restoreConfirmOpen.value = true
}

async function confirmRestore() {
  const target = editingPrompt.value
  if (!target) return
  try {
    const updated = await store.restoreRevision(target.stage, pendingRestoreRevision.value)
    editingPrompt.value = updated
    form.value = {
      systemPrompt: updated.systemPrompt ?? '',
      userPrompt: updated.userPrompt,
      changeNote: '',
    }
    notify.success(t('ugc.shorts.prompts.restored', { revision: pendingRestoreRevision.value }))
    store.fetchRevisions(target.stage).catch(() => undefined)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.prompts.restoreFailed'))
  }
}

onMounted(async () => {
  try {
    await store.fetchPrompts()
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.prompts.loadFailed'))
  }
})
</script>
