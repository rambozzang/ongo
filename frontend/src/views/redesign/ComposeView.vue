<template>
  <div class="grid h-full" style="grid-template-columns: minmax(0, 1fr) 372px">
    <!-- 좌: 입력 -->
    <div class="overflow-y-auto border-r border-line px-5 pb-[120px] pt-[18px] scrollbar-dark">
      <!-- 입력 소스 -->
      <div class="mb-2.5 flex items-center gap-1 rounded-lg border border-line bg-surface-input p-1">
        <button
          v-for="mode in sourceModes"
          :key="mode.key"
          type="button"
          class="flex-1 rounded-md px-3 py-1.5 text-[11px] font-semibold transition-colors"
          :class="sourceMode === mode.key ? 'bg-surface-card text-content shadow-sm' : 'text-content-tertiary hover:text-content'"
          @click="selectSourceMode(mode.key)"
        >
          {{ mode.label }}
        </button>
      </div>

      <!-- 파일 -->
      <div class="flex items-center gap-3.5 rounded-[11px] border border-line bg-surface-card p-3.5">
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
        <input
          ref="fileInput"
          type="file"
          accept="video/*"
          class="hidden"
          @change="onFileChosen"
        />
      </div>

      <div v-if="sourceMode === 'url'" class="mt-2 rounded-[11px] border border-line bg-surface-card p-3.5">
        <label class="block text-[11.5px] font-semibold text-content-secondary" for="source-video-url">
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
            {{ importing ? t('redesign.compose.importing') : t('redesign.compose.importUrlAction') }}
          </button>
        </div>
        <p v-if="importAvailability?.available === false" class="mt-2 text-[11px] text-warn">
          {{ importAvailability.reason || t('redesign.compose.importUnavailable') }}
        </p>
        <p v-else class="mt-2 text-[10.5px] text-content-tertiary">
          {{ t('redesign.compose.importUrlHint') }}
        </p>
      </div>

      <!-- 업로드 진행 — 파일 카드 안에 텍스트를 병기한다 -->
      <div v-if="uploadStore.isUploading" class="mt-2">
        <div class="h-1 overflow-hidden rounded-full bg-line">
          <div class="h-full bg-accent transition-[width]" :style="{ width: `${uploadStore.progress.percentage}%` }" />
        </div>
        <p class="mt-1 font-mono text-[10.5px] text-content-tertiary">
          {{ t('redesign.compose.uploading', { percent: uploadStore.progress.percentage }) }}
        </p>
      </div>

      <!-- 발행 대상 -->
      <section class="mt-[18px]">
        <h2 class="text-[12px] font-bold text-content">{{ t('redesign.compose.targetsTitle') }}</h2>
        <p class="mt-1 text-[11px] text-content-tertiary">{{ t('redesign.compose.targetsHint') }}</p>
        <div class="mt-2.5 flex flex-wrap gap-2">
          <button
            v-for="opt in platformOptions"
            :key="opt.code"
            type="button"
            class="flex items-center gap-2 rounded-md border px-2.5 py-2 transition-colors"
            :class="
              isOn(opt.code)
                ? 'border-line-hover'
                : 'border-line bg-transparent text-content-tertiary hover:border-line-hover'
            "
            :style="isOn(opt.code) ? onChipStyle(opt.code) : undefined"
            :aria-pressed="isOn(opt.code)"
            @click="toggle(opt.code)"
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
          <label class="block text-[11.5px] text-content-secondary">{{ t('redesign.compose.title') }}</label>
          <div class="mt-1.5 flex items-center gap-2">
            <input v-model="form.title" type="text" class="input-field !text-[12.5px]" />
            <span class="shrink-0 font-mono text-[10px]" :class="titleOver ? 'text-bad' : 'text-content-tertiary'">
              {{ form.title.length }} / {{ titleLimit }}
            </span>
          </div>

          <label class="mt-3.5 block text-[11.5px] text-content-secondary">
            {{ t('redesign.compose.description') }}
          </label>
          <textarea v-model="form.description" class="input-field mt-1.5 min-h-[92px] !text-[12.5px]" />

          <label class="mt-3.5 block text-[11.5px] text-content-secondary">
            {{ t('redesign.compose.hashtags') }}
          </label>
          <textarea v-model="form.hashtags" class="input-field mt-1.5 min-h-[62px] !text-[12.5px] !text-accent" />

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

      <div class="mt-2.5 flex gap-1">
        <button
          v-for="p in previewTabs"
          :key="p"
          type="button"
          class="rounded-md border px-2.5 py-1.5 text-[11px] transition-colors"
          :class="
            previewPlatform === p
              ? 'border-line-hover bg-surface-raised text-content'
              : 'border-transparent text-content-tertiary hover:text-content'
          "
          @click="previewPlatform = p"
        >
          {{ p }}
        </button>
      </div>

      <div class="mt-3 overflow-hidden rounded-[11px] border border-line" style="aspect-ratio: 9 / 16">
        <div class="relative h-full w-full" :style="PLACEHOLDER">
          <div class="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/80 to-transparent p-3">
            <p class="line-clamp-2 text-[12.5px] font-bold text-white">
              {{ form.title || t('redesign.compose.previewTitleEmpty') }}
            </p>
            <p v-if="form.hashtags" class="mt-1 line-clamp-1 text-[10.5px] text-accent">{{ form.hashtags }}</p>
          </div>
        </div>
      </div>

      <!-- 발행 예약 -->
      <h2 class="mt-[18px] text-[12px] font-bold text-content">{{ t('redesign.compose.scheduleTitle') }}</h2>
      <div class="mt-2.5 space-y-2">
        <button
          v-for="opt in scheduleOptions"
          :key="opt.key"
          type="button"
          class="flex w-full items-start gap-2.5 rounded-lg border px-3 py-2.5 text-left transition-colors"
          :class="schedMode === opt.key ? 'border-accent bg-accent-dim' : 'border-line-control hover:border-line-hover'"
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
            <span v-if="opt.hint" class="mt-0.5 block font-mono text-[10.5px] text-content-tertiary">
              {{ opt.hint }}
            </span>
          </span>
        </button>
      </div>

      <input v-if="schedMode === 'fix'" v-model="fixedAt" type="datetime-local" class="input-field mt-2 !text-[12px]" />

      <!-- 액션 -->
      <div class="mt-auto pt-[18px]">
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
            :disabled="!!blockedReason || submitting"
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
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLocale } from '@/composables/useLocale'
import ThumbPlaceholder from '@/components/redesign/ThumbPlaceholder.vue'
import { aiApi } from '@/api/ai'
import { channelApi } from '@/api/channel'
import { videoApi } from '@/api/video'
import { useUploadStore } from '@/stores/upload'
import type { Channel, Platform } from '@/types/channel'
import type { PlatformPublishConfig } from '@/types/video'

