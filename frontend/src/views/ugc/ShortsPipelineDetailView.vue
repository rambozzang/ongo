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
        <p v-if="run.errorMessage" class="w-full text-body-xs" :class="run.status === 'PARTIALLY_COMPLETED' ? 'text-warning-strong' : 'text-error-strong'">
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
          <!--
            대상은 연결된 채널에서만 나온다. 고를 수 없는 플랫폼을 보여주면 사용자가
            선택한 뒤 비동기 단계에서 실패하고, 그때는 성공 토스트를 이미 본 뒤다.
          -->
          <p
            v-if="scheduleBlocked"
            class="rounded-lg bg-warning-subtle px-3 py-2 text-body-xs text-warning-strong"
            role="status"
          >
            {{ scheduleBlockedMessage }}
          </p>
          <div v-else class="flex flex-wrap gap-3">
            <label
              v-for="target in publishTargets"
              :key="target.value"
              class="inline-flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-body transition-colors"
              :class="scheduleForm.platforms.includes(target.value)
                ? 'border-primary-500 bg-primary-50 text-primary-700 dark:bg-primary-900/20 dark:text-primary-300'
                : 'border-gray-200 text-gray-600 dark:border-gray-700 dark:text-gray-300'"
            >
              <input
                type="checkbox"
                class="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                :checked="scheduleForm.platforms.includes(target.value)"
                @change="togglePlatform(target.value)"
              />
              <!-- 같은 플랫폼 계정이 둘이면 이름으로만 구분된다. -->
              {{ platformLabels[target.platform] ?? target.platform }} · {{ target.channelName }}
            </label>
          </div>
        </fieldset>

        <div class="mt-4 flex justify-end">
          <button class="btn-primary" :disabled="acting || scheduleBlocked" @click="submitSchedule">
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
            <!--
              대상 중 실패가 섞여 있으면 원문 상태(SCHEDULED)를 그대로 쓰지 않는다.
              그 값은 대상 하나라도 성공하면 붙어서 부분 실패를 성공처럼 보이게 한다.
            -->
            <span
              class="rounded-full px-2 py-0.5 text-caption"
              :class="clipStatusClass(clip)"
            >
              {{ clipStatusLabel(clip) }}
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
            <!--
              대상별 게시 결과. 클립 상태 하나로는 어느 계정이 실패했는지 알 수 없다.
              외부 게시물 URL 은 저장하는 곳이 없어 만들지 않는다.
            -->
            <ul
              v-if="clip.publications.length > 0"
              class="mt-2 flex w-full flex-col gap-1"
            >
              <li
                v-for="pub in clip.publications"
                :key="pub.platform"
                class="flex flex-wrap items-center gap-2 text-body-xs"
              >
                <span class="font-medium text-gray-700 dark:text-gray-300">{{ publicationLabel(pub) }}</span>
                <span
                  class="rounded-full px-2 py-0.5 text-caption"
                  :class="publicationBadgeClass(pub.status)"
                >
                  {{ pub.status }}
                </span>
                <span v-if="pub.scheduledAt" class="text-gray-400">
                  {{ formatDate(pub.scheduledAt) }}
                </span>
                <!-- 실패·건너뜀은 원인을 그대로 보여준다. 없으면 무엇을 고칠지 알 수 없다. -->
                <span v-if="pub.errorMessage" class="text-error-strong">
                  {{ pub.errorMessage }}
                </span>
              </li>
            </ul>

            <div class="ml-auto flex flex-wrap items-center justify-end gap-1">
              <span
                v-if="isRenderedClip(clip.status)"
                class="rounded-full bg-success-subtle px-2 py-0.5 text-caption text-success-strong"
              >
                {{ $t('ugc.shorts.runs.detail.renderedAttached') }}
              </span>

              <!--
                결과물이 연결된 클립의 1차 행동.
                판정 근거를 renderJobFor(세션 한정)가 아니라 clip.renderedVideoId(서버 응답)에
                두므로, 새로고침하거나 나중에 다시 들어와도 그대로 나온다.
              -->
              <button
                v-if="clip.renderedVideoId != null"
                class="btn-primary inline-flex items-center gap-1"
                @click="goToRenderedVideo(clip)"
              >
                <ArrowTopRightOnSquareIcon class="h-4 w-4" />
                {{ primaryActionLabel(clip) }}
              </button>

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
              <!--
                렌더 전 검수. 스펙은 RENDER_SPEC 단계에서 만들어지므로 그 이후 클립에만 붙는다.
                훅 선택 단계에는 스펙이 아직 없어 버튼을 둘 자리가 아니다.
              -->
              <button
                v-if="clip.hasRenderSpec"
                type="button"
                class="btn-secondary inline-flex min-h-[44px] items-center gap-1"
                :aria-expanded="isReviewOpen(clip.id)"
                :aria-controls="`clip-review-${clip.id}`"
                @click="toggleReview(clip)"
              >
                {{ isReviewOpen(clip.id)
                  ? $t('ugc.shorts.runs.review.hide')
                  : $t('ugc.shorts.runs.review.show') }}
              </button>
            </div>

            <!-- w-full 이라 flex-wrap 이 줄을 바꿔 행 아래로 펼쳐진다 -->
            <div
              v-if="clip.hasRenderSpec && isReviewOpen(clip.id)"
              :id="`clip-review-${clip.id}`"
              class="mt-1 w-full rounded-xl border border-gray-200 bg-gray-50 p-3 dark:border-gray-700 dark:bg-gray-900/40"
            >
              <p v-if="reviewOf(clip.id)?.loading" class="text-body-xs text-gray-500 dark:text-gray-400" role="status">
                {{ $t('ugc.shorts.runs.review.loading') }}
              </p>

              <div v-else-if="reviewOf(clip.id)?.error" role="alert">
                <p class="text-body-xs text-error-strong">{{ reviewOf(clip.id)?.error }}</p>
                <button
                  type="button"
                  class="mt-2 inline-flex min-h-[44px] items-center rounded-lg px-2 text-body-xs font-semibold text-primary-600 dark:text-primary-400"
                  @click="loadClipReview(clip)"
                >
                  {{ $t('action.retry') }}
                </button>
              </div>

              <div v-else-if="reviewOf(clip.id)" class="grid gap-4 tablet:grid-cols-2">
                <!-- 원본 위에 실제 잘릴 영역 -->
                <div>
                  <p class="mb-1.5 text-body-xs font-semibold text-gray-700 dark:text-gray-300">
                    {{ $t('ugc.shorts.runs.review.cropTitle') }}
                  </p>
                  <div class="relative w-full overflow-hidden rounded-lg bg-gray-900">
                    <!--
                      회색 상자가 아니라 진짜 원본을 보여준다. 어디가 잘리는지는 프레임 위에
                      겹쳐 봐야 판단할 수 있다. metadata 가 오면 클립 시작 지점으로 이동한다.
                    -->
                    <video
                      v-if="reviewOf(clip.id)!.sourceUrl"
                      :key="reviewOf(clip.id)!.sourceUrl!"
                      :ref="(el) => registerReviewVideo(clip.id, el as HTMLVideoElement | null)"
                      class="block w-full"
                      preload="metadata"
                      muted
                      playsinline
                      controls
                      :src="reviewOf(clip.id)!.sourceUrl!"
                      @loadedmetadata="onReviewMetadata(clip.id)"
                      @error="onReviewMediaError(clip.id)"
                    />
                    <div v-else class="aspect-video w-full" />

                    <div
                      v-if="cropRectStyle(reviewOf(clip.id)!)"
                      class="pointer-events-none absolute border-2 border-primary-400 bg-primary-400/20"
                      :style="cropRectStyle(reviewOf(clip.id)!)!"
                    />
                  </div>
                  <!--
                    native 해상도를 못 읽으면 좌표를 프레임 비율로 환산할 수 없다.
                    그래도 사각형을 그리면 사용자가 그 위치를 사실로 믿는다.
                  -->
                  <p class="mt-1.5 text-body-xs text-gray-500 dark:text-gray-400">
                    <template v-if="reviewOf(clip.id)!.mediaError">
                      {{ $t('ugc.shorts.runs.review.sourceUnavailable') }}
                    </template>
                    <template v-else-if="!reviewOf(clip.id)!.crop">
                      {{ $t('ugc.shorts.runs.review.cropFullFrame') }}
                    </template>
                    <template v-else-if="!cropRectStyle(reviewOf(clip.id)!)">
                      {{ $t('ugc.shorts.runs.review.cropUnknownSize') }}
                    </template>
                    <template v-else>
                      {{ $t('ugc.shorts.runs.review.cropMeasured', {
                        width: reviewOf(clip.id)!.sourceWidth,
                        height: reviewOf(clip.id)!.sourceHeight,
                      }) }}
                    </template>
                  </p>
                  <!--
                    조회 실패든 URL 만료든 다시 물어보면 해결되는 경우가 많다.
                    재조회는 새 URL 을 받으므로 서명 만료도 같이 풀린다.
                  -->
                  <button
                    v-if="reviewOf(clip.id)!.mediaError"
                    type="button"
                    class="mt-1 inline-flex min-h-[44px] items-center rounded-lg px-2 text-body-xs font-semibold text-primary-600 dark:text-primary-400"
                    :aria-label="$t('ugc.shorts.runs.review.retrySource')"
                    @click="loadClipReview(clip)"
                  >
                    {{ $t('action.retry') }}
                  </button>
                </div>

                <!-- 구간 + 자막 -->
                <div>
                  <p class="mb-1.5 text-body-xs font-semibold text-gray-700 dark:text-gray-300">
                    {{ $t('ugc.shorts.runs.review.cutTitle') }}
                  </p>
                  <p class="text-body-xs text-gray-600 dark:text-gray-400">
                    <template v-if="reviewOf(clip.id)!.startMs !== null">
                      {{ formatMs(reviewOf(clip.id)!.startMs!) }} –
                      {{ formatMs(reviewOf(clip.id)!.endMs ?? 0) }}
                    </template>
                    <template v-else>{{ $t('ugc.shorts.runs.review.cutUnknown') }}</template>
                  </p>

                  <p class="mb-1.5 mt-3 text-body-xs font-semibold text-gray-700 dark:text-gray-300">
                    {{ $t('ugc.shorts.runs.review.subtitleTitle', {
                      count: reviewOf(clip.id)!.subtitles.length,
                    }) }}
                  </p>
                  <ul
                    v-if="reviewOf(clip.id)!.subtitles.length > 0"
                    class="space-y-1 text-body-xs text-gray-600 dark:text-gray-400"
                  >
                    <li v-for="(line, i) in reviewSubtitlePreview(reviewOf(clip.id)!)" :key="i" class="truncate">
                      {{ line.text }}
                    </li>
                  </ul>
                  <p v-else class="text-body-xs text-gray-500 dark:text-gray-400">
                    {{ $t('ugc.shorts.runs.review.subtitleNone') }}
                  </p>
                </div>
              </div>
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
  type ClipPublicationResponse,
  type HookVariant,
  type HookSelectionItem,
} from '@/api/ugcShortsPipeline'
import { ugcShortsSheetApi, type SheetPreviewResponse } from '@/api/ugcShortsSheet'
import { videoApi } from '@/api/video'
import { channelApi } from '@/api/channel'
import type { Video, PlatformUploadCapability } from '@/types/video'
import type { Channel } from '@/types/channel'
import type { RenderJobStatusResponse } from '@/api/ugcShortsPipeline'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import {
  ArrowDownTrayIcon,
  ArrowLeftIcon,
  ArrowPathIcon,
  ArrowTopRightOnSquareIcon,
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

/**
 * 쇼츠가 게시를 지원하는 플랫폼. 여기 있다고 **선택 가능한 것은 아니다** —
 * 아래 `publishTargets` 가 실제 연결 채널과 교집합을 낸다.
 */
const SHORTS_PLATFORMS = ['YOUTUBE', 'TIKTOK', 'INSTAGRAM'] as const
const platformLabels: Record<string, string> = {
  YOUTUBE: 'YouTube',
  TIKTOK: 'TikTok',
  INSTAGRAM: 'Instagram',
}

const POLL_INTERVAL_MS = 3000
let pollTimer: number | undefined

/*
 * 렌더 전 검수.
 *
 * 지금까지는 렌더가 끝나야 결과를 처음 봤다. 크롭이 얼굴을 자르거나 자막이 어긋나면
 * 되돌릴 방법이 재렌더뿐인데, 동시 렌더가 1건이라 재시도 비용이 크다. 스펙은 이미
 * 서버에 있으므로 **보여주기만** 해도 "렌더 후에야 안다"가 "렌더 전에 안다"로 바뀐다.
 *
 * 편집은 하지 않는다 — 편집을 열면 스펙 수정 API·재계산·권한이 따라온다. 사용자는
 * 이미 있는 폐기(discard)로 대응할 수 있다.
 */
interface CropBox {
  x: number
  y: number
  width: number
  height: number
}

interface SubtitleLine {
  startMs: number
  endMs: number
  text: string
}

interface ClipReview {
  /** 요청 중. 실패와 구분해야 사용자가 기다릴지 다시 누를지 안다. */
  loading: boolean
  /** 요청 또는 파싱 실패. 값이 있으면 재시도 버튼을 보여준다. */
  error: string | null
  startMs: number | null
  endMs: number | null
  subtitles: SubtitleLine[]
  /** 스펙에 crop 이 없으면 전체 화면을 쓴다는 뜻이다. null 과 "없음"은 다른 상태다. */
  crop: CropBox | null
  /** 원본 재생 URL. 없으면 프레임을 못 보여주므로 크롭도 그리지 않는다. */
  sourceUrl: string | null
  /**
   * 원본 해상도. **없으면 크롭 사각형을 그리지 않는다.**
   * loadedmetadata 에서 native videoWidth/videoHeight 로만 채운다 — 실제 디코딩된
   * 프레임 크기여야 crop 좌표를 화면 비율로 정확히 환산할 수 있다. 추측해서 그리면
   * 사용자가 "이렇게 잘린다"고 믿어버린다 — 검수 화면이 오히려 오도한다.
   */
  sourceWidth: number | null
  sourceHeight: number | null
  /** 파일 자체를 재생할 수 없는 상태. 크롭/정확성을 주장하지 않는다. */
  mediaError: boolean
}

const clipReviews = ref<Record<number, ClipReview>>({})
const openReviewIds = ref<Set<number>>(new Set())
/** loadedmetadata 시점에 seek 하려면 element 참조가 필요하다. */
const reviewVideoEls = new Map<number, HTMLVideoElement>()

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
    case 'PARTIALLY_COMPLETED':
      return 'bg-warning-subtle text-warning-strong'
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

// ---- 예약 대상(실제 연결 채널) ----

/**
 * 예약 화면이 고를 수 있는 실제 대상.
 *
 * 예전에는 플랫폼 3종을 하드코딩해 보여줬다. 연결하지 않은 플랫폼도 고를 수 있었고,
 * 차단은 비동기 게시 단계에서야 일어나 사용자는 성공 토스트를 본 뒤 실패를 봤다.
 * 그래서 **연결된 채널에서 목록을 만든다** — 고를 수 없는 것은 애초에 보이지 않는다.
 *
 * `value` 가 `PLATFORM#channelId` 인 이유: 서버는 이 형식을 파싱해 계정별로 게시 결과를
 * 기록한다(`parseShortsPublishTarget`). 플랫폼 이름만 보내면 서버가 그 플랫폼의 채널 중
 * 하나를 임의로 고르므로, 같은 플랫폼에 계정이 둘이면 어디에 올라갈지 알 수 없다.
 */
interface PublishTargetOption {
  value: string
  platform: string
  channelName: string
}

const targetsLoading = ref(true)
const targetsLoadFailed = ref(false)
const connectedChannels = ref<Channel[]>([])
const uploadCapabilities = ref<PlatformUploadCapability[]>([])

const publishTargets = computed<PublishTargetOption[]>(() => {
  const shortsPlatforms = new Set<string>(SHORTS_PLATFORMS)
  return connectedChannels.value
    .filter((c) => shortsPlatforms.has(c.platform))
    // 만료·해제된 토큰으로는 게시가 서버에서 거절된다. 고르게 두면 같은 함정이 남는다.
    .filter((c) => c.tokenStatus === 'ACTIVE')
    .filter((c) => {
      // 이 배포가 그 플랫폼을 실제로 올릴 수 있는지. 능력표에 없으면 대상이 아니다.
      const cap = uploadCapabilities.value.find((x) => x.platform === c.platform)
      return cap != null && (cap.directVideoUpload || cap.cloudVideoUpload)
    })
    .map((c) => ({
      value: `${c.platform}#${c.id}`,
      platform: c.platform,
      channelName: c.channelName,
    }))
})

/** 대상을 하나도 고를 수 없는 상태. 제출을 막고 이유를 밝힌다. */
const hasNoPublishTarget = computed(
  () => !targetsLoading.value && !targetsLoadFailed.value && publishTargets.value.length === 0,
)

const scheduleBlocked = computed(
  () => targetsLoading.value || targetsLoadFailed.value || hasNoPublishTarget.value,
)

/** 막힌 이유를 그대로 말한다. 셋은 사용자가 할 일이 서로 다르다. */
const scheduleBlockedMessage = computed(() => {
  if (targetsLoading.value) return t('ugc.shorts.runs.schedule.targetsLoading')
  if (targetsLoadFailed.value) return t('ugc.shorts.runs.schedule.targetsLoadFailed')
  return t('ugc.shorts.runs.schedule.noConnectedTarget')
})

async function loadPublishTargets() {
  targetsLoading.value = true
  targetsLoadFailed.value = false
  try {
    const [channelsRes, capabilities] = await Promise.all([
      channelApi.list(),
      videoApi.getUploadCapabilities(),
    ])
    connectedChannels.value = channelsRes.channels ?? []
    uploadCapabilities.value = capabilities ?? []
  } catch {
    /*
     * 조회에 실패하면 "연결된 채널이 없다"고 단정할 수 없다. 빈 목록을 그대로 보여주면
     * 실제로는 연결돼 있는 사용자에게 거짓말이 된다. 실패는 실패로 표시한다.
     */
    targetsLoadFailed.value = true
    connectedChannels.value = []
    uploadCapabilities.value = []
  } finally {
    targetsLoading.value = false
  }
}

// ---- 예약 게이트 ----
function togglePlatform(p: string) {
  const idx = scheduleForm.value.platforms.indexOf(p)
  if (idx >= 0) scheduleForm.value.platforms.splice(idx, 1)
  else scheduleForm.value.platforms.push(p)
}

async function submitSchedule() {
  /*
   * 대상을 확정할 수 없으면 보내지 않는다. 보내면 서버가 비동기 게시 단계에서야 거절하고,
   * 그때는 사용자가 이미 성공 토스트를 본 뒤다.
   */
  if (scheduleBlocked.value) {
    notify.error(scheduleBlockedMessage.value)
    return
  }
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
/**
 * 결과물이 연결된 클립의 1차 행동 문구.
 *
 * RENDERED 는 아직 게시 전이라 다음 할 일이 "게시 준비"이고, SCHEDULED/PUBLISHED 는
 * 이미 예약·게시된 뒤라 할 일이 "게시 현황" 확인이다. 두 경우의 이동 지점은 같지만
 * 사용자가 기대하는 것이 다르므로 문구를 나눈다.
 */
function primaryActionLabel(clip: ShortsClipResponse): string {
  return clip.status === 'RENDERED'
    ? t('ugc.shorts.runs.detail.preparePublish')
    : t('ugc.shorts.runs.detail.publishStatus')
}

/**
 * 결과물의 1차 행동으로 이동한다.
 *
 * ## 왜 상태별로 목적지가 다른가
 *
 * 렌더가 만든 영상은 `DRAFT` 로 저장된다(`RenderedClipPersister`). 그리고 영상 상세
 * 화면에는 게시·예약을 실행하는 버튼이 없고, 편집을 눌러 `/compose?videoId=...` 로
 * 넘어가야 실제로 게시할 수 있다.
 *
 * 즉 아직 게시 전인 `RENDERED` 클립을 영상 상세로 보내면 **다음 할 일을 한 단계 더
 * 숨기는 셈**이다. "게시 준비"라는 이름이 데려다줘야 하는 곳은 실제로 게시를 실행할 수
 * 있는 작성 화면이다.
 *
 * 반대로 `SCHEDULED`/`PUBLISHED` 는 이미 예약·게시된 뒤라 할 일이 실행이 아니라 확인이다.
 * 그때는 영상 상세가 맞는 목적지다.
 */
function goToRenderedVideo(clip: ShortsClipResponse) {
  const videoId = clip.renderedVideoId
  if (videoId == null) return

  if (clip.status === 'RENDERED') {
    router.push({ path: '/compose', query: { videoId: String(videoId) } })
    return
  }
  router.push({ name: 'video-detail', params: { id: videoId } })
}

/** 대상이 실제로 게시 대기·완료인 상태. 나머지는 사용자가 손봐야 한다. */
const PUBLICATION_OK = ['SCHEDULED', 'PUBLISHED']

/** 대상 중 하나라도 실패·건너뜀이 있는가. */
function hasFailedPublication(clip: ShortsClipResponse): boolean {
  return clip.publications.some((p) => !PUBLICATION_OK.includes(p.status))
}

/**
 * 클립 상태 배지 문구.
 *
 * 서버의 `clip.status` 는 대상 **하나라도** 성공하면 SCHEDULED 가 된다. 그 원문을 그대로
 * 쓰면 3개 중 1개만 올라간 클립이 전부 성공한 것처럼 보인다. 실패가 섞여 있으면 그
 * 사실을 먼저 말한다.
 *
 * 대상이 없는 클립(렌더 전·게시 미요청)은 판단할 근거가 없으므로 기존 상태를 그대로 쓴다.
 */
function clipStatusLabel(clip: ShortsClipResponse): string {
  if (hasFailedPublication(clip)) return t('ugc.shorts.runs.detail.partiallyPublished')
  return clip.status
}

function clipStatusClass(clip: ShortsClipResponse): string {
  if (hasFailedPublication(clip)) return 'bg-warning-subtle text-warning-strong'
  return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
}

function publicationBadgeClass(status: string): string {
  return PUBLICATION_OK.includes(status)
    ? 'bg-success-subtle text-success-strong'
    : 'bg-error-subtle text-error-strong'
}

function publicationLabel(publication: ClipPublicationResponse): string {
  if (!publication.channelName) return publication.platform
  const platform = publication.platform.split('#')[0]
  return `${platformLabels[platform] ?? platform} · ${publication.channelName}`
}

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

function isReviewOpen(clipId: number): boolean {
  return openReviewIds.value.has(clipId)
}

function reviewOf(clipId: number): ClipReview | undefined {
  return clipReviews.value[clipId]
}

/**
 * 검수 패널을 연다. 스펙은 **누를 때** 가져온다.
 *
 * 실행 하나에 클립이 여러 개라, 화면을 열자마자 전부 받아오면 대부분 보지도 않을
 * 스펙을 위해 요청이 그만큼 나간다. 성공한 결과는 캐시해 다시 열 때 재요청하지 않는다.
 */
async function toggleReview(clip: ShortsClipResponse) {
  const next = new Set(openReviewIds.value)
  if (next.has(clip.id)) {
    next.delete(clip.id)
    openReviewIds.value = next
    return
  }
  next.add(clip.id)
  openReviewIds.value = next

  // 성공한 캐시가 있으면 그대로 쓴다. 실패는 캐시하지 않으므로 다시 시도된다.
  const cached = clipReviews.value[clip.id]
  if (cached && !cached.error && !cached.loading) return
  await loadClipReview(clip)
}

async function loadClipReview(clip: ShortsClipResponse) {
  clipReviews.value = {
    ...clipReviews.value,
    [clip.id]: {
      loading: true,
      error: null,
      startMs: null,
      endMs: null,
      subtitles: [],
      crop: null,
      sourceUrl: null,
      sourceWidth: null,
      sourceHeight: null,
      mediaError: false,
    },
  }

  try {
    const blob = await ugcShortsPipelineApi.downloadRenderSpec(
      await requireWorkspaceId(),
      runId,
      clip.id,
    )
    const parsed = JSON.parse(await blob.text()) as Record<string, unknown>

    const cut = parsed.cut as { startMs?: number; endMs?: number } | undefined
    const reframe = parsed.reframe as { crop?: CropBox | null } | undefined
    const rawSubtitles = Array.isArray(parsed.subtitles) ? (parsed.subtitles as SubtitleLine[]) : []
    const source = parsed.source as { videoId?: number } | undefined

    /*
     * 원본 파일 URL은 별도 조회다. 실패해도 나머지 검수 정보는 보여준다 —
     * 자막과 구간만으로도 판단할 수 있는 것이 있고, 크롭만 "확인 불가"로 표시하면 된다.
     * 해상도는 여기서 받지 않는다. 실제로 재생되는 프레임의 videoWidth/videoHeight 여야
     * 크롭 좌표 환산이 맞고, 서버 메타데이터는 비어 있거나 어긋날 수 있다.
     */
    let sourceUrl: string | null = null
    if (source?.videoId) {
      const video = await videoApi.get(source.videoId).catch(() => null)
      // 빈 문자열도 재생할 수 없다. "있는데 못 읽음"과 "아예 없음"을 여기서 합친다.
      sourceUrl = video?.fileUrl || null
    }

    clipReviews.value = {
      ...clipReviews.value,
      [clip.id]: {
        loading: false,
        error: null,
        startMs: typeof cut?.startMs === 'number' ? cut.startMs : null,
        endMs: typeof cut?.endMs === 'number' ? cut.endMs : null,
        subtitles: rawSubtitles.filter((line) => typeof line?.text === 'string'),
        crop: reframe?.crop ?? null,
        sourceUrl,
        sourceWidth: null,
        sourceHeight: null,
        /*
         * 원본 조회가 실패했거나 URL 이 비면 미리보기 프레임 자체가 없다.
         * "재생은 되는데 해상도를 아직 못 읽음"과는 다른 상태다 — 후자는 곧 채워지지만
         * 이쪽은 기다려도 아무것도 안 나온다. 처음부터 그렇게 말하고 재조회 경로를 준다.
         */
        mediaError: !sourceUrl,
      },
    }
  } catch {
    // 요청 실패와 파싱 실패를 사용자 입장에서 구분할 이유가 없다. 둘 다 "다시 시도"가 답이다.
    clipReviews.value = {
      ...clipReviews.value,
      [clip.id]: {
        loading: false,
        error: t('ugc.shorts.runs.review.loadFailed'),
        startMs: null,
        endMs: null,
        subtitles: [],
        crop: null,
        sourceUrl: null,
        sourceWidth: null,
        sourceHeight: null,
        mediaError: false,
      },
    }
  }
}

function registerReviewVideo(clipId: number, el: HTMLVideoElement | null) {
  if (el) reviewVideoEls.set(clipId, el)
  else reviewVideoEls.delete(clipId)
}

/**
 * 원본 메타데이터가 도착한 시점. 두 가지를 여기서만 한다.
 *
 * 1) native videoWidth/videoHeight 확정 — 이 값이 있어야 crop 좌표를 프레임 비율로
 *    환산할 수 있다. 그 전에는 사각형을 그리지 않는다.
 * 2) 클립 시작 지점으로 이동 — metadata 이전에는 seek 이 무시되거나 예외가 난다.
 */
function onReviewMetadata(clipId: number) {
  const el = reviewVideoEls.get(clipId)
  const review = clipReviews.value[clipId]
  if (!el || !review) return

  clipReviews.value = {
    ...clipReviews.value,
    [clipId]: {
      ...review,
      sourceWidth: el.videoWidth || null,
      sourceHeight: el.videoHeight || null,
      mediaError: false,
    },
  }

  /*
   * 길이를 모르거나(NaN/Infinity — 스트리밍) 시작점이 영상 밖이면 seek 하지 않는다.
   * 범위를 벗어난 seek 은 브라우저마다 무시/예외/0 으로 갈려 첫 프레임 신뢰를 깬다.
   */
  const startSec = (review.startMs ?? 0) / 1000
  if (!Number.isFinite(el.duration) || startSec <= 0 || startSec >= el.duration) return
  try {
    el.currentTime = startSec
  } catch {
    // seek 실패는 치명적이지 않다. 첫 프레임에서 크롭 영역은 그대로 확인할 수 있다.
  }
}

/** 파일을 못 여는 상태. 해상도를 지워 크롭 사각형이 남지 않게 한다. */
function onReviewMediaError(clipId: number) {
  const review = clipReviews.value[clipId]
  if (!review) return
  clipReviews.value = {
    ...clipReviews.value,
    [clipId]: { ...review, sourceWidth: null, sourceHeight: null, mediaError: true },
  }
}

/**
 * 크롭 사각형을 프레임 비율 기준 백분율로 환산한다.
 * native 해상도를 못 읽었으면 그리지 않는다 — 위치를 추측해 그리면 사용자는 그 좌표를
 * 사실로 믿고 검수를 통과시킨다. 재생 실패는 onReviewMediaError 가 해상도를 지워 여기로 온다.
 */
function cropRectStyle(review: ClipReview): Record<string, string> | null {
  const { crop, sourceWidth, sourceHeight } = review
  if (!crop || !sourceWidth || !sourceHeight) return null
  return {
    left: `${(crop.x / sourceWidth) * 100}%`,
    top: `${(crop.y / sourceHeight) * 100}%`,
    width: `${(crop.width / sourceWidth) * 100}%`,
    height: `${(crop.height / sourceHeight) * 100}%`,
  }
}

function reviewSubtitlePreview(review: ClipReview): SubtitleLine[] {
  return review.subtitles.slice(0, 3)
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
    // 예약 대상은 별도로 읽는다. 여기서 실패해도 실행 상세는 보여야 하므로
    // loadPublishTargets 가 자체적으로 실패를 흡수하고 상태 플래그로만 알린다.
    await Promise.all([
      store.fetchDetail(runId),
      store.fetchRenderAvailability(),
      loadPublishTargets(),
    ])
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
