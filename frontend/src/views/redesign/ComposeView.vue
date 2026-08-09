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

      <!-- 업로드/임포트 파일 -->
      <div
        v-if="sourceMode !== 'generate'"
        class="flex items-center gap-3.5 rounded-[11px] border border-line bg-surface-card p-3.5"
      >
        <ThumbPlaceholder :src="file.thumbnailUrl" :width="104" :height="62" />
        <div class="min-w-0 flex-1">
          <p class="truncate text-[13px] font-semibold text-content">
            {{ file.name || t('redesign.compose.noFile') }}
          </p>
          <p class="mt-1 font-mono text-[10.5px] text-content-tertiary">{{ fileMeta }}</p>
        </div>
        <div class="flex shrink-0 items-center gap-2">
          <button
            v-if="sourceMode === 'file'"
            type="button"
            class="btn-secondary !text-[11px]"
            @click="pickFile"
          >
            {{ t('redesign.compose.replace') }}
          </button>
          <button
            type="button"
            class="btn-secondary !text-[11px]"
            :disabled="captioning || (!importedVideoId && !uploadStore.videoId)"
            @click="requestCaptions"
          >
            {{ captioning ? t('redesign.compose.captioning') : t('redesign.compose.autoCaption') }}
          </button>
        </div>
        <input ref="fileInput" type="file" accept="video/*" class="hidden" @change="onFileChosen" />
      </div>

      <!-- 서버 영상 생성 -->
      <div v-else class="rounded-[11px] border border-line bg-surface-card p-3.5">
        <label for="generate-video-prompt" class="block text-[11.5px] font-semibold text-content-secondary">
          {{ t('redesign.compose.generatePromptLabel') }}
        </label>
        <textarea
          id="generate-video-prompt"
          v-model="generationPrompt"
          maxlength="2000"
          class="input-field mt-2 min-h-[100px] w-full !text-[12px]"
          :placeholder="t('redesign.compose.generatePromptPlaceholder')"
        />
        <div class="mt-2 flex items-end gap-2">
          <label class="min-w-0 flex-1 text-[11px] text-content-secondary">
            {{ t('redesign.compose.generateOutputLabel') }}
            <select v-model="generationOutput" class="input-field mt-1 w-full !text-[11px]">
              <option value="vertical">{{ t('redesign.compose.generateVertical') }}</option>
              <option value="horizontal">{{ t('redesign.compose.generateHorizontal') }}</option>
            </select>
          </label>
          <div class="flex shrink-0 items-center gap-2">
            <button
              type="button"
              class="btn-primary !text-[11px]"
              :disabled="generating || !generationPrompt.trim()"
              @click="generateVideo"
            >
              {{ generating ? t('redesign.compose.generating') : t('redesign.compose.generateAction') }}
            </button>
            <button
              v-if="importedVideoId"
              type="button"
              class="btn-secondary !text-[11px]"
              :disabled="captioning"
              @click="requestCaptions"
            >
              {{ captioning ? t('redesign.compose.captioning') : t('redesign.compose.autoCaption') }}
            </button>
          </div>
        </div>
        <p class="mt-2 text-[10.5px] leading-4 text-content-tertiary">
          {{ t('redesign.compose.generateHint') }}
        </p>
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
            :key="opt.channel.id"
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
        <div class="flex border-b border-line-row" role="tablist">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            type="button"
            role="tab"
            :aria-selected="activeTab === tab.key"
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
          <div
            v-if="activeTab !== 'common' && activePlatformChannels.length > 1"
            class="mb-3 rounded-lg border border-line-control bg-surface-input p-2.5"
          >
            <p class="mb-1.5 text-[10.5px] font-semibold text-content-secondary">게시 계정</p>
            <div class="flex flex-wrap gap-1.5">
              <button
                v-for="channel in activePlatformChannels"
                :key="channel.id"
                type="button"
                class="rounded-md px-2.5 py-1.5 text-[11px] transition-colors"
                :class="activeChannelId === channel.id ? 'bg-accent text-white' : 'bg-surface-card text-content-secondary hover:text-content'"
                :aria-pressed="activeChannelId === channel.id"
                @click="activeChannelId = channel.id"
              >
                {{ channel.channelName }}
              </button>
            </div>
          </div>
          <div class="flex items-center justify-between gap-2">
            <label for="compose-title" class="block text-[11.5px] text-content-secondary">{{
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
              id="compose-title"
              v-model="activeDraft.title"
              type="text"
              class="input-field !text-[12.5px]"
              :maxlength="titleLimit > 0 ? titleLimit : undefined"
              :aria-invalid="titleOver"
              @input="markDraftDirty('title')"
            />
            <span
              class="shrink-0 font-mono text-[10px]"
              :class="titleOver ? 'text-bad' : 'text-content-tertiary'"
            >
              {{ activeDraft.title.length }} / {{ titleLimit }}
            </span>
          </div>

          <label for="compose-description" class="mt-3.5 block text-[11.5px] text-content-secondary">
            {{ t('redesign.compose.description') }}
          </label>
          <textarea
            id="compose-description"
            v-model="activeDraft.description"
            class="input-field mt-1.5 min-h-[92px] !text-[12.5px]"
            :maxlength="descriptionLimit > 0 ? descriptionLimit : undefined"
            :aria-invalid="descriptionOver"
            @input="markDraftDirty('description')"
          />
          <div
            class="mt-1 text-right font-mono text-[10px]"
            :class="descriptionOver ? 'text-bad' : 'text-content-tertiary'"
          >
            {{ activeDraft.description.length }} / {{ descriptionLimit || '∞' }}
          </div>

          <label for="compose-hashtags" class="mt-3.5 block text-[11.5px] text-content-secondary">
            {{ t('redesign.compose.hashtags') }}
          </label>
          <textarea
            id="compose-hashtags"
            v-model="activeDraft.hashtags"
            class="input-field mt-1.5 min-h-[62px] !text-[12.5px] !text-accent"
            :aria-invalid="tagsOver"
            @input="markDraftDirty('hashtags')"
          />
          <div
            class="mt-1 text-right font-mono text-[10px]"
            :class="tagsOver ? 'text-bad' : 'text-content-tertiary'"
          >
            {{ hashtagCount }} / {{ tagLimit === null ? '∞' : tagLimit }}
          </div>

          <label for="compose-visibility" class="mt-3.5 block text-[11.5px] text-content-secondary">
            {{ t('settings.defaults.visibility') }}
          </label>
          <select
            id="compose-visibility"
            v-model="activeDraft.visibility"
            class="input-field mt-1.5 !text-[12px]"
            @change="markDraftDirty('visibility')"
          >
            <option value="PUBLIC">{{ t('settings.defaults.visibilityPublic') }}</option>
            <option value="UNLISTED">{{ t('settings.defaults.visibilityUnlisted') }}</option>
            <option value="PRIVATE">{{ t('settings.defaults.visibilityPrivate') }}</option>
          </select>

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
          :targets="previewTargets"
          :thumbnail="file.thumbnailUrl || undefined"
          :channel-names="previewChannelNames"
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
            :aria-pressed="schedMode === opt.key"
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
        id="compose-fixed-at"
        v-model="fixedAt"
        type="datetime-local"
        :aria-label="t('redesign.compose.fixedAtLabel')"
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
            <select
              v-model="recurringFrequency"
              class="input-field !text-[11.5px]"
              :aria-label="t('redesign.compose.recurringFrequencyLabel')"
            >
              <option value="DAILY">매일</option>
              <option value="WEEKLY">매주</option>
              <option value="BIWEEKLY">격주</option>
              <option value="MONTHLY">매월</option>
            </select>
            <input
              v-model="recurringTime"
              type="time"
              class="input-field !text-[11.5px]"
              :aria-label="t('redesign.compose.recurringTimeLabel')"
            />
          </div>
          <select
            v-if="recurringFrequency === 'WEEKLY' || recurringFrequency === 'BIWEEKLY'"
            v-model.number="recurringDayOfWeek"
            class="input-field !text-[11.5px]"
            :aria-label="t('redesign.compose.recurringDayOfWeekLabel')"
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
            :aria-label="t('redesign.compose.recurringDayOfMonthLabel')"
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
            :disabled="saving || uploadStore.isUploading"
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
import { settingsApi } from '@/api/settings'
import { templatesApi } from '@/api/templates'
import { ugcShortsPipelineApi, type PipelineRunDetailResponse } from '@/api/ugcShortsPipeline'
import { recurringApi, type RecurringFrequency } from '@/api/recurring'
import PlatformPreviewPanel from '@/components/preview/PlatformPreviewPanel.vue'
import { useUploadStore } from '@/stores/upload'
import { useWorkspaceStore } from '@/stores/workspace'
import type { Channel, Platform } from '@/types/channel'
import type { PlatformPublishConfig, PlatformUploadCapability, Visibility } from '@/types/video'
import {
  composePublishCaption,
  parsePublishHashtags,
  validatePublishDrafts,
} from '@/utils/publishValidation'
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

type ChipCode = 'YT' | 'IG' | 'TT' | 'FB' | 'NV' | 'TH' | 'TW' | 'PI' | 'LI' | 'WP' | 'DM' | 'VI' | 'TU'
const CHIP: Partial<Record<Platform, ChipCode>> = {
  YOUTUBE: 'YT',
  INSTAGRAM: 'IG',
  TIKTOK: 'TT',
  FACEBOOK: 'FB',
  NAVER_CLIP: 'NV',
  THREADS: 'TH',
  TWITTER: 'TW',
  PINTEREST: 'PI',
  LINKEDIN: 'LI',
  WORDPRESS: 'WP',
  DAILYMOTION: 'DM',
  VIMEO: 'VI',
  TUMBLR: 'TU',
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
  LI: { bg: '#0A66C2', fg: '#ffffff' },
  WP: { bg: '#21759B', fg: '#ffffff' },
  DM: { bg: '#00D2F3', fg: '#06141a' },
  VI: { bg: '#1AB7EA', fg: '#ffffff' },
  TU: { bg: '#36465D', fg: '#ffffff' },
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
const router = useRouter()
const uploadStore = useUploadStore()
const workspaceStore = useWorkspaceStore()

const fileInput = ref<HTMLInputElement | null>(null)
const sourceMode = ref<'file' | 'url' | 'generate'>('file')
const importUrl = ref('')
const importing = ref(false)
const generationPrompt = ref('')
const generationOutput = ref<'vertical' | 'horizontal'>('vertical')
const generating = ref(false)
const importedVideoId = ref<number | null>(null)
const importAvailability = ref<{ available: boolean; reason?: string | null } | null>(null)
const submitting = ref(false)
const saving = ref(false)
const captioning = ref(false)
const metadataGenerating = ref(false)
const metadataGeneratedForVideoId = ref<number | null>(null)
const metadataGeneratedPlatforms = ref<Platform[]>([])
const draftLoaded = ref(false)
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
type MetadataTab =
  | 'common'
  | 'YT'
  | 'IG'
  | 'TT'
  | 'FB'
  | 'NV'
  | 'TH'
  | 'TW'
  | 'PI'
  | 'LI'
  | 'WP'
  | 'DM'
  | 'VI'
  | 'TU'

const activeTab = ref<MetadataTab>('common')
const schedMode = ref<'now' | 'best' | 'fix'>('best')
const fixedAt = ref('')
const recurringEnabled = ref(false)
const recurringFrequency = ref<RecurringFrequency>('WEEKLY')
const recurringTime = ref('09:00')
const recurringDayOfWeek = ref(1)
const recurringDayOfMonth = ref(1)

type FormDraft = { title: string; description: string; hashtags: string; visibility: Visibility }
const form = reactive<FormDraft>({ title: '', description: '', hashtags: '', visibility: 'PUBLIC' })
const channelForms = reactive<Record<number, FormDraft>>({})
const channelDraftDirty = reactive<Record<number, boolean>>({})
const commonDraftDirty = reactive<Record<keyof FormDraft, boolean>>({
  title: false,
  description: false,
  hashtags: false,
  visibility: false,
})
const pendingPlatformForms = reactive<Partial<Record<Platform, FormDraft>>>({})
const pendingPlatformDirty = reactive<Partial<Record<Platform, boolean>>>({})
const file = reactive({ name: '', thumbnailUrl: null as string | null, meta: '' })

const sourceModes = computed(() => [
  { key: 'file' as const, label: t('redesign.compose.fileSourceUpload') },
  { key: 'url' as const, label: t('redesign.compose.fileSourceUrl') },
  { key: 'generate' as const, label: t('redesign.compose.fileSourceGenerate') },
])
const importAvailable = computed(() => importAvailability.value?.available === true)

function selectSourceMode(mode: 'file' | 'url' | 'generate') {
  if (sourceMode.value === mode) return
  sourceMode.value = mode
  // A source switch starts a new source-selection flow. Keeping the previous
  // server video ID here could publish the old video when the new source has
  // not been imported or generated yet.
  importedVideoId.value = null
  uploadStore.resetUpload()
  file.name = ''
  file.thumbnailUrl = null
  file.meta = ''
  importUrl.value = ''
}

const TAB_PLATFORM: Record<Exclude<MetadataTab, 'common'>, Platform> = {
  YT: 'YOUTUBE',
  IG: 'INSTAGRAM',
  TT: 'TIKTOK',
  FB: 'FACEBOOK',
  NV: 'NAVER_CLIP',
  TH: 'THREADS',
  TW: 'TWITTER',
  PI: 'PINTEREST',
  LI: 'LINKEDIN',
  WP: 'WORDPRESS',
  DM: 'DAILYMOTION',
  VI: 'VIMEO',
  TU: 'TUMBLR',
}

const allTabs = [
  { key: 'common' as const, label: t('redesign.compose.tabCommon') },
  { key: 'YT' as const, label: 'YouTube' },
  { key: 'IG' as const, label: 'Instagram' },
  { key: 'TT' as const, label: 'TikTok' },
  { key: 'FB' as const, label: 'Facebook' },
  { key: 'NV' as const, label: 'Naver' },
  { key: 'TH' as const, label: 'Threads' },
  { key: 'TW' as const, label: 'X (Twitter)' },
  { key: 'PI' as const, label: 'Pinterest' },
  { key: 'LI' as const, label: 'LinkedIn' },
  { key: 'WP' as const, label: 'WordPress.com' },
  { key: 'DM' as const, label: 'Dailymotion' },
  { key: 'VI' as const, label: 'Vimeo' },
  { key: 'TU' as const, label: 'Tumblr' },
]

const tabs = computed(() => {
  const selectedPlatforms = new Set(selectedChannels.value.map((channel) => channel.platform))
  return allTabs.filter(
    (tab) => tab.key === 'common' || selectedPlatforms.has(TAB_PLATFORM[tab.key]),
  )
})

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
const activeChannelId = ref<number | null>(null)
const activePlatformChannels = computed(() => {
  const platform = platformForTab(activeTab.value)
  return platform ? selectedChannels.value.filter((channel) => channel.platform === platform) : []
})
const activeChannel = computed(() => {
  const candidates = activePlatformChannels.value
  return candidates.find((channel) => channel.id === activeChannelId.value) ?? candidates[0] ?? null
})
const selectedCount = computed(() => selectedChannels.value.length)
const shortsPlatforms = computed(() =>
  selectedChannels.value
    .map((channel) => channel.platform)
    .filter((platform): platform is Platform =>
      ['YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'NAVER_CLIP'].includes(platform),
    ),
)
const shortsTargets = computed(() =>
  selectedChannels.value
    .filter((channel) => ['YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'NAVER_CLIP'].includes(channel.platform))
    .map((channel) => `${channel.platform}#${channel.id}`),
)

function platformForTab(tab: MetadataTab): Platform | null {
  return tab === 'common' ? null : TAB_PLATFORM[tab]
}

function draftForChannel(channel: Channel): FormDraft {
  if (!channelForms[channel.id]) {
    channelForms[channel.id] = reactive({
      ...(pendingPlatformForms[channel.platform] ?? form),
    })
    channelDraftDirty[channel.id] = pendingPlatformDirty[channel.platform] ?? false
  }
  return channelForms[channel.id]!
}

function draftFor(platform: Platform | null): FormDraft {
  if (!platform) return form
  const channel =
    activeChannel.value?.platform === platform
      ? activeChannel.value
      : selectedChannels.value.find((item) => item.platform === platform)
  return channel ? draftForChannel(channel) : (pendingPlatformForms[platform] ?? form)
}

/** 공통 문구는 채널별로 따로 편집하기 전까지 자동으로 따라간다. */
watch(
  () => [form.title, form.description, form.hashtags, form.visibility],
  () => {
    for (const channelId of Object.keys(channelForms).map(Number)) {
      if (channelDraftDirty[channelId]) continue
      const draft = channelForms[channelId]
      if (!draft) continue
      draft.title = form.title
      draft.description = form.description
      draft.hashtags = form.hashtags
      draft.visibility = form.visibility
    }
  },
)

function markDraftDirty(field: keyof FormDraft) {
  if (activeTab.value === 'common') {
    commonDraftDirty[field] = true
  } else if (activeChannel.value) {
    channelDraftDirty[activeChannel.value.id] = true
  }
}

const activeDraft = computed(() => draftFor(platformForTab(activeTab.value)))
const selectedPreviewPlatforms = computed(() => [
  ...new Set(selectedChannels.value.map((channel) => channel.platform)),
])

const previewTargets = computed(() =>
  selectedChannels.value.map((channel) => {
    const draft = draftForChannel(channel)
    return {
      key: `${channel.platform}#${channel.id}`,
      platform: channel.platform,
      channelName: channel.channelName,
      metadata: {
        title: draft.title,
        description: draft.description,
        tags: parseHashtags(draft.hashtags),
      },
    }
  }),
)

watch(
  [activePlatformChannels, activeTab],
  ([channelsForTab]) => {
    if (!channelsForTab.some((channel) => channel.id === activeChannelId.value)) {
      activeChannelId.value = channelsForTab[0]?.id ?? null
    }
  },
  { immediate: true },
)

watch(
  tabs,
  (visibleTabs) => {
    if (!visibleTabs.some((tab) => tab.key === activeTab.value)) activeTab.value = 'common'
  },
  { immediate: true },
)
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
/**
 * 미리보기는 플랫폼별 문구와 계정명을 함께 보여줘야 한다.
 * 같은 플랫폼 계정이 여러 개면 현재 편집 중인 계정을 우선하고,
 * 다른 플랫폼은 해당 플랫폼에서 선택된 첫 계정을 사용한다.
 */
const previewChannelNames = computed<Partial<Record<Platform, string>>>(() =>
  Object.fromEntries(
    selectedPreviewPlatforms.value.map((platform) => {
      const channel = activeChannel.value?.platform === platform
        ? activeChannel.value
        : selectedChannels.value.find((item) => item.platform === platform)
      return [platform, channel?.channelName ?? '']
    }),
  ) as Partial<Record<Platform, string>>,
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
const tagLimit = computed(() => activeCapability.value?.maxTagCount ?? null)
const captionLimit = computed(() => activeCapability.value?.maxCaptionLength ?? null)
const titleOver = computed(() => activeDraft.value.title.length > titleLimit.value)
const descriptionOver = computed(
  () => descriptionLimit.value > 0 && activeDraft.value.description.length > descriptionLimit.value,
)
const tagsOver = computed(() => {
  if (!activeCapability.value || tagLimit.value === null) return false
  return tagLimit.value === 0
    ? hashtagCount.value > 0
    : hashtagCount.value > tagLimit.value
})
const captionLength = computed(() => {
  const platform = platformForTab(activeTab.value)
  if (!platform) return null
  return composePublishCaption(platform, activeDraft.value)?.length ?? null
})
const captionOver = computed(
  () =>
    captionLimit.value !== null &&
    captionLength.value !== null &&
    captionLength.value > captionLimit.value,
)

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
    out.push(
      tagLimit.value === 0
        ? t('redesign.compose.warnTagsUnsupported')
        : t('redesign.compose.warnTagCount', { limit: tagLimit.value }),
    )
  }
  if (captionOver.value) {
    out.push(t('redesign.compose.warnCaptionLength', { limit: captionLimit.value }))
  }
  return out
})

const validationIssues = computed(() => {
  return validatePublishDrafts(
    selectedChannels.value.map((channel) => ({
      platform: channel.platform,
      channelName: channel.channelName,
      channelId: channel.id,
    })),
    capabilities.value,
    Object.fromEntries(
      selectedChannels.value.map((channel) => [String(channel.id), draftForChannel(channel)]),
    ),
  ).map((issue) => {
    const message =
      issue.field === 'title'
        ? t('redesign.compose.warnTitleLength', { limit: issue.limit })
        : issue.field === 'description'
          ? t('redesign.compose.warnDescriptionLength', { limit: issue.limit })
          : issue.field === 'caption'
            ? t('redesign.compose.warnCaptionLength', { limit: issue.limit })
          : t('redesign.compose.warnTagCount', { limit: issue.limit })
    return `${issue.channelName}: ${message}`
  })
})

function applyCommonToPlatforms() {
  for (const channel of selectedChannels.value) {
    const draft = draftForChannel(channel)
    draft.title = form.title
    draft.description = form.description
    draft.hashtags = form.hashtags
    draft.visibility = form.visibility
    channelDraftDirty[channel.id] = false
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
        visibility: form.visibility,
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
      const generatedDraft: FormDraft = {
        title: item.titleCandidates[0] || form.title,
        description: item.description || form.description,
        hashtags: item.hashtags.map((tag) => `#${tag.replace(/^#/, '')}`).join(' '),
        visibility: form.visibility,
      }
      // Keep the generated platform default for accounts connected after the
      // first generation pass. Otherwise a newly selected second account
      // silently falls back to the common copy instead of the platform copy.
      if (!pendingPlatformDirty[platform]) {
        pendingPlatformForms[platform] = reactive({ ...generatedDraft })
        pendingPlatformDirty[platform] = true
      }
      // A later channel selection must never erase copy the creator already edited.
      for (const channel of selectedChannels.value.filter((candidate) => candidate.platform === platform)) {
        const draft = draftForChannel(channel)
        if (channelDraftDirty[channel.id]) continue
        // AI가 만든 채널별 문구는 공통 문구와 독립된 편집 초안으로 취급한다.
        channelDraftDirty[channel.id] = true
        if (!commonDraftDirty.title) draft.title = generatedDraft.title
        if (!commonDraftDirty.description) draft.description = generatedDraft.description
        if (!commonDraftDirty.hashtags) draft.hashtags = generatedDraft.hashtags
      }
    }
    const first = result.platforms[0]
    if (first && firstGeneration) {
      if (!commonDraftDirty.title) form.title = first.titleCandidates[0] || form.title
      if (!commonDraftDirty.description) form.description = first.description || form.description
      if (!commonDraftDirty.hashtags) {
        form.hashtags = first.hashtags.map((tag) => `#${tag.replace(/^#/, '')}`).join(' ')
      }
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
  if (uploadStore.isUploading) {
    notice.value = t('redesign.compose.uploadInProgress')
    return
  }
  saving.value = true
  notice.value = ''
  try {
    const metadata = {
      title: form.title,
      description: form.description || undefined,
      tags: parseHashtags(form.hashtags),
      visibility: form.visibility,
      mediaType: 'VIDEO' as const,
    }
    // Keep the exact per-platform copy in the same save operation. The
    // backend stores these as editable DRAFT rows and never publishes them.
    const platforms = selectedChannels.value.map((channel) => {
      const draft = draftForChannel(channel)
      return {
        platform: channel.platform,
        channelId: channel.id,
        title: draft.title,
        description: draft.description || undefined,
        tags: parseHashtags(draft.hashtags),
        visibility: draft.visibility,
      }
    })
    // File selection/presigned upload creates the server video before the user
    // presses Save draft. Reuse that row instead of creating an orphan draft.
    const existingVideoId = importedVideoId.value ?? uploadStore.videoId
    if (existingVideoId) await videoApi.update(existingVideoId, { ...metadata, platforms })
    else {
      const created = await videoApi.create(metadata)
      // Keep the server-created draft attached to this compose session so a
      // later save/edit does not create a second orphan draft.
      importedVideoId.value = created.id
      await videoApi.update(created.id, { ...metadata, platforms })
    }
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
  let shortsFailure = ''
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
          visibility: form.visibility,
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
      visibility: form.visibility,
      mediaType: 'VIDEO',
      platforms: selectedChannels.value.map((channel) => {
        const draft = draftForChannel(channel)
        return {
          platform: channel.platform,
          channelId: channel.id,
          title: draft.title,
          description: draft.description || undefined,
          tags: parseHashtags(draft.hashtags),
          visibility: draft.visibility,
        }
      }),
    })

    const configs: PlatformPublishConfig[] = selectedChannels.value.map((ch, index) => {
      const draft = draftForChannel(ch)
      return {
        platform: ch.platform,
        channelId: ch.id,
        title: draft.title,
        description: draft.description,
        tags: parseHashtags(draft.hashtags),
        visibility: draft.visibility,
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
          // Recurring executions must retain the exact connected account;
          // platform-only values would silently fall back to the first account.
          platforms: selectedChannels.value.map((channel) => `${channel.platform}#${channel.id}`),
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
      try {
        await publishAutomaticShorts(sourceVideoId)
      } catch (error) {
        // 원본 게시와 반복 게시가 끝난 뒤의 쇼츠 파이프라인 장애도
        // 이미 성공한 작업을 실패로 오인하게 만들거나 재전송하게 하지 않는다.
        shortsFailure = error instanceof Error ? error.message : t('redesign.compose.shortsFailed')
      }
    }
    const followUpFailures = [recurringFailure, shortsFailure].filter(Boolean)
    if (followUpFailures.length > 0) {
      notice.value = t('redesign.compose.partialActionFailed', {
        error: followUpFailures.join(' · '),
      })
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
    if (
      detail.run.status === 'FAILED' ||
      detail.run.status === 'PARTIALLY_COMPLETED' ||
      detail.run.status === 'CANCELLED'
    ) {
      throw new Error(detail.run.errorMessage || t('redesign.compose.shortsFailed'))
    }
    if (detail.run.status === expected) return detail
    await wait(2000)
  }
  throw new Error(t('redesign.compose.shortsTimedOut'))
}

/** 쇼츠 생성만 요청한다. 후킹·렌더·예약 게시의 나머지는 서버 워커가 이어서 처리한다. */
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

  const scheduledStart = scheduledAtFor(0, shortsPlatforms.value[0])
  const startAt = new Date(
    Math.max(
      Date.now() + 5 * 60 * 1000,
      scheduledStart ? kstWallClockToInstant(scheduledStart).getTime() : Date.now(),
    ),
  )
  const run = await ugcShortsPipelineApi.create(workspaceId, {
    sourceVideoId,
    templateId: null,
    autoSchedule: true,
    scheduleStartAt: startAt.toISOString(),
    scheduleIntervalHours: 2,
    platforms: shortsTargets.value,
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

async function generateVideo() {
  const prompt = generationPrompt.value.trim()
  if (!prompt || generating.value) return
  generating.value = true
  notice.value = ''
  try {
    const result = await videoApi.generate({
      type: 'image-text-slides',
      output: generationOutput.value,
      customParams: {
        prompt,
        title: form.title || prompt.split(/\r?\n/, 1)[0].slice(0, 100),
        tags: parseHashtags(form.hashtags),
      },
    })
    const generated = result[0]
    if (!generated?.id) throw new Error(t('redesign.compose.generateFailed'))
    const videoId = Number(generated.id)
    if (!Number.isSafeInteger(videoId) || videoId <= 0) {
      throw new Error(t('redesign.compose.generateFailed'))
    }
    importedVideoId.value = videoId
    uploadStore.resetUpload()
    uploadStore.videoId = videoId
    file.name = `generated-${videoId}.mp4`
    file.meta = generationOutput.value === 'vertical' ? '8초 · 9:16' : '8초 · 16:9'
    if (!form.title) form.title = prompt.split(/\r?\n/, 1)[0].slice(0, 100)
    notice.value = t('redesign.compose.generateSuccess')
    await generateMetadataFor(videoId)
  } catch (error) {
    notice.value = error instanceof Error ? error.message : t('redesign.compose.generateFailed')
  } finally {
    generating.value = false
  }
}

async function loadDraft(videoId: number) {
  const video = await videoApi.get(videoId)
  importedVideoId.value = videoId
  uploadStore.videoId = videoId
  form.title = video.title
  form.description = video.description ?? ''
  form.hashtags = video.tags.join(' ')
  form.visibility = video.visibility
  // A loaded draft is an explicit creator decision. Never replace its common
  // copy with a fresh AI suggestion during the publish preflight.
  commonDraftDirty.title = form.title.trim().length > 0
  commonDraftDirty.description = form.description.trim().length > 0
  commonDraftDirty.hashtags = form.hashtags.trim().length > 0
  commonDraftDirty.visibility = true
  file.name = video.title
  for (const upload of video.uploads ?? []) {
    const meta = upload.meta
    if (!meta) continue
    const draft = reactive({
      title: meta.title ?? video.title,
      description: meta.description ?? '',
      hashtags: meta.tags.join(' '),
      visibility: meta.visibility ?? video.visibility,
    })
    if (upload.channelId != null) {
      channelForms[upload.channelId] = draft
      // A persisted override must stay independent when the common copy changes.
      channelDraftDirty[upload.channelId] = true
    } else {
      // Legacy drafts did not persist a channel id. Keep them available until
      // the channel list arrives, then clone them into the matching accounts.
      pendingPlatformForms[upload.platform] = draft
      pendingPlatformDirty[upload.platform] = true
    }
  }
  draftLoaded.value = true
  notice.value = '저장된 초안을 불러왔습니다. 내용을 확인한 뒤 게시하세요.'
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
  const draftId = Number(route.query.videoId)
  if (Number.isInteger(draftId) && draftId > 0) {
    try {
      await loadDraft(draftId)
    } catch (error) {
      notice.value = error instanceof Error ? error.message : '초안을 불러오지 못했습니다.'
    }
  }
  const templateId = Number(route.query.templateId)
  if ((!Number.isInteger(draftId) || draftId <= 0) && Number.isInteger(templateId) && templateId > 0) {
    try {
      const template = await templatesApi.get(templateId)
      form.title = template.titleTemplate ?? ''
      form.description = template.descriptionTemplate ?? ''
      form.hashtags = template.tags.join(' ')
      commonDraftDirty.title = form.title.trim().length > 0
      commonDraftDirty.description = form.description.trim().length > 0
      commonDraftDirty.hashtags = form.hashtags.trim().length > 0
      notice.value = '템플릿을 적용했습니다. 영상을 선택한 뒤 내용을 확인해 주세요.'
    } catch (error) {
      notice.value = error instanceof Error ? error.message : '템플릿을 불러오지 못했습니다.'
    }
  }
  window.addEventListener('keydown', onKeydown)
  try {
    const settings = await settingsApi.getSettings()
    if (!draftLoaded.value && ['PUBLIC', 'PRIVATE', 'UNLISTED'].includes(settings.defaultVisibility)) {
      form.visibility = settings.defaultVisibility as Visibility
    }
  } catch (error) {
    // 공개 범위 기본값은 기존 PUBLIC fallback을 사용하되, 설정 장애는 사용자에게 알린다.
    // 설정 조회 실패만으로 이미 입력한 게시 작업 전체를 막지는 않는다.
    notice.value = error instanceof Error ? error.message : t('redesign.compose.dataLoadFailed')
  }
  try {
    // list() 는 { channels, maxAllowed, currentCount } 형태다
    channels.value = (await channelApi.list())?.channels ?? []
    if (Number.isInteger(draftId) && draftId > 0) {
      // A legacy platform-only draft is copied to each matching account. New
      // drafts remain scoped to their persisted channel id.
      for (const channel of channels.value) {
        const legacy = pendingPlatformForms[channel.platform]
        if (legacy && !channelForms[channel.id]) {
          channelForms[channel.id] = reactive({ ...legacy })
          channelDraftDirty[channel.id] = pendingPlatformDirty[channel.platform] ?? true
        }
      }
      const savedChannelIds = new Set(Object.keys(channelForms).map(Number))
      const savedPlatforms = new Set(Object.keys(pendingPlatformForms))
      channels.value.forEach((channel) => {
        const hasSavedDraft =
          savedChannelIds.has(channel.id) || savedPlatforms.has(channel.platform)
        if (savedChannelIds.size > 0 || savedPlatforms.size > 0) {
          disabled[channel.id] = !hasSavedDraft
        }
      })
    }
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