/**
 * 새 업로드 — 파일 → 대상 → 문구 → 예약을 화면 이동 없이 한 번에.
 *
 * 규칙 경고(플랫폼별 제목 길이·해시태그 상한)는 발행 시점이 아니라 입력 시점에 띄운다.
 * 만료된 채널이 대상에 포함되면 예약 버튼을 막고 사유를 보여준다.
 */
const { t } = useLocale()
const route = useRoute()

const PLACEHOLDER = {
  background: 'repeating-linear-gradient(135deg,#262a41 0 6px,#2d3149 6px 12px)',
}

type ChipCode = 'YT' | 'IG' | 'TT' | 'FB' | 'NV' | 'TH'
const CHIP: Partial<Record<Platform, ChipCode>> = {
  YOUTUBE: 'YT',
  INSTAGRAM: 'IG',
  TIKTOK: 'TT',
  FACEBOOK: 'FB',
  NAVER_CLIP: 'NV',
  THREADS: 'TH',
}

const CHIP_VARS: Record<ChipCode, { bg: string; fg: string }> = {
  YT: { bg: 'var(--platform-yt-bg)', fg: 'var(--platform-yt-fg)' },
  IG: { bg: 'var(--platform-ig-bg)', fg: 'var(--platform-ig-fg)' },
  TT: { bg: 'var(--platform-tt-bg)', fg: 'var(--platform-tt-fg)' },
  FB: { bg: 'var(--platform-fb-bg)', fg: 'var(--platform-fb-fg)' },
  NV: { bg: 'var(--platform-nv-bg)', fg: 'var(--platform-nv-fg)' },
  TH: { bg: 'var(--platform-th-bg)', fg: 'var(--platform-th-fg)' },
}

