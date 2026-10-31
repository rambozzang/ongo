<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseModal from '@/components/common/BaseModal.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { ugcShortsPipelineApi, type ClipEditResponse, type ClipEditorResponse } from '@/api/ugcShortsPipeline'
import { videoApi } from '@/api/video'
import type { SpeakerTrackingResponse } from '@/api/ugcShortsTracking'
import ShortsSpeakerTrackButton from '@/components/ugc/ShortsSpeakerTrackButton.vue'
import { buildEditRequest, draftFromEditor, msToSec, sameSec, secToMs, type ClipDraft } from '@/views/ugc/shortsClipEditor'

/**
 * 클립 편집기: 구간 다듬기 · 자막 줄 수정 · 제목/설명 · 크롭 · 플랫폼별 메타.
 *
 * 저장은 바뀐 항목만 보낸다. 구간·자막·크롭을 바꾸면 서버가 이전 렌더 결과를 버리므로,
 * 저장 전에 그 사실을 알리고 저장 뒤에는 "다시 렌더"가 필요하다고 말한다.
 *
 * 크롭 정규화(9:16 고정·원본 범위 안)는 서버(`ShortsCropPolicy`)가 한다. 화면은 원본 해상도를 알아야
 * 정규화가 정확하므로, 미리보기 영상 요소의 `videoWidth/videoHeight` 를 함께 보낸다.
 */
const props = defineProps<{
  modelValue: boolean
  workspaceId: number
  runId: number
  clipId: number
  sourceVideoId: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: [result: ClipEditResponse]
}>()

const { t } = useI18n()

const loading = ref(false)
const loadError = ref<string | null>(null)
const saving = ref(false)
const saveError = ref<string | null>(null)
const editor = ref<ClipEditorResponse | null>(null)
const draft = ref<ClipDraft | null>(null)

const sourceUrl = ref<string | null>(null)
const sourceSize = ref<{ width: number; height: number } | null>(null)

const open = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

async function load() {
  loading.value = true
  loadError.value = null
  saveError.value = null
  try {
    const data = await ugcShortsPipelineApi.getClipEditor(props.workspaceId, props.runId, props.clipId)
    editor.value = data
    draft.value = draftFromEditor(data)
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : t('ugc.shorts.editor.loadFailed')
  } finally {
    loading.value = false
  }
  // 미리보기는 실패해도 편집은 할 수 있다. 크롭만 원본 해상도 없이 저장된다.
  videoApi
    .get(props.sourceVideoId)
    .then((video) => {
      sourceUrl.value = video?.fileUrl || null
    })
    .catch(() => {
      sourceUrl.value = null
    })
}

watch(
  () => [props.modelValue, props.clipId] as const,
  ([isOpen]) => {
    if (isOpen) load()
  },
  { immediate: true },
)

const disabled = computed(() => !editor.value?.editable || saving.value)
const built = computed(() => (editor.value && draft.value ? buildEditRequest(editor.value, draft.value, sourceSize.value) : null))

const draftLengthSec = computed(() => {
  if (!draft.value) return null
  const length = secToMs(draft.value.endSec) - secToMs(draft.value.startSec)
  return Number.isFinite(length) && length > 0 ? msToSec(length) : null
})

/** 단어 강조가 있던 줄을 고쳤는지 — 저장하면 그 줄은 줄 단위로 보인다. */
function lineLosesWordTiming(index: number): boolean {
  const line = draft.value?.subtitles[index]
  const original = editor.value?.subtitles[index]
  if (!line || !original || !line.hadWordTiming) return false
  return line.text !== original.text || !sameSec(line.startSec, original.startMs) || !sameSec(line.endSec, original.endMs)
}

function addLine() {
  if (!draft.value) return
  const last = draft.value.subtitles[draft.value.subtitles.length - 1]
  const start = last ? secToMs(last.endSec) : 0
  draft.value.subtitles.push({
    startSec: msToSec(Number.isFinite(start) ? start : 0),
    endSec: msToSec((Number.isFinite(start) ? start : 0) + 2_000),
    text: '',
    hadWordTiming: false,
  })
}

function removeLine(index: number) {
  draft.value?.subtitles.splice(index, 1)
}

