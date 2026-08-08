<template>
  <div class="grid h-full" style="grid-template-columns: minmax(0, 1fr) 372px">
    <!-- 좌: 입력 -->
    <div class="overflow-y-auto border-r border-line px-5 pb-[120px] pt-[18px] scrollbar-dark">
      <!-- 입력 소스 -->
      <div
        class="mb-2.5 flex items-center gap-1 rounded-lg border border-line bg-surface-input p-1"
      >
        <button
          v-for="mode in sourceModes"
          :key="mode.key"
          type="button"
          class="flex-1 rounded-md px-3 py-1.5 text-[11px] font-semibold transition-colors"
          :class="
            sourceMode === mode.key
              ? 'bg-surface-card text-content shadow-sm'
              : 'text-content-tertiary hover:text-content'
          "
          @click="selectSourceMode(mode.key)"
        >
          {{ mode.label }}
        </button>
      </div>

      <!-- 파일 -->
      <div
        class="flex items-center gap-3.5 rounded-[11px] border border-line bg-surface-card p-3.5"
      >
        <ThumbPlaceholder :src="file.thumbnailUrl" :width="104" :height="62" />
        <div class="min-w-0 flex-1">
          <p class="truncate text-[13px] font-semibold text-content">
            {{ file.name || t('redesign.compose.noFile') }}
          </p>
          <p class="mt-1 font-mono text-[10.5px] text-content-tertiary">{{ fileMeta }}</p>
        </div>
        <div v-if="sourceMode === 'file'" class="flex shrink-0 items-center gap-2">
          <button type="button" class="btn-secondary !text-[11px]" @click="pickFile">
            {{ t('redesign.compose.replace') }}
          </button>
          <button
            type="button"
            class="btn-secondary !text-[11px]"
            :disabled="captioning"
            @click="requestCaptions"
          >
            {{ captioning ? t('redesign.compose.captioning') : t('redesign.compose.autoCaption') }}
          </button>
        </div>
        <input ref="fileInput" type="file" accept="video/*" class="hidden" @change="onFileChosen" />
      </div>

      <div
        v-if="sourceMode === 'url'"
        class="mt-2 rounded-[11px] border border-line bg-surface-card p-3.5"
      >
        <label
          class="block text-[11.5px] font-semibold text-content-secondary"
          for="source-video-url"
        >
          {{ t('redesign.compose.importUrlLabel') }}
        </label>
        <div class="mt-2 flex gap-2">
          <input
            id="source-video-url"
            v-model.trim="importUrl"
            type="url"
            maxlength="2000"
            :disabled="!importAvailable || importing"
            :placeholder="t('redesign.compose.importUrlPlaceholder')"
            class="input-field min-w-0 flex-1 !text-[12px]"
            @keydown.enter.prevent="importFromUrl"
          />
          <button
            type="button"
            class="btn-primary shrink-0 !text-[11px]"
            :disabled="!importAvailable || !importUrl || importing"
            @click="importFromUrl"
          >
            {{
              importing ? t('redesign.compose.importing') : t('redesign.compose.importUrlAction')
            }}
          </button>
        </div>
        <p v-if="importAvailability?.available !== true" class="mt-2 text-[11px] text-warn">
          {{ importAvailability?.reason || t('redesign.compose.importUnavailable') }}
        </p>
        <p v-else class="mt-2 text-[10.5px] text-content-tertiary">
          {{ t('redesign.compose.importUrlHint') }}
        </p>
      </div>

      <!-- 업로드 진행 — 파일 카드 안에 텍스트를 병기한다 -->
      <div v-if="uploadStore.isUploading" class="mt-2">
        <div class="h-1 overflow-hidden rounded-full bg-line">
          <div
            class="h-full bg-accent transition-[width]"
            :style="{ width: `${uploadStore.progress.percentage}%` }"
          />
        </div>
        <p class="mt-1 font-mono text-[10.5px] text-content-tertiary">
          {{ t('redesign.compose.uploading', { percent: uploadStore.progress.percentage }) }}
        </p>
      </div>

      <!-- 발행 대상 -->
      <section class="mt-[18px]">
        <h2 class="text-[12px] font-bold text-content">{{ t('redesign.compose.targetsTitle') }}</h2>
        <p class="mt-1 text-[11px] text-content-tertiary">
          {{ t('redesign.compose.targetsHint') }}
        </p>
        <div class="mt-2.5 flex flex-wrap gap-2">
          <button
            v-for="opt in platformOptions"
            :key="opt.code"
            type="button"
            class="flex items-center gap-2 rounded-md border px-2.5 py-2 transition-colors"
            :class="
              !opt.supported
                ? 'cursor-not-allowed border-line bg-transparent text-content-tertiary opacity-50'
                : isOn(opt.channel.id)
                  ? 'border-line-hover'
                  : 'border-line bg-transparent text-content-tertiary hover:border-line-hover'
            "
            :style="isOn(opt.channel.id) && opt.supported ? onChipStyle(opt.code) : undefined"
            :aria-pressed="isOn(opt.channel.id)"
            :aria-disabled="!opt.supported"
            :disabled="!opt.supported"
            :title="!opt.supported ? t('redesign.compose.platformUnavailable') : undefined"
            @click="toggle(opt.channel.id)"
          >
            <span class="h-[7px] w-[7px] shrink-0" :style="{ background: 'currentColor' }" />
            <span class="text-[12px] font-semibold">{{ opt.label }}</span>
            <span v-if="opt.handle" class="font-mono text-[10px] opacity-65">{{ opt.handle }}</span>
          </button>
        </div>
        <p v-if="platformOptions.length === 0" class="mt-2 text-[11.5px] text-content-tertiary">
          {{ t('redesign.compose.noChannels') }}
        </p>
      </section>

      <!-- 원본 게시와 쇼츠 제작을 같은 작업으로 묶는다 -->
      <section class="mt-3 rounded-[11px] border border-line bg-surface-card p-3.5">
        <label class="flex cursor-pointer items-start gap-2.5">
          <input
            v-model="shortsEnabled"
            type="checkbox"
            class="mt-0.5 h-4 w-4 accent-[var(--accent-primary)]"
          />
          <span>
            <span class="block text-[12px] font-bold text-content">{{
              t('redesign.compose.autoShorts')
            }}</span>
            <span class="mt-1 block text-[10.5px] leading-4 text-content-tertiary">
              {{ t('redesign.compose.autoShortsHint') }}
            </span>
          </span>
        </label>
        <div v-if="shortsProcessing" class="mt-2.5 rounded-lg bg-surface-input px-3 py-2">
          <div class="flex items-center justify-between gap-2">
            <span class="text-[11px] font-semibold text-content">{{
              t('redesign.compose.shortsProcessing')
            }}</span>
            <span class="font-mono text-[10px] text-content-tertiary">{{ shortsStatus }}</span>
          </div>
          <div class="mt-2 h-1 overflow-hidden rounded-full bg-line">
            <div class="h-full w-1/2 animate-pulse rounded-full bg-accent" />
          </div>
        </div>
        <p
          v-else-if="shortsEnabled && selectedCount > 0 && shortsPlatforms.length === 0"
          class="mt-2.5 rounded-lg border border-warning-subtle bg-warning-subtle px-3 py-2 text-[10.5px] text-warning-strong"
        >
          {{ t('redesign.compose.shortsSkippedForTargets') }}
        </p>
      </section>

      <!-- 문구 탭 -->
      <section class="mt-[18px] overflow-hidden rounded-[11px] border border-line bg-surface-card">
        <div class="flex border-b border-line-row">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            type="button"
            class="px-[15px] py-[11px] text-[12px] transition-colors"
            :class="
              activeTab === tab.key
                ? 'font-bold text-content shadow-[inset_0_-2px_0_0_var(--accent-primary)]'
                : 'text-content-secondary hover:text-content'
            "
            @click="activeTab = tab.key"
          >
            {{ tab.label }}
          </button>
        </div>

        <div class="p-[15px]">
          <div class="flex items-center justify-between gap-2">
            <label class="block text-[11.5px] text-content-secondary">{{
              t('redesign.compose.title')
            }}</label>
            <div class="flex items-center gap-2">
              <button
                v-if="activeTab !== 'common'"
                type="button"
                class="text-[10px] font-semibold text-accent hover:underline"
                @click="applyCommonToPlatforms"
              >
                {{ t('redesign.compose.applyCommon') }}
              </button>
              <span v-if="metadataGenerating" class="text-[10px] text-accent">{{
                t('redesign.compose.metadataGenerating')
              }}</span>
            </div>
          </div>
          <div class="mt-1.5 flex items-center gap-2">
            <input
              v-model="activeDraft.title"
              type="text"
              class="input-field !text-[12.5px]"
              :maxlength="titleLimit > 0 ? titleLimit : undefined"
              :aria-invalid="titleOver"
              @input="markActivePlatformDraftDirty"
            />
            <span
              class="shrink-0 font-mono text-[10px]"
              :class="titleOver ? 'text-bad' : 'text-content-tertiary'"
            >
              {{ activeDraft.title.length }} / {{ titleLimit }}
            </span>
          </div>

          <label class="mt-3.5 block text-[11.5px] text-content-secondary">
            {{ t('redesign.compose.description') }}
          </label>
          <textarea
            v-model="activeDraft.description"
            class="input-field mt-1.5 min-h-[92px] !text-[12.5px]"
            :maxlength="descriptionLimit > 0 ? descriptionLimit : undefined"
            :aria-invalid="descriptionOver"
            @input="markActivePlatformDraftDirty"
          />
          <div
            class="mt-1 text-right font-mono text-[10px]"
            :class="descriptionOver ? 'text-bad' : 'text-content-tertiary'"
          >
            {{ activeDraft.description.length }} / {{ descriptionLimit || '∞' }}
          </div>

          <label class="mt-3.5 block text-[11.5px] text-content-secondary">
            {{ t('redesign.compose.hashtags') }}
          </label>
          <textarea
            v-model="activeDraft.hashtags"
            class="input-field mt-1.5 min-h-[62px] !text-[12.5px] !text-accent"
            :aria-invalid="tagsOver"
            @input="markActivePlatformDraftDirty"
          />
          <div
            class="mt-1 text-right font-mono text-[10px]"
            :class="tagsOver ? 'text-bad' : 'text-content-tertiary'"
          >
            {{ hashtagCount }} / {{ tagLimit || '∞' }}
          </div>

          <!-- 규칙 경고는 발행 시점이 아니라 입력 시점에 뜬다 -->
          <div
            v-for="warning in warnings"
            :key="warning"
            class="mt-3.5 flex items-start gap-2 rounded-lg border border-line-control bg-surface-input px-3 py-2.5"
          >
            <span class="mt-1 h-1.5 w-1.5 shrink-0 rounded-full bg-warn" />
            <p class="text-[11.5px] text-content-secondary">{{ warning }}</p>
          </div>
        </div>
      </section>
    </div>

    <!-- 우: 미리보기 + 예약 -->
    <aside class="flex flex-col overflow-y-auto bg-surface-input px-4 py-[18px] scrollbar-dark">
      <h2 class="text-[12px] font-bold text-content">{{ t('redesign.compose.preview') }}</h2>

      <div class="mt-2.5">
        <PlatformPreviewPanel
          :platforms="selectedPreviewPlatforms"
          :platform-metadata="previewMetadata"
          :platform-limits="previewLimits"
          :thumbnail="file.thumbnailUrl || undefined"
          :channel-name="selectedChannels[0]?.channelName"
          :comparison-mode="selectedPreviewPlatforms.length > 1"
        />
      </div>

      <!-- 발행 예약 -->
      <h2 class="mt-[18px] text-[12px] font-bold text-content">
        {{ t('redesign.compose.scheduleTitle') }}
      </h2>
      <div class="mt-2.5 space-y-2">
        <button
          v-for="opt in scheduleOptions"
          :key="opt.key"
          type="button"
          class="flex w-full items-start gap-2.5 rounded-lg border px-3 py-2.5 text-left transition-colors"
          :class="
            schedMode === opt.key
              ? 'border-accent bg-accent-dim'
              : 'border-line-control hover:border-line-hover'
          "
          @click="schedMode = opt.key"
        >
          <span
            class="mt-0.5 flex h-3 w-3 shrink-0 items-center justify-center rounded-full border"
            :class="schedMode === opt.key ? 'border-accent' : 'border-line-hover'"
          >
            <span v-if="schedMode === opt.key" class="h-1.5 w-1.5 rounded-full bg-accent" />
          </span>
          <span class="min-w-0">
            <span class="block text-[12px] font-semibold text-content">{{ opt.label }}</span>
            <span
              v-if="opt.hint"
              class="mt-0.5 block font-mono text-[10.5px] text-content-tertiary"
            >
              {{ opt.hint }}
            </span>
          </span>
        </button>
      </div>
      <p
        v-if="optimalTimesError && schedMode === 'best'"
        class="mt-2 rounded-lg border border-warning-subtle bg-warning-subtle px-3 py-2 text-[10.5px] text-warning-strong"
        role="status"
      >
        {{ optimalTimesError }}
      </p>

      <input
        v-if="schedMode === 'fix'"
        v-model="fixedAt"
        type="datetime-local"
        class="input-field mt-2 !text-[12px]"
      />

      <div class="mt-3 rounded-lg border border-line-control bg-surface-raised p-3">
        <label
          class="flex cursor-pointer items-center gap-2 text-[11.5px] font-semibold text-content"
        >
          <input v-model="recurringEnabled" type="checkbox" class="h-4 w-4 accent-primary-600" />
          {{ t('redesign.compose.recurringToggle') }}
        </label>
        <div v-if="recurringEnabled" class="mt-2.5 space-y-2">
          <div class="grid grid-cols-2 gap-2">
            <select v-model="recurringFrequency" class="input-field !text-[11.5px]">
              <option value="DAILY">매일</option>
              <option value="WEEKLY">매주</option>
              <option value="BIWEEKLY">격주</option>
              <option value="MONTHLY">매월</option>
            </select>
            <input v-model="recurringTime" type="time" class="input-field !text-[11.5px]" />
          </div>
          <select
            v-if="recurringFrequency === 'WEEKLY' || recurringFrequency === 'BIWEEKLY'"
            v-model.number="recurringDayOfWeek"
            class="input-field !text-[11.5px]"
          >
            <option :value="1">월요일</option>
            <option :value="2">화요일</option>
            <option :value="3">수요일</option>
            <option :value="4">목요일</option>
            <option :value="5">금요일</option>
            <option :value="6">토요일</option>
            <option :value="7">일요일</option>
          </select>
          <input
            v-if="recurringFrequency === 'MONTHLY'"
            v-model.number="recurringDayOfMonth"
            type="number"
            min="1"
            max="31"
            class="input-field !text-[11.5px]"
            placeholder="매월 일자 (1~31)"
          />
          <p class="text-[10.5px] leading-4 text-content-tertiary">
            {{ t('redesign.compose.recurringHint') }}
          </p>
        </div>
      </div>

      <!-- 액션 -->
      <div class="mt-auto pt-[18px]">
        <p
          v-if="dataLoadError"
          class="mb-2 rounded-lg border border-error-subtle bg-error-subtle px-3 py-2 text-[11px] text-error-strong"
          role="alert"
        >
          {{ dataLoadError }}
        </p>
        <p v-if="blockedReason" class="mb-2 text-[11px] text-error-strong">{{ blockedReason }}</p>
        <p v-else-if="notice" class="mb-2 text-[11px] text-content-secondary">{{ notice }}</p>
        <div class="flex gap-2">
          <button
            type="button"
            class="btn-secondary flex-1 !text-[12px]"
            :disabled="saving"
            @click="saveDraft"
          >
            {{ saving ? t('redesign.compose.saving') : t('redesign.compose.saveDraft') }}
          </button>
          <button
            type="button"
            class="btn-primary !text-[12px]"
            style="flex: 1.4"
            :disabled="
              !!blockedReason || submitting || uploadStore.isUploading || metadataGenerating
            "
            @click="submit"
          >
            {{
              submitting
                ? t('redesign.compose.scheduling')
                : t('redesign.compose.schedule', { count: selectedCount })
            }}
          </button>
        </div>
        <p class="mt-2 text-center font-mono text-[10.5px] text-content-tertiary">
          {{ t('redesign.compose.shortcutHint') }}
        </p>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLocale } from '@/composables/useLocale'