/** 플랫폼별 제목 상한. 핸드오프 검증 규칙. */
const TITLE_LIMIT: Record<string, number> = { common: 100, YT: 100, IG: 2200, TT: 150, NV: 100 }
/** TikTok 은 해시태그 5개까지만 반영된다. */
const TIKTOK_HASHTAG_LIMIT = 5

const router = useRouter()
const uploadStore = useUploadStore()

const fileInput = ref<HTMLInputElement | null>(null)
const sourceMode = ref<'file' | 'url'>('file')
const importUrl = ref('')
const importing = ref(false)
const importedVideoId = ref<number | null>(null)
const importAvailability = ref<{ available: boolean; reason?: string | null } | null>(null)
const submitting = ref(false)
const saving = ref(false)
const captioning = ref(false)
/** 사용자에게 보여줄 한 줄 안내(성공·실패 공용). */
const notice = ref('')

const channels = ref<Channel[]>([])
const disabled = reactive<Record<string, boolean>>({})
const activeTab = ref<'common' | 'YT' | 'IG' | 'TT' | 'NV'>('common')
const previewPlatform = ref<'Instagram' | 'TikTok' | 'YouTube'>('Instagram')
const schedMode = ref<'now' | 'best' | 'fix'>('best')
const fixedAt = ref('')

const form = reactive({ title: '', description: '', hashtags: '' })
const file = reactive({ name: '', thumbnailUrl: null as string | null, meta: '' })

const sourceModes = computed(() => [
  { key: 'file' as const, label: t('redesign.compose.fileSourceUpload') },
  { key: 'url' as const, label: t('redesign.compose.fileSourceUrl') },
])
const importAvailable = computed(() => importAvailability.value?.available !== false)

function selectSourceMode(mode: 'file' | 'url') {
  sourceMode.value = mode
  if (mode === 'file') importedVideoId.value = null
}

const tabs = [
  { key: 'common' as const, label: t('redesign.compose.tabCommon') },
  { key: 'YT' as const, label: 'YouTube' },
  { key: 'IG' as const, label: 'Instagram' },
  { key: 'TT' as const, label: 'TikTok' },
  { key: 'NV' as const, label: 'Naver' },
]
const previewTabs = ['Instagram', 'TikTok', 'YouTube'] as const

const scheduleOptions = computed(() => [
  { key: 'now' as const, label: t('redesign.compose.schedNow'), hint: '' },
  { key: 'best' as const, label: t('redesign.compose.schedBest'), hint: '09:00 · 12:30 · 19:00' },
  { key: 'fix' as const, label: t('redesign.compose.schedFixed'), hint: fixedAt.value },
])

const platformOptions = computed(() =>
  channels.value.map((ch) => ({
    code: CHIP[ch.platform] ?? 'TH',
    label: ch.channelName,
    handle: '',
    channel: ch,
  })),
)

const isOn = (code: string) => !disabled[code]
const toggle = (code: string) => {
  disabled[code] = !disabled[code]
}
const onChipStyle = (code: string) => {
  const v = CHIP_VARS[code as ChipCode] ?? CHIP_VARS.TH
  return { background: v.bg, color: v.fg }
}

const selectedChannels = computed(() =>
  platformOptions.value.filter((o) => isOn(o.code)).map((o) => o.channel),
)
const selectedCount = computed(() => selectedChannels.value.length)

const titleLimit = computed(() => TITLE_LIMIT[activeTab.value] ?? 100)
const titleOver = computed(() => form.title.length > titleLimit.value)