function onVideoMetadata(event: Event) {
  const el = event.target as HTMLVideoElement
  if (el.videoWidth > 0 && el.videoHeight > 0) sourceSize.value = { width: el.videoWidth, height: el.videoHeight }
}

/** 원본 가운데 가장 큰 9:16 창. 크롭이 없는 클립에서 시작점으로 쓴다(서버가 다시 정규화한다). */
function startCenteredCrop() {
  if (!draft.value || !sourceSize.value) return
  const { width: sw, height: sh } = sourceSize.value
  const height = Math.min(sh, Math.floor((sw * 16) / 9))
  const width = Math.floor((height * 9) / 16)
  draft.value.crop = {
    x: String(Math.floor((sw - width) / 2)),
    y: String(Math.floor((sh - height) / 2)),
    width: String(width),
    height: String(height),
  }
}

const cropOverlay = computed(() => {
  const crop = draft.value?.crop
  const size = sourceSize.value
  if (!crop || !size) return null
  const [x, y, w, h] = [crop.x, crop.y, crop.width, crop.height].map(Number)
  if (![x, y, w, h].every(Number.isFinite)) return null
  return {
    left: `${(x! / size.width) * 100}%`,
    top: `${(y! / size.height) * 100}%`,
    width: `${(w! / size.width) * 100}%`,
    height: `${(h! / size.height) * 100}%`,
  }
})

const draftErrorMessage = computed(() => {
  const result = built.value
  if (!result || result.ok) return null
  return t(`ugc.shorts.editor.errors.${result.error}`, { line: result.line ?? 0 })
})

const hasUnsavedChanges = computed(() => !!built.value && (!built.value.ok || built.value.changed))

/** 화자 추적 결과는 서버 저장까지 끝난 편집 응답이다. 저장 직후와 똑같이 상태를 바꾼다. */
function onSpeakerTracked(result: SpeakerTrackingResponse) {
  editor.value = result.edit.editor
  draft.value = draftFromEditor(result.edit.editor)
  emit('saved', result.edit)
}

