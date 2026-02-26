<template>
  <!-- Mobile Layout -->
  <div v-if="!isTablet" class="space-y-4">
    <div>
      <h1 class="text-lg font-bold text-gray-900 dark:text-gray-100">
        {{ $t('qualityScore.title') }}
      </h1>
      <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
        {{ $t('qualityScore.description') }}
      </p>
    </div>

    <PageGuide
      :title="$t('qualityScore.pageGuideTitle')"
      :items="($tm('qualityScore.pageGuideMobile') as string[])"
    />

    <!-- Credit Display (Mobile) -->
    <div
      class="flex items-center gap-2 rounded-lg border px-3 py-2 text-xs"
      :class="isLow
        ? 'border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-900/20'
        : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800'"
    >
      <SparklesIcon class="h-4 w-4" :class="isLow ? 'text-red-500' : 'text-primary-600'" />
      <span class="text-gray-600 dark:text-gray-300">{{ $t('qualityScore.remaining') }}</span>
      <span class="font-bold" :class="isLow ? 'text-red-600' : 'text-primary-600'">
        {{ balance.toLocaleString() }}
      </span>
    </div>

    <!-- Loading State -->
    <LoadingSpinner v-if="loading" size="lg" :full-page="true" />

    <template v-else>
      <!-- Check Form (Mobile) -->
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <h2 class="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('qualityScore.checkForm') }}
        </h2>

        <!-- Title input -->
        <div class="mb-3">
          <label class="mb-1 block text-xs font-medium text-gray-700 dark:text-gray-300">
            {{ $t('qualityScore.videoTitle') }}
          </label>
          <input
            v-model="form.title"
            type="text"
            :placeholder="$t('qualityScore.videoTitlePlaceholder')"
            class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
          />
        </div>

        <!-- Description textarea -->
        <div class="mb-3">
          <label class="mb-1 block text-xs font-medium text-gray-700 dark:text-gray-300">
            {{ $t('qualityScore.videoDescription') }}
          </label>
          <textarea
            v-model="form.description"
            rows="3"
            :placeholder="$t('qualityScore.videoDescriptionPlaceholder')"
            class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
          />
        </div>

        <!-- Tags input -->
        <div class="mb-3">
          <label class="mb-1 block text-xs font-medium text-gray-700 dark:text-gray-300">
            {{ $t('qualityScore.tags') }}
          </label>
          <div class="flex flex-wrap gap-1.5 rounded-lg border border-gray-300 bg-white px-3 py-2 dark:border-gray-600 dark:bg-gray-800">
            <span
              v-for="(tag, idx) in form.tags"
              :key="idx"
              class="inline-flex items-center gap-1 rounded-full bg-primary-100 px-2.5 py-0.5 text-xs font-medium text-primary-700 dark:bg-primary-900/30 dark:text-primary-300"
            >
              {{ tag }}
              <button
                class="ml-0.5 text-primary-500 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-200"
                @click="removeTag(idx)"
              >
                <XMarkIcon class="h-3 w-3" />
              </button>
            </span>
            <input
              v-model="tagInput"
              type="text"
              :placeholder="form.tags.length === 0 ? $t('qualityScore.tagsPlaceholder') : ''"
              class="min-w-[80px] flex-1 bg-transparent text-sm text-gray-900 placeholder-gray-400 outline-none dark:text-gray-100 dark:placeholder-gray-500"
              @keydown.enter.prevent="addTag"
              @keydown.,="addTag"
            />
          </div>
          <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
            {{ $t('qualityScore.tagsHint') }}
          </p>
        </div>

        <!-- Platform select -->
        <div class="mb-4">
          <label class="mb-1 block text-xs font-medium text-gray-700 dark:text-gray-300">
            {{ $t('qualityScore.platform') }}
          </label>
          <select
            v-model="form.platform"
            class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
          >
            <option value="youtube">YouTube</option>
            <option value="tiktok">TikTok</option>
            <option value="instagram">Instagram Reels</option>
            <option value="naverclip">Naver Clip</option>
          </select>
        </div>

        <!-- Check button -->
        <button
          class="flex w-full items-center justify-center gap-2 rounded-lg bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="checking || !isFormValid"
          @click="handleCheck"
        >
          <template v-if="checking">
            <svg class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
            {{ $t('qualityScore.checking') }}
          </template>
          <template v-else>
            <MagnifyingGlassIcon class="h-4 w-4" />
            {{ $t('qualityScore.checkButton') }}
            <span class="rounded-full bg-white/20 px-2 py-0.5 text-xs">
              2 {{ $t('qualityScore.credits') }}
            </span>
          </template>
        </button>
      </div>

      <!-- Report Section (Mobile) -->
      <template v-if="currentReport">
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <h2 class="mb-4 text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('qualityScore.report') }}
          </h2>

          <!-- Overall Score -->
          <ScoreGauge
            :score="currentReport.overallScore"
            :grade="currentReport.overallGrade"
            :competitor-avg="currentReport.competitorAvgScore"
          />

          <!-- Competitor comparison bar -->
          <div class="mt-4 rounded-lg bg-gray-50 p-3 dark:bg-gray-800/50">
            <div class="mb-1.5 flex items-center justify-between text-xs">
              <span class="text-gray-500 dark:text-gray-400">{{ $t('qualityScore.yourScore') }}</span>
              <span class="font-semibold text-gray-900 dark:text-gray-100">{{ currentReport.overallScore }}</span>
            </div>
            <div class="h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
              <div
                class="h-full rounded-full bg-primary-500 transition-all duration-700"
                :style="{ width: `${currentReport.overallScore}%` }"
              />
            </div>
            <div class="mt-2 flex items-center justify-between text-xs">
              <span class="text-gray-500 dark:text-gray-400">{{ $t('qualityScore.competitorAvg') }}</span>
              <span class="font-semibold text-gray-900 dark:text-gray-100">{{ currentReport.competitorAvgScore }}</span>
            </div>
            <div class="h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
              <div
                class="h-full rounded-full bg-gray-400 transition-all duration-700 dark:bg-gray-500"
                :style="{ width: `${currentReport.competitorAvgScore}%` }"
              />
            </div>
          </div>
        </div>

        <!-- Metrics Grid (Mobile) -->
        <div class="space-y-3">
          <h2 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('qualityScore.metrics') }}
          </h2>
          <QualityMetricCard
            v-for="metric in currentReport.metrics"
            :key="metric.category"
            :metric="metric"
          />
        </div>

        <!-- Top Issues (Mobile) -->
        <div v-if="currentReport.topIssues.length > 0" class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <h2 class="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-100">
            <ExclamationTriangleIcon class="mr-1 inline h-4 w-4 text-yellow-500" />
            {{ $t('qualityScore.topIssues') }}
          </h2>
          <ul class="space-y-2">
            <li
              v-for="(issue, idx) in currentReport.topIssues"
              :key="idx"
              class="flex items-start gap-2 text-xs text-gray-700 dark:text-gray-300"
            >
              <span class="mt-1.5 h-1.5 w-1.5 flex-shrink-0 rounded-full bg-yellow-400" />
              {{ issue }}
            </li>
          </ul>
        </div>

        <!-- Improvements (Mobile) -->
        <div v-if="currentReport.improvements.length > 0" class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <h2 class="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-100">
            <ArrowTrendingUpIcon class="mr-1 inline h-4 w-4 text-green-500" />
            {{ $t('qualityScore.improvements') }}
          </h2>
          <div class="space-y-2">
            <ImprovementItem
              v-for="(item, idx) in currentReport.improvements"
              :key="idx"
              :improvement="item"
            />
          </div>
        </div>
      </template>

      <!-- History (Mobile) -->
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <h2 class="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('qualityScore.history') }}
        </h2>
        <div v-if="history.length > 0" class="space-y-2">
          <div
            v-for="report in history"
            :key="report.id"
            class="flex items-center justify-between rounded-lg border border-gray-100 bg-gray-50 px-3 py-2 dark:border-gray-700 dark:bg-gray-800/50"
            @click="currentReport = report"
          >
            <div class="min-w-0 flex-1">
              <p class="truncate text-xs font-medium text-gray-900 dark:text-gray-100">
                {{ report.videoTitle }}
              </p>
              <p class="text-xs text-gray-500 dark:text-gray-400">
                {{ formatDate(report.checkedAt) }}
              </p>
            </div>
            <div class="ml-3 flex items-center gap-2">
              <span class="text-sm font-bold" :class="gradeTextClass(report.overallGrade)">
                {{ report.overallScore }}
              </span>
              <span
                class="inline-flex h-6 w-6 items-center justify-center rounded-full text-xs font-bold text-white"
                :class="gradeBgClass(report.overallGrade)"
              >
                {{ report.overallGrade }}
              </span>
            </div>
          </div>
        </div>
        <div v-else class="py-6 text-center">
          <ClipboardDocumentCheckIcon class="mx-auto h-8 w-8 text-gray-300 dark:text-gray-600" />
          <p class="mt-2 text-xs text-gray-500 dark:text-gray-400">
            {{ $t('qualityScore.noHistory') }}
          </p>
        </div>
      </div>
    </template>
  </div>

  <!-- Desktop/Tablet Layout -->
  <div v-else>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('qualityScore.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('qualityScore.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <div
          class="flex items-center gap-2 rounded-lg border px-4 py-2 text-sm"
          :class="isLow
            ? 'border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-900/20'
            : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800'"
        >
          <SparklesIcon class="h-4 w-4" :class="isLow ? 'text-red-500' : 'text-primary-600'" />
          <span class="text-gray-600 dark:text-gray-300">{{ $t('qualityScore.remaining') }}</span>
          <span class="font-bold" :class="isLow ? 'text-red-600' : 'text-primary-600'">
            {{ balance.toLocaleString() }}
          </span>
        </div>
      </div>
    </div>

    <PageGuide
      :title="$t('qualityScore.pageGuideTitle')"
      :items="($tm('qualityScore.pageGuide') as string[])"
    />

    <!-- Loading State -->
    <LoadingSpinner v-if="loading" size="lg" :full-page="true" />

    <template v-else>
      <!-- Two-column layout -->
      <div class="grid grid-cols-12 gap-6">
        <!-- Left Panel: Check Form -->
        <div class="col-span-12 desktop:col-span-5">
          <div class="sticky top-6 space-y-6">
            <div class="rounded-xl border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-900">
              <h2 class="mb-4 text-base font-semibold text-gray-900 dark:text-gray-100">
                {{ $t('qualityScore.checkForm') }}
              </h2>

              <!-- Title input -->
              <div class="mb-4">
                <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  {{ $t('qualityScore.videoTitle') }}
                </label>
                <input
                  v-model="form.title"
                  type="text"
                  :placeholder="$t('qualityScore.videoTitlePlaceholder')"
                  class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2.5 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
                />
              </div>

              <!-- Description textarea -->
              <div class="mb-4">
                <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  {{ $t('qualityScore.videoDescription') }}
                </label>
                <textarea
                  v-model="form.description"
                  rows="4"
                  :placeholder="$t('qualityScore.videoDescriptionPlaceholder')"
                  class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2.5 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
                />
              </div>

              <!-- Tags input -->
              <div class="mb-4">
                <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  {{ $t('qualityScore.tags') }}
                </label>
                <div class="flex flex-wrap gap-1.5 rounded-lg border border-gray-300 bg-white px-3 py-2.5 focus-within:border-primary-500 focus-within:ring-1 focus-within:ring-primary-500 dark:border-gray-600 dark:bg-gray-800">
                  <span
                    v-for="(tag, idx) in form.tags"
                    :key="idx"
                    class="inline-flex items-center gap-1 rounded-full bg-primary-100 px-2.5 py-0.5 text-xs font-medium text-primary-700 dark:bg-primary-900/30 dark:text-primary-300"
                  >
                    {{ tag }}
                    <button
                      class="ml-0.5 text-primary-500 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-200"
                      @click="removeTag(idx)"
                    >
                      <XMarkIcon class="h-3 w-3" />
                    </button>
                  </span>
                  <input
                    v-model="tagInput"
                    type="text"
                    :placeholder="form.tags.length === 0 ? $t('qualityScore.tagsPlaceholder') : ''"
                    class="min-w-[100px] flex-1 bg-transparent text-sm text-gray-900 placeholder-gray-400 outline-none dark:text-gray-100 dark:placeholder-gray-500"
                    @keydown.enter.prevent="addTag"
                    @keydown.,="addTag"
                  />
                </div>
                <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
                  {{ $t('qualityScore.tagsHint') }}
                </p>
              </div>

              <!-- Platform select -->
              <div class="mb-5">
                <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  {{ $t('qualityScore.platform') }}
                </label>
                <select
                  v-model="form.platform"
                  class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2.5 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
                >
                  <option value="youtube">YouTube</option>
                  <option value="tiktok">TikTok</option>
                  <option value="instagram">Instagram Reels</option>
                  <option value="naverclip">Naver Clip</option>
                </select>
              </div>

              <!-- Check button -->
              <button
                class="flex w-full items-center justify-center gap-2 rounded-lg bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50"
                :disabled="checking || !isFormValid"
                @click="handleCheck"
              >
                <template v-if="checking">
                  <svg class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                  {{ $t('qualityScore.checking') }}
                </template>
                <template v-else>
                  <MagnifyingGlassIcon class="h-4 w-4" />
                  {{ $t('qualityScore.checkButton') }}
                  <span class="rounded-full bg-white/20 px-2 py-0.5 text-xs">
                    2 {{ $t('qualityScore.credits') }}
                  </span>
                </template>
              </button>
            </div>

            <!-- History (Desktop) -->
            <div class="rounded-xl border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-900">
              <h2 class="mb-4 text-base font-semibold text-gray-900 dark:text-gray-100">
                {{ $t('qualityScore.history') }}
              </h2>
              <div v-if="history.length > 0" class="space-y-2">
                <button
                  v-for="report in history"
                  :key="report.id"
                  class="flex w-full items-center justify-between rounded-lg border border-gray-100 bg-gray-50 px-4 py-3 text-left transition-colors hover:bg-gray-100 dark:border-gray-700 dark:bg-gray-800/50 dark:hover:bg-gray-800"
                  @click="currentReport = report"
                >
                  <div class="min-w-0 flex-1">
                    <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
                      {{ report.videoTitle }}
                    </p>
                    <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
                      {{ formatDate(report.checkedAt) }}
                    </p>
                  </div>
                  <div class="ml-4 flex items-center gap-2">
                    <span class="text-lg font-bold" :class="gradeTextClass(report.overallGrade)">
                      {{ report.overallScore }}
                    </span>
                    <span
                      class="inline-flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold text-white"
                      :class="gradeBgClass(report.overallGrade)"
                    >
                      {{ report.overallGrade }}
                    </span>
                  </div>
                </button>
              </div>
              <div v-else class="py-8 text-center">
                <ClipboardDocumentCheckIcon class="mx-auto h-10 w-10 text-gray-300 dark:text-gray-600" />
                <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">
                  {{ $t('qualityScore.noHistory') }}
                </p>
              </div>
            </div>
          </div>
        </div>

        <!-- Right Panel: Report -->
        <div class="col-span-12 desktop:col-span-7">
          <template v-if="currentReport">
            <!-- Overall Score Card -->
            <div class="rounded-xl border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-900">
              <div class="flex flex-col items-center gap-6 tablet:flex-row">
                <ScoreGauge
                  :score="currentReport.overallScore"
                  :grade="currentReport.overallGrade"
                  :competitor-avg="currentReport.competitorAvgScore"
                />

                <!-- Competitor comparison bar -->
                <div class="flex-1 space-y-4">
                  <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
                    {{ currentReport.videoTitle }}
                  </h2>
                  <div class="rounded-lg bg-gray-50 p-4 dark:bg-gray-800/50">
                    <div class="mb-2 flex items-center justify-between text-sm">
                      <span class="text-gray-500 dark:text-gray-400">{{ $t('qualityScore.yourScore') }}</span>
                      <span class="font-bold text-gray-900 dark:text-gray-100">{{ currentReport.overallScore }}{{ $t('qualityScore.points') }}</span>
                    </div>
                    <div class="h-3 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
                      <div
                        class="h-full rounded-full bg-primary-500 transition-all duration-700"
                        :style="{ width: `${currentReport.overallScore}%` }"
                      />
                    </div>
                    <div class="mt-3 flex items-center justify-between text-sm">
                      <span class="text-gray-500 dark:text-gray-400">{{ $t('qualityScore.competitorAvg') }}</span>
                      <span class="font-bold text-gray-900 dark:text-gray-100">{{ currentReport.competitorAvgScore }}{{ $t('qualityScore.points') }}</span>
                    </div>
                    <div class="h-3 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
                      <div
                        class="h-full rounded-full bg-gray-400 transition-all duration-700 dark:bg-gray-500"
                        :style="{ width: `${currentReport.competitorAvgScore}%` }"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Metrics Grid -->
            <div class="mt-6">
              <h2 class="mb-4 text-base font-semibold text-gray-900 dark:text-gray-100">
                {{ $t('qualityScore.metrics') }}
              </h2>
              <div class="grid grid-cols-1 gap-4 tablet:grid-cols-2">
                <QualityMetricCard
                  v-for="metric in currentReport.metrics"
                  :key="metric.category"
                  :metric="metric"
                />
              </div>
            </div>

            <!-- Top Issues -->
            <div v-if="currentReport.topIssues.length > 0" class="mt-6">
              <div class="rounded-xl border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-900">
                <h2 class="mb-4 flex items-center gap-2 text-base font-semibold text-gray-900 dark:text-gray-100">
                  <ExclamationTriangleIcon class="h-5 w-5 text-yellow-500" />
                  {{ $t('qualityScore.topIssues') }}
                </h2>
                <ul class="space-y-3">
                  <li
                    v-for="(issue, idx) in currentReport.topIssues"
                    :key="idx"
                    class="flex items-start gap-3 rounded-lg bg-yellow-50 px-4 py-3 text-sm text-yellow-800 dark:bg-yellow-900/20 dark:text-yellow-300"
                  >
                    <span class="mt-1 h-2 w-2 flex-shrink-0 rounded-full bg-yellow-400" />
                    {{ issue }}
                  </li>
                </ul>
              </div>
            </div>

            <!-- Improvements -->
            <div v-if="currentReport.improvements.length > 0" class="mt-6">
              <div class="rounded-xl border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-900">
                <h2 class="mb-4 flex items-center gap-2 text-base font-semibold text-gray-900 dark:text-gray-100">
                  <ArrowTrendingUpIcon class="h-5 w-5 text-green-500" />
                  {{ $t('qualityScore.improvements') }}
                </h2>
                <div class="space-y-3">
                  <ImprovementItem
                    v-for="(item, idx) in currentReport.improvements"
                    :key="idx"
                    :improvement="item"
                  />
                </div>
              </div>
            </div>
          </template>

          <!-- Empty state -->
          <div v-else class="flex flex-col items-center justify-center rounded-xl border border-gray-200 bg-white py-20 shadow-sm dark:border-gray-700 dark:bg-gray-900">
            <div class="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-700">
              <ChartBarIcon class="h-8 w-8 text-gray-400 dark:text-gray-500" />
            </div>
            <h3 class="mb-2 text-lg font-medium text-gray-900 dark:text-gray-100">
              {{ $t('qualityScore.noReport') }}
            </h3>
            <p class="text-sm text-gray-500 dark:text-gray-400">
              {{ $t('qualityScore.noReportDesc') }}
            </p>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useMediaQuery } from '@vueuse/core'
