<template>
  <div class="min-h-full space-y-5 py-5 text-content">
    <PageHeader :title="$t('ugc.shorts.templates.title')" :description="$t('ugc.shorts.templates.description')">
      <template #actions>
        <router-link to="/ugc/shorts/prompts" class="btn-secondary inline-flex items-center gap-2">
          <ChatBubbleLeftRightIcon class="h-5 w-5" />
          {{ $t('ugc.shorts.templates.toPrompts') }}
        </router-link>
        <button class="btn-primary inline-flex items-center gap-2" @click="openCreate">
          <PlusIcon class="h-5 w-5" />
          {{ $t('ugc.shorts.templates.newTemplate') }}
        </button>
      </template>
    </PageHeader>

    <LoadingSpinner v-if="store.templatesLoading" full-page />

    <div
      v-else-if="store.templatesLoadError"
      class="card flex flex-col items-center justify-center gap-3 py-16 text-center"
      role="alert"
    >
      <ExclamationTriangleIcon class="h-10 w-10 text-error-strong" />
      <p class="text-body text-error-strong">{{ store.templatesLoadError }}</p>
      <button type="button" class="btn-secondary mt-2" @click="retryTemplates">
        {{ $t('action.retry') }}
      </button>
    </div>

    <EmptyState
      v-else-if="!store.templatesLoadError && store.templates.length === 0"
      :title="$t('ugc.shorts.templates.empty')"
      :description="$t('ugc.shorts.templates.emptyDescription')"
      :action-label="$t('ugc.shorts.templates.newTemplate')"
      :icon="RectangleGroupIcon"
      @action="openCreate"
    />

    <!-- 템플릿 카드 그리드 -->
    <div v-else class="grid gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
      <div v-for="tpl in store.templates" :key="tpl.id" class="card flex flex-col gap-3">
        <!-- 레퍼런스 이미지 / 배경 스타일 미리보기 -->
        <div
          class="flex h-40 items-center justify-center overflow-hidden rounded-lg"
          :class="previewClass(tpl.backgroundStyle)"
        >
          <img
            v-if="tpl.referenceImageUrl"
            :src="tpl.referenceImageUrl"
            :alt="tpl.name"
            class="h-full w-full object-cover"
          />
          <span v-else class="text-body-xs">{{ $t(`ugc.shorts.templates.backgroundStyles.${tpl.backgroundStyle}`) }}</span>
        </div>

        <div class="min-w-0">
          <div class="flex items-center gap-2">
            <span class="truncate font-semibold text-gray-900 dark:text-gray-100">{{ tpl.name }}</span>
            <span v-if="tpl.isDefault" class="rounded-full bg-primary-100 px-2 py-0.5 text-caption text-primary-700 dark:bg-primary-900/30 dark:text-primary-300">
              {{ $t('ugc.shorts.templates.defaultBadge') }}
            </span>
          </div>
          <p v-if="tpl.description" class="mt-1 truncate text-body text-gray-500 dark:text-gray-400">
            {{ tpl.description }}
          </p>
          <div class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-body-xs text-gray-400">
            <span>{{ $t('ugc.shorts.templates.size') }}: {{ tpl.width }}×{{ tpl.height }} ({{ tpl.aspectRatio }})</span>
            <span>
              {{ $t('ugc.shorts.templates.sectionSafeArea') }}:
              {{ tpl.safeAreaTop }} / {{ tpl.safeAreaBottom }}
            </span>
          </div>
        </div>

        <div class="mt-auto flex justify-end gap-2">
          <button class="btn-secondary" @click="openEdit(tpl)">
            {{ $t('ugc.shorts.templates.edit') }}
          </button>
          <button class="btn-danger" @click="askDelete(tpl)">
            {{ $t('ugc.shorts.templates.delete') }}
          </button>
        </div>
      </div>
    </div>

    <!-- 생성/수정 모달 -->
    <BaseModal v-model="formOpen" :title="formTitle" max-width="xl">
      <div class="space-y-5">
        <!-- 기본 정보 -->
        <div class="grid gap-4">
          <div>
            <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300" for="shorts-tpl-name">
              {{ $t('ugc.shorts.templates.fieldName') }}
            </label>
            <input
              id="shorts-tpl-name"
              v-model="form.name"
              type="text"
              class="input-field w-full"
              :placeholder="$t('ugc.shorts.templates.namePlaceholder')"
            />
          </div>
          <div>
            <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300" for="shorts-tpl-description">
              {{ $t('ugc.shorts.templates.fieldDescription') }}
            </label>
            <input
              id="shorts-tpl-description"
              v-model="form.description"
              type="text"
              class="input-field w-full"
              :placeholder="$t('ugc.shorts.templates.descriptionPlaceholder')"
            />
          </div>
        </div>

        <!-- 크기와 배경 -->
        <fieldset>
          <legend class="mb-2 text-body font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('ugc.shorts.templates.sectionLayout') }}
          </legend>
          <div class="grid gap-4 mobile:grid-cols-2">
            <div>
              <label class="mb-1 block text-body-xs text-gray-500 dark:text-gray-400" for="shorts-tpl-aspect">
                {{ $t('ugc.shorts.templates.aspectRatio') }}
              </label>
              <input id="shorts-tpl-aspect" v-model="form.aspectRatio" type="text" class="input-field w-full" placeholder="9:16" />
            </div>
            <div>
              <label class="mb-1 block text-body-xs text-gray-500 dark:text-gray-400" for="shorts-tpl-bg">
                {{ $t('ugc.shorts.templates.backgroundStyle') }}
              </label>
              <select id="shorts-tpl-bg" v-model="form.backgroundStyle" class="input-field w-full">
                <option v-for="opt in backgroundStyleOptions" :key="opt" :value="opt">
                  {{ $t(`ugc.shorts.templates.backgroundStyles.${opt}`) }}
                </option>
              </select>
            </div>
            <div>
              <label class="mb-1 block text-body-xs text-gray-500 dark:text-gray-400" for="shorts-tpl-width">
                {{ $t('ugc.shorts.templates.width') }}
              </label>
              <input id="shorts-tpl-width" v-model.number="form.width" type="number" min="1" class="input-field w-full" />
            </div>
            <div>
              <label class="mb-1 block text-body-xs text-gray-500 dark:text-gray-400" for="shorts-tpl-height">
                {{ $t('ugc.shorts.templates.height') }}
              </label>
              <input id="shorts-tpl-height" v-model.number="form.height" type="number" min="1" class="input-field w-full" />
            </div>
          </div>
        </fieldset>

        <!-- 후킹 문구 스타일 -->
        <fieldset>
          <legend class="mb-2 text-body font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('ugc.shorts.templates.sectionHook') }}
          </legend>
          <div class="grid gap-4 mobile:grid-cols-2">
            <div>
              <label class="mb-1 block text-body-xs text-gray-500 dark:text-gray-400" for="shorts-tpl-hook-font">
                {{ $t('ugc.shorts.templates.fontFamily') }}
              </label>
              <input id="shorts-tpl-hook-font" v-model="form.hookFontFamily" type="text" class="input-field w-full" :placeholder="$t('ugc.shorts.templates.fontFamilyPlaceholder')" />
            </div>
            <div>
              <label class="mb-1 block text-body-xs text-gray-500 dark:text-gray-400" for="shorts-tpl-hook-size">
                {{ $t('ugc.shorts.templates.fontSize') }}
              </label>
              <input id="shorts-tpl-hook-size" v-model.number="form.hookFontSize" type="number" min="1" class="input-field w-full" />
            </div>
            <div>
              <label class="mb-1 block text-body-xs text-gray-500 dark:text-gray-400" for="shorts-tpl-hook-color">
                {{ $t('ugc.shorts.templates.fontColor') }}
              </label>
              <input id="shorts-tpl-hook-color" v-model="form.hookFontColor" type="text" class="input-field w-full" :placeholder="$t('ugc.shorts.templates.colorPlaceholder')" />
            </div>
            <div>
              <label class="mb-1 block text-body-xs text-gray-500 dark:text-gray-400" for="shorts-tpl-hook-stroke">
                {{ $t('ugc.shorts.templates.strokeColor') }}
              </label>
              <input id="shorts-tpl-hook-stroke" v-model="form.hookStrokeColor" type="text" class="input-field w-full" :placeholder="$t('ugc.shorts.templates.colorPlaceholder')" />
            </div>
            <div>
              <label class="mb-1 block text-body-xs text-gray-500 dark:text-gray-400" for="shorts-tpl-hook-position">
                {{ $t('ugc.shorts.templates.position') }}
              </label>
              <select id="shorts-tpl-hook-position" v-model="form.hookPosition" class="input-field w-full">
                <option v-for="opt in positionOptions" :key="opt" :value="opt">
                  {{ $t(`ugc.shorts.templates.positions.${opt}`) }}
                </option>
              </select>
            </div>
          </div>
        </fieldset>

        <!-- 자막 스타일 -->
        <fieldset>
          <legend class="mb-2 text-body font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('ugc.shorts.templates.sectionCaption') }}
          </legend>
          <div class="grid gap-4 mobile:grid-cols-2">
            <div>
              <label class="mb-1 block text-body-xs text-gray-500 dark:text-gray-400" for="shorts-tpl-caption-font">
                {{ $t('ugc.shorts.templates.fontFamily') }}
              </label>
              <input id="shorts-tpl-caption-font" v-model="form.captionFontFamily" type="text" class="input-field w-full" :placeholder="$t('ugc.shorts.templates.fontFamilyPlaceholder')" />
            </div>
            <div>
              <label class="mb-1 block text-body-xs text-gray-500 dark:text-gray-400" for="shorts-tpl-caption-size">
                {{ $t('ugc.shorts.templates.fontSize') }}
              </label>
              <input id="shorts-tpl-caption-size" v-model.number="form.captionFontSize" type="number" min="1" class="input-field w-full" />
            </div>
            <div>
              <label class="mb-1 block text-body-xs text-gray-500 dark:text-gray-400" for="shorts-tpl-caption-color">
                {{ $t('ugc.shorts.templates.fontColor') }}
              </label>
              <input id="shorts-tpl-caption-color" v-model="form.captionFontColor" type="text" class="input-field w-full" :placeholder="$t('ugc.shorts.templates.colorPlaceholder')" />
            </div>
            <div>
              <label class="mb-1 block text-body-xs text-gray-500 dark:text-gray-400" for="shorts-tpl-caption-stroke">
                {{ $t('ugc.shorts.templates.strokeColor') }}
              </label>
              <input id="shorts-tpl-caption-stroke" v-model="form.captionStrokeColor" type="text" class="input-field w-full" :placeholder="$t('ugc.shorts.templates.colorPlaceholder')" />
            </div>
            <div>
              <label class="mb-1 block text-body-xs text-gray-500 dark:text-gray-400" for="shorts-tpl-caption-position">
                {{ $t('ugc.shorts.templates.position') }}
              </label>
              <select id="shorts-tpl-caption-position" v-model="form.captionPosition" class="input-field w-full">
                <option v-for="opt in positionOptions" :key="opt" :value="opt">
                  {{ $t(`ugc.shorts.templates.positions.${opt}`) }}
                </option>
              </select>
            </div>
          </div>
        </fieldset>

        <!-- 세이프에어리어 -->
        <fieldset>
          <legend class="mb-2 text-body font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('ugc.shorts.templates.sectionSafeArea') }}
          </legend>
          <div class="grid gap-4 mobile:grid-cols-2">
            <div>
              <label class="mb-1 block text-body-xs text-gray-500 dark:text-gray-400" for="shorts-tpl-safe-top">
                {{ $t('ugc.shorts.templates.safeAreaTop') }}
              </label>
              <input id="shorts-tpl-safe-top" v-model.number="form.safeAreaTop" type="number" min="0" class="input-field w-full" />
            </div>
            <div>
              <label class="mb-1 block text-body-xs text-gray-500 dark:text-gray-400" for="shorts-tpl-safe-bottom">
                {{ $t('ugc.shorts.templates.safeAreaBottom') }}
              </label>
              <input id="shorts-tpl-safe-bottom" v-model.number="form.safeAreaBottom" type="number" min="0" class="input-field w-full" />
            </div>
          </div>
        </fieldset>

        <!-- 레퍼런스 이미지 -->
        <fieldset>
          <legend class="mb-2 text-body font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('ugc.shorts.templates.referenceImage') }}
          </legend>
          <p class="mb-2 text-body-xs text-gray-400">{{ $t('ugc.shorts.templates.referenceImageHint') }}</p>
          <template v-if="editingTemplate">
            <div v-if="editingTemplate.referenceImageUrl" class="mb-3">
              <img
                :src="editingTemplate.referenceImageUrl"
                :alt="$t('ugc.shorts.templates.referenceImage')"
                class="max-h-48 rounded-lg border border-gray-200 object-contain dark:border-gray-700"
              />
            </div>
            <input ref="fileInputRef" type="file" accept="image/*" class="hidden" @change="onFileChange" />
            <button class="btn-secondary inline-flex items-center gap-2" :disabled="uploading" @click="fileInputRef?.click()">
              <ArrowUpTrayIcon class="h-5 w-5" />
              {{ uploading ? $t('ugc.shorts.templates.uploading') : $t('ugc.shorts.templates.selectImage') }}
            </button>
          </template>
          <p v-else class="text-body-xs text-gray-400">{{ $t('ugc.shorts.templates.referenceImageAfterCreate') }}</p>
        </fieldset>

        <!-- 기본 템플릿 지정 -->
        <label class="flex items-center gap-2 text-body text-gray-700 dark:text-gray-300" for="shorts-tpl-default">
          <input id="shorts-tpl-default" v-model="form.isDefault" type="checkbox" class="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
          {{ $t('ugc.shorts.templates.setDefault') }}
        </label>
      </div>

      <template #footer>
        <button class="btn-primary" :disabled="saving" @click="save">
          {{ saving ? $t('ugc.shorts.templates.saving') : (editingTemplate ? $t('ugc.shorts.templates.save') : $t('ugc.shorts.templates.create')) }}
        </button>
      </template>
    </BaseModal>

    <!-- 삭제 확인 -->
    <ConfirmModal
      v-model="deleteConfirmOpen"
      danger
      :title="$t('ugc.shorts.templates.deleteConfirmTitle')"
      :message="$t('ugc.shorts.templates.deleteConfirmMessage', { name: deletingTemplate?.name ?? '' })"
      @confirm="confirmDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUgcShortsStore } from '@/stores/ugcShorts'
