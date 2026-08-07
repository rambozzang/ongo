<template>
  <div class="min-h-full space-y-5 py-5 text-content">
    <router-link
      to="/ugc/shorts/runs"
      class="mb-4 inline-flex items-center gap-1 text-body text-gray-500 hover:text-primary-600 dark:text-gray-400 dark:hover:text-primary-400"
    >
      <ArrowLeftIcon class="h-4 w-4" />
      {{ $t('ugc.shorts.runs.detail.back') }}
    </router-link>

    <PageHeader :title="runTitle" :description="runDescription">
      <template #actions>
        <button
          v-if="hasAnyRenderSpec"
          class="btn-secondary inline-flex items-center gap-2"
          :disabled="bundleDownloading"
          @click="downloadBundle"
        >
          <ArrowDownTrayIcon class="h-5 w-5" />
          {{ $t('ugc.shorts.runs.detail.downloadBundle') }}
        </button>
        <button class="btn-danger inline-flex items-center gap-2" @click="deleteConfirmOpen = true">
          <TrashIcon class="h-5 w-5" />
          {{ $t('ugc.shorts.runs.detail.delete') }}
        </button>
      </template>
    </PageHeader>

    <LoadingSpinner v-if="store.detailLoading && !run" full-page />

    <template v-else-if="run">
      <!-- 실행 상태 요약 -->
      <div class="card mb-4 flex flex-wrap items-center gap-x-4 gap-y-2">
        <span :class="['rounded-full px-2 py-0.5 text-caption', statusBadgeClass(run.status)]">
          {{ $t(`ugc.shorts.runs.status.${run.status}`) }}
        </span>
        <span class="text-body-xs text-gray-400">
          {{ $t('ugc.shorts.runs.clipCount', { count: run.clipCount }) }}
        </span>
        <span v-if="run.currentStage" class="text-body-xs text-gray-400">
          {{ $t('ugc.shorts.runs.currentStage') }}: {{ $t(`ugc.shorts.runs.stageNames.${run.currentStage}`) }}
        </span>
        <p v-if="run.status === 'FAILED' && run.errorMessage" class="w-full text-body-xs text-error-strong">
          {{ run.errorMessage }}
        </p>
      </div>

      <!-- 9단계 진행 표시 -->
      <section class="card mb-4">
        <h2 class="mb-3 font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('ugc.shorts.runs.detail.stages') }}
        </h2>
        <ol class="space-y-2">
          <li
            v-for="(s, i) in stages"
            :key="s.stage"
            class="rounded-xl border border-gray-200 p-3 dark:border-gray-700"
          >
            <div class="flex flex-wrap items-center gap-2">
              <span
                class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-gray-100 text-caption text-gray-500 dark:bg-gray-700 dark:text-gray-300"
              >
                {{ i + 1 }}
              </span>
              <span class="font-medium text-gray-900 dark:text-gray-100">
                {{ $t(`ugc.shorts.runs.stageNames.${s.stage}`) }}
              </span>
              <span :class="['rounded-full px-2 py-0.5 text-caption', stageBadgeClass(s.status)]">
                {{ $t(`ugc.shorts.runs.stageStatus.${s.status}`) }}
              </span>
              <button
                class="btn-secondary ml-auto inline-flex items-center gap-1"
                :disabled="isActive || acting"
                @click="askRerun(s.stage)"
              >
                <ArrowPathIcon class="h-4 w-4" />
                {{ $t('ugc.shorts.runs.detail.rerun') }}
              </button>
            </div>
            <div class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-body-xs text-gray-400">
              <span v-if="stageDuration(s)">{{ stageDuration(s) }}</span>
              <span v-if="s.creditCost > 0">
                {{ $t('ugc.shorts.runs.detail.credit', { cost: s.creditCost }) }}
              </span>
              <span v-if="s.promptRevision != null">
                {{ $t('ugc.shorts.runs.detail.revision', { revision: s.promptRevision }) }}
              </span>
              <span v-if="s.aiProvider">{{ s.aiProvider }}</span>
            </div>
            <p v-if="s.status === 'FAILED' && s.errorMessage" class="mt-1 text-body-xs text-error-strong">
              {{ s.errorMessage }}
            </p>
          </li>
        </ol>
      </section>

      <!-- 후킹 선택 게이트 -->
      <section v-if="run.status === 'AWAITING_HOOK_SELECTION'" class="card mb-4">
        <h2 class="font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('ugc.shorts.runs.hooks.title') }}
        </h2>
        <p class="mt-1 text-body text-gray-500 dark:text-gray-400">
          {{ $t('ugc.shorts.runs.hooks.description') }}
        </p>

        <div class="mt-4 space-y-4">
          <div
            v-for="clip in clips"
            :key="clip.id"
            class="rounded-xl border border-gray-200 p-4 dark:border-gray-700"
          >
            <div class="mb-3 flex flex-wrap items-center gap-2">
              <span class="font-semibold text-gray-900 dark:text-gray-100">
                {{ $t('ugc.shorts.runs.detail.clipSeq', { seq: clip.seq }) }}
              </span>
              <span class="text-body-xs text-gray-400">{{ formatClipRange(clip) }}</span>
              <span v-if="clip.title" class="truncate text-body-xs text-gray-500 dark:text-gray-400">
                {{ clip.title }}
              </span>
              <label class="ml-auto inline-flex cursor-pointer items-center gap-2 text-body-xs text-gray-500 dark:text-gray-400">
                <input
                  type="checkbox"
                  :checked="hookChoices[clip.id]?.discarded"
                  class="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                  @change="toggleDiscard(clip.id)"
                />
                {{ $t('ugc.shorts.runs.hooks.discard') }}
              </label>
            </div>

            <div :class="hookChoices[clip.id]?.discarded ? 'pointer-events-none opacity-50' : ''">
              <!-- A안 / B안 카드 -->
              <div class="grid gap-3 mobile:grid-cols-2">
                <button
                  v-for="variant in (['A', 'B'] as const)"
                  :key="variant"
                  type="button"
                  class="rounded-xl border p-3 text-left transition-colors"
                  :class="hookChoices[clip.id]?.variant === variant
                    ? 'border-primary-500 ring-2 ring-primary-500/30'
                    : 'border-gray-200 hover:border-primary-300 dark:border-gray-700 dark:hover:border-primary-700'"
                  @click="chooseVariant(clip.id, variant)"
                >
                  <span class="mb-1 block text-body-xs font-semibold text-primary-600 dark:text-primary-400">
                    {{ $t(`ugc.shorts.runs.hooks.variant${variant}`) }}
                  </span>
                  <span class="text-body text-gray-900 dark:text-gray-100">
                    {{ hookText(clip, variant) }}
                  </span>
                </button>
              </div>

              <!-- 직접 입력 -->
              <div class="mt-3">
                <label
                  class="mb-1 block text-body-xs font-medium text-gray-500 dark:text-gray-400"
                  :for="`hook-custom-${clip.id}`"
                >
                  {{ $t('ugc.shorts.runs.hooks.custom') }}
                </label>
                <input
                  :id="`hook-custom-${clip.id}`"
                  v-model="hookChoices[clip.id].customText"
                  type="text"
                  class="input-field w-full"
                  :class="hookChoices[clip.id]?.variant === 'CUSTOM' ? 'border-primary-500 ring-2 ring-primary-500/30' : ''"
                  :placeholder="$t('ugc.shorts.runs.hooks.customPlaceholder')"
                  maxlength="300"
                  @focus="chooseVariant(clip.id, 'CUSTOM')"
                />
              </div>
            </div>
          </div>
        </div>

        <div class="mt-4 flex justify-end">
          <button class="btn-primary" :disabled="acting" @click="submitHooks">
            {{ acting ? $t('ugc.shorts.runs.hooks.submitting') : $t('ugc.shorts.runs.hooks.submit') }}
          </button>
        </div>
      </section>

      <!-- 예약 게이트 -->
      <section v-else-if="run.status === 'AWAITING_SCHEDULE'" class="card mb-4">
        <h2 class="font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('ugc.shorts.runs.schedule.title') }}
        </h2>
        <p class="mt-1 text-body text-gray-500 dark:text-gray-400">
          {{ $t('ugc.shorts.runs.schedule.description') }}
        </p>

        <div class="mt-4 grid gap-4 mobile:grid-cols-2">
          <div>
            <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300" for="shorts-schedule-start">
              {{ $t('ugc.shorts.runs.schedule.startAt') }}
            </label>
            <input
              id="shorts-schedule-start"
              v-model="scheduleForm.startAt"
              type="datetime-local"
              class="input-field w-full"
            />
          </div>
          <div>
            <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300" for="shorts-schedule-interval">
              {{ $t('ugc.shorts.runs.schedule.intervalHours') }}
            </label>
            <input
              id="shorts-schedule-interval"
              v-model.number="scheduleForm.intervalHours"
              type="number"
              min="1"
              class="input-field w-full"
            />
          </div>
        </div>

        <fieldset class="mt-4">
          <legend class="mb-2 text-body font-medium text-gray-700 dark:text-gray-300">
            {{ $t('ugc.shorts.runs.schedule.platforms') }}
          </legend>
          <div class="flex flex-wrap gap-3">
            <label
              v-for="p in PLATFORM_OPTIONS"
              :key="p"
              class="inline-flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-body transition-colors"
              :class="scheduleForm.platforms.includes(p)
                ? 'border-primary-500 bg-primary-50 text-primary-700 dark:bg-primary-900/20 dark:text-primary-300'
                : 'border-gray-200 text-gray-600 dark:border-gray-700 dark:text-gray-300'"
            >
              <input
                type="checkbox"
                class="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                :checked="scheduleForm.platforms.includes(p)"
                @change="togglePlatform(p)"
              />
              {{ platformLabels[p] }}
            </label>
          </div>
        </fieldset>

        <div class="mt-4 flex justify-end">
          <button class="btn-primary" :disabled="acting" @click="submitSchedule">
            {{ acting ? $t('ugc.shorts.runs.schedule.submitting') : $t('ugc.shorts.runs.schedule.submit') }}
          </button>
        </div>
      </section>

      <!-- 예약표 (엑셀 양방향) -->
      <section v-if="clips.length > 0" class="card mb-4">
        <h2 class="font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('ugc.shorts.runs.sheet.title') }}
        </h2>
        <p class="mt-1 text-body text-gray-500 dark:text-gray-400">
          {{ $t('ugc.shorts.runs.sheet.description') }}
        </p>
        <div class="mt-4 flex flex-wrap gap-2">
          <button
            class="btn-secondary inline-flex items-center gap-2"
            :disabled="sheetDownloading"
            @click="downloadSheet"
          >
            <ArrowDownTrayIcon class="h-5 w-5" />
            {{ sheetDownloading ? $t('ugc.shorts.runs.sheet.downloading') : $t('ugc.shorts.runs.sheet.download') }}
          </button>
          <button
            class="btn-primary inline-flex items-center gap-2"
            :disabled="sheetUploading"
            @click="sheetFileInput?.click()"
          >
            <ArrowUpTrayIcon class="h-5 w-5" />
            {{ sheetUploading ? $t('ugc.shorts.runs.sheet.uploading') : $t('ugc.shorts.runs.sheet.upload') }}
          </button>
          <input
            ref="sheetFileInput"
            type="file"
            accept=".xlsx"
            class="hidden"
            @change="onSheetFileChange"
          />
        </div>
      </section>

      <!-- 클립 목록 (후킹 게이트가 아닐 때) -->
      <section v-if="clips.length > 0 && run.status !== 'AWAITING_HOOK_SELECTION'" class="card">
        <h2 class="mb-3 font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('ugc.shorts.runs.detail.clips') }}
        </h2>
        <div class="space-y-2">
          <div
            v-for="clip in clips"
            :key="clip.id"
            class="flex flex-wrap items-center gap-2 rounded-xl border border-gray-200 p-3 dark:border-gray-700"
          >
            <span class="font-medium text-gray-900 dark:text-gray-100">
              {{ $t('ugc.shorts.runs.detail.clipSeq', { seq: clip.seq }) }}
            </span>
            <span class="text-body-xs text-gray-400">{{ formatClipRange(clip) }}</span>
            <span class="rounded-full bg-gray-100 px-2 py-0.5 text-caption text-gray-600 dark:bg-gray-700 dark:text-gray-300">
              {{ clip.status }}
            </span>
            <span v-if="clip.title" class="truncate text-body-xs text-gray-500 dark:text-gray-400">
              {{ clip.title }}
            </span>
            <span class="text-body-xs text-gray-400">
              {{ $t('ugc.shorts.runs.detail.subtitleCount', { count: clip.subtitleCount }) }}
            </span>
            <span v-if="clip.scheduledAt" class="text-body-xs text-gray-400">
              {{ $t('ugc.shorts.runs.detail.scheduledAt') }}: {{ formatDate(clip.scheduledAt) }}
            </span>
            <div class="ml-auto flex flex-wrap items-center justify-end gap-1">
              <span
                v-if="isRenderedClip(clip.status)"
                class="rounded-full bg-success-subtle px-2 py-0.5 text-caption text-success-strong"
              >
                {{ $t('ugc.shorts.runs.detail.renderedAttached') }}
              </span>

              <!-- 서버 렌더 UI -->
              <template v-if="renderEnabled && clip.hasRenderSpec && canStartRender(clip.status)">
                <button
                  v-if="!renderJobFor(clip)"
                  class="btn-primary inline-flex items-center gap-1"
                  :disabled="renderActingIds.has(clip.id)"
                  @click="startRenderFor(clip)"
                >
                  <VideoCameraIcon class="h-4 w-4" />
                  {{ renderActingIds.has(clip.id) ? $t('ugc.shorts.runs.render.starting') : $t('ugc.shorts.runs.render.start') }}
                </button>
                <div
                  v-else-if="renderJobFor(clip)?.status === 'QUEUED'"
                  class="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2 py-0.5 text-caption text-gray-600 dark:bg-gray-700 dark:text-gray-300"
                >
                  <LoadingSpinner inline size="sm" />
                  {{ $t('ugc.shorts.runs.render.queued') }}
                </div>
                <div
                  v-else-if="renderJobFor(clip)?.status === 'RUNNING'"
                  class="inline-flex items-center gap-2 rounded-full bg-primary-50 px-2 py-0.5 text-caption text-primary-700 dark:bg-primary-900/20 dark:text-primary-300"
                >
                  <LoadingSpinner inline size="sm" />
                  <span>{{ $t('ugc.shorts.runs.render.running') }}</span>
                  <span v-if="renderJobFor(clip)?.progress != null">{{ renderJobFor(clip)?.progress }}%</span>
                </div>
                <button
                  v-else-if="renderJobFor(clip)?.status === 'FAILED'"
                  class="btn-danger inline-flex items-center gap-1"
                  :disabled="renderActingIds.has(clip.id)"
                  @click="startRenderFor(clip)"
                >
                  <ArrowPathIcon class="h-4 w-4" />
                  {{ $t('ugc.shorts.runs.render.retry') }}
                </button>
              </template>

              <template v-if="renderJobFor(clip)?.status === 'FAILED'">
                <span class="text-body-xs text-error-strong">
                  {{ renderJobFor(clip)?.failureReason || $t('ugc.shorts.runs.render.failed') }}
                </span>
              </template>

              <template v-if="renderJobFor(clip)?.status === 'COMPLETED' && renderJobFor(clip)?.videoId">
                <button
                  class="btn-secondary inline-flex items-center gap-1"
                  @click="openRenderPreview(renderJobFor(clip)!.videoId!)"
                >
                  <PlayIcon class="h-4 w-4" />
                  {{ $t('ugc.shorts.runs.render.preview') }}
                </button>
                <a
                  v-if="renderVideoUrl(renderJobFor(clip)!.videoId!)"
                  class="btn-secondary inline-flex items-center gap-1"
                  :href="renderVideoUrl(renderJobFor(clip)!.videoId!)"
                  download
                >
                  <ArrowDownTrayIcon class="h-4 w-4" />
                  {{ $t('ugc.shorts.runs.render.download') }}
                </a>
              </template>

              <button
                v-if="canAttachClip(clip.status)"
                class="btn-secondary inline-flex items-center gap-1"
                @click="openAttachModal(clip)"
              >
                <LinkIcon class="h-4 w-4" />
                {{
                  isRenderedClip(clip.status)
                    ? $t('ugc.shorts.runs.detail.reattachVideo')
                    : $t('ugc.shorts.runs.detail.attachVideo')
                }}
              </button>
              <button
                v-if="clip.hasRenderSpec"
                class="btn-secondary inline-flex items-center gap-1"
                :disabled="specDownloadingId === clip.id"
                @click="downloadSpec(clip)"
              >
                <ArrowDownTrayIcon class="h-4 w-4" />
                {{ $t('ugc.shorts.runs.detail.downloadSpec') }}
              </button>
            </div>
          </div>
        </div>
      </section>
    </template>

    <!-- 단계 재실행 확인 -->
    <ConfirmModal
      v-model="rerunConfirmOpen"
      :title="$t('ugc.shorts.runs.detail.rerunConfirmTitle')"
      :message="rerunConfirmMessage"
      @confirm="confirmRerun"
    />

    <!-- 실행 삭제 확인 -->
    <ConfirmModal
      v-model="deleteConfirmOpen"
      danger
      :title="$t('ugc.shorts.runs.detail.deleteConfirmTitle')"
      :message="$t('ugc.shorts.runs.detail.deleteConfirmMessage')"
      @confirm="confirmDelete"
    />

    <!-- 예약표 가져오기 미리보기: diff를 확인한 뒤에만 반영한다 -->
    <BaseModal
      v-model="sheetPreviewOpen"
      :title="$t('ugc.shorts.runs.sheet.previewTitle')"
      max-width="xl"
    >
      <div v-if="sheetPreview">
        <p v-if="sheetPreview.rows.length === 0" class="text-body text-gray-500 dark:text-gray-400">
          {{ $t('ugc.shorts.runs.sheet.noChanges') }}
        </p>
        <table v-else class="w-full text-left text-body-xs">
          <thead>
            <tr class="border-b border-gray-200 text-gray-500 dark:border-gray-700 dark:text-gray-400">
              <th class="py-2 pr-3 font-medium">{{ $t('ugc.shorts.runs.sheet.colClip') }}</th>
              <th class="py-2 pr-3 font-medium">{{ $t('ugc.shorts.runs.sheet.colField') }}</th>
              <th class="py-2 pr-3 font-medium">{{ $t('ugc.shorts.runs.sheet.colBefore') }}</th>
              <th class="py-2 font-medium">{{ $t('ugc.shorts.runs.sheet.colAfter') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(row, i) in sheetPreview.rows"
              :key="i"
              class="border-b border-gray-100 text-gray-700 dark:border-gray-800 dark:text-gray-300"
            >
              <td class="py-2 pr-3">{{ $t('ugc.shorts.runs.detail.clipSeq', { seq: row.seq }) }}</td>
              <td class="py-2 pr-3">{{ $t(`ugc.shorts.runs.sheet.fields.${row.field}`) }}</td>
              <td class="max-w-40 truncate py-2 pr-3">{{ row.before ?? '-' }}</td>
              <td class="max-w-40 truncate py-2">{{ row.after ?? '-' }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="sheetPreview.unknownClipIds.length > 0" class="mt-3 text-body-xs text-warning-strong">
          {{ $t('ugc.shorts.runs.sheet.unknownClips', { ids: sheetPreview.unknownClipIds.join(', ') }) }}
        </p>
        <ul v-if="sheetPreview.invalidRows.length > 0" class="mt-2 list-disc pl-4 text-body-xs text-warning-strong">
          <li v-for="(msg, i) in sheetPreview.invalidRows" :key="i">{{ msg }}</li>
        </ul>
      </div>
      <template #footer>
        <button class="btn-secondary" :disabled="sheetApplying" @click="sheetPreviewOpen = false">
          {{ $t('ugc.shorts.runs.sheet.cancel') }}
        </button>
        <button
          class="btn-primary"
          :disabled="!sheetPreview || sheetPreview.rows.length === 0 || sheetApplying"
          @click="applySheet"
        >
          {{ sheetApplying ? $t('ugc.shorts.runs.sheet.applying') : $t('ugc.shorts.runs.sheet.apply') }}
        </button>
      </template>
    </BaseModal>

    <!-- 서버 렌더 완성 영상 미리보기 -->
    <BaseModal
      v-model="renderPreviewOpen"
      :title="$t('ugc.shorts.runs.render.previewTitle')"
      max-width="lg"
    >
      <LoadingSpinner v-if="!renderPreviewVideo" />
      <div v-else class="space-y-3">
        <video
          v-if="renderPreviewVideo.fileUrl"
          controls
          class="w-full rounded-xl"
          :src="renderPreviewVideo.fileUrl"
          :poster="renderPreviewVideo.thumbnailUrl ?? undefined"
        />
        <p v-else class="text-body text-gray-500 dark:text-gray-400">
          {{ $t('ugc.shorts.runs.render.previewNoUrl') }}
        </p>
        <a
          v-if="renderPreviewVideo.fileUrl"
          class="btn-secondary inline-flex w-full items-center justify-center gap-2"
          :href="renderPreviewVideo.fileUrl"
          download
        >
          <ArrowDownTrayIcon class="h-5 w-5" />
          {{ $t('ugc.shorts.runs.render.download') }}
        </a>
      </div>
    </BaseModal>

    <!-- 완성 영상 연결: render.sh 산출물을 업로드한 뒤 클립에 붙인다 -->
    <BaseModal
      v-model="attachModalOpen"
      :title="$t('ugc.shorts.runs.detail.attachTitle')"
      max-width="lg"
    >
      <LoadingSpinner v-if="attachVideosLoading" />
      <p v-else-if="attachVideos.length === 0" class="text-body text-gray-500 dark:text-gray-400">
        {{ $t('ugc.shorts.runs.detail.attachEmpty') }}
      </p>
      <ul v-else class="space-y-2">
        <li v-for="v in attachVideos" :key="v.id">
          <button
            type="button"
            class="w-full rounded-xl border p-3 text-left transition-colors"
            :class="attachVideoId === v.id
              ? 'border-primary-500 ring-2 ring-primary-500/30'
              : 'border-gray-200 hover:border-primary-300 dark:border-gray-700 dark:hover:border-primary-700'"
            @click="attachVideoId = v.id"
          >
            <span class="text-body text-gray-900 dark:text-gray-100">{{ v.title }}</span>
          </button>
        </li>
      </ul>
      <template #footer>
        <button class="btn-secondary" :disabled="attaching" @click="attachModalOpen = false">
          {{ $t('ugc.shorts.runs.detail.attachCancel') }}
        </button>
        <button
          class="btn-primary"
          :disabled="attachVideoId == null || attaching"
          @click="submitAttach"
        >
          {{ attaching ? $t('ugc.shorts.runs.detail.attaching') : $t('ugc.shorts.runs.detail.attachSubmit') }}
        </button>
      </template>
    </BaseModal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUgcShortsPipelineStore } from '@/stores/ugcShortsPipeline'
import { useWorkspaceStore } from '@/stores/workspace'
import { useNotificationStore } from '@/stores/notification'
import {
  ugcShortsPipelineApi,
  type PipelineStage,
  type PipelineRunStatus,
  type RunStageStatus,
  type RunStageResponse,
  type ShortsClipResponse,
  type HookVariant,
  type HookSelectionItem,
} from '@/api/ugcShortsPipeline'
import { ugcShortsSheetApi, type SheetPreviewResponse } from '@/api/ugcShortsSheet'
import { videoApi } from '@/api/video'
import type { Video } from '@/types/video'
import type { RenderJobStatusResponse } from '@/api/ugcShortsPipeline'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import {
  ArrowDownTrayIcon,
  ArrowLeftIcon,
  ArrowPathIcon,
  ArrowUpTrayIcon,
  LinkIcon,
  PlayIcon,
  TrashIcon,
  VideoCameraIcon,
} from '@heroicons/vue/24/outline'

const { t } = useI18n({ useScope: 'global' })
const route = useRoute()
const router = useRouter()
const store = useUgcShortsPipelineStore()
const workspaceStore = useWorkspaceStore()
const notify = useNotificationStore()

const runId = Number(route.params.id)

// 파이프라인 9단계 고정 순서 — 응답에 없는 단계는 PENDING 으로 채워 항상 9칸을 보여 준다
const STAGE_ORDER: PipelineStage[] = [
  'TRANSCRIBE',
  'REFRAME',
  'SEGMENT',
  'SUBTITLE',
  'HOOK',
  'TEMPLATE',
  'RENDER_SPEC',
  'VALIDATE',
  'SCHEDULE',
]

const PLATFORM_OPTIONS = ['YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'NAVER_CLIP'] as const
const platformLabels: Record<string, string> = {
  YOUTUBE: 'YouTube',
  TIKTOK: 'TikTok',
  INSTAGRAM: 'Instagram',
  NAVER_CLIP: 'Naver Clip',
}

const POLL_INTERVAL_MS = 3000
let pollTimer: number | undefined

const acting = ref(false)
const rerunConfirmOpen = ref(false)
const pendingRerunStage = ref<PipelineStage | null>(null)
const deleteConfirmOpen = ref(false)
const bundleDownloading = ref(false)
const specDownloadingId = ref<number | null>(null)

// 예약표 엑셀 — 올린 파일은 apply 때 다시 쓰므로 들고 있는다
const sheetDownloading = ref(false)
const sheetUploading = ref(false)
const sheetApplying = ref(false)
const sheetPreviewOpen = ref(false)
const sheetPreview = ref<SheetPreviewResponse | null>(null)
const sheetFile = ref<File | null>(null)
const sheetFileInput = ref<HTMLInputElement | null>(null)

// 완성 영상 연결 — 모달을 열 때 영상 목록을 새로 읽는다
const attachModalOpen = ref(false)
const attachClip = ref<ShortsClipResponse | null>(null)
const attachVideos = ref<Video[]>([])
const attachVideosLoading = ref(false)
const attachVideoId = ref<number | null>(null)
const attaching = ref(false)

// 서버 렌더 — ffmpeg 가용성, 클립별 job 상태, 완료 비디오 캐시
const renderActingIds = ref<Set<number>>(new Set())
const activeRenderClipIds = ref<Set<number>>(new Set())
const renderVideoCache = ref<Record<number, Video>>({})
const renderPreviewOpen = ref(false)
const renderPreviewVideoId = ref<number | null>(null)
let renderPollTimer: number | undefined

const renderPreviewVideo = computed(() =>
  renderPreviewVideoId.value != null ? renderVideoCache.value[renderPreviewVideoId.value] : null,
)

watch(renderPreviewOpen, (open) => {
  if (!open) renderPreviewVideoId.value = null
})

// RENDERED 이상이면 완성 영상이 연결된 클립이다
const RENDERED_CLIP_STATUSES = ['RENDERED', 'SCHEDULED', 'PUBLISHED']

// 클립별 후킹 선택 상태 — variant 는 A/B/CUSTOM, discarded 면 제외 대상
interface HookChoice {
  variant: HookVariant | null
  customText: string
  discarded: boolean
}
const hookChoices = ref<Record<number, HookChoice>>({})

// 예약 폼 — 시작 일시는 datetime-local 값, 전송 시 ISO 로 변환한다
const scheduleForm = ref({
  startAt: '',
  intervalHours: 24,
  platforms: [] as string[],
})

const run = computed(() => store.detail?.run ?? null)
const clips = computed(() => store.detail?.clips ?? [])

const runTitle = computed(() => {
  if (!run.value) return t('ugc.shorts.runs.title')
  return run.value.sourceVideoTitle || `#${run.value.sourceVideoId}`
})

const runDescription = computed(() =>
  run.value?.createdAt ? formatDate(run.value.createdAt) : '',
)

/** PENDING/RUNNING 만 "실행 중" — 게이트 대기와 종료 상태에서는 폴링하지 않는다 */
const isActive = computed(
  () => run.value?.status === 'PENDING' || run.value?.status === 'RUNNING',
)

const hasAnyRenderSpec = computed(() => clips.value.some((c) => c.hasRenderSpec))

const renderEnabled = computed(
  () => store.renderAvailability?.available === true,
)

const stages = computed<RunStageResponse[]>(() =>
  STAGE_ORDER.map((stage) => {
    const found = store.detail?.stages.find((s) => s.stage === stage)
    return (
      found ?? {
        stage,
        status: 'PENDING',
        promptId: null,
        promptRevision: null,
        aiProvider: null,
        creditCost: 0,
        errorMessage: null,
        startedAt: null,
        completedAt: null,
      }
    )
  }),
)

const rerunConfirmMessage = computed(() =>
  pendingRerunStage.value
    ? t('ugc.shorts.runs.detail.rerunConfirmMessage', {
        stage: t(`ugc.shorts.runs.stageNames.${pendingRerunStage.value}`),
      })
    : '',
)

function statusBadgeClass(status: PipelineRunStatus): string {
  switch (status) {
    case 'RUNNING':
      return 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
    case 'AWAITING_HOOK_SELECTION':
    case 'AWAITING_SCHEDULE':
      return 'bg-warning-subtle text-warning-strong'
    case 'COMPLETED':
      return 'bg-success-subtle text-success-strong'
    case 'FAILED':
      return 'bg-error-subtle text-error-strong'
    default:
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
  }
}

function stageBadgeClass(status: RunStageStatus): string {
  switch (status) {
    case 'RUNNING':
      return 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
    case 'COMPLETED':
      return 'bg-success-subtle text-success-strong'
    case 'FAILED':
      return 'bg-error-subtle text-error-strong'
    default:
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
  }
}

function formatDate(iso: string): string {
  return iso.slice(0, 16).replace('T', ' ')
}

function formatMs(ms: number): string {
  const total = Math.floor(ms / 1000)
  const m = Math.floor(total / 60)
  const s = total % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

function formatClipRange(clip: ShortsClipResponse): string {
  return `${formatMs(clip.startMs)} - ${formatMs(clip.endMs)}`
}

/** 단계 소요 시간 — startedAt ~ completedAt, 둘 다 있을 때만 */
function stageDuration(s: RunStageResponse): string | null {
  if (!s.startedAt || !s.completedAt) return null
  const ms = new Date(s.completedAt).getTime() - new Date(s.startedAt).getTime()
  return t('ugc.shorts.runs.detail.duration', { seconds: (ms / 1000).toFixed(1) })
}

function hookText(clip: ShortsClipResponse, variant: 'A' | 'B'): string {
  return clip.hooks.find((h) => h.variant === variant)?.text ?? '-'
}

// ---- 실행 상태 갱신 ----
// 실행 중(PENDING/RUNNING)에만 3초 간격으로 폴하고, 게이트 대기·종료 상태면 반드시 중단한다
function stopPolling() {
  if (pollTimer !== undefined) {
    window.clearInterval(pollTimer)
    pollTimer = undefined
  }
}

function startPolling() {
  stopPolling()
  pollTimer = window.setInterval(() => {
    refreshDetail().catch(() => undefined)
  }, POLL_INTERVAL_MS)
}

async function refreshDetail() {
  await store.fetchDetail(runId)
  if (!isActive.value) stopPolling()
}

// ---- 후킹 선택 게이트 ----
function initHookChoices(target: ShortsClipResponse[]) {
  const next: Record<number, HookChoice> = {}
  for (const clip of target) {
    const selected = clip.hooks.find((h) => h.selected)
    next[clip.id] = {
      variant: selected?.variant ?? null,
      customText: selected?.variant === 'CUSTOM' ? selected.text : '',
      discarded: false,
    }
  }
  hookChoices.value = next
}

function choiceOf(clipId: number): HookChoice {
  const existing = hookChoices.value[clipId]
  if (existing) return existing
  const created: HookChoice = { variant: null, customText: '', discarded: false }
  hookChoices.value[clipId] = created
  return created
}

function chooseVariant(clipId: number, variant: HookVariant) {
  const c = choiceOf(clipId)
  if (c.discarded) return
  c.variant = variant
}

function toggleDiscard(clipId: number) {
  const c = choiceOf(clipId)
  c.discarded = !c.discarded
}

async function submitHooks() {
  const selections: HookSelectionItem[] = []
  const discardClipIds: number[] = []
  for (const clip of clips.value) {
    const c = hookChoices.value[clip.id]
    if (!c) continue
    if (c.discarded) {
      discardClipIds.push(clip.id)
      continue
    }
    if (!c.variant) {
      notify.error(t('ugc.shorts.runs.hooks.selectionRequired'))
      return
    }
    if (c.variant === 'CUSTOM' && !c.customText.trim()) {
      notify.error(t('ugc.shorts.runs.hooks.customRequired'))
      return
    }
    selections.push({
      clipId: clip.id,
      variant: c.variant,
      ...(c.variant === 'CUSTOM' ? { customText: c.customText.trim() } : {}),
    })
  }
  acting.value = true
  try {
    await store.selectHooks(runId, { selections, discardClipIds })
    notify.success(t('ugc.shorts.runs.hooks.submitted'))
    if (isActive.value) startPolling()
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.runs.hooks.submitFailed'))
  } finally {
    acting.value = false
  }
}

// ---- 예약 게이트 ----
function togglePlatform(p: string) {
  const idx = scheduleForm.value.platforms.indexOf(p)
  if (idx >= 0) scheduleForm.value.platforms.splice(idx, 1)
  else scheduleForm.value.platforms.push(p)
}

async function submitSchedule() {
  if (!scheduleForm.value.startAt) {
    notify.error(t('ugc.shorts.runs.schedule.startAtRequired'))
    return
  }
  if (scheduleForm.value.platforms.length === 0) {
    notify.error(t('ugc.shorts.runs.schedule.platformRequired'))
    return
  }
  acting.value = true
  try {
    await store.confirmSchedule(runId, {
      startAt: new Date(scheduleForm.value.startAt).toISOString(),
      intervalHours: scheduleForm.value.intervalHours,
      platforms: scheduleForm.value.platforms,
    })
    notify.success(t('ugc.shorts.runs.schedule.submitted'))
    if (isActive.value) startPolling()
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.runs.schedule.submitFailed'))
  } finally {
    acting.value = false
  }
}

// ---- 단계 재실행 ----
function askRerun(stage: PipelineStage) {
  pendingRerunStage.value = stage
  rerunConfirmOpen.value = true
}

async function confirmRerun() {
  if (!pendingRerunStage.value) return
  acting.value = true
  try {
    await store.rerunStage(runId, pendingRerunStage.value)
    notify.success(t('ugc.shorts.runs.detail.rerunStarted'))
    if (isActive.value) startPolling()
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.runs.detail.rerunFailed'))
  } finally {
    acting.value = false
  }
}

// ---- 산출물 다운로드 ----
async function requireWorkspaceId(): Promise<number> {
  const id = await workspaceStore.ensureActiveWorkspace()
  if (id == null) {
    throw new Error('활성 워크스페이스가 없습니다. 먼저 워크스페이스를 선택하세요.')
  }
  return id
}

function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

async function downloadSpec(clip: ShortsClipResponse) {
  specDownloadingId.value = clip.id
  try {
    const blob = await ugcShortsPipelineApi.downloadRenderSpec(await requireWorkspaceId(), runId, clip.id)
    saveBlob(blob, `clip-${clip.seq}-render-spec.json`)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.runs.detail.downloadFailed'))
  } finally {
    specDownloadingId.value = null
  }
}

async function downloadBundle() {
  bundleDownloading.value = true
  try {
    const blob = await ugcShortsPipelineApi.downloadRenderBundle(await requireWorkspaceId(), runId)
    saveBlob(blob, `shorts-run-${runId}-render-bundle.zip`)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.runs.detail.downloadFailed'))
  } finally {
    bundleDownloading.value = false
  }
}

// ---- 예약표 엑셀 ----
async function downloadSheet() {
  sheetDownloading.value = true
  try {
    const blob = await ugcShortsSheetApi.downloadSheet(await requireWorkspaceId(), runId)
    saveBlob(blob, `shorts-run-${runId}-schedule.xlsx`)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.runs.sheet.downloadFailed'))
  } finally {
    sheetDownloading.value = false
  }
}

/** 파일을 고륾면 바로 preview만 요청한다. 실제 반영은 모달에서 확인 뒤 apply */
async function onSheetFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = '' // 같은 파일을 다시 골라도 change가 뜨도록 초기화
  if (!file) return
  sheetUploading.value = true
  try {
    sheetPreview.value = await ugcShortsSheetApi.previewSheet(await requireWorkspaceId(), runId, file)
    sheetFile.value = file
    sheetPreviewOpen.value = true
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.runs.sheet.uploadFailed'))
  } finally {
    sheetUploading.value = false
  }
}

async function applySheet() {
  if (!sheetFile.value) return
  sheetApplying.value = true
  try {
    const result = await ugcShortsSheetApi.applySheet(await requireWorkspaceId(), runId, sheetFile.value)
    sheetPreviewOpen.value = false
    sheetPreview.value = null
    sheetFile.value = null
    notify.success(t('ugc.shorts.runs.sheet.applied', { count: result.rows.length }))
    await store.fetchDetail(runId)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.runs.sheet.applyFailed'))
  } finally {
    sheetApplying.value = false
  }
}

// ---- 완성 영상 연결 ----
function isRenderedClip(status: string): boolean {
  return RENDERED_CLIP_STATUSES.includes(status)
}

/**
 * 연결 버튼을 보일지 판단한다.
 * 잘못 렌더한 영상을 붙였을 수 있으므로 RENDERED 상태에서는 다시 연결할 수 있어야 한다.
 * 다만 이미 예약·게시된 클립은 바꾸면 안 되므로 막는다.
 */
function canAttachClip(status: string): boolean {
  return !['DISCARDED', 'SCHEDULED', 'PUBLISHED'].includes(status)
}

function canStartRender(status: string): boolean {
  return !['DISCARDED', 'RENDERED', 'SCHEDULED', 'PUBLISHED'].includes(status)
}

function renderJobKey(runId: number, clipId: number): string {
  return `${runId}:${clipId}`
}

function renderJobFor(clip: ShortsClipResponse): RenderJobStatusResponse | undefined {
  return store.renderJobs[renderJobKey(runId, clip.id)]
}

function renderVideoUrl(videoId: number): string | undefined {
  return renderVideoCache.value[videoId]?.fileUrl
}

async function loadRenderVideo(videoId: number) {
  if (renderVideoCache.value[videoId]) return
  try {
    const v = await videoApi.get(videoId)
    renderVideoCache.value[videoId] = v
  } catch {
    // 캐시 실패는 무시, UI에서 URL이 없을 때 대체 메시지를 보여준다
  }
}

async function startRenderFor(clip: ShortsClipResponse) {
  renderActingIds.value.add(clip.id)
  try {
    await store.startRender(runId, clip.id)
    notify.success(t('ugc.shorts.runs.render.started', { seq: clip.seq }))
    startRenderPolling(clip.id)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.runs.render.startFailed'))
  } finally {
    renderActingIds.value.delete(clip.id)
  }
}

function startRenderPolling(clipId: number) {
  activeRenderClipIds.value.add(clipId)
  if (renderPollTimer === undefined) {
    renderPollTimer = window.setInterval(pollRenderStatuses, POLL_INTERVAL_MS)
  }
}

function stopRenderPolling(clipId?: number) {
  if (clipId !== undefined) activeRenderClipIds.value.delete(clipId)
  if (activeRenderClipIds.value.size === 0 && renderPollTimer !== undefined) {
    window.clearInterval(renderPollTimer)
    renderPollTimer = undefined
  }
}

async function pollRenderStatuses() {
  for (const clipId of Array.from(activeRenderClipIds.value)) {
    try {
      const status = await store.fetchRenderStatus(runId, clipId)
      if (status.status === 'COMPLETED') {
        if (status.videoId != null) {
          await loadRenderVideo(status.videoId)
        }
        stopRenderPolling(clipId)
        await refreshDetail()
      } else if (status.status === 'FAILED') {
        stopRenderPolling(clipId)
      }
    } catch {
      // 개별 폴 실패는 무시, 다음 주기에 재시도
    }
  }
}

async function openRenderPreview(videoId: number) {
  renderPreviewVideoId.value = videoId
  await loadRenderVideo(videoId)
  renderPreviewOpen.value = true
}

/** 모달을 열 때마다 최신 영상 목록을 읽는다. 동영상만 연결 대상이다 */
async function openAttachModal(clip: ShortsClipResponse) {
  attachClip.value = clip
  attachVideoId.value = null
  attachModalOpen.value = true
  attachVideosLoading.value = true
  try {
    const res = await videoApi.list({ page: 0, size: 50 })
    attachVideos.value = res.content.filter((v) => v.mediaType === 'VIDEO')
  } catch {
    attachVideos.value = []
  } finally {
    attachVideosLoading.value = false
  }
}

async function submitAttach() {
  if (!attachClip.value || attachVideoId.value == null) return
  attaching.value = true
  try {
    await store.attachRenderedVideo(runId, attachClip.value.id, attachVideoId.value)
    notify.success(t('ugc.shorts.runs.detail.attachSuccess'))
    attachModalOpen.value = false
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.runs.detail.attachFailed'))
  } finally {
    attaching.value = false
  }
}

// ---- 실행 삭제 ----
async function confirmDelete() {
  try {
    await store.deleteRun(runId)
    notify.success(t('ugc.shorts.runs.detail.deleted'))
    router.push('/ugc/shorts/runs')
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.runs.detail.deleteFailed'))
  }
}

// 게이트 상태로 진입하면(폼 작성 중 폴림은 없으므로 덮어써도 안전) 선택 상태를 초기화한다
watch(
  () => store.detail,
  (d) => {
    if (d && d.run.status === 'AWAITING_HOOK_SELECTION') initHookChoices(d.clips)
  },
)

onMounted(async () => {
  try {
    await Promise.all([store.fetchDetail(runId), store.fetchRenderAvailability()])
    if (isActive.value) startPolling()
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.runs.detail.loadFailed'))
  }
})

onBeforeUnmount(() => {
  stopPolling()
  stopRenderPolling()
})
</script>
