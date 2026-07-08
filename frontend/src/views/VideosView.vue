<template>
  <div>
    <!-- Header -->
    <PageHeader :title="$t('videos.title')" :description="$t('videos.description')">
      <template #actions>
        <router-link to="/upload" class="btn-primary inline-flex items-center gap-2">
          <PlusIcon class="h-5 w-5" />
          {{ $t('videos.uploadNew') }}
        </router-link>
      </template>
    </PageHeader>

    <!-- Platform Filter Tabs + Sort -->
    <div class="card mb-6">
      <div class="flex flex-col gap-3 tablet:flex-row tablet:items-center tablet:justify-between">
        <!-- Platform Tabs -->
        <div class="flex flex-wrap gap-2">
          <button
            class="rounded-lg px-3 py-1.5 text-sm font-medium transition-colors"
            :class="
              !selectedPlatform
                ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
                : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'
            "
            @click="selectPlatform(undefined)"
          >
            {{ $t('videos.allPlatforms') }}
          </button>
          <button
            v-for="p in availablePlatforms"
            :key="p"
            class="rounded-lg px-3 py-1.5 text-sm font-medium transition-colors"
            :class="
              selectedPlatform === p
                ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
                : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'
            "
            @click="selectPlatform(p)"
          >
            {{ PLATFORM_CONFIG[p]?.label || p }}
          </button>
        </div>

        <!-- Sort -->
        <select v-model="sortBy" class="input-field text-sm w-auto" @change="loadFeed">
          <option value="recent">{{ $t('videos.sortRecent') }}</option>
          <option value="views">{{ $t('videos.sortViews') }}</option>
          <option value="likes">{{ $t('videos.sortLikes') }}</option>
          <option value="comments">{{ $t('videos.sortComments') }}</option>
        </select>
      </div>
    </div>

    <!-- Platform Errors -->
    <div
      v-if="feedErrors?.length"
      class="mb-4 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-700 dark:border-amber-800 dark:bg-amber-900/20 dark:text-amber-300"
    >
      <span v-for="(err, i) in feedErrors" :key="i">
        {{ $t('videos.platformError', { platform: err }) }}
        <span v-if="i < feedErrors.length - 1">, </span>
      </span>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="loading" />

    <!-- Empty: No channels -->
    <EmptyState
      v-else-if="feedItems.length === 0 && !loading && availablePlatforms.length === 0"
      :title="$t('videos.noChannels')"
      :description="$t('videos.noChannelsDesc')"
    >
      <template #action>
        <router-link to="/channels" class="btn-primary">
          {{ $t('videos.connectChannel') }}
        </router-link>
      </template>
    </EmptyState>

    <!-- Empty: No videos -->
    <EmptyState
      v-else-if="feedItems.length === 0 && !loading"
      :title="$t('videos.noVideos')"
      :description="''"
    />

    <!-- Feed Table (tablet+) -->
    <div v-else class="hidden tablet:block">
      <div class="card overflow-hidden">
        <table class="w-full text-left text-sm">
          <thead class="border-b border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-800/50">
            <tr>
              <th class="px-4 py-3 font-medium text-gray-600 dark:text-gray-400">{{ $t('videos.title') }}</th>
              <th class="px-4 py-3 font-medium text-gray-600 dark:text-gray-400 w-28 text-center">{{ $t('videos.views') }}</th>
              <th class="px-4 py-3 font-medium text-gray-600 dark:text-gray-400 w-24 text-center">{{ $t('videos.likes') }}</th>
              <th class="px-4 py-3 font-medium text-gray-600 dark:text-gray-400 w-24 text-center">{{ $t('videos.comments') }}</th>
              <th class="px-4 py-3 font-medium text-gray-600 dark:text-gray-400 w-24 text-center">{{ $t('videos.shares') }}</th>
              <th class="px-4 py-3 font-medium text-gray-600 dark:text-gray-400 w-28">{{ $t('videos.publishedAt') }}</th>
              <th class="px-4 py-3 font-medium text-gray-600 dark:text-gray-400 w-36 text-center">AI</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 dark:divide-gray-800">
            <tr
              v-for="item in feedItems"
              :key="`${item.platform}-${item.platformVideoId}`"
              class="cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors"
              @click="openDetail(item)"
            >
              <td class="px-4 py-3">
                <div class="flex items-center gap-3">
                  <!-- Thumbnail -->
                  <div class="h-12 w-20 flex-shrink-0 overflow-hidden rounded-md bg-gray-200 dark:bg-gray-700">
                    <img
                      v-if="item.thumbnailUrl"
                      :src="item.thumbnailUrl"
                      :alt="item.title"
                      class="h-full w-full object-cover"
                      loading="lazy"
                    />
                    <div v-else class="flex h-full w-full items-center justify-center">
                      <FilmIcon class="h-5 w-5 text-gray-400" />
                    </div>
                  </div>
                  <!-- Title + Platform -->
                  <div class="min-w-0">
                    <p class="truncate font-medium text-gray-900 dark:text-white">{{ item.title }}</p>
                    <div class="flex items-center gap-1.5 mt-0.5">
                      <span
                        class="inline-block h-2 w-2 rounded-full"
                        :style="{ backgroundColor: PLATFORM_CONFIG[item.platform]?.color || '#666' }"
                      ></span>
                      <span class="text-xs text-gray-500 dark:text-gray-400">
                        {{ PLATFORM_CONFIG[item.platform]?.label || item.platform }} · {{ item.channelName }}
                      </span>
                    </div>
                  </div>
                </div>
              </td>
              <td class="px-4 py-3 text-center tabular-nums text-gray-700 dark:text-gray-300">{{ formatCount(item.viewCount) }}</td>
              <td class="px-4 py-3 text-center tabular-nums text-gray-700 dark:text-gray-300">{{ formatCount(item.likeCount) }}</td>
              <td class="px-4 py-3 text-center tabular-nums text-gray-700 dark:text-gray-300">{{ formatCount(item.commentCount) }}</td>
              <td class="px-4 py-3 text-center tabular-nums text-gray-700 dark:text-gray-300">{{ formatCount(item.shareCount) }}</td>
              <td class="px-4 py-3 text-sm text-gray-500 dark:text-gray-400">{{ formatDate(item.publishedAt) }}</td>
              <td class="px-4 py-3 text-center" @click.stop>
                <!-- AI 도구 드롭다운 -->
                <div :ref="(el) => setDropdownRef(el, `${item.platform}-${item.platformVideoId}`)" class="relative inline-block">
                  <button
                    class="inline-flex items-center gap-1 rounded-md bg-primary-50 px-2 py-1 text-xs font-medium text-primary-700 hover:bg-primary-100 dark:bg-primary-900/30 dark:text-primary-300 dark:hover:bg-primary-900/50"
                    @click.stop="toggleDropdown(`${item.platform}-${item.platformVideoId}`)"
                  >
                    <SparklesIcon class="h-3.5 w-3.5" />
                    {{ $t('videosView.aiTools.button') }}
                    <ChevronDownIcon class="h-3 w-3" />
                  </button>
                  <div
                    v-if="openDropdownKey === `${item.platform}-${item.platformVideoId}`"
                    class="absolute right-0 z-50 mt-1 w-40 rounded-lg border border-gray-200 bg-white shadow-lg dark:border-gray-700 dark:bg-gray-800"
                  >
                    <button
                      class="flex w-full items-center gap-2 px-3 py-2 text-left text-xs text-gray-700 hover:bg-gray-50 dark:text-gray-300 dark:hover:bg-gray-700 rounded-t-lg"
                      @click.stop="openSeoModal(item)"
                    >
                      <MagnifyingGlassIcon class="h-3.5 w-3.5 text-primary-500" />
                      {{ $t('videosView.seoScore.button') }}
                    </button>
                    <button
                      class="flex w-full items-center gap-2 px-3 py-2 text-left text-xs text-gray-700 hover:bg-gray-50 dark:text-gray-300 dark:hover:bg-gray-700"
                      @click.stop="openPredictModal(item)"
                    >
                      <ChartBarIcon class="h-3.5 w-3.5 text-primary-500" />
                      {{ $t('videosView.viewsPrediction.button') }}
                    </button>
                    <button
                      v-if="isDraft(item)"
                      class="flex w-full items-center gap-2 px-3 py-2 text-left text-xs text-gray-700 hover:bg-gray-50 dark:text-gray-300 dark:hover:bg-gray-700"
                      @click.stop="openChecklistModal(item)"
                    >
                      <ClipboardDocumentCheckIcon class="h-3.5 w-3.5 text-primary-500" />
                      {{ $t('videosView.publishChecklist.button') }}
                    </button>
                    <button
                      v-if="isHighPerforming(item)"
                      class="flex w-full items-center gap-2 px-3 py-2 text-left text-xs text-gray-700 hover:bg-gray-50 dark:text-gray-300 dark:hover:bg-gray-700"
                      @click.stop="openRewriteModal(item)"
                    >
                      <SparklesIcon class="h-3.5 w-3.5 text-primary-500" />
                      {{ $t('videosView.aiRewrite.button') }}
                    </button>
                    <button
                      class="flex w-full items-center gap-2 px-3 py-2 text-left text-xs text-gray-700 hover:bg-gray-50 dark:text-gray-300 dark:hover:bg-gray-700 rounded-b-lg"
                      @click.stop="openRepurposeModal(item)"
                    >
                      <FilmIcon class="h-3.5 w-3.5 text-gray-400" />
                      {{ $t('videosView.repurpose.button') }}
                    </button>
                  </div>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Feed Cards (mobile) -->
    <div class="tablet:hidden space-y-3">
      <div
        v-for="item in feedItems"
        :key="`m-${item.platform}-${item.platformVideoId}`"
        class="card cursor-pointer p-3"
        @click="openDetail(item)"
      >
        <div class="flex gap-3">
          <!-- Thumbnail -->
          <div class="h-16 w-24 flex-shrink-0 overflow-hidden rounded-md bg-gray-200 dark:bg-gray-700">
            <img
              v-if="item.thumbnailUrl"
              :src="item.thumbnailUrl"
              :alt="item.title"
              class="h-full w-full object-cover"
              loading="lazy"
            />
            <div v-else class="flex h-full w-full items-center justify-center">
              <FilmIcon class="h-5 w-5 text-gray-400" />
            </div>
          </div>
          <!-- Info -->
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium text-gray-900 dark:text-white">{{ item.title }}</p>
            <div class="flex items-center gap-1.5 mt-0.5">
              <span
                class="inline-block h-2 w-2 rounded-full"
                :style="{ backgroundColor: PLATFORM_CONFIG[item.platform]?.color || '#666' }"
              ></span>
              <span class="text-xs text-gray-500 dark:text-gray-400">{{ PLATFORM_CONFIG[item.platform]?.label || item.platform }}</span>
            </div>
            <!-- Metrics -->
            <div class="mt-1.5 flex flex-wrap gap-x-3 gap-y-0.5 text-xs text-gray-500 dark:text-gray-400">
              <span>
                <EyeIcon class="inline h-3.5 w-3.5 mr-0.5" />
                {{ formatCount(item.viewCount) }}
              </span>
              <span>
                <HeartIcon class="inline h-3.5 w-3.5 mr-0.5" />
                {{ formatCount(item.likeCount) }}
              </span>
              <span>
                <ChatBubbleLeftIcon class="inline h-3.5 w-3.5 mr-0.5" />
                {{ formatCount(item.commentCount) }}
              </span>
            </div>
            <!-- AI Tools (mobile) -->
            <div class="mt-2 flex gap-1.5 flex-wrap" @click.stop>
              <button
                class="inline-flex items-center gap-1 rounded-md bg-primary-50 px-2 py-1 text-xs font-medium text-primary-700 hover:bg-primary-100 dark:bg-primary-900/30 dark:text-primary-300 dark:hover:bg-primary-900/50"
                @click.stop="openSeoModal(item)"
              >
                <MagnifyingGlassIcon class="h-3.5 w-3.5" />
                SEO
              </button>
              <button
                class="inline-flex items-center gap-1 rounded-md bg-primary-50 px-2 py-1 text-xs font-medium text-primary-700 hover:bg-primary-100 dark:bg-primary-900/30 dark:text-primary-300 dark:hover:bg-primary-900/50"
                @click.stop="openPredictModal(item)"
              >
                <ChartBarIcon class="h-3.5 w-3.5" />
                {{ $t('videosView.viewsPrediction.button') }}
              </button>
              <button
                v-if="isDraft(item)"
                class="inline-flex items-center gap-1 rounded-md bg-gray-50 px-2 py-1 text-xs font-medium text-gray-600 hover:bg-gray-100 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-gray-700"
                @click.stop="openChecklistModal(item)"
              >
                <ClipboardDocumentCheckIcon class="h-3.5 w-3.5" />
                {{ $t('videosView.publishChecklist.button') }}
              </button>
              <button
                v-if="isHighPerforming(item)"
                class="inline-flex items-center gap-1 rounded-md bg-gray-50 px-2 py-1 text-xs font-medium text-gray-600 hover:bg-gray-100 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-gray-700"
                @click.stop="openRewriteModal(item)"
              >
                <SparklesIcon class="h-3.5 w-3.5" />
                {{ $t('videosView.aiRewrite.button') }}
              </button>
              <button
                class="inline-flex items-center gap-1 rounded-md bg-gray-50 px-2 py-1 text-xs font-medium text-gray-600 hover:bg-gray-100 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-gray-700"
                @click.stop="openRepurposeModal(item)"
              >
                <FilmIcon class="h-3.5 w-3.5" />
                {{ $t('videosView.repurpose.button') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Detail Panel (slide) -->
    <VideoDetailPanel
      v-if="selectedItem"
      :item="selectedItem"
      @close="selectedItem = null"
    />

    <!-- SEO Score Modal -->
    <BaseModal
      v-model="seoModal.open"
      :title="$t('videosView.seoScore.modalTitle')"
      max-width="lg"
    >
      <div class="space-y-4">
        <div v-if="seoModal.loading" class="flex flex-col items-center py-8 gap-3">
          <LoadingSpinner />
          <p class="text-sm text-gray-500 dark:text-gray-400">{{ $t('videosView.seoScore.analyzing') }}</p>
        </div>
        <div
          v-else-if="seoModal.error"
          class="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/20 dark:text-red-300"
        >
          {{ seoModal.error }}
        </div>
        <div v-else-if="seoModal.result">
          <!-- Overall Score -->
          <div class="flex items-center justify-center py-4">
            <div class="relative flex h-28 w-28 items-center justify-center">
              <svg class="absolute inset-0 -rotate-90" viewBox="0 0 100 100">
                <circle cx="50" cy="50" r="42" fill="none" stroke="currentColor" class="text-gray-100 dark:text-gray-700" stroke-width="8" />
                <circle
                  cx="50" cy="50" r="42" fill="none"
                  :stroke="seoScoreColor(seoModal.result.overallScore)"
                  stroke-width="8"
                  stroke-linecap="round"
                  :stroke-dasharray="`${seoModal.result.overallScore * 2.639} 263.9`"
                />
              </svg>
              <div class="text-center">
                <p class="text-3xl font-bold text-gray-900 dark:text-white">{{ seoModal.result.overallScore }}</p>
                <p class="text-xs text-gray-500 dark:text-gray-400">/ 100</p>
              </div>
            </div>
          </div>
          <!-- Category Bars -->
          <div class="space-y-3">
            <div v-for="cat in seoModal.result.categories" :key="cat.name">
              <div class="mb-1 flex items-center justify-between text-xs">
                <span class="text-gray-700 dark:text-gray-300">{{ cat.name }}</span>
                <span class="font-medium text-gray-900 dark:text-white">{{ cat.score }}/{{ cat.maxScore }}</span>
              </div>
              <div class="h-2 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
                <div
                  class="h-full rounded-full transition-all"
                  :class="cat.score / cat.maxScore >= 0.7 ? 'bg-green-500' : cat.score / cat.maxScore >= 0.4 ? 'bg-amber-500' : 'bg-red-500'"
                  :style="{ width: `${(cat.score / cat.maxScore) * 100}%` }"
                />
              </div>
            </div>
          </div>
          <!-- Suggestions -->
          <div v-if="seoModal.result.suggestions.length" class="mt-4">
            <h4 class="mb-2 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
              {{ $t('videosView.seoScore.suggestions') }}
            </h4>
            <ul class="space-y-1.5">
              <li
                v-for="(s, i) in seoModal.result.suggestions"
                :key="i"
                class="flex items-start gap-2 text-sm text-gray-600 dark:text-gray-400"
              >
                <span class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-primary-500"></span>
                {{ s }}
              </li>
            </ul>
          </div>
          <p class="mt-3 text-xs text-gray-400 dark:text-gray-500">
            {{ $t('videosView.seoScore.creditsUsed', { count: seoModal.result.creditsUsed }) }}
          </p>
        </div>
        <div v-else class="space-y-2">
          <p class="text-sm text-gray-600 dark:text-gray-400">
            {{ $t('videosView.seoScore.prompt', { title: seoModal.item?.title }) }}
          </p>
          <p class="text-xs text-gray-400 dark:text-gray-500">{{ $t('videosView.seoScore.creditNotice') }}</p>
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-3">
          <button class="btn-secondary" @click="seoModal.open = false">{{ $t('action.close') }}</button>
          <button
            v-if="!seoModal.result && !seoModal.loading"
            class="btn-primary inline-flex items-center gap-2"
            @click="runSeoAnalysis"
          >
            <SparklesIcon class="h-4 w-4" />
            {{ $t('videosView.seoScore.run') }}
          </button>
        </div>
      </template>
    </BaseModal>

    <!-- Views Prediction Modal -->
    <BaseModal
      v-model="predictModal.open"
      :title="$t('videosView.viewsPrediction.modalTitle')"
      max-width="lg"
    >
      <div class="space-y-4">
        <div v-if="predictModal.loading" class="flex flex-col items-center py-8 gap-3">
          <LoadingSpinner />
          <p class="text-sm text-gray-500 dark:text-gray-400">{{ $t('videosView.viewsPrediction.analyzing') }}</p>
        </div>
        <div
          v-else-if="predictModal.error"
          class="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/20 dark:text-red-300"
        >
          {{ predictModal.error }}
        </div>
        <div v-else-if="predictModal.result">
          <!-- Prediction Cards -->
          <div class="grid grid-cols-3 gap-3">
            <div class="rounded-lg bg-gray-50 p-3 text-center dark:bg-gray-800/50">
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('videosView.viewsPrediction.24h') }}</p>
              <p class="mt-1 text-xl font-bold text-gray-900 dark:text-white">{{ formatCount(predictModal.result.predicted24h) }}</p>
            </div>
            <div class="rounded-lg bg-primary-50 p-3 text-center dark:bg-primary-900/20">
              <p class="text-xs text-primary-600 dark:text-primary-400">{{ $t('videosView.viewsPrediction.7d') }}</p>
              <p class="mt-1 text-xl font-bold text-primary-700 dark:text-primary-300">{{ formatCount(predictModal.result.predicted7d) }}</p>
            </div>
            <div class="rounded-lg bg-gray-50 p-3 text-center dark:bg-gray-800/50">
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('videosView.viewsPrediction.30d') }}</p>
              <p class="mt-1 text-xl font-bold text-gray-900 dark:text-white">{{ formatCount(predictModal.result.predicted30d) }}</p>
            </div>
          </div>
          <!-- Confidence + Comparison -->
          <div class="mt-3 flex gap-3">
            <div class="flex-1 rounded-lg border border-gray-200 p-3 dark:border-gray-700">
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('videosView.viewsPrediction.confidence') }}</p>
              <p class="mt-0.5 text-lg font-semibold text-gray-900 dark:text-white">{{ predictModal.result.confidenceScore }}%</p>
            </div>
            <div class="flex-1 rounded-lg border border-gray-200 p-3 dark:border-gray-700">
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('videosView.viewsPrediction.vsAverage') }}</p>
              <p
                class="mt-0.5 text-lg font-semibold"
                :class="predictModal.result.comparisonToAverage >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
              >
                {{ predictModal.result.comparisonToAverage >= 0 ? '+' : '' }}{{ predictModal.result.comparisonToAverage }}%
              </p>
            </div>
          </div>
          <!-- Influencing Factors -->
          <div v-if="predictModal.result.influencingFactors.length" class="mt-4">
            <h4 class="mb-2 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
              {{ $t('videosView.viewsPrediction.factors') }}
            </h4>
            <div class="space-y-2">
              <div
                v-for="(f, i) in predictModal.result.influencingFactors"
                :key="i"
                class="flex items-start gap-2 text-sm"
              >
                <span
                  class="mt-0.5 shrink-0 rounded-full px-1.5 py-0.5 text-[10px] font-semibold"
                  :class="f.impact === 'POSITIVE' ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                    : f.impact === 'NEGATIVE' ? 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
                    : 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'"
                >
                  {{ f.impact === 'POSITIVE' ? '+' : f.impact === 'NEGATIVE' ? '-' : '=' }}
                </span>
                <div>
                  <p class="font-medium text-gray-900 dark:text-white">{{ f.factor }}</p>
                  <p class="text-xs text-gray-500 dark:text-gray-400">{{ f.description }}</p>
                </div>
              </div>
            </div>
          </div>
          <p class="mt-3 text-xs text-gray-400 dark:text-gray-500">
            {{ $t('videosView.viewsPrediction.creditsUsed', { count: predictModal.result.creditsUsed }) }}
          </p>
        </div>
        <div v-else class="space-y-2">
          <p class="text-sm text-gray-600 dark:text-gray-400">
            {{ $t('videosView.viewsPrediction.prompt', { title: predictModal.item?.title }) }}
          </p>
          <p class="text-xs text-gray-400 dark:text-gray-500">{{ $t('videosView.viewsPrediction.creditNotice') }}</p>
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-3">
          <button class="btn-secondary" @click="predictModal.open = false">{{ $t('action.close') }}</button>
          <button
            v-if="!predictModal.result && !predictModal.loading"
            class="btn-primary inline-flex items-center gap-2"
            @click="runPrediction"
          >
            <SparklesIcon class="h-4 w-4" />
            {{ $t('videosView.viewsPrediction.run') }}
          </button>
        </div>
      </template>
    </BaseModal>

    <!-- Publish Checklist Modal -->
    <BaseModal
      v-model="checklistModal.open"
      :title="$t('videosView.publishChecklist.modalTitle')"
      max-width="lg"
    >
      <div class="space-y-4">
        <div v-if="checklistModal.loading" class="flex flex-col items-center py-8 gap-3">
          <LoadingSpinner />
          <p class="text-sm text-gray-500 dark:text-gray-400">{{ $t('videosView.publishChecklist.checking') }}</p>
        </div>
        <div
          v-else-if="checklistModal.error"
          class="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/20 dark:text-red-300"
        >
          {{ checklistModal.error }}
        </div>
        <div v-else-if="checklistModal.result">
          <!-- Ready Badge + Score -->
          <div class="flex items-center gap-4">
            <span
              class="rounded-full px-3 py-1 text-sm font-semibold"
              :class="checklistModal.result.ready
                ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                : 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'"
            >
              {{ checklistModal.result.ready ? $t('videosView.publishChecklist.ready') : $t('videosView.publishChecklist.notReady') }}
            </span>
            <span class="text-sm text-gray-500 dark:text-gray-400">
              {{ $t('videosView.publishChecklist.score', { score: checklistModal.result.score }) }}
            </span>
          </div>
          <!-- Checklist Items -->
          <div class="mt-4 space-y-2">
            <div
              v-for="item in checklistModal.result.items"
              :key="item.key"
              class="rounded-lg border p-3"
              :class="item.passed
                ? 'border-green-200 bg-green-50 dark:border-green-800 dark:bg-green-900/10'
                : 'border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-900/10'"
            >
              <div class="flex items-start gap-2">
                <span class="mt-0.5 shrink-0">
                  <CheckCircleIcon v-if="item.passed" class="h-4 w-4 text-green-600 dark:text-green-400" />
                  <ExclamationCircleIcon v-else class="h-4 w-4 text-amber-600 dark:text-amber-400" />
                </span>
                <div>
                  <p class="text-sm font-medium text-gray-900 dark:text-white">{{ item.key }}</p>
                  <p v-if="item.suggestion" class="mt-0.5 text-xs text-gray-600 dark:text-gray-400">{{ item.suggestion }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="space-y-2">
          <p class="text-sm text-gray-600 dark:text-gray-400">
            {{ $t('videosView.publishChecklist.prompt', { title: checklistModal.item?.title }) }}
          </p>
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-3">
          <button class="btn-secondary" @click="checklistModal.open = false">{{ $t('action.close') }}</button>
          <button
            v-if="!checklistModal.result && !checklistModal.loading"
            class="btn-primary inline-flex items-center gap-2"
            @click="runChecklist"
          >
            <ClipboardDocumentCheckIcon class="h-4 w-4" />
            {{ $t('videosView.publishChecklist.run') }}
          </button>
        </div>
      </template>
    </BaseModal>

    <!-- Repurpose Modal -->
    <BaseModal
      v-model="repurposeModal.open"
      :title="$t('videosView.repurpose.modalTitle')"
      max-width="lg"
    >
      <div class="space-y-4">
        <div v-if="repurposeModal.loading" class="flex flex-col items-center py-8 gap-3">
          <LoadingSpinner />
          <p class="text-sm text-gray-500 dark:text-gray-400">{{ $t('videosView.repurpose.analyzing') }}</p>
        </div>
        <div
          v-else-if="repurposeModal.error"
          class="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/20 dark:text-red-300"
        >
          {{ repurposeModal.error }}
        </div>
        <div v-else-if="repurposeModal.clips.length > 0" class="space-y-3">
          <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('videosView.repurpose.clipsFound', { count: repurposeModal.clips.length }) }}</p>
          <div
            v-for="(clip, i) in repurposeModal.clips"
            :key="i"
            class="rounded-lg border border-gray-200 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-800/50"
          >
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0 flex-1">
                <p class="text-sm font-medium text-gray-900 dark:text-white">{{ clip.title }}</p>
                <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
                  {{ formatSeconds(clip.startSeconds) }} ~ {{ formatSeconds(clip.endSeconds) }}
                </p>
                <p class="mt-1 text-xs text-gray-600 dark:text-gray-400">{{ clip.reason }}</p>
              </div>
              <span class="shrink-0 rounded-full bg-primary-100 px-2 py-0.5 text-xs font-semibold text-primary-700 dark:bg-primary-900/30 dark:text-primary-300">
                {{ clip.viralScore }}
              </span>
            </div>
          </div>
        </div>
        <div v-else class="space-y-2">
          <p class="text-sm text-gray-600 dark:text-gray-400">
            {{ $t('videosView.repurpose.prompt', { title: repurposeModal.item?.title }) }}
          </p>
          <p class="text-xs text-gray-400 dark:text-gray-500">{{ $t('videosView.repurpose.creditNotice') }}</p>
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-3">
          <button class="btn-secondary" @click="repurposeModal.open = false">{{ $t('action.close') }}</button>
          <button
            v-if="!repurposeModal.clips.length && !repurposeModal.loading"
            class="btn-primary inline-flex items-center gap-2"
            @click="runRepurpose"
          >
            <SparklesIcon class="h-4 w-4" />
            {{ $t('videosView.repurpose.run') }}
          </button>
        </div>
      </template>
    </BaseModal>

    <!-- AI Rewrite Modal -->
    <BaseModal
      v-model="rewriteModal.open"
      :title="$t('videosView.aiRewrite.modalTitle')"
    >
      <div class="space-y-4">
        <div v-if="rewriteModal.loading" class="flex flex-col items-center py-8 gap-3">
          <LoadingSpinner />
          <p class="text-sm text-gray-500 dark:text-gray-400">{{ $t('videosView.aiRewrite.analyzing') }}</p>
        </div>
        <div v-else-if="rewriteModal.error" class="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/20 dark:text-red-300">
          {{ rewriteModal.error }}
        </div>
        <div v-else-if="rewriteModal.result">
          <div class="rounded-lg border border-gray-200 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-800/50">
            <h4 class="mb-2 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
              {{ $t('videosView.aiRewrite.original') }}
            </h4>
            <p class="text-sm font-medium text-gray-900 dark:text-white">{{ rewriteModal.result.originalTitle }}</p>
            <p v-if="rewriteModal.result.originalDescription" class="mt-1 text-xs text-gray-600 dark:text-gray-400 line-clamp-3">
              {{ rewriteModal.result.originalDescription }}
            </p>
          </div>
          <div class="mt-4 space-y-3">
            <h4 class="text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
              {{ $t('videosView.aiRewrite.suggestions') }}
            </h4>
            <div
              v-for="(suggestion, idx) in rewriteModal.result.suggestions"
              :key="idx"
              class="cursor-pointer rounded-lg border p-4 transition-colors"
              :class="rewriteModal.selectedIdx === idx
                ? 'border-primary-500 bg-primary-50 dark:border-primary-400 dark:bg-primary-900/20'
                : 'border-gray-200 bg-white hover:border-gray-300 dark:border-gray-700 dark:bg-gray-800 dark:hover:border-gray-600'"
              @click="rewriteModal.selectedIdx = idx"
            >
              <div class="flex items-start justify-between gap-2">
                <div class="min-w-0 flex-1">
                  <p class="text-sm font-medium text-gray-900 dark:text-white">{{ suggestion.title }}</p>
                  <p class="mt-1 text-xs text-gray-600 dark:text-gray-400 line-clamp-2">{{ suggestion.description }}</p>
                  <div v-if="suggestion.tags.length > 0" class="mt-2 flex flex-wrap gap-1">
                    <span
                      v-for="tag in suggestion.tags.slice(0, 5)"
                      :key="tag"
                      class="rounded bg-gray-100 px-1.5 py-0.5 text-[11px] text-gray-600 dark:bg-gray-700 dark:text-gray-400"
                    >#{{ tag }}</span>
                  </div>
                </div>
                <span
                  class="ml-2 mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2"
                  :class="rewriteModal.selectedIdx === idx ? 'border-primary-500 bg-primary-500' : 'border-gray-300 dark:border-gray-600'"
                >
                  <span v-if="rewriteModal.selectedIdx === idx" class="h-2 w-2 rounded-full bg-white"></span>
                </span>
              </div>
            </div>
          </div>
          <p class="mt-3 text-xs text-gray-400 dark:text-gray-500">
            {{ $t('videosView.aiRewrite.creditsUsed', { count: rewriteModal.result.creditsUsed }) }}
          </p>
        </div>
        <div v-else class="space-y-3">
          <p class="text-sm text-gray-600 dark:text-gray-400">
            {{ $t('videosView.aiRewrite.prompt', { title: rewriteModal.item?.title }) }}
          </p>
          <p class="text-xs text-gray-400 dark:text-gray-500">{{ $t('videosView.aiRewrite.creditNotice') }}</p>
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-3">
          <button class="btn-secondary" @click="closeRewriteModal">{{ $t('action.cancel') }}</button>
          <button
            v-if="!rewriteModal.result && !rewriteModal.loading"
            class="btn-primary inline-flex items-center gap-2"
            @click="runRewrite"
          >
            <SparklesIcon class="h-4 w-4" />
            {{ $t('videosView.aiRewrite.run') }}
          </button>
          <button
            v-if="rewriteModal.result && rewriteModal.selectedIdx !== null"
            class="btn-primary"
            @click="closeRewriteModal"
          >
            {{ $t('action.close') }}
          </button>
        </div>
      </template>
    </BaseModal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import {
  PlusIcon,
  FilmIcon,
  EyeIcon,
  HeartIcon,
  ChatBubbleLeftIcon,
  SparklesIcon,
  ChevronDownIcon,
  MagnifyingGlassIcon,
  ChartBarIcon,
  ClipboardDocumentCheckIcon,
  CheckCircleIcon,
  ExclamationCircleIcon,
} from '@heroicons/vue/24/outline'
import type { Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'
import type { VideoFeedItem } from '@/types/video'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import VideoDetailPanel from '@/components/video/VideoDetailPanel.vue'
import { useVideoStore } from '@/stores/video'
import { storeToRefs } from 'pinia'
import { formatCount, formatDate } from '@/utils/format'
import { metaRewriteApi, type MetaRewriteResponse } from '@/api/metaRewrite'
import { repurposeApi } from '@/api/repurpose'
import type { RepurposeClip } from '@/types/repurpose'
import { videoSeoApi, type SeoScoreResponse } from '@/api/videoSeo'
import { viewsPredictionApi, type ViewsPredictionResponse } from '@/api/viewsPrediction'
import { publishChecklistApi, type PublishChecklistResponse } from '@/api/publishChecklist'

const videoStore = useVideoStore()
const { feedItems, feedPlatforms, feedErrors, isFeedLoading: loading } = storeToRefs(videoStore)

const selectedPlatform = ref<Platform | undefined>()
const sortBy = ref('recent')
const selectedItem = ref<VideoFeedItem | null>(null)

const availablePlatforms = computed(() => feedPlatforms.value || [])

// Top 3 by viewCount for AI rewrite eligibility
const topViewedIds = computed(() => {
  const sorted = [...feedItems.value].sort((a, b) => b.viewCount - a.viewCount)
  return new Set(sorted.slice(0, 3).map(i => `${i.platform}-${i.platformVideoId}`))
})

function isHighPerforming(item: VideoFeedItem) {
  return topViewedIds.value.has(`${item.platform}-${item.platformVideoId}`)
}

function isDraft(item: VideoFeedItem) {
  return (item as unknown as { status?: string }).status === 'DRAFT'
}

// ── AI 도구 드롭다운 ──────────────────────────────────────────────────
const openDropdownKey = ref<string | null>(null)
const dropdownRefs = ref<Map<string, Element>>(new Map())

function setDropdownRef(el: unknown, key: string) {
  if (el instanceof Element) {
    dropdownRefs.value.set(key, el)
  } else {
    dropdownRefs.value.delete(key)
  }
}

function toggleDropdown(key: string) {
  openDropdownKey.value = openDropdownKey.value === key ? null : key
}

function handleClickOutside(event: MouseEvent) {
  if (openDropdownKey.value === null) return
  const ref = dropdownRefs.value.get(openDropdownKey.value)
  if (ref && !ref.contains(event.target as Node)) {
    openDropdownKey.value = null
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  loadFeed()
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})

// ── SEO Score Modal ───────────────────────────────────────────────────
const seoModal = ref<{
  open: boolean
  loading: boolean
  error: string | null
  item: VideoFeedItem | null
  result: SeoScoreResponse | null
}>({ open: false, loading: false, error: null, item: null, result: null })

function openSeoModal(item: VideoFeedItem) {
  openDropdownKey.value = null
  seoModal.value = { open: true, loading: false, error: null, item, result: null }
}

async function runSeoAnalysis() {
  const item = seoModal.value.item
  if (!item) return
  const videoIdNum = parseInt(item.platformVideoId, 10)
  if (isNaN(videoIdNum)) {
    seoModal.value.error = 'SEO 분석은 내부 업로드 영상에서만 가능합니다'
    return
  }
  seoModal.value.loading = true
  seoModal.value.error = null
  try {
    seoModal.value.result = await videoSeoApi.analyze(videoIdNum)
  } catch (e) {
    seoModal.value.error = e instanceof Error ? e.message : 'SEO 분석 실패'
  } finally {
    seoModal.value.loading = false
  }
}

function seoScoreColor(score: number): string {
  if (score >= 70) return '#22c55e'
  if (score >= 40) return '#f59e0b'
  return '#ef4444'
}

// ── Views Prediction Modal ────────────────────────────────────────────
const predictModal = ref<{
  open: boolean
  loading: boolean
  error: string | null
  item: VideoFeedItem | null
  result: ViewsPredictionResponse | null
}>({ open: false, loading: false, error: null, item: null, result: null })

function openPredictModal(item: VideoFeedItem) {
  openDropdownKey.value = null
  predictModal.value = { open: true, loading: false, error: null, item, result: null }
}

async function runPrediction() {
  const item = predictModal.value.item
  if (!item) return
  const videoIdNum = parseInt(item.platformVideoId, 10)
  if (isNaN(videoIdNum)) {
    predictModal.value.error = '조회수 예측은 내부 업로드 영상에서만 가능합니다'
    return
  }
  predictModal.value.loading = true
  predictModal.value.error = null
  try {
    predictModal.value.result = await viewsPredictionApi.predict(videoIdNum)
  } catch (e) {
    predictModal.value.error = e instanceof Error ? e.message : '조회수 예측 실패'
  } finally {
    predictModal.value.loading = false
  }
}

// ── Publish Checklist Modal ───────────────────────────────────────────
const checklistModal = ref<{
  open: boolean
  loading: boolean
  error: string | null
  item: VideoFeedItem | null
  result: PublishChecklistResponse | null
}>({ open: false, loading: false, error: null, item: null, result: null })

function openChecklistModal(item: VideoFeedItem) {
  openDropdownKey.value = null
  checklistModal.value = { open: true, loading: false, error: null, item, result: null }
}

async function runChecklist() {
  const item = checklistModal.value.item
  if (!item) return
  const videoIdNum = parseInt(item.platformVideoId, 10)
  if (isNaN(videoIdNum)) {
    checklistModal.value.error = '게시 체크는 내부 업로드 영상에서만 가능합니다'
    return
  }
  checklistModal.value.loading = true
  checklistModal.value.error = null
  try {
    checklistModal.value.result = await publishChecklistApi.check(videoIdNum)
  } catch (e) {
    checklistModal.value.error = e instanceof Error ? e.message : '게시 체크 실패'
  } finally {
    checklistModal.value.loading = false
  }
}

// ── AI Rewrite Modal ──────────────────────────────────────────────────
const rewriteModal = ref<{
  open: boolean
  loading: boolean
  error: string | null
  item: VideoFeedItem | null
  result: MetaRewriteResponse | null
  selectedIdx: number | null
}>({ open: false, loading: false, error: null, item: null, result: null, selectedIdx: null })

function openRewriteModal(item: VideoFeedItem) {
  openDropdownKey.value = null
  rewriteModal.value = { open: true, loading: false, error: null, item, result: null, selectedIdx: null }
}

function closeRewriteModal() {
  rewriteModal.value.open = false
}

async function runRewrite() {
  const item = rewriteModal.value.item
  if (!item) return
  rewriteModal.value.loading = true
  rewriteModal.value.error = null
  try {
    const result = await metaRewriteApi.rewriteByPlatform({
      platform: item.platform,
      platformVideoId: item.platformVideoId,
      title: item.title,
      description: item.description,
    })
    rewriteModal.value.result = result
    rewriteModal.value.selectedIdx = 0
  } catch (e) {
    rewriteModal.value.error = e instanceof Error ? e.message : 'AI 리라이트 실패'
  } finally {
    rewriteModal.value.loading = false
  }
}

// ── Repurpose Modal ───────────────────────────────────────────────────
const repurposeModal = ref<{
  open: boolean
  loading: boolean
  error: string | null
  item: VideoFeedItem | null
  clips: RepurposeClip[]
}>({ open: false, loading: false, error: null, item: null, clips: [] })

function openRepurposeModal(item: VideoFeedItem) {
  openDropdownKey.value = null
  repurposeModal.value = { open: true, loading: false, error: null, item, clips: [] }
}

async function runRepurpose() {
  const item = repurposeModal.value.item
  if (!item) return
  repurposeModal.value.loading = true
  repurposeModal.value.error = null
  try {
    const videoIdNum = parseInt(item.platformVideoId, 10)
    if (isNaN(videoIdNum)) {
      throw new Error('숏폼 추출은 내부 업로드 영상에서만 가능합니다')
    }
    const job = await repurposeApi.analyzeForRepurpose(videoIdNum)
    repurposeModal.value.clips = job.clips
  } catch (e) {
    repurposeModal.value.error = e instanceof Error ? e.message : '숏폼 추출 실패'
  } finally {
    repurposeModal.value.loading = false
  }
}

function formatSeconds(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

function selectPlatform(p: Platform | undefined) {
  selectedPlatform.value = p
  loadFeed()
}

function loadFeed() {
  videoStore.feedFilter.platform = selectedPlatform.value
  videoStore.feedFilter.sort = sortBy.value
  videoStore.fetchFeed()
}

function openDetail(item: VideoFeedItem) {
  selectedItem.value = item
}
</script>