import { useNotificationStore } from '@/stores/notification'
import type {
  ShortsBackgroundStyle,
  ShortsTemplateRequest,
  ShortsTemplateResponse,
  ShortsTextPosition,
} from '@/api/ugcShortsTemplate'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import {
  ArrowUpTrayIcon,
  ChatBubbleLeftRightIcon,
  ExclamationTriangleIcon,
  PlusIcon,
  RectangleGroupIcon,
} from '@heroicons/vue/24/outline'

const { t } = useI18n({ useScope: 'global' })
const store = useUgcShortsStore()
const notify = useNotificationStore()

const backgroundStyleOptions: ShortsBackgroundStyle[] = ['BLACK_BARS', 'BLURRED', 'SOLID']
const positionOptions: ShortsTextPosition[] = ['TOP', 'CENTER', 'BOTTOM']

const formOpen = ref(false)
const editingTemplate = ref<ShortsTemplateResponse | null>(null)
const saving = ref(false)
const uploading = ref(false)
const deleteConfirmOpen = ref(false)
const deletingTemplate = ref<ShortsTemplateResponse | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

/** 빈 템플릿 폼 — 시스템 기본값과 동일한 초기값 */
function emptyForm() {
  return {
    name: '',
    description: '',
    aspectRatio: '9:16',
    width: 1080,
    height: 1920,
    backgroundStyle: 'BLACK_BARS' as ShortsBackgroundStyle,
    hookFontFamily: '',
    hookFontSize: null as number | null,
    hookFontColor: '',
    hookStrokeColor: '',
    hookPosition: 'TOP' as ShortsTextPosition,
    captionFontFamily: '',
    captionFontSize: null as number | null,
    captionFontColor: '',
    captionStrokeColor: '',
    captionPosition: 'BOTTOM' as ShortsTextPosition,
    safeAreaTop: 0,
    safeAreaBottom: 0,
    isDefault: false,
  }
}

