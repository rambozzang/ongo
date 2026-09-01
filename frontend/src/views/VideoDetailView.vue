<template>
  <!-- 영상 상세 - Dev Guide Section 10.2 -->
  <div class="relative min-h-full space-y-5 py-5 text-content">
    <!-- Header with back navigation -->
    <div class="mb-6 flex items-center gap-3">
      <button
        class="rounded-lg p-1.5 text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700"
        :aria-label="$t('videoDetail.backToList')"
        @click="router.push('/videos')"
      >
        <ArrowLeftIcon class="h-5 w-5" />
      </button>
      <h1 class="text-h1 font-bold text-gray-900 dark:text-gray-100">{{ $t('videoDetail.title') }}</h1>
    </div>

    <PageGuide
      :title="$t('videoDetail.pageGuideTitle')"
      :items="($tm('videoDetail.pageGuide') as string[])"
    />

    <LoadingSpinner v-if="loading" full-page />

    <div v-else-if="videoStore.detailLoadError" class="card py-16 text-center" role="alert">
      <ExclamationTriangleIcon class="mx-auto mb-4 h-12 w-12 text-error-strong" />
      <p class="text-title font-medium text-content">{{ videoStore.detailLoadError }}</p>
      <button class="btn-primary mt-4" @click="videoStore.fetchVideo(Number(props.id))">
        {{ $t('action.retry') }}
      </button>
    </div>

    <!-- Not Found -->
    <div v-else-if="!video" class="card py-16 text-center">
      <ExclamationTriangleIcon class="mx-auto mb-4 h-12 w-12 text-gray-400 dark:text-gray-500" />
      <p class="text-title font-medium text-gray-600 dark:text-gray-300">{{ $t('videoDetail.notFound') }}</p>
      <button class="btn-primary mt-4" @click="router.push('/videos')">
        {{ $t('videoDetail.backToList') }}
      </button>
    </div>

    <template v-else>
      <!-- Video Preview + Basic Info -->
      <div class="page-grid page-grid--wide mb-6">
        <!-- Video preview; the thumbnail fallback is shown only when no image exists. -->
        <div class="desktop:col-span-1">
          <div class="card overflow-hidden p-0">
            <div class="relative aspect-video w-full bg-gray-900">
              <img
                v-if="video.thumbnailUrl"
                :src="video.thumbnailUrl"
                :alt="video.title"
                class="h-full w-full object-cover"
              />
              <div v-else class="flex h-full items-center justify-center">
                <VideoCameraIcon class="h-16 w-16 text-gray-600 dark:text-gray-300" />
              </div>
            </div>
            <!-- Status bar -->
            <div class="flex items-center justify-between border-t border-gray-100 dark:border-gray-700 px-4 py-3">
              <StatusBadge :status="video.status" />
            </div>
          </div>
        </div>

        <!-- Basic Info -->
        <div class="desktop:col-span-2">
          <div class="card h-full">
            <div class="mb-4 flex items-start justify-between gap-3">
              <div class="min-w-0 flex-1">
                <div class="mb-1 flex items-center gap-2">
                  <h2 class="text-h2 font-bold text-gray-900 dark:text-gray-100">{{ video.title }}</h2>
                  <FavoriteButton :video-id="video.id" />
                </div>
                <p v-if="video.description" class="line-clamp-3 text-body text-gray-600 dark:text-gray-300">
                  {{ video.description }}
                </p>
              </div>
            </div>

            <!-- Info Grid -->
            <div class="mb-5 grid grid-cols-2 gap-4 tablet:grid-cols-4">
              <div>
                <p class="text-caption text-gray-500 dark:text-gray-400">{{ $t('videoDetail.uploadedAt') }}</p>
                <p class="mt-0.5 text-body font-semibold text-gray-900 dark:text-gray-100">
                  {{ formatDate(video.createdAt) }}
                </p>
              </div>
              <div>
                <p class="text-caption text-gray-500 dark:text-gray-400">
                  {{ $t('video.mediaInfo.fileSize') }}
                </p>
                <p class="mt-0.5 text-body font-semibold text-gray-900 dark:text-gray-100">
                  {{ video.fileSize ? formatFileSize(video.fileSize) : '-' }}
                </p>
              </div>
              <div>
                <p class="text-caption text-gray-500 dark:text-gray-400">{{ $t('videoDetail.category') }}</p>
                <p class="mt-0.5 text-body font-semibold text-gray-900 dark:text-gray-100">
                  {{ video.category ?? $t('videoDetail.uncategorized') }}
                </p>
              </div>
            </div>

            <!-- Tags -->
            <div v-if="video.tags.length > 0" class="mb-5">
              <p class="mb-2 text-caption text-gray-500 dark:text-gray-400">
                {{ $t('videoDetail.tags') }}
              </p>
              <div class="flex flex-wrap gap-1.5">
                <span
                  v-for="tag in video.tags"
                  :key="tag"
                  class="badge-gray"
                >
                  #{{ tag }}
                </span>
              </div>
            </div>

            <!-- Platform Upload Badges -->
            <div v-if="video.uploads.length > 0" class="mb-5">
              <p class="mb-2 text-caption text-gray-500 dark:text-gray-400">
                {{ $t('videoDetail.uploadPlatforms') }}
              </p>
              <div class="flex flex-wrap gap-2">
                <div
                  v-for="upload in video.uploads"
                  :key="upload.id"
                  class="flex min-w-0 flex-wrap items-center gap-1.5"
                >
                  <PlatformBadge :platform="upload.platform" />
                  <span v-if="upload.channelName" class="text-body-xs text-gray-500 dark:text-gray-400">
                    {{ upload.channelName }}
                  </span>
                  <StatusBadge :status="upload.status" />
                  <a
                    v-if="upload.platformUrl"
                    :href="upload.platformUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="text-body-xs text-primary-600 underline underline-offset-2 dark:text-primary-400"
                  >
                    {{ $t('videoDetail.openPlatform') }}
                  </a>
                  <span v-if="upload.errorMessage" class="basis-full text-body-xs text-error-strong">
                    {{ upload.errorMessage }}
                  </span>
                  <button
                    v-if="upload.status === 'FAILED' || upload.status === 'REJECTED' || upload.status === 'UNCONFIRMED'"
                    type="button"
                    class="basis-full text-left text-body-xs font-semibold text-primary-600 underline underline-offset-2 disabled:opacity-50 dark:text-primary-400"
                    :disabled="retryingUploadId === upload.id"
                    @click="handleUploadRecovery(upload)"
                  >
                    {{ retryingUploadId === upload.id
                      ? $t('videoDetail.recoveringUpload')
                      : upload.status === 'UNCONFIRMED'
                        ? $t('videoDetail.recheckUpload')
                        : $t('videoDetail.retryUpload') }}
                  </button>
                </div>
              </div>
            </div>

            <!-- Action Buttons -->
            <div class="flex flex-wrap gap-2 border-t border-gray-100 dark:border-gray-700 pt-4">
              <button class="btn-primary" @click="showPreviewModal = true">
                <PlayIcon class="mr-1.5 h-4 w-4" />
                {{ $t('videoDetail.preview') }}
              </button>
              <button class="btn-primary" @click="handleEdit">
                <PencilSquareIcon class="mr-1.5 h-4 w-4" />
                {{ $t('action.edit') }}
              </button>
              <button class="btn-secondary" @click="handleRecycle">
                <ArrowPathRoundedSquareIcon class="mr-1.5 h-4 w-4" />
                {{ $t('videos.contextRecycle') }}
              </button>
              <button class="btn-secondary" @click="handleReUpload">
                <ArrowPathIcon class="mr-1.5 h-4 w-4" />
                {{ $t('videos.contextReupload') }}
              </button>
              <button class="btn-danger" @click="showDeleteModal = true">
                <TrashIcon class="mr-1.5 h-4 w-4" />
                {{ $t('action.delete') }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!--
        자체 성과 점수.

        **집계가 하나라도 있을 때만 계산한다.** `calculateVideoScore` 의 커버리지 항목은
        업로드 개수만으로 점수가 나오므로(플랫폼 수 × 25), 아직 아무것도 수집되지 않은
        영상도 0 이 아닌 총점을 받는다. 예: 2개 플랫폼 게시 → 커버리지 50 → 총점 8점.
        조회·참여·성장은 전부 0 인데 "8점"만 보이면 성과가 나빴다는 뜻으로 읽힌다.
      -->
      <div v-if="video.uploads.length > 0" class="mb-6">
        <PerformanceScore
          v-if="hasCollectedAnalytics"
          :video="video"
          :analytics="analyticsData"
        />
        <div v-else class="card" data-testid="local-score-unavailable">
          <h3 class="mb-2 text-title font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('videoDetail.selfScoreTitle') }}
          </h3>
          <p class="text-body text-gray-500 dark:text-gray-400">
            {{ $t('videoDetail.selfScoreUnavailable') }}
          </p>
        </div>
      </div>

      <!-- AI Performance Score Card -->
      <div v-if="video.uploads.length > 0" class="mb-6">
        <PerformanceScoreCard :video-id="video.id" />
      </div>

      <!-- Anomaly Alerts -->
      <div v-if="video.uploads.length > 0" class="mb-6">
        <AnomalyAlertCard @analyze="handleAnalyzeAnomaly" />
      </div>

      <!-- Platform Analytics Section -->
      <div v-if="video.uploads.length > 0" class="mb-6">
        <div
          v-if="analyticsError"
          class="mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-error-subtle bg-error-subtle px-3 py-2.5 text-body text-error-strong"
          role="alert"
        >
          <span class="flex-1">
            {{ analyticsError }}
            <!--
              숫자를 보존하는 대신 신선도는 속이지 않는다. 이 문구가 없으면 오류 배너와
              함께 뜬 수치가 방금 조회된 값처럼 보인다.
            -->
            <span v-if="showsStaleAnalytics" data-testid="stale-analytics-notice">
              {{ $t('videoDetail.analyticsStaleNotice') }}
            </span>
          </span>
          <button
            type="button"
            class="btn-secondary min-h-11"
            :disabled="analyticsLoading"
            @click="video && fetchAnalytics(video.id)"
          >
            {{ analyticsLoading ? $t('action.loading') : $t('action.retry') }}
          </button>
        </div>

        <!--
          성공한 뒤에도 다시 불러올 수 있어야 한다. 오류 배너 안의 재시도 버튼은 오류일
          때만 보이므로, 그것만으로는 "성공 → 재조회 실패" 상태에 도달할 수 없었다.
          중복 요청은 analyticsLoading 으로 막는다.
        -->
        <div class="mb-3 flex justify-end">
          <button
            type="button"
            class="btn-secondary min-h-11"
            :disabled="analyticsLoading"
            :aria-label="$t('videoDetail.analyticsRefreshAria')"
            data-testid="analytics-refresh"
            @click="video && fetchAnalytics(video.id)"
          >
            {{ analyticsLoading ? $t('videoDetail.metricLoading') : $t('videoDetail.analyticsRefresh') }}
          </button>
        </div>

        <!-- Platform Tab Selector -->
        <div class="mb-4 flex gap-1 overflow-x-auto rounded-lg bg-gray-100 dark:bg-gray-800 p-1">
          <button
            v-for="upload in video.uploads"
            :key="upload.platform"
            class="flex-shrink-0 rounded-md px-4 py-2 text-body font-medium transition-colors"
            :class="
              selectedPlatform === upload.platform
                ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm'
                : 'text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-gray-100'
            "
            @click="selectedPlatform = upload.platform"
          >
            <span
              class="mr-1.5 inline-block h-2 w-2 rounded-full"
              :style="{ backgroundColor: PLATFORM_CONFIG[upload.platform].color }"
            />
            {{ PLATFORM_CONFIG[upload.platform].label }}
          </button>
        </div>

        <!-- Performance Cards -->
        <div class="page-grid page-grid--metrics mb-6">
          <!-- Views -->
          <div class="card text-center">
            <EyeIcon class="mx-auto mb-2 h-6 w-6 text-gray-400 dark:text-gray-500" />
            <p class="text-caption text-gray-500 dark:text-gray-400">{{ $t('videos.views') }}</p>
            <p class="mt-1 text-h1 font-bold text-gray-900 dark:text-gray-100">
              {{ metricText(currentAnalytics?.views) }}
            </p>
            <div
              v-if="currentAnalytics?.viewsChange != null"
              class="mt-1 flex items-center justify-center gap-0.5 text-caption"
              :class="changeColorClass(currentAnalytics.viewsChange)"
            >
              <ArrowTrendingUpIcon v-if="currentAnalytics.viewsChange > 0" class="h-3 w-3" />
              <ArrowTrendingDownIcon v-else-if="currentAnalytics.viewsChange < 0" class="h-3 w-3" />
              {{ formatChange(currentAnalytics.viewsChange) }}
            </div>
          </div>

          <!-- Likes -->
          <div class="card text-center">
            <HeartIcon class="mx-auto mb-2 h-6 w-6 text-gray-400 dark:text-gray-500" />
            <p class="text-caption text-gray-500 dark:text-gray-400">{{ $t('videos.likes') }}</p>
            <p class="mt-1 text-h1 font-bold text-gray-900 dark:text-gray-100">
              {{ metricText(currentAnalytics?.likes) }}
            </p>
            <div
              v-if="currentAnalytics?.likesChange != null"
              class="mt-1 flex items-center justify-center gap-0.5 text-caption"
              :class="changeColorClass(currentAnalytics.likesChange)"
            >
              <ArrowTrendingUpIcon v-if="currentAnalytics.likesChange > 0" class="h-3 w-3" />
              <ArrowTrendingDownIcon v-else-if="currentAnalytics.likesChange < 0" class="h-3 w-3" />
              {{ formatChange(currentAnalytics.likesChange) }}
            </div>
          </div>

          <!-- Comments -->
          <div class="card text-center">
            <ChatBubbleLeftEllipsisIcon class="mx-auto mb-2 h-6 w-6 text-gray-400 dark:text-gray-500" />
            <p class="text-caption text-gray-500 dark:text-gray-400">{{ $t('videos.comments') }}</p>
            <p class="mt-1 text-h1 font-bold text-gray-900 dark:text-gray-100">
              {{ metricText(currentAnalytics?.comments) }}
            </p>
          </div>

          <!-- Shares -->
          <div class="card text-center">
            <ShareIcon class="mx-auto mb-2 h-6 w-6 text-gray-400 dark:text-gray-500" />
            <p class="text-caption text-gray-500 dark:text-gray-400">{{ $t('videos.shares') }}</p>
            <p class="mt-1 text-h1 font-bold text-gray-900 dark:text-gray-100">
              {{ metricText(currentAnalytics?.shares) }}
            </p>
          </div>
        </div>

        <!-- Charts Row -->
        <div class="page-grid page-grid--split mb-6">
          <!--
            일별 조회수 추이 — API 의 dailyTrend 로 그리는 막대 그래프.
            데이터가 없으면 아래 v-else 가 미수집 안내를 보여 준다.
          -->
          <div class="card">
            <h3 class="mb-4 text-h3 text-gray-900 dark:text-gray-100">
              {{ $t('videoDetail.dailyViewsTrend') }}
            </h3>
            <div
              v-if="currentAnalytics && currentAnalytics.dailyTrend.length > 0"
              class="relative h-64"
            >
              <!-- Simple bar visualization backed by the API's daily trend data. -->
              <div class="flex h-full items-end gap-1">
                <div
                  v-for="(point, idx) in currentAnalytics.dailyTrend"
                  :key="idx"
                  class="group relative flex flex-1 flex-col items-center justify-end"
                >
                  <div
                    class="w-full rounded-t transition-colors"
                    :style="{
                      height: `${trendBarHeight(point.totalViews)}%`,
                      backgroundColor: selectedPlatform ? PLATFORM_CONFIG[selectedPlatform].color : '#6B7280',
                      opacity: 0.7,
                    }"
                  />
                  <!-- Tooltip on hover -->
                  <div
                    class="pointer-events-none absolute -top-10 left-1/2 z-10 hidden -translate-x-1/2 whitespace-nowrap rounded bg-gray-900 px-2 py-1 text-body-xs text-white shadow group-hover:block"
                  >
                    {{ formatShortDate(point.date) }}: {{ formatCompactNumber(point.totalViews) }}
                  </div>
                </div>
              </div>
              <!-- X-axis labels -->
              <div class="mt-2 flex justify-between text-body-xs text-gray-400 dark:text-gray-500">
                <span>{{ formatShortDate(currentAnalytics.dailyTrend[0]?.date) }}</span>
                <span>
                  {{
                    formatShortDate(
                      currentAnalytics.dailyTrend[
                        Math.floor(currentAnalytics.dailyTrend.length / 2)
                      ]?.date
                    )
                  }}
                </span>
                <span>
                  {{
                    formatShortDate(
                      currentAnalytics.dailyTrend[currentAnalytics.dailyTrend.length - 1]?.date
                    )
                  }}
                </span>
              </div>
            </div>
            <div v-else class="flex h-64 items-center justify-center">
              <div class="text-center">
                <ChartBarIcon class="mx-auto mb-2 h-10 w-10 text-gray-300 dark:text-gray-600" />
                <p class="text-body text-gray-400 dark:text-gray-500">
                  {{ $t('videoDetail.notEnoughData') }}
                </p>
              </div>
            </div>
          </div>

          <!--
            플랫폼 비교 — API 의 플랫폼별 합계로 그리는 막대 그래프.
            수집된 적 없는 플랫폼은 제외한다(comparablePlatforms). 포함하면 합계 0 이
            "조회수 0회"처럼 보인다.
          -->
          <div class="card">
            <h3 class="mb-4 text-h3 text-gray-900 dark:text-gray-100">
              {{ $t('videoDetail.platformComparison') }}
            </h3>
            <div v-if="comparablePlatforms.length > 0" class="h-64 space-y-4 overflow-y-auto">
              <!-- Views comparison -->
              <div>
                <p class="mb-1 text-caption text-gray-500 dark:text-gray-400">
                  {{ $t('videos.views') }}
                </p>
                <div class="space-y-1">
                  <div
                    v-for="a in comparablePlatforms"
                    :key="`views-${a.platform}`"
                    :data-testid="`comparison-row-${a.platform}`"
                    class="flex items-center gap-2"
                  >
                    <span class="w-20 text-body-xs text-gray-600 dark:text-gray-300">
                      {{ PLATFORM_CONFIG[a.platform].label }}
                    </span>
                    <div class="h-4 flex-1 rounded-full bg-gray-100 dark:bg-gray-800">
                      <!-- 미수집 지표는 막대를 그리지 않는다. 폭 0 은 "0 을 기록했다" 로 보인다. -->
                      <div
                        v-if="a.views !== null"
                        class="h-full rounded-full transition-all"
                        :style="{
                          width: `${comparisonBarWidth(a.views, maxViews)}%`,
                          backgroundColor: PLATFORM_CONFIG[a.platform].color,
                        }"
                      />
                    </div>
                    <span class="w-14 text-right text-caption text-gray-700 dark:text-gray-300">
                      {{ a.views === null ? $t('analyticsView.notMeasured') : formatCompactNumber(a.views) }}
                    </span>
                  </div>
                </div>
              </div>
              <!-- Likes comparison -->
              <div>
                <p class="mb-1 text-caption text-gray-500 dark:text-gray-400">
                  {{ $t('videos.likes') }}
                </p>
                <div class="space-y-1">
                  <div
                    v-for="a in comparablePlatforms"
                    :key="`likes-${a.platform}`"
                    class="flex items-center gap-2"
                  >
                    <span class="w-20 text-body-xs text-gray-600 dark:text-gray-300">
                      {{ PLATFORM_CONFIG[a.platform].label }}
                    </span>
                    <div class="h-4 flex-1 rounded-full bg-gray-100 dark:bg-gray-800">
                      <!-- 미수집 지표는 막대를 그리지 않는다. 폭 0 은 "0 을 기록했다" 로 보인다. -->
                      <div
                        v-if="a.likes !== null"
                        class="h-full rounded-full transition-all"
                        :style="{
                          width: `${comparisonBarWidth(a.likes, maxLikes)}%`,
                          backgroundColor: PLATFORM_CONFIG[a.platform].color,
                        }"
                      />
                    </div>
                    <span class="w-14 text-right text-caption text-gray-700 dark:text-gray-300">
                      {{ a.likes === null ? $t('analyticsView.notMeasured') : formatCompactNumber(a.likes) }}
                    </span>
                  </div>
                </div>
              </div>
              <!-- Comments comparison -->
              <div>
                <p class="mb-1 text-caption text-gray-500 dark:text-gray-400">
                  {{ $t('videos.comments') }}
                </p>
                <div class="space-y-1">
                  <div
                    v-for="a in comparablePlatforms"
                    :key="`comments-${a.platform}`"
                    class="flex items-center gap-2"
                  >
                    <span class="w-20 text-body-xs text-gray-600 dark:text-gray-300">
                      {{ PLATFORM_CONFIG[a.platform].label }}
                    </span>
                    <div class="h-4 flex-1 rounded-full bg-gray-100 dark:bg-gray-800">
                      <!-- 미수집 지표는 막대를 그리지 않는다. 폭 0 은 "0 을 기록했다" 로 보인다. -->
                      <div
                        v-if="a.comments !== null"
                        class="h-full rounded-full transition-all"
                        :style="{
                          width: `${comparisonBarWidth(a.comments, maxComments)}%`,
                          backgroundColor: PLATFORM_CONFIG[a.platform].color,
                        }"
                      />
                    </div>
                    <span class="w-14 text-right text-caption text-gray-700 dark:text-gray-300">
                      {{ a.comments === null ? $t('analyticsView.notMeasured') : formatCompactNumber(a.comments) }}
                    </span>
                  </div>
                </div>
              </div>
              <!-- Shares comparison -->
              <div>
                <p class="mb-1 text-caption text-gray-500 dark:text-gray-400">
                  {{ $t('videos.shares') }}
                </p>
                <div class="space-y-1">
                  <div
                    v-for="a in comparablePlatforms"
                    :key="`shares-${a.platform}`"
                    class="flex items-center gap-2"
                  >
                    <span class="w-20 text-body-xs text-gray-600 dark:text-gray-300">
                      {{ PLATFORM_CONFIG[a.platform].label }}
                    </span>
                    <div class="h-4 flex-1 rounded-full bg-gray-100 dark:bg-gray-800">
                      <!-- 미수집 지표는 막대를 그리지 않는다. 폭 0 은 "0 을 기록했다" 로 보인다. -->
                      <div
                        v-if="a.shares !== null"
                        class="h-full rounded-full transition-all"
                        :style="{
                          width: `${comparisonBarWidth(a.shares, maxShares)}%`,
                          backgroundColor: PLATFORM_CONFIG[a.platform].color,
                        }"
                      />
                    </div>
                    <span class="w-14 text-right text-caption text-gray-700 dark:text-gray-300">
                      {{ a.shares === null ? $t('analyticsView.notMeasured') : formatCompactNumber(a.shares) }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="flex h-64 items-center justify-center">
              <div class="text-center">
                <ChartBarIcon class="mx-auto mb-2 h-10 w-10 text-gray-300 dark:text-gray-600" />
                <p class="text-body text-gray-400 dark:text-gray-500">
                  {{ $t('videoDetail.noAnalyticsData') }}
                </p>
              </div>
            </div>
            <!--
              비교에서 뺀 플랫폼을 밝힌다. 조용히 빼면 "그 플랫폼에 안 올렸나?" 로 읽힌다.
              올리긴 했고 아직 수집되지 않았다는 사실을 그대로 말한다.
            -->
            <p
              v-if="uncollectedPlatforms.length > 0"
              class="mt-3 text-caption text-gray-500 dark:text-gray-400"
            >
              {{
                $t('videoDetail.comparisonExcludesUncollected', {
                  platforms: uncollectedPlatforms
                    .map((a) => PLATFORM_CONFIG[a.platform].label)
                    .join(', '),
                })
              }}
            </p>
          </div>
        </div>

      </div>

      <!-- No Uploads State -->
      <div v-else class="card py-12 text-center">
        <CloudArrowUpIcon class="mx-auto mb-3 h-12 w-12 text-gray-300 dark:text-gray-600" />
        <p class="mb-1 text-body-lg font-medium text-gray-600 dark:text-gray-300">
          {{ $t('videoDetail.noUploadsTitle') }}
        </p>
        <p class="mb-4 text-body text-gray-400 dark:text-gray-500">
          {{ $t('videoDetail.noUploadsDescription') }}
        </p>
        <button class="btn-primary" @click="handleReUpload">
          <ArrowPathIcon class="mr-1.5 h-4 w-4" />
          {{ $t('videoDetail.uploadNow') }}
        </button>
      </div>
    </template>

    <!-- Delete Confirmation Modal -->
    <ConfirmModal
      v-model="showDeleteModal"
      :title="$t('videos.deleteTitle')"
      :message="$t('videoDetail.deleteMessage')"
      :confirm-text="$t('action.delete')"
      :cancel-text="$t('action.cancel')"
      danger
      @confirm="handleDelete"
    />

    <!-- Recycle Modal -->
    <RecycleModal
      v-if="video"
      v-model="showRecycleModal"
      :video="video"
      @confirm="handleRecycleConfirm"
    />

    <!-- Video Preview Modal -->
    <VideoPreviewModal
      v-if="video"
      v-model="showPreviewModal"
      :video="video"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import {
  ArrowLeftIcon,
  PencilSquareIcon,
  TrashIcon,
  ArrowPathIcon,
  ArrowPathRoundedSquareIcon,
  EyeIcon,
  HeartIcon,
  ChatBubbleLeftEllipsisIcon,
  ShareIcon,
  VideoCameraIcon,
  ChartBarIcon,
  CloudArrowUpIcon,
  ExclamationTriangleIcon,
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
} from '@heroicons/vue/24/outline'
import { PlayIcon } from '@heroicons/vue/24/solid'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import RecycleModal from '@/components/video/RecycleModal.vue'
import PerformanceScore from '@/components/video/PerformanceScore.vue'
import PerformanceScoreCard from '@/components/analytics/PerformanceScoreCard.vue'
import AnomalyAlertCard from '@/components/analytics/AnomalyAlertCard.vue'
import FavoriteButton from '@/components/video/FavoriteButton.vue'
import VideoPreviewModal from '@/components/video/VideoPreviewModal.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import { useLocale } from '@/composables/useLocale'
import { useVideoStore } from '@/stores/video'
import { useNotificationStore } from '@/stores/notification'
import { analyticsApi } from '@/api/analytics'
import { videoApi } from '@/api/video'
import type { VideoAnalytics } from '@/types/analytics'
import type { VideoUpload } from '@/types/video'
import type { Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'
import { resolveMetricDisplay, shouldWarnStaleMetrics } from '@/utils/metricDisplay'

// ---- Props ----

const props = defineProps<{
  id: string
}>()

// ---- Router & Store ----

const router = useRouter()
const { t } = useI18n()
const { currentLocale } = useLocale()
const videoStore = useVideoStore()
const notificationStore = useNotificationStore()
const { currentVideo: video, isLoadingDetail: loading } = storeToRefs(videoStore)

// ---- Reactive State ----

const showDeleteModal = ref(false)
const showRecycleModal = ref(false)
const showPreviewModal = ref(false)
const selectedPlatform = ref<Platform | null>(null)
const analyticsData = ref<VideoAnalytics[]>([])
const analyticsLoading = ref(false)
const analyticsError = ref<string | null>(null)
const retryingUploadId = ref<number | null>(null)

// ---- Computed ----

/** Analytics data for the currently selected platform tab */
const currentAnalytics = computed(() => {
  if (!selectedPlatform.value) return null
  return analyticsData.value.find((a) => a.platform === selectedPlatform.value) ?? null
})

/**
 * 플랫폼 비교에 실제로 그릴 수 있는 플랫폼.
 *
 * **서버는 수집이 없는 업로드에도 합계 0 인 행을 만든다**(`AnalyticsUseCase` 가 업로드마다
 * `dailyData = []` 로 행을 채운다). 그래서 `analyticsData` 는 한 번도 수집되지 않은
 * 플랫폼까지 담고 있고, 그대로 그리면 막대 0 과 숫자 "0" 이 나온다. 크리에이터는 그것을
 * **"조회수가 0회"** 로 읽는다 — 아직 아무것도 수집되지 않았다는 사실과 구분되지 않는다.
 *
 * 바로 위 KPI 칸은 이미 `hasData` 로 이 둘을 구분하고 있다(`metricText`). 비교 차트만
 * 예외로 두면 같은 화면이 서로 다른 답을 준다.
 *
 * `hasData` 가 `undefined` 인 것은 **판단 불가**(필드가 없는 옛 응답)라 숨기지 않는다.
 * 숨기면 실제 데이터를 감출 수 있고, 그건 가짜 0 보다 나쁘다.
 */
const comparablePlatforms = computed(() =>
  analyticsData.value.filter((a) => a.hasData !== false),
)

/**
 * 집계가 하나라도 수집된 플랫폼이 있는가.
 *
 * 자체 성과 점수는 이것이 참일 때만 계산한다. 커버리지 항목이 업로드 개수만으로 점수를
 * 내기 때문에, 이 조건 없이는 **한 번도 측정되지 않은 영상이 0 이 아닌 총점**을 받는다.
 *
 * `hasData` 가 `undefined` 인 옛 응답은 판단 불가라 있는 것으로 본다 — 숨겨서 실제 점수를
 * 감추는 쪽이 더 나쁘다.
 */
const hasCollectedAnalytics = computed(() =>
  analyticsData.value.some((a) => a.hasData !== false),
)

/** 아직 수집된 적이 없어 비교에서 뺀 플랫폼. 사라진 것처럼 보이지 않게 아래에 알린다. */
const uncollectedPlatforms = computed(() =>
  analyticsData.value.filter((a) => a.hasData === false),
)

/**
 * Max metric values for scaling comparison bar widths.
 *
 * 그리는 대상과 같은 집합에서 구한다. 전체에서 구하면 숨긴 플랫폼이 축을 늘려 보이는
 * 막대가 실제보다 짧아진다.
 */
const maxViews = computed(() => Math.max(...comparablePlatforms.value.map((a) => a.views).filter((v): v is number => v !== null), 1))
const maxLikes = computed(() => Math.max(...comparablePlatforms.value.map((a) => a.likes).filter((v): v is number => v !== null), 1))
const maxComments = computed(() => Math.max(...comparablePlatforms.value.map((a) => a.comments).filter((v): v is number => v !== null), 1))
const maxShares = computed(() => Math.max(...comparablePlatforms.value.map((a) => a.shares).filter((v): v is number => v !== null), 1))

/** Max daily trend value for scaling bar heights */
const maxTrendValue = computed(() => {
  if (!currentAnalytics.value) return 1
  return Math.max(...currentAnalytics.value.dailyTrend.map((p) => p.totalViews), 1)
})

// ---- Formatters ----

/** Format bytes into human-readable MB/GB */
function formatFileSize(bytes: number): string {
  if (bytes >= 1_073_741_824) {
    return `${(bytes / 1_073_741_824).toFixed(1)} GB`
  }
  if (bytes >= 1_048_576) {
    return `${(bytes / 1_048_576).toFixed(1)} MB`
  }
  if (bytes >= 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${bytes} B`
}

/** Format ISO date string to Korean locale long date */
function formatDate(dateStr: string): string {
  const d = new Date(dateStr)
  return d.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })
}

/** Format ISO date string to short M/D format */
function formatShortDate(dateStr: string | undefined): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

/** Compact number formatter — locale-aware units (ko: 천/만/억, en: K/M/B) */
const compactNumberFormat = computed(
  () => new Intl.NumberFormat(currentLocale.value, { notation: 'compact', maximumFractionDigits: 1 })
)

/** Format large numbers with locale-specific units */
function formatCompactNumber(n: number): string {
  return compactNumberFormat.value.format(n)
}

/** 로딩·오류·미집계를 0 으로 뭉개지 않는다. 판정은 [resolveMetricDisplay] 가 한다. */
const metricContext = computed(() => ({
  loading: analyticsLoading.value,
  hasError: analyticsError.value != null,
  // 서버는 수집이 없는 업로드에도 합계 0 인 행을 만든다. 일별 데이터 유무로만
  // "실제 0회"와 "미수집"을 가를 수 있다.
  hasData: currentAnalytics.value?.hasData,
}))

function metricText(value: number | null | undefined): string {
  const display = resolveMetricDisplay(value, metricContext.value)
  switch (display.kind) {
    case 'value':
      return formatCompactNumber(display.value)
    case 'loading':
      return t('videoDetail.metricLoading')
    case 'unavailable':
      return display.reason === 'error'
        ? t('videoDetail.metricUnavailableError')
        : t('videoDetail.metricNoData')
  }
}

/**
 * 오류인데 이전 데이터가 남아 있는 경우. 화면의 숫자가 **최신이 아닐 수 있다**는 것을
 * 따로 알린다 — 숫자는 보존하되 신선도는 속이지 않는다.
 */
const showsStaleAnalytics = computed(() =>
  shouldWarnStaleMetrics({
    hasError: analyticsError.value != null,
    hasLoadedValue: currentAnalytics.value != null,
  }),
)

/** Format a change percentage with sign */
function formatChange(value: number): string {
  if (value === 0) return '0%'
  return `${value > 0 ? '+' : ''}${value.toFixed(1)}%`
}

/** Return Tailwind color class based on positive/negative/zero change */
function changeColorClass(value: number): string {
  if (value > 0) return 'text-success-strong'
  if (value < 0) return 'text-error-strong'
  return 'text-gray-400'
}

/** Calculate bar height percentage for trend chart */
function trendBarHeight(value: number): number {
  if (maxTrendValue.value === 0) return 0
  return Math.max((value / maxTrendValue.value) * 100, 2)
}

/** Calculate bar width percentage for comparison chart */
/**
 * 비교 막대의 폭. **미수집 지표(`null`)는 0 폭이다.**
 *
 * 호출부는 `v-if` 로 막대 자체를 그리지 않으므로 이 0 이 화면에 나오지는 않는다.
 * `?? 0` 을 값 쪽에 쓰면 "0 을 기록했다" 로 읽히므로 여기서 명시적으로 판정한다.
 */
function comparisonBarWidth(value: number | null, max: number): number {
  if (value === null || max === 0) return 0
  return Math.max((value / max) * 100, 2)
}

// ---- Actions ----

function handleAnalyzeAnomaly(videoId: number) {
  router.push(`/videos/${videoId}`)
}

function handleEdit() {
  // DRAFTs use the production compose flow so their server-persisted
  // platform overrides are restored. Published videos keep the legacy
  // metadata editor because it also handles remote platform updates.
  if (video.value?.status === 'DRAFT') {
    router.push({ path: '/compose', query: { videoId: String(props.id) } })
    return
  }
  router.push(`/upload?edit=${props.id}`)
}

function handleRecycle() {
  showRecycleModal.value = true
}

async function handleRecycleConfirm() {
  await videoStore.fetchVideo(Number(props.id))
}

async function handleUploadRecovery(upload: VideoUpload) {
  if (!video.value) return
  retryingUploadId.value = upload.id
  try {
    if (upload.status === 'UNCONFIRMED') {
      await videoApi.recheckUpload(video.value.id, upload.id)
    } else {
      await videoApi.retryUpload(video.value.id, upload.id)
    }
    await videoStore.fetchVideo(video.value.id)
  } catch (error) {
    notificationStore.error(
      error instanceof Error ? error.message : t('videoDetail.recoveryFailed'),
    )
  } finally {
    retryingUploadId.value = null
  }
}

function handleReUpload() {
  router.push(`/upload?reupload=${props.id}`)
}

async function handleDelete() {
  try {
    const result = await videoStore.deleteVideo(Number(props.id))
    if (result.externalFailures.length > 0 || result.storageDeletionFailed) {
      const platforms = result.externalFailures.map(({ platform }) => platform).join(', ')
      const externalMessage = platforms
        ? `${platforms} 플랫폼의 외부 영상 삭제를 확인하지 못했습니다.`
        : ''
      const storageMessage = result.storageDeletionFailed
        ? '스토리지 파일 정리를 확인하지 못했습니다.'
        : ''
      notificationStore.warning(
        `내 라이브러리에서는 삭제했지만 ${[externalMessage, storageMessage].filter(Boolean).join(' ')} ${platforms ? '각 플랫폼에서 직접 확인해 주세요.' : '관리자 확인이 필요합니다.'}`,
        platforms ? '외부 플랫폼 확인 필요' : '스토리지 확인 필요',
      )
    }
    router.push('/videos')
  } catch {
    // Error handled by API client interceptor
  }
}

async function fetchAnalytics(videoId: number) {
  analyticsLoading.value = true
  analyticsError.value = null
  try {
    analyticsData.value = await analyticsApi.videoAnalytics(videoId)
  } catch {
    // 이전에 성공한 값은 지우지 않는다. 이미 확인한 숫자를 오류 하나로 지우면
    // 사용자는 성과가 사라진 줄 안다. 최신이 아닐 수 있다는 사실은 배너가 알린다.
    analyticsError.value = t('videoDetail.analyticsLoadFailed')
  } finally {
    analyticsLoading.value = false
  }
}

// ---- Lifecycle ----

onMounted(async () => {
  const videoId = Number(props.id)
  await videoStore.fetchVideo(videoId)

  if (video.value && video.value.uploads.length > 0) {
    selectedPlatform.value = video.value.uploads[0].platform
    await fetchAnalytics(videoId)
  }
})

// Set default selected platform when video data loads
watch(video, (v) => {
  if (v && v.uploads.length > 0 && !selectedPlatform.value) {
    selectedPlatform.value = v.uploads[0].platform
  }
})
</script>