import ThumbPlaceholder from '@/components/redesign/ThumbPlaceholder.vue'
import { aiApi } from '@/api/ai'
import { analyticsApi } from '@/api/analytics'
import { channelApi } from '@/api/channel'
import {
  parseCues,
  serializeCues,
  countWords,
  totalDurationOf,
  subtitleEditorApi,
} from '@/api/subtitleEditor'
import { videoApi } from '@/api/video'
import { templatesApi } from '@/api/templates'
import { ugcShortsPipelineApi, type PipelineRunDetailResponse } from '@/api/ugcShortsPipeline'
import { recurringApi, type RecurringFrequency } from '@/api/recurring'
import PlatformPreviewPanel from '@/components/preview/PlatformPreviewPanel.vue'
import { useUploadStore } from '@/stores/upload'
import { useWorkspaceStore } from '@/stores/workspace'
import type { Channel, Platform } from '@/types/channel'
import type { PlatformPublishConfig, PlatformUploadCapability } from '@/types/video'
import { parsePublishHashtags, validatePublishDrafts } from '@/utils/publishValidation'
import type { OptimalTimeSlot } from '@/types/analytics'
import { fallbackOptimalSlot, kstWallClockToInstant, nextOptimalDateTime } from '@/utils/optimalSchedule'

/**
 * 새 업로드 — 파일 → 대상 → 문구 → 예약을 화면 이동 없이 한 번에.
 *
 * 규칙 경고(플랫폼별 제목 길이·해시태그 상한)는 발행 시점이 아니라 입력 시점에 띄운다.
 * 만료된 채널이 대상에 포함되면 예약 버튼을 막고 사유를 보여준다.
 */