const form = ref(emptyForm())

const formTitle = computed(() =>
  editingTemplate.value
    ? t('ugc.shorts.templates.editTitle')
    : t('ugc.shorts.templates.createTitle'),
)

function previewClass(style: ShortsBackgroundStyle): string {
  switch (style) {
    case 'BLACK_BARS':
      return 'bg-gray-900 text-gray-300'
    case 'BLURRED':
      return 'bg-gray-200 text-gray-500 dark:bg-gray-700 dark:text-gray-300'
    default:
      return 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400'
  }
}

function openCreate() {
  editingTemplate.value = null
  form.value = emptyForm()
  formOpen.value = true
}

function openEdit(tpl: ShortsTemplateResponse) {
  editingTemplate.value = tpl
  form.value = {
    name: tpl.name,
    description: tpl.description ?? '',
    aspectRatio: tpl.aspectRatio,
    width: tpl.width,
    height: tpl.height,
    backgroundStyle: tpl.backgroundStyle,
    hookFontFamily: tpl.hookFontFamily ?? '',
    hookFontSize: tpl.hookFontSize,
    hookFontColor: tpl.hookFontColor ?? '',
    hookStrokeColor: tpl.hookStrokeColor ?? '',
    hookPosition: tpl.hookPosition,
    captionFontFamily: tpl.captionFontFamily ?? '',
    captionFontSize: tpl.captionFontSize,
    captionFontColor: tpl.captionFontColor ?? '',
    captionStrokeColor: tpl.captionStrokeColor ?? '',
    captionPosition: tpl.captionPosition,
    safeAreaTop: tpl.safeAreaTop,
    safeAreaBottom: tpl.safeAreaBottom,
    isDefault: tpl.isDefault,
  }
  formOpen.value = true
}