const fileMeta = computed(() => file.meta || t('redesign.compose.noFileMeta'))

const hashtagCount = computed(() => form.hashtags.split('#').filter((s) => s.trim()).length)

const warnings = computed(() => {
  const out: string[] = []
  if (titleOver.value) {
    out.push(t('redesign.compose.warnTitleLength', { limit: titleLimit.value }))
  }
  const hasTikTok = selectedChannels.value.some((c) => c.platform === 'TIKTOK')
  if (hasTikTok && hashtagCount.value > TIKTOK_HASHTAG_LIMIT) {
    out.push(t('redesign.compose.warnTiktokHashtags', { limit: TIKTOK_HASHTAG_LIMIT }))
  }
  return out
})

/** 만료 채널이 대상에 포함되면 예약을 막는다. */
const blockedReason = computed(() => {
  if (selectedCount.value === 0) return t('redesign.compose.blockNoTarget')
  const expired = selectedChannels.value.filter(
    (c) => c.tokenStatus === 'EXPIRED' || c.tokenStatus === 'DISCONNECTED',
  )
  if (expired.length > 0) {
    return t('redesign.compose.blockExpired', { name: expired[0].channelName })
  }
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
    if (res?.text && !form.description) form.description = res.text
    notice.value = t('redesign.compose.captionsDone')
  } catch {
    notice.value = t('redesign.compose.captionsFailed')
  } finally {
    captioning.value = false
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
  try {
    const configs: PlatformPublishConfig[] = selectedChannels.value.map((ch, index) => ({
      platform: ch.platform,
      title: form.title,
      description: [form.description, form.hashtags].filter(Boolean).join('\n\n'),
      tags: parseHashtags(form.hashtags),
      visibility: 'PUBLIC' as const,
      scheduledAt: scheduledAtFor(index),
    }))

    if (importedVideoId.value) {
      await videoApi.publish(importedVideoId.value, { platforms: configs })
    } else if (selected) {
      await uploadStore.streamPublish(
        selected,
        {
          title: form.title,
          description: form.description,
          tags: parseHashtags(form.hashtags),
          category: '',
          visibility: 'PUBLIC',
          thumbnailUrl: '',
        },
        configs,
      )
    }
    notice.value = t('redesign.compose.scheduled')
    router.push('/today')
  } catch (e) {
    notice.value = uploadStore.uploadError || t('redesign.compose.scheduleFailed')
  } finally {
    submitting.value = false
  }
}

/** 채널별 예약 시각. 최적 시간은 09:00·12:30·19:00 슬롯을 순서대로 돌려 쓴다. */
function scheduledAtFor(index: number): string | undefined {
  if (schedMode.value === 'now') return undefined
  if (schedMode.value === 'fix') return fixedAt.value ? new Date(fixedAt.value).toISOString() : undefined

  const slots = [
    [9, 0],
    [12, 30],
    [19, 0],
  ]
  const [h, m] = slots[index % slots.length]
  const at = new Date()
  at.setHours(h, m, 0, 0)
  if (at.getTime() <= Date.now()) at.setDate(at.getDate() + 1)
  return at.toISOString()
}

function parseHashtags(raw: string): string[] {
  return raw
    .split(/[\s,]+/)
    .map((s) => s.replace(/^#/, '').trim())
    .filter(Boolean)
}

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
    const result = await videoApi.importUrl({ url: importUrl.value, title: form.title || undefined })
    importedVideoId.value = result.videoId
    uploadStore.resetUpload()
    file.name = result.title
    file.meta = `${result.provider} · ${t('redesign.compose.imported')}`
    if (!form.title) form.title = result.title
    notice.value = t('redesign.compose.importedSuccess')
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
  window.addEventListener('keydown', onKeydown)
  try {
    // list() 는 { channels, maxAllowed, currentCount } 형태다
    channels.value = (await channelApi.list())?.channels ?? []
  } catch {
    channels.value = []
  }
  try {
    importAvailability.value = await videoApi.getImportAvailability()
  } catch {
    importAvailability.value = null
  }
})

onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>