const { t } = useLocale()
const route = useRoute()

type ChipCode = 'YT' | 'IG' | 'TT' | 'FB' | 'NV' | 'TH' | 'TW' | 'PI'
const CHIP: Partial<Record<Platform, ChipCode>> = {
  YOUTUBE: 'YT',
  INSTAGRAM: 'IG',
  TIKTOK: 'TT',
  FACEBOOK: 'FB',
  NAVER_CLIP: 'NV',
  THREADS: 'TH',
  TWITTER: 'TW',
  PINTEREST: 'PI',
}

const CHIP_VARS: Record<ChipCode, { bg: string; fg: string }> = {
  YT: { bg: 'var(--platform-yt-bg)', fg: 'var(--platform-yt-fg)' },
  IG: { bg: 'var(--platform-ig-bg)', fg: 'var(--platform-ig-fg)' },
  TT: { bg: 'var(--platform-tt-bg)', fg: 'var(--platform-tt-fg)' },
  FB: { bg: 'var(--platform-fb-bg)', fg: 'var(--platform-fb-fg)' },
  NV: { bg: 'var(--platform-nv-bg)', fg: 'var(--platform-nv-fg)' },
  TH: { bg: 'var(--platform-th-bg)', fg: 'var(--platform-th-fg)' },
  TW: { bg: 'var(--platform-x-bg, #111827)', fg: 'var(--platform-x-fg, #ffffff)' },
  PI: { bg: '#E60023', fg: '#ffffff' },
}