async function save() {
  const result = built.value
  if (!result || !result.ok || !result.changed || !editor.value) return
  saving.value = true
  saveError.value = null
  try {
    const response = await ugcShortsPipelineApi.editClip(props.workspaceId, props.runId, props.clipId, result.request)
    editor.value = response.editor
    draft.value = draftFromEditor(response.editor)
    emit('saved', response)
  } catch (e) {
    // 서버 문구를 그대로 보인다 — 어느 줄·어느 한도인지 서버만 정확히 안다.
    saveError.value = e instanceof Error ? e.message : t('ugc.shorts.editor.saveFailed')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <BaseModal v-model="open" :title="$t('ugc.shorts.editor.title', { seq: editor?.seq ?? '' })" max-width="xl">
    <div v-if="loading" class="flex justify-center py-8"><LoadingSpinner /></div>

    <div v-else-if="loadError" role="alert" class="space-y-2">
      <p class="text-body text-error-strong">{{ loadError }}</p>
      <button type="button" class="btn-secondary" @click="load">{{ $t('ugc.shorts.editor.retry') }}</button>
    </div>

    <div v-else-if="editor && draft" class="space-y-5" data-testid="clip-editor">
      <p
        v-if="!editor.editable"
        role="status"
        class="rounded-xl border border-warning bg-warning-subtle p-3 text-body-xs text-warning-strong"
        data-testid="clip-editor-locked"
      >
        {{ editor.notEditableReason }}
      </p>

      <!-- 구간 -->
      <section>
        <h3 class="mb-2 text-body font-semibold text-gray-900 dark:text-gray-100">{{ $t('ugc.shorts.editor.range') }}</h3>
        <div class="grid grid-cols-3 gap-2">
          <label class="text-body-xs text-gray-600 dark:text-gray-300">
            {{ $t('ugc.shorts.editor.startSec') }}
            <input v-model="draft.startSec" :disabled="disabled" type="number" step="0.1" min="0" class="input-field mt-1 w-full" data-testid="clip-start" />
          </label>
          <label class="text-body-xs text-gray-600 dark:text-gray-300">
            {{ $t('ugc.shorts.editor.endSec') }}
            <input v-model="draft.endSec" :disabled="disabled" type="number" step="0.1" min="0" class="input-field mt-1 w-full" data-testid="clip-end" />
          </label>
          <p class="self-end pb-2 text-body-xs text-gray-500 dark:text-gray-400">
            {{ draftLengthSec ? $t('ugc.shorts.editor.length', { sec: draftLengthSec }) : '' }}
          </p>
        </div>
        <p class="mt-1 text-caption text-gray-400">
          {{ editor.sourceDurationMs != null
            ? $t('ugc.shorts.editor.rangeHint', { max: msToSec(editor.sourceDurationMs) })
            : $t('ugc.shorts.editor.rangeHintUnknown') }}
        </p>
      </section>

      <!-- 제목·설명 -->
      <section class="grid gap-2">
        <label class="text-body-xs text-gray-600 dark:text-gray-300">
          {{ $t('ugc.shorts.editor.clipTitle') }}
          <input v-model="draft.title" :disabled="disabled" maxlength="300" class="input-field mt-1 w-full" data-testid="clip-title" />
        </label>
        <label class="text-body-xs text-gray-600 dark:text-gray-300">
          {{ $t('ugc.shorts.editor.clipCaption') }}
          <textarea v-model="draft.caption" :disabled="disabled" rows="2" maxlength="2200" class="input-field mt-1 w-full" />
        </label>
      </section>

      <!-- 자막 -->
      <section>
        <div class="mb-2 flex items-center justify-between">
          <h3 class="text-body font-semibold text-gray-900 dark:text-gray-100">{{ $t('ugc.shorts.editor.subtitles') }}</h3>
          <button type="button" class="btn-secondary" :disabled="disabled" data-testid="subtitle-add" @click="addLine">
            {{ $t('ugc.shorts.editor.addLine') }}
          </button>
        </div>
        <p class="mb-2 text-caption text-gray-400">{{ $t('ugc.shorts.editor.subtitlesHint') }}</p>
        <p v-if="draft.subtitles.length === 0" class="text-body-xs text-gray-500 dark:text-gray-400">
          {{ $t('ugc.shorts.editor.noSubtitles') }}
        </p>
        <ul class="space-y-2">
          <li v-for="(line, index) in draft.subtitles" :key="index" class="grid grid-cols-[5rem_5rem_1fr_auto] items-start gap-2" data-testid="subtitle-line">
            <input v-model="line.startSec" :disabled="disabled" type="number" step="0.1" min="0" class="input-field" :aria-label="$t('ugc.shorts.editor.lineStart', { n: index + 1 })" />
            <input v-model="line.endSec" :disabled="disabled" type="number" step="0.1" min="0" class="input-field" :aria-label="$t('ugc.shorts.editor.lineEnd', { n: index + 1 })" />
            <div>
              <input v-model="line.text" :disabled="disabled" maxlength="200" class="input-field w-full" :aria-label="$t('ugc.shorts.editor.lineText', { n: index + 1 })" />
              <p v-if="lineLosesWordTiming(index)" class="mt-0.5 text-caption text-warning-strong" data-testid="word-timing-lost">
                {{ $t('ugc.shorts.editor.wordTimingLost') }}
              </p>
            </div>
            <button type="button" class="btn-secondary" :disabled="disabled" :aria-label="$t('ugc.shorts.editor.removeLine', { n: index + 1 })" @click="removeLine(index)">
              ✕
            </button>
          </li>
        </ul>
      </section>

      <!-- 크롭 -->
      <section>
        <h3 class="mb-2 text-body font-semibold text-gray-900 dark:text-gray-100">{{ $t('ugc.shorts.editor.crop') }}</h3>
        <div
          v-if="sourceUrl"
          class="relative mb-2 w-full max-w-md overflow-hidden rounded-lg bg-black"
          :style="sourceSize ? { aspectRatio: `${sourceSize.width} / ${sourceSize.height}` } : undefined"
        >
          <video :src="sourceUrl" class="h-full w-full" muted playsinline preload="metadata" @loadedmetadata="onVideoMetadata" />
          <div v-if="cropOverlay" class="pointer-events-none absolute border-2 border-primary-500 bg-primary-500/10" :style="cropOverlay" data-testid="crop-overlay" />
        </div>
        <p v-else class="mb-2 text-caption text-gray-400">{{ $t('ugc.shorts.editor.noPreview') }}</p>

        <div v-if="draft.crop" class="grid grid-cols-4 gap-2">
          <label v-for="key in (['x', 'y', 'width', 'height'] as const)" :key="key" class="text-body-xs text-gray-600 dark:text-gray-300">
            {{ key }}
            <input v-model="draft.crop[key]" :disabled="disabled" type="number" step="1" min="0" class="input-field mt-1 w-full" :data-testid="`crop-${key}`" />
          </label>
        </div>
        <button
          v-else
          type="button"
          class="btn-secondary"
          :disabled="disabled || !sourceSize"
          data-testid="crop-start"
          @click="startCenteredCrop"
        >
          {{ $t('ugc.shorts.editor.cropStart') }}
        </button>
        <p class="mt-1 text-caption text-gray-400">{{ $t('ugc.shorts.editor.cropHint') }}</p>
        <!--
          화자 추적(R3). 결과는 서버가 편집 저장(ShortsClipEditUseCase.edit)을 거쳐 반영한다.
          저장 안 한 편집이 있으면 잠근다 — 추적 결과로 화면을 다시 채우면 그 편집이 사라진다.
        -->
        <div class="mt-2">
          <ShortsSpeakerTrackButton
            :workspace-id="workspaceId"
            :run-id="runId"
            :clip-id="clipId"
            :disabled="disabled || hasUnsavedChanges"
            @applied="onSpeakerTracked"
          />
          <p v-if="hasUnsavedChanges && editor.editable" class="mt-1 text-caption text-gray-400" data-testid="track-needs-save">
            {{ $t('ugc.shorts.editor.saveBeforeTracking') }}
          </p>
        </div>
      </section>

      <!-- 플랫폼별 메타 -->
      <section>
        <h3 class="mb-2 text-body font-semibold text-gray-900 dark:text-gray-100">{{ $t('ugc.shorts.editor.platformMeta') }}</h3>
        <p v-if="draft.platformMeta.length === 0" class="text-body-xs text-gray-500 dark:text-gray-400">
          {{ $t('ugc.shorts.editor.noPlatformMeta') }}
        </p>
        <div v-for="meta in draft.platformMeta" :key="meta.platform" class="mb-3 rounded-xl border border-gray-200 p-3 dark:border-gray-700" data-testid="platform-meta">
          <p class="mb-1 text-body-xs font-semibold text-gray-700 dark:text-gray-200">{{ meta.platform }}</p>
          <input v-model="meta.title" :disabled="disabled" class="input-field mb-1 w-full" :aria-label="$t('ugc.shorts.editor.metaTitle', { platform: meta.platform })" />
          <textarea v-model="meta.description" :disabled="disabled" rows="2" class="input-field mb-1 w-full" :aria-label="$t('ugc.shorts.editor.metaDescription', { platform: meta.platform })" />
          <input
            v-model="meta.hashtagsText"
            :disabled="disabled"
            class="input-field w-full"
            :placeholder="$t('ugc.shorts.editor.hashtagsPlaceholder')"
            :aria-label="$t('ugc.shorts.editor.metaHashtags', { platform: meta.platform })"
            :data-testid="`meta-hashtags-${meta.platform}`"
          />
        </div>
        <p class="text-caption text-gray-400">{{ $t('ugc.shorts.editor.platformMetaHint') }}</p>
      </section>

      <p v-if="draftErrorMessage" role="alert" class="text-body-xs text-error-strong" data-testid="draft-error">{{ draftErrorMessage }}</p>
      <p v-if="saveError" role="alert" class="text-body-xs text-error-strong" data-testid="save-error">{{ saveError }}</p>
      <p
        v-if="built?.ok && built.touchesRender && editor.hasRenderedVideo"
        class="text-body-xs text-warning-strong"
        data-testid="rerender-warning"
      >
        {{ $t('ugc.shorts.editor.rerenderWarning') }}
      </p>
    </div>

    <template #footer>
      <button type="button" class="btn-secondary" @click="open = false">{{ $t('ugc.shorts.editor.close') }}</button>
      <button
        type="button"
        class="btn-primary"
        :disabled="disabled || !built?.ok || !built.changed"
        data-testid="clip-editor-save"
        @click="save"
      >
        {{ saving ? $t('ugc.shorts.editor.saving') : $t('ugc.shorts.editor.save') }}
      </button>
    </template>
  </BaseModal>
</template>