/** 빈 문자열은 null 로 정규화해 요청 본문을 만든다 */
function toRequest(): ShortsTemplateRequest {
  const f = form.value
  const str = (v: string) => (v.trim() ? v.trim() : null)
  return {
    name: f.name.trim(),
    description: str(f.description),
    aspectRatio: f.aspectRatio.trim() || '9:16',
    width: f.width,
    height: f.height,
    backgroundStyle: f.backgroundStyle,
    hookFontFamily: str(f.hookFontFamily),
    hookFontSize: f.hookFontSize || null,
    hookFontColor: str(f.hookFontColor),
    hookStrokeColor: str(f.hookStrokeColor),
    hookPosition: f.hookPosition,
    captionFontFamily: str(f.captionFontFamily),
    captionFontSize: f.captionFontSize || null,
    captionFontColor: str(f.captionFontColor),
    captionStrokeColor: str(f.captionStrokeColor),
    captionPosition: f.captionPosition,
    safeAreaTop: f.safeAreaTop,
    safeAreaBottom: f.safeAreaBottom,
    isDefault: f.isDefault,
  }
}

async function save() {
  if (!form.value.name.trim()) {
    notify.error(t('ugc.shorts.templates.nameRequired'))
    return
  }
  saving.value = true
  try {
    if (editingTemplate.value) {
      const updated = await store.updateTemplate(editingTemplate.value.id, toRequest())
      editingTemplate.value = updated
      notify.success(t('ugc.shorts.templates.updated'))
    } else {
      await store.createTemplate(toRequest())
      notify.success(t('ugc.shorts.templates.created'))
    }
    formOpen.value = false
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.templates.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  const target = editingTemplate.value
  if (!file || !target) return
  uploading.value = true
  try {
    const updated = await store.uploadReferenceImage(target.id, file)
    editingTemplate.value = updated
    notify.success(t('ugc.shorts.templates.uploaded'))
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.templates.uploadFailed'))
  } finally {
    uploading.value = false
  }
}

function askDelete(tpl: ShortsTemplateResponse) {
  deletingTemplate.value = tpl
  deleteConfirmOpen.value = true
}

async function retryTemplates() {
  try {
    await store.fetchTemplates()
  } catch {
    // The store exposes the error inline; keep the retry event handled.
  }
}

async function confirmDelete() {
  const target = deletingTemplate.value
  if (!target) return
  try {
    await store.deleteTemplate(target.id)
    notify.success(t('ugc.shorts.templates.deleted'))
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.templates.deleteFailed'))
  } finally {
    deletingTemplate.value = null
  }
}

onMounted(async () => {
  try {
    await store.fetchTemplates()
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.templates.loadFailed'))
  }
})
</script>