/** 플랫폼별 제목 상한. 핸드오프 검증 규칙. */
const TITLE_LIMIT: Record<string, number> = {
  common: 100,
  YT: 100,
  IG: 2200,
  TT: 2200,
  FB: 255,
  NV: 100,
  TH: 500,
}
/** TikTok 은 해시태그 5개까지만 반영된다. */
const TIKTOK_HASHTAG_LIMIT = 5

const router = useRouter()
const uploadStore = useUploadStore()
const workspaceStore = useWorkspaceStore()

const fileInput = ref<HTMLInputElement | null>(null)
const sourceMode = ref<'file' | 'url'>('file')
const importUrl = ref('')
const importing = ref(false)
const importedVideoId = ref<number | null>(null)
const importAvailability = ref<{ available: boolean; reason?: string | null } | null>(null)
const submitting = ref(false)
const saving = ref(false)
const captioning = ref(false)
const metadataGenerating = ref(false)
const metadataGeneratedForVideoId = ref<number | null>(null)
const metadataGeneratedPlatforms = ref<Platform[]>([])
const dataLoadError = ref('')
const shortsEnabled = ref(true)
const shortsProcessing = ref(false)
const shortsStatus = ref('')
/** 사용자에게 보여줄 한 줄 안내(성공·실패 공용). */
const notice = ref('')

const channels = ref<Channel[]>([])
const capabilities = ref<PlatformUploadCapability[]>([])
const optimalSlots = ref<Partial<Record<Platform, OptimalTimeSlot[]>>>({})
const optimalTimesError = ref('')
const disabled = reactive<Record<number, boolean>>({})
const activeTab = ref<'common' | 'YT' | 'IG' | 'TT' | 'FB' | 'NV' | 'TH' | 'TW' | 'PI'>('common')
const schedMode = ref<'now' | 'best' | 'fix'>('best')
const fixedAt = ref('')
const recurringEnabled = ref(false)
const recurringFrequency = ref<RecurringFrequency>('WEEKLY')
const recurringTime = ref('09:00')
const recurringDayOfWeek = ref(1)
const recurringDayOfMonth = ref(1)

type FormDraft = { title: string; description: string; hashtags: string }
const form = reactive<FormDraft>({ title: '', description: '', hashtags: '' })
const platformForms = reactive<Partial<Record<Platform, FormDraft>>>({})
const platformDraftDirty = reactive<Partial<Record<Platform, boolean>>>({})
const file = reactive({ name: '', thumbnailUrl: null as string | null, meta: '' })

const sourceModes = computed(() => [
  { key: 'file' as const, label: t('redesign.compose.fileSourceUpload') },
  { key: 'url' as const, label: t('redesign.compose.fileSourceUrl') },
])
const importAvailable = computed(() => importAvailability.value?.available === true)

function selectSourceMode(mode: 'file' | 'url') {
  sourceMode.value = mode
  if (mode === 'file') importedVideoId.value = null
}

const tabs = [
  { key: 'common' as const, label: t('redesign.compose.tabCommon') },
  { key: 'YT' as const, label: 'YouTube' },
  { key: 'IG' as const, label: 'Instagram' },
  { key: 'TT' as const, label: 'TikTok' },
  { key: 'FB' as const, label: 'Facebook' },
  { key: 'NV' as const, label: 'Naver' },
  { key: 'TH' as const, label: 'Threads' },
  { key: 'TW' as const, label: 'X (Twitter)' },
  { key: 'PI' as const, label: 'Pinterest' },
]

const scheduleOptions = computed(() => [
  { key: 'now' as const, label: t('redesign.compose.schedNow'), hint: '' },
  { key: 'best' as const, label: t('redesign.compose.schedBest'), hint: bestTimeHint.value },
  { key: 'fix' as const, label: t('redesign.compose.schedFixed'), hint: fixedAt.value },
])

const bestTimeHint = computed(() => {
  const times = selectedChannels.value.flatMap(
    (channel) => optimalSlots.value[channel.platform]?.slice(0, 3).map((slot) => slot.timeLabel) ?? [],
  )
  const uniqueTimes = [...new Set(times)]
  return uniqueTimes.length > 0
    ? uniqueTimes.slice(0, 3).join(' · ')
    : t('redesign.compose.optimalFallback')
})

const platformOptions = computed(() =>
  channels.value.map((ch) => ({
    code: CHIP[ch.platform] ?? 'TH',
    label: ch.channelName,
    handle: '',
    channel: ch,
    supported: capabilities.value.some((capability) => capability.platform === ch.platform),
  })),
)

const isOn = (channelId: number) => !disabled[channelId]
const toggle = (channelId: number) => {
  disabled[channelId] = !disabled[channelId]
}
const onChipStyle = (code: string) => {
  const v = CHIP_VARS[code as ChipCode] ?? CHIP_VARS.TH
  return { background: v.bg, color: v.fg }
}