import {
  SparklesIcon,
  MagnifyingGlassIcon,
  XMarkIcon,
  ExclamationTriangleIcon,
  ArrowTrendingUpIcon,
  ChartBarIcon,
  ClipboardDocumentCheckIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import ScoreGauge from '@/components/qualityscore/ScoreGauge.vue'
import QualityMetricCard from '@/components/qualityscore/QualityMetricCard.vue'
import ImprovementItem from '@/components/qualityscore/ImprovementItem.vue'
import { useQualityScoreStore } from '@/stores/qualityScore'
import { useCredit } from '@/composables/useCredit'
import { useNotification } from '@/composables/useNotification'
import type { QualityGrade } from '@/types/qualityScore'

const { t } = useI18n({ useScope: 'global' })
const store = useQualityScoreStore()
const { balance, isLow, checkAndUse, fetchBalance } = useCredit()
const { success, error: showError } = useNotification()

const isTablet = useMediaQuery('(min-width: 768px)')

const { currentReport, history, checking, loading } = storeToRefs(store)

const tagInput = ref('')
const form = reactive({
  title: '',
  description: '',
  tags: [] as string[],
  platform: 'youtube',
})

const isFormValid = computed(() => form.title.trim().length > 0)

function addTag() {
  const value = tagInput.value.replace(/,/g, '').trim()
  if (value && !form.tags.includes(value)) {
    form.tags.push(value)
  }
  tagInput.value = ''
}

function removeTag(index: number) {
  form.tags.splice(index, 1)
}

async function handleCheck() {
  const hasCredits = await checkAndUse(2, t('qualityScore.title'))
  if (!hasCredits) return

  try {
    await store.checkQuality({
      title: form.title,
      description: form.description,
      tags: form.tags,
      platform: form.platform,
    })
    success(t('qualityScore.checkSuccess'))
    await fetchBalance()
  } catch {
    showError(t('qualityScore.checkError'))
  }
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleString()
}

const gradeBgMap: Record<QualityGrade, string> = {
  S: 'bg-purple-600',
  A: 'bg-green-600',
  B: 'bg-blue-600',
  C: 'bg-yellow-600',
  D: 'bg-red-600',
}

const gradeTextMap: Record<QualityGrade, string> = {
  S: 'text-purple-600 dark:text-purple-400',
  A: 'text-green-600 dark:text-green-400',
  B: 'text-blue-600 dark:text-blue-400',
  C: 'text-yellow-600 dark:text-yellow-400',
  D: 'text-red-600 dark:text-red-400',
}

function gradeBgClass(grade: QualityGrade): string {
  return gradeBgMap[grade] ?? 'bg-gray-600'
}

function gradeTextClass(grade: QualityGrade): string {
  return gradeTextMap[grade] ?? 'text-gray-600 dark:text-gray-400'
}

onMounted(() => {
  store.fetchHistory()
  fetchBalance()
})
</script>
