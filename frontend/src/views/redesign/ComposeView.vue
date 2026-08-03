<template>
  <div class="grid h-full" style="grid-template-columns: minmax(0, 1fr) 372px">
    <!-- 좌: 입력 -->
    <div class="overflow-y-auto border-r border-line px-5 pb-[120px] pt-[18px] scrollbar-dark">
      <!-- 파일 -->
      <div class="flex items-center gap-3.5 rounded-[11px] border border-line bg-surface-card p-3.5">
        <ThumbPlaceholder :src="file.thumbnailUrl" :width="104" :height="62" />
        <div class="min-w-0 flex-1">
          <p class="truncate text-[13px] font-semibold text-content">
            {{ file.name || t('redesign.compose.noFile') }}
          </p>
          <p class="mt-1 font-mono text-[10.5px] text-content-tertiary">{{ fileMeta }}</p>
        </div>
        <div class="flex shrink-0 items-center gap-2">
          <button type="button" class="btn-secondary !text-[11px]" @click="pickFile">
            {{ t('redesign.compose.replace') }}
          </button>
          <button type="button" class="btn-secondary !text-[11px]" @click="requestCaptions">
            {{ t('redesign.compose.autoCaption') }}
          </button>
        </div>
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
        <div class="flex gap-2">
          <button type="button" class="btn-secondary flex-1 !text-[12px]" @click="saveDraft">
            {{ t('redesign.compose.saveDraft') }}
          </button>
          <button
            type="button"
            class="btn-primary !text-[12px]"
            style="flex: 1.4"
            :disabled="!!blockedReason"
            @click="submit"
          >
            {{ t('redesign.compose.schedule', { count: selectedCount }) }}
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
import { useRoute } from 'vue-router'
import { useLocale } from '@/composables/useLocale'
import ThumbPlaceholder from '@/components/redesign/ThumbPlaceholder.vue'
import { channelApi } from '@/api/channel'
import type { Channel, Platform } from '@/types/channel'

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

const channels = ref<Channel[]>([])
const disabled = reactive<Record<string, boolean>>({})
const activeTab = ref<'common' | 'YT' | 'IG' | 'TT' | 'NV'>('common')
const previewPlatform = ref<'Instagram' | 'TikTok' | 'YouTube'>('Instagram')
const schedMode = ref<'now' | 'best' | 'fix'>('best')
const fixedAt = ref('')

const form = reactive({ title: '', description: '', hashtags: '' })
const file = reactive({ name: '', thumbnailUrl: null as string | null, meta: '' })

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

function pickFile() {
  /* 업로드 흐름은 기존 Tus 업로더로 연결한다 */
}
function requestCaptions() {
  /* 자동 자막 요청 */
}
function saveDraft() {
  /* 임시 저장 */
}
function submit() {
  if (blockedReason.value) return
  /* 예약 생성 */
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
})

onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>