const selectedChannels = computed(() =>
  platformOptions.value.filter((o) => o.supported && isOn(o.channel.id)).map((o) => o.channel),
)
const selectedCount = computed(() => selectedChannels.value.length)
const shortsPlatforms = computed(() =>
  selectedChannels.value
    .map((channel) => channel.platform)
    .filter((platform): platform is Platform =>
      ['YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'NAVER_CLIP'].includes(platform),
    ),
)

function platformForTab(tab: typeof activeTab.value): Platform | null {
  return (
    (
      {
        YT: 'YOUTUBE',
        IG: 'INSTAGRAM',
        TT: 'TIKTOK',
        FB: 'FACEBOOK',
        NV: 'NAVER_CLIP',
        TH: 'THREADS',
        TW: 'TWITTER',
        PI: 'PINTEREST',
      } as Record<string, Platform>
    )[tab] ?? null
  )
}

function draftFor(platform: Platform | null): FormDraft {
  if (!platform) return form
  if (!platformForms[platform]) {
    platformForms[platform] = reactive({ ...form })
    platformDraftDirty[platform] = false
  }
  return platformForms[platform]!
}

/** 공통 문구는 채널별로 따로 편집하기 전까지 자동으로 따라간다. */
watch(
  () => [form.title, form.description, form.hashtags],
  () => {
    for (const platform of Object.keys(platformForms) as Platform[]) {
      if (platformDraftDirty[platform]) continue
      const draft = platformForms[platform]
      if (!draft) continue
      draft.title = form.title
      draft.description = form.description
      draft.hashtags = form.hashtags
    }
  },
)

function markActivePlatformDraftDirty() {
  const platform = platformForTab(activeTab.value)
  if (platform) platformDraftDirty[platform] = true
}

const activeDraft = computed(() => draftFor(platformForTab(activeTab.value)))
const selectedPreviewPlatforms = computed(() => [
  ...new Set(selectedChannels.value.map((channel) => channel.platform)),
])
const previewMetadata = computed(() =>
  Object.fromEntries(
    selectedPreviewPlatforms.value.map((platform) => {
      const draft = draftFor(platform)
      return [
        platform,
        {
          title: draft.title,
          description: draft.description,
          tags: parseHashtags(draft.hashtags),
        },
      ]
    }),
  ),
)
const previewLimits = computed(() =>
  Object.fromEntries(
    capabilities.value.map((capability) => [
      capability.platform,
      {
        title: capability.maxTitleLength,
        description: capability.maxDescriptionLength,
        tags: capability.maxTagCount,
      },
    ]),
  ),
)

const activeCapability = computed(() => {
  const platform = platformForTab(activeTab.value)
  return platform ? capabilities.value.find((item) => item.platform === platform) : null
})
const titleLimit = computed(
  () => activeCapability.value?.maxTitleLength ?? TITLE_LIMIT[activeTab.value] ?? 100,
)
const descriptionLimit = computed(() => activeCapability.value?.maxDescriptionLength ?? 0)
const tagLimit = computed(() => activeCapability.value?.maxTagCount ?? 0)
const titleOver = computed(() => activeDraft.value.title.length > titleLimit.value)
const descriptionOver = computed(
  () => descriptionLimit.value > 0 && activeDraft.value.description.length > descriptionLimit.value,
)
const tagsOver = computed(() => tagLimit.value > 0 && hashtagCount.value > tagLimit.value)

const fileMeta = computed(() => file.meta || t('redesign.compose.noFileMeta'))

// Count the same normalized tokens that are sent to the API. Splitting only
// on '#' made comma/space-separated tags display an incorrect quota.
const hashtagCount = computed(() => parsePublishHashtags(activeDraft.value.hashtags).length)

const warnings = computed(() => {
  const out: string[] = []
  if (titleOver.value) {
    out.push(t('redesign.compose.warnTitleLength', { limit: titleLimit.value }))
  }
  if (descriptionOver.value) {
    out.push(t('redesign.compose.warnDescriptionLength', { limit: descriptionLimit.value }))
  }
  if (tagsOver.value) {
    out.push(t('redesign.compose.warnTagCount', { limit: tagLimit.value }))
  }
  const hasTikTok = selectedChannels.value.some((c) => c.platform === 'TIKTOK')
  if (hasTikTok && hashtagCount.value > TIKTOK_HASHTAG_LIMIT && activeTab.value === 'common') {
    out.push(t('redesign.compose.warnTiktokHashtags', { limit: TIKTOK_HASHTAG_LIMIT }))
  }
  return out
})

const validationIssues = computed(() => {
  return validatePublishDrafts(
    selectedChannels.value,
    capabilities.value,
    Object.fromEntries(
      selectedChannels.value.map((channel) => [channel.platform, draftFor(channel.platform)]),
    ),
  ).map((issue) => {
    const message =
      issue.field === 'title'
        ? t('redesign.compose.warnTitleLength', { limit: issue.limit })
        : issue.field === 'description'
          ? t('redesign.compose.warnDescriptionLength', { limit: issue.limit })
          : t('redesign.compose.warnTagCount', { limit: issue.limit })
    return `${issue.channelName}: ${message}`
  })
})

function applyCommonToPlatforms() {
  for (const channel of selectedChannels.value) {
    const draft = draftFor(channel.platform)
    draft.title = form.title
    draft.description = form.description
    draft.hashtags = form.hashtags
    platformDraftDirty[channel.platform] = false
  }
  notice.value = t('redesign.compose.commonApplied')
}

/** 채널별 성과 분석을 예약 시간 계산에 연결한다. 실패 시 일반 기본값을 사용하되 사용자에게 알린다. */
async function loadOptimalTimes(targetChannels: Channel[]) {
  const platforms = [...new Set(targetChannels.map((channel) => channel.platform))]
  if (platforms.length === 0) {
    optimalSlots.value = {}
    optimalTimesError.value = ''
    return
  }

  const results = await Promise.allSettled(
    platforms.map(async (platform) => ({
      platform,
      result: await analyticsApi.getOptimalTimes(platform),
    })),
  )
  const next: Partial<Record<Platform, OptimalTimeSlot[]>> = {}
  let failures = 0
  for (const result of results) {
    if (result.status === 'fulfilled') next[result.value.platform] = result.value.result.slots
    else failures += 1
  }
  optimalSlots.value = next
  optimalTimesError.value = failures > 0 ? t('redesign.compose.optimalTimesFailed') : ''
}

/** 만료 채널이 대상에 포함되면 예약을 막는다. */
const blockedReason = computed(() => {
  if (dataLoadError.value) return dataLoadError.value
  if (selectedCount.value === 0) return t('redesign.compose.blockNoTarget')
  const expired = selectedChannels.value.filter(
    (c) => c.tokenStatus === 'EXPIRED' || c.tokenStatus === 'DISCONNECTED',
  )
  if (expired.length > 0) {
    return t('redesign.compose.blockExpired', { name: expired[0].channelName })
  }
  if (validationIssues.value.length > 0) return validationIssues.value[0]
  return ''
})

/** 파일 선택 — 숨은 input 을 열고, 고른 파일을 업로드 스토어에 등록한다. */
function pickFile() {
  fileInput.value?.click()
}

async function onFileChosen(e: Event) {
  const picked = (e.target as HTMLInputElement).files?.[0]
  if (!picked) return
  importedVideoId.value = null
  sourceMode.value = 'file'
  notice.value = ''
  await uploadStore.startUpload(picked)
  file.name = picked.name
  file.meta = `${formatBytes(picked.size)} · ${picked.type || 'video'}`
  if (!form.title) form.title = picked.name.replace(/\.[^.]+$/, '')

  try {
    // 파일 선택 시 원본을 먼저 보관해 두어 자동 문구를 게시 전에 편집할 수 있게 한다.
    const uploaded = await uploadStore.cloudPublish(
      picked,
      {
        title: form.title,
        description: form.description,
        tags: parseHashtags(form.hashtags),
        category: 'general',
        visibility: 'PUBLIC',
        thumbnailUrl: '',
      },
      [],
    )
    await generateMetadataFor(uploaded.videoId)
  } catch (error) {
    notice.value = error instanceof Error ? error.message : t('redesign.compose.scheduleFailed')
  }
}

/**
 * 자동 자막 — 업로드가 끝나 videoId 가 생긴 뒤에만 가능하다.
 * 아직 게시 전이라 videoId 가 없으면 사용자에게 순서를 알려준다.
 */
async function requestCaptions() {
  const id = uploadStore.videoId
  const sourceId = importedVideoId.value ?? id
  if (!sourceId) {
    notice.value = t('redesign.compose.captionsNeedUpload')
    return
  }
  captioning.value = true
  notice.value = ''
  try {
    const res = await aiApi.stt({ videoId: sourceId })
    await saveSubtitleTrack(sourceId, res.text, res.segments)
    if (res?.text && !form.description) form.description = res.text
    notice.value = t('redesign.compose.captionsDone')
  } catch {
    notice.value = t('redesign.compose.captionsFailed')
  } finally {
    captioning.value = false
  }
}

/** 기존 트랙은 보존하고, 트랙이 없을 때만 STT 결과를 시간축 자막으로 저장한다. */
async function transcriptFor(videoId: number): Promise<string> {
  const tracks = await subtitleEditorApi.listTracksByVideo(videoId)
  const existing = tracks.find((track) => track.status !== 'FAILED')
  if (existing) {
    const cues = parseCues(existing.cues)
    if (cues.length > 0) return cues.map((cue) => cue.text).join(' ')
  }

  const stt = await aiApi.stt({ videoId })
  if (stt.text && !existing) await saveSubtitleTrack(videoId, stt.text, stt.segments)
  return stt.text
}

async function saveSubtitleTrack(
  videoId: number,
  text: string,
  segments: { startTime: number; endTime: number; text: string }[],
) {
  if (!text || segments.length === 0) return
  const cues = segments.map((segment) => ({
    start: segment.startTime,
    end: segment.endTime,
    text: segment.text,
  }))
  const existing = await subtitleEditorApi.listTracksByVideo(videoId)
  if (existing.some((track) => track.status !== 'FAILED')) return
  await subtitleEditorApi.createTrack({
    videoId,
    language: 'ko',
    cues: serializeCues(cues),
    totalDuration: totalDurationOf(cues),
    wordCount: countWords(cues),
  })
}

/** 업로드 화면 안에서 자막/대본을 확보한 뒤 플랫폼별 메타데이터를 채운다. */
async function generateMetadataFor(videoId: number) {
  const platforms = selectedChannels.value.map((channel) => channel.platform)
  const targetPlatforms = [...new Set(platforms.length > 0 ? platforms : ['YOUTUBE' as Platform])]
  if (metadataGeneratedForVideoId.value !== videoId) {
    metadataGeneratedForVideoId.value = videoId
    metadataGeneratedPlatforms.value = []
  }
  const missingPlatforms = targetPlatforms.filter(
    (platform) => !metadataGeneratedPlatforms.value.includes(platform),
  )
  if (missingPlatforms.length === 0)
    return
  if (metadataGenerating.value) return
  const firstGeneration = metadataGeneratedPlatforms.value.length === 0
  metadataGenerating.value = true
  try {
    const script = await transcriptFor(videoId)
    const result = await aiApi.generateMeta({
      script: script || form.description || form.title,
      videoId,
      useStt: false,
      targetPlatforms: missingPlatforms,
      tone: 'friendly',
      category: 'general',
    })

    for (const item of result.platforms) {
      const platform = item.platform as Platform
      // A later channel selection must never erase copy the creator already edited.
      if (platformDraftDirty[platform]) continue
      const draft = draftFor(platform)
      // AI가 만든 채널별 문구는 공통 문구와 독립된 편집 초안으로 취급한다.
      platformDraftDirty[platform] = true
      draft.title = item.titleCandidates[0] || draft.title
      draft.description = item.description || draft.description
      draft.hashtags = item.hashtags.map((tag) => `#${tag.replace(/^#/, '')}`).join(' ')
    }
    const first = result.platforms[0]
    if (first && firstGeneration) {
      form.title = first.titleCandidates[0] || form.title
      form.description = first.description || form.description
      form.hashtags = first.hashtags.map((tag) => `#${tag.replace(/^#/, '')}`).join(' ')
    }
    metadataGeneratedPlatforms.value = [
      ...new Set([...metadataGeneratedPlatforms.value, ...missingPlatforms]),
    ]
    notice.value = t('redesign.compose.metadataGenerated')
  } finally {
    metadataGenerating.value = false
  }
}

/** 임시 저장 — 파일 업로드 없이 메타데이터만 영상 레코드로 남긴다. */
async function saveDraft() {
  if (!form.title.trim()) {
    notice.value = t('redesign.compose.draftNeedTitle')
    return
  }
  saving.value = true
  notice.value = ''
  try {
    const metadata = {
      title: form.title,
      description: form.description || undefined,
      tags: parseHashtags(form.hashtags),
      visibility: 'PUBLIC' as const,
      mediaType: 'VIDEO' as const,
    }
    if (importedVideoId.value) await videoApi.update(importedVideoId.value, metadata)
    else await videoApi.create(metadata)
    notice.value = t('redesign.compose.draftSaved')
  } catch {
    notice.value = t('redesign.compose.draftFailed')
  } finally {
    saving.value = false
  }
}

/**
 * 예약 — 파일 업로드와 플랫폼별 예약 게시를 한 번에 수행한다.
 * 예약 시각은 선택한 방식(now / best / fix)에 따라 플랫폼마다 계산한다.
 */
async function submit() {
  if (blockedReason.value || submitting.value) return
  const selected = uploadStore.file
  if (!selected && !importedVideoId.value) {
    notice.value = t('redesign.compose.needFile')
    return
  }

  submitting.value = true
  notice.value = ''
  let publishCompleted = false
  let recurringFailure = ''
  try {
    // 쇼츠를 선택한 상태에서 렌더러가 내려가 있으면 원본만 먼저 게시하지 않는다.
    // 그래야 사용자가 한 번의 작업으로 원본과 쇼츠가 함께 처리될 것이라는 기대를
    // 어기고 부분 성공을 재전송하는 상황을 예방할 수 있다.
    if (shortsEnabled.value && shortsPlatforms.value.length > 0) {
      const availability = await ugcShortsPipelineApi.getRenderAvailability()
      if (!availability.available) {
        throw new Error(availability.reason || t('redesign.compose.shortsUnavailable'))
      }
    }
    let sourceVideoId = importedVideoId.value ?? uploadStore.videoId
    if (!sourceVideoId && selected) {
      // 원본을 보관해야 쇼츠 렌더와 재시도에서 같은 영상 ID를 계속 사용할 수 있다.
      const uploaded = await uploadStore.cloudPublish(
        selected,
        {
          title: form.title || selected.name.replace(/\.[^.]+$/, ''),
          description: form.description,
          tags: parseHashtags(form.hashtags),
          category: 'general',
          visibility: 'PUBLIC',
          thumbnailUrl: '',
        },
        [],
      )
      sourceVideoId = uploaded.videoId
    }
    if (!sourceVideoId) throw new Error(t('redesign.compose.needFile'))

    try {
      await generateMetadataFor(sourceVideoId)
    } catch (error) {
      // AI 장애가 원본 게시 자체를 막지는 않는다. 사용자가 검토·입력한 문구로 계속 진행한다.
      if (!form.title.trim()) throw error
      notice.value = t('redesign.compose.metadataFailed')
    }
    await videoApi.update(sourceVideoId, {
      title: form.title,
      description: form.description,
      tags: parseHashtags(form.hashtags),
      category: 'general',
      visibility: 'PUBLIC',
      mediaType: 'VIDEO',
    })

    const configs: PlatformPublishConfig[] = selectedChannels.value.map((ch, index) => {
      const draft = draftFor(ch.platform)
      return {
        platform: ch.platform,
        title: draft.title,
        description: draft.description,
        tags: parseHashtags(draft.hashtags),
        visibility: 'PUBLIC' as const,
        scheduledAt: scheduledAtFor(index, ch.platform),
      }
    })

    await videoApi.publish(sourceVideoId, { platforms: configs })
    publishCompleted = true
    if (recurringEnabled.value) {
      try {
        await recurringApi.create({
          videoId: sourceVideoId,
          name: form.title || '반복 게시',
          frequency: recurringFrequency.value,
          dayOfWeek:
            recurringFrequency.value === 'WEEKLY' || recurringFrequency.value === 'BIWEEKLY'
              ? recurringDayOfWeek.value
              : undefined,
          dayOfMonth:
            recurringFrequency.value === 'MONTHLY' ? recurringDayOfMonth.value : undefined,
          timeOfDay: recurringTime.value,
          timezone: 'Asia/Seoul',
          platforms: [...new Set(selectedChannels.value.map((channel) => channel.platform))],
          titleTemplate: form.title,
          descriptionTemplate: form.description,
          tags: parseHashtags(form.hashtags),
          isActive: true,
        })
      } catch (error) {
        // 원본 게시 성공을 실패로 오인하게 만들거나 재전송을 유도하지 않는다.
        recurringFailure =
          error instanceof Error ? error.message : t('redesign.compose.recurringFailed')
      }
    }
    // Shorts is an optional follow-up. A Facebook/X-only post must still be
    // considered successful when no selected channel can host a vertical clip.
    if (shortsEnabled.value && shortsPlatforms.value.length > 0) {
      await publishAutomaticShorts(sourceVideoId)
    }
    if (recurringFailure) {
      notice.value = t('redesign.compose.partialActionFailed', { error: recurringFailure })
      return
    }
    notice.value = t('redesign.compose.scheduled')
    router.push('/today')
  } catch (e) {
    const detail =
      e instanceof Error
        ? e.message
        : uploadStore.uploadError || t('redesign.compose.scheduleFailed')
    notice.value = publishCompleted
      ? t('redesign.compose.partialActionFailed', { error: detail })
      : detail
  } finally {
    submitting.value = false
    shortsProcessing.value = false
  }
}

const wait = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))

async function waitForShortsState(
  workspaceId: number,
  runId: number,
  expected: PipelineRunDetailResponse['run']['status'],
): Promise<PipelineRunDetailResponse> {
  const deadline = Date.now() + 15 * 60 * 1000
  while (Date.now() < deadline) {
    const detail = await ugcShortsPipelineApi.get(workspaceId, runId)
    shortsStatus.value = detail.run.currentStage || detail.run.status
    if (detail.run.status === 'FAILED' || detail.run.status === 'CANCELLED') {
      throw new Error(detail.run.errorMessage || t('redesign.compose.shortsFailed'))
    }
    if (detail.run.status === expected) return detail
    await wait(2000)
  }
  throw new Error(t('redesign.compose.shortsTimedOut'))
}

async function waitForRender(workspaceId: number, runId: number, clipId: number): Promise<number> {
  const deadline = Date.now() + 15 * 60 * 1000
  while (Date.now() < deadline) {
    const status = await ugcShortsPipelineApi.getRenderStatus(workspaceId, runId, clipId)
    shortsStatus.value = `${t('redesign.compose.shortsRendering')} ${status.progress ?? 0}%`
    if (status.status === 'COMPLETED' && status.videoId != null) return status.videoId
    if (status.status === 'FAILED')
      throw new Error(status.failureReason || t('redesign.compose.shortsRenderFailed'))
    await wait(2000)
  }
  throw new Error(t('redesign.compose.shortsTimedOut'))
}

/** 쇼츠 생성 → 기본 후킹 선택 → 서버 렌더 → 선택 채널 예약 게시까지 한 번에 처리한다. */
async function publishAutomaticShorts(sourceVideoId: number) {
  if (shortsPlatforms.value.length === 0)
    throw new Error(t('redesign.compose.shortsNoCompatibleTargets'))
  const workspaceId = await workspaceStore.ensureActiveWorkspace()
  if (workspaceId == null) throw new Error(t('redesign.compose.shortsWorkspaceRequired'))

  shortsProcessing.value = true
  shortsStatus.value = t('redesign.compose.shortsStarting')
  const availability = await ugcShortsPipelineApi.getRenderAvailability()
  if (!availability.available)
    throw new Error(availability.reason || t('redesign.compose.shortsUnavailable'))

  const run = await ugcShortsPipelineApi.create(workspaceId, { sourceVideoId, templateId: null })
  let detail = await waitForShortsState(workspaceId, run.id, 'AWAITING_HOOK_SELECTION')
  const selections = detail.clips
    .filter((clip) => clip.status !== 'DISCARDED')
    .map((clip) => {
      const hook = clip.hooks.find((candidate) => candidate.variant === 'A') || clip.hooks[0]
      return hook ? { clipId: clip.id, variant: hook.variant } : null
    })
    .filter(
      (selection): selection is { clipId: number; variant: 'A' | 'B' | 'CUSTOM' } =>
        selection !== null,
    )
  if (selections.length === 0) throw new Error(t('redesign.compose.shortsNoClips'))

  await ugcShortsPipelineApi.selectHooks(workspaceId, run.id, { selections, discardClipIds: [] })
  detail = await waitForShortsState(workspaceId, run.id, 'AWAITING_SCHEDULE')

  const renderableClips = detail.clips.filter((clip) => clip.status !== 'DISCARDED')
  shortsStatus.value = t('redesign.compose.shortsRendering')
  await Promise.all(
    renderableClips.map(async (clip) => {
      await ugcShortsPipelineApi.startRender(workspaceId, run.id, clip.id)
      return waitForRender(workspaceId, run.id, clip.id)
    }),
  )

  const scheduledStart = scheduledAtFor(0, shortsPlatforms.value[0])
  const startAt = new Date(
    Math.max(
      Date.now() + 5 * 60 * 1000,
      scheduledStart ? kstWallClockToInstant(scheduledStart).getTime() : Date.now(),
    ),
  )
  await ugcShortsPipelineApi.confirmSchedule(workspaceId, run.id, {
    startAt: startAt.toISOString(),
    intervalHours: 2,
    platforms: shortsPlatforms.value,
  })
  await waitForShortsState(workspaceId, run.id, 'COMPLETED')
  shortsStatus.value = t('redesign.compose.shortsDone')
}

/** 채널별 예약 시각. 최적 모드는 서버 분석값을 사용하고, 이력이 없을 때만 일반 기본값을 쓴다. */
function scheduledAtFor(index: number, platform?: Platform): string | undefined {
  if (schedMode.value === 'now') return undefined
  if (schedMode.value === 'fix')
    return fixedAt.value ? fixedAt.value.slice(0, 16) : undefined

  const slots = platform ? optimalSlots.value[platform] ?? [] : []
  const slot = slots.length > 0 ? slots[index % slots.length] : fallbackOptimalSlot(index)
  return nextOptimalDateTime(new Date(), slot)
}

const parseHashtags = parsePublishHashtags

function formatBytes(n: number): string {
  if (n < 1024) return `${n}B`
  if (n < 1024 ** 2) return `${(n / 1024).toFixed(1)}KB`
  if (n < 1024 ** 3) return `${(n / 1024 ** 2).toFixed(1)}MB`
  return `${(n / 1024 ** 3).toFixed(1)}GB`
}

async function importFromUrl() {
  if (!importUrl.value || !importAvailable.value || importing.value) return
  importing.value = true
  notice.value = ''
  try {
    const result = await videoApi.importUrl({
      url: importUrl.value,
      title: form.title || undefined,
    })
    importedVideoId.value = result.videoId
    uploadStore.resetUpload()
    file.name = result.title
    file.meta = `${result.provider} · ${t('redesign.compose.imported')}`
    if (!form.title) form.title = result.title
    notice.value = t('redesign.compose.importedSuccess')
    // URL 가져오기는 즉시 videoId 를 얻으므로 같은 화면에서 메타데이터를 바로 준비한다.
    void generateMetadataFor(result.videoId).catch((error) => {
      notice.value = error instanceof Error ? error.message : t('redesign.compose.metadataFailed')
    })
  } catch (error) {
    notice.value = error instanceof Error ? error.message : t('redesign.compose.importFailed')
  } finally {
    importing.value = false
  }
}

function onKeydown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
    e.preventDefault()
    submit()
  }
}

onMounted(async () => {
  // 캘린더 빈 슬롯에서 넘어온 경우 해당 시각을 프리필한다
  const at = route.query.at
  if (typeof at === 'string' && at) {
    schedMode.value = 'fix'
    fixedAt.value = at
  }
  const templateId = Number(route.query.templateId)
  if (Number.isInteger(templateId) && templateId > 0) {
    try {
      const template = await templatesApi.get(templateId)
      form.title = template.titleTemplate ?? ''
      form.description = template.descriptionTemplate ?? ''
      form.hashtags = template.tags.join(' ')
      notice.value = '템플릿을 적용했습니다. 영상을 선택한 뒤 내용을 확인해 주세요.'
    } catch (error) {
      notice.value = error instanceof Error ? error.message : '템플릿을 불러오지 못했습니다.'
    }
  }
  window.addEventListener('keydown', onKeydown)
  try {
    // list() 는 { channels, maxAllowed, currentCount } 형태다
    channels.value = (await channelApi.list())?.channels ?? []
    // 분석은 선택적인 예약 보강이므로 채널·게시 조건 로딩을 막지 않는다.
    void loadOptimalTimes(channels.value)
  } catch (error) {
    channels.value = []
    dataLoadError.value =
      error instanceof Error ? error.message : t('redesign.compose.dataLoadFailed')
  }
  try {
    capabilities.value = await videoApi.getUploadCapabilities()
  } catch (error) {
    capabilities.value = []
    dataLoadError.value ||=
      error instanceof Error ? error.message : t('redesign.compose.dataLoadFailed')
  }
  try {
    importAvailability.value = await videoApi.getImportAvailability()
  } catch (error) {
    const message = error instanceof Error ? error.message : t('redesign.compose.dataLoadFailed')
    importAvailability.value = { available: false, reason: message }
    dataLoadError.value ||= message
  }
})

onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>
