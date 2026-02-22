<template>
  <div>
    <!-- Header -->
    <div class="mb-6">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-3">
          <SparklesIcon class="h-7 w-7 text-primary-600" />
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ $t('aiView.title') }}</h1>
        </div>
        <div class="flex items-center gap-3">
          <div
            class="flex items-center gap-2 rounded-lg border px-4 py-2 text-sm"
            :class="isLow ? 'border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-900/20' : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800'"
          >
            <SparklesIcon class="h-4 w-4" :class="isLow ? 'text-red-500' : 'text-primary-600'" />
            <span class="text-gray-600 dark:text-gray-300">{{ $t('aiView.usedToday') }}</span>
            <span class="font-bold text-primary-600 dark:text-primary-400">
              {{ creditsUsedToday.toLocaleString() }}
            </span>
            <span class="text-gray-400 dark:text-gray-500 mx-1">|</span>
            <span class="text-gray-600 dark:text-gray-300">{{ $t('aiView.remaining') }}</span>
            <span class="font-bold" :class="isLow ? 'text-red-600' : 'text-primary-600'">
              {{ balance.toLocaleString() }}
            </span>
          </div>
        </div>
      </div>

      <!-- Tab Navigation -->
      <div class="border-b border-gray-200 dark:border-gray-700">
        <nav class="-mb-px flex gap-6">
          <button
            class="border-b-2 px-1 py-3 text-sm font-medium transition-colors"
            :class="activeTab === 'tools'
              ? 'border-primary-500 text-primary-600 dark:text-primary-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:border-gray-300 dark:hover:border-gray-600 hover:text-gray-700 dark:hover:text-gray-300'"
            @click="activeTab = 'tools'"
          >
            {{ $t('aiView.tabs.tools') }}
          </button>
          <button
            class="border-b-2 px-1 py-3 text-sm font-medium transition-colors"
            :class="activeTab === 'presets'
              ? 'border-primary-500 text-primary-600 dark:text-primary-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:border-gray-300 dark:hover:border-gray-600 hover:text-gray-700 dark:hover:text-gray-300'"
            @click="activeTab = 'presets'"
          >
            {{ $t('aiView.tabs.presets') }}
          </button>
          <button
            class="border-b-2 px-1 py-3 text-sm font-medium transition-colors"
            :class="activeTab === 'history'
              ? 'border-primary-500 text-primary-600 dark:text-primary-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:border-gray-300 dark:hover:border-gray-600 hover:text-gray-700 dark:hover:text-gray-300'"
            @click="activeTab = 'history'"
          >
            {{ $t('aiView.tabs.history') }}
          </button>
        </nav>
      </div>
    </div>

    <PageGuide :title="$t('aiView.title')" :items="($tm('aiView.pageGuide') as string[])" />

    <!-- Tab Content -->
    <div class="mt-6">
      <!-- AI Tools Tab -->
      <div v-show="activeTab === 'tools'">
        <!-- AI Tool Cards Grid -->
    <div class="grid gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
      <div
        v-for="tool in aiTools"
        :key="tool.id"
        class="card group cursor-pointer transition-all hover:shadow-md hover:border-primary-200"
        role="button"
        tabindex="0"
        @click="handleToolClick(tool)"
        @keydown.enter="handleToolClick(tool)"
      >
        <div class="mb-3 flex items-start justify-between">
          <div
            class="flex h-10 w-10 items-center justify-center rounded-lg"
            :class="tool.iconBg"
          >
            <component :is="tool.icon" class="h-5 w-5" :class="tool.iconColor" />
          </div>
          <span class="badge-blue">{{ tool.credits }} {{ $t('aiView.credits') }}</span>
        </div>
        <h3 class="mb-1 font-semibold text-gray-900 dark:text-gray-100 group-hover:text-primary-600 transition-colors">
          {{ tool.name }}
        </h3>
        <p class="mb-4 text-sm leading-relaxed text-gray-500 dark:text-gray-400">{{ tool.description }}</p>
        <button
          class="btn-primary w-full"
          @click.stop="handleToolClick(tool)"
        >
          {{ $t('aiView.useButton') }}
        </button>
      </div>
    </div>
      </div>

      <!-- Presets Tab -->
      <div v-show="activeTab === 'presets'">
        <AiPresetList @preset-selected="handlePresetSelected" />
      </div>

      <!-- History Tab -->
      <div v-show="activeTab === 'history'">
        <AiUsageHistory />
      </div>
    </div>

    <!-- Tool Form Modal -->
    <Teleport to="body">
      <div v-if="selectedTool" class="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" :aria-label="selectedTool.name">
        <div class="fixed inset-0 bg-black/50" @click="closeTool" />
        <div class="relative max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-xl bg-white dark:bg-gray-800 shadow-xl">
          <!-- Modal Header -->
          <div class="sticky top-0 z-10 flex items-center justify-between border-b dark:border-gray-700 bg-white dark:bg-gray-800 px-6 py-4">
            <div class="flex items-center gap-3">
              <div
                class="flex h-8 w-8 items-center justify-center rounded-lg"
                :class="selectedTool.iconBg"
              >
                <component :is="selectedTool.icon" class="h-4 w-4" :class="selectedTool.iconColor" />
              </div>
              <div>
                <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ selectedTool.name }}</h2>
                <p class="text-xs text-gray-500 dark:text-gray-400">{{ selectedTool.credits }} {{ $t('aiView.creditsUsed') }}</p>
              </div>
            </div>
            <button
              class="rounded-lg p-2 text-gray-400 dark:text-gray-500 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-gray-600 dark:hover:text-gray-300"
              @click="closeTool"
            >
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <!-- Modal Body -->
          <div class="p-6">
            <!-- Error message -->
            <div
              v-if="aiStore.error"
              class="mb-4 rounded-lg border border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/20 px-4 py-3 text-sm text-red-700 dark:text-red-400"
            >
              {{ aiStore.error }}
            </div>

            <!-- Tool Forms -->

            <!-- 1. 제목/설명 생성 -->
            <template v-if="selectedTool.id === 'meta'">
              <div v-if="!aiStore.metaResult" class="relative space-y-4">
                <AiLoadingOverlay
                  :visible="aiStore.loading"
                  stage="generating"
                  type="title"
                />

                <div>
                  <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('aiView.form.script') }}</label>
                  <textarea
                    v-model="metaForm.script"
                    class="input-field min-h-[120px] resize-y"
                    :placeholder="$t('aiView.form.scriptPlaceholder')"
                    :disabled="aiStore.loading"
                  />
                </div>
                <div>
                  <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('aiView.form.platform') }}</label>
                  <div class="flex flex-wrap gap-2">
                    <label
                      v-for="p in platforms"
                      :key="p.value"
                      class="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm transition-colors"
                      :class="[
                        metaForm.platforms.includes(p.value)
                          ? 'border-primary-300 dark:border-primary-600 bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300'
                          : 'border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700',
                        aiStore.loading ? 'opacity-50 pointer-events-none' : ''
                      ]"
                    >
                      <input
                        v-model="metaForm.platforms"
                        type="checkbox"
                        :value="p.value"
                        class="sr-only"
                        :disabled="aiStore.loading"
                      />
                      {{ p.label }}
                    </label>
                  </div>
                </div>
                <div class="grid gap-4 tablet:grid-cols-2">
                  <div>
                    <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('aiView.form.tone') }}</label>
                    <select v-model="metaForm.tone" class="input-field" :disabled="aiStore.loading">
                      <option value="FRIENDLY">{{ $t('aiView.tones.friendly') }}</option>
                      <option value="PROFESSIONAL">{{ $t('aiView.tones.professional') }}</option>
                      <option value="HUMOROUS">{{ $t('aiView.tones.humorous') }}</option>
                    </select>
                  </div>
                  <div>
                    <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('aiView.form.category') }}</label>
                    <select v-model="metaForm.category" class="input-field" :disabled="aiStore.loading">
                      <option value="">{{ $t('aiView.form.select') }}</option>
                      <option v-for="cat in categories" :key="cat" :value="cat">
                        {{ cat }}
                      </option>
                    </select>
                  </div>
                </div>
                <div class="flex justify-end gap-3 pt-2">
                  <button class="btn-secondary" @click="closeTool" :disabled="aiStore.loading">{{ $t('aiView.form.cancel') }}</button>
                  <button
                    class="btn-primary inline-flex items-center gap-2"
                    :disabled="!metaForm.script || metaForm.platforms.length === 0 || !metaForm.category || aiStore.loading"
                    @click="submitMeta"
                  >
                    <SparklesIcon class="h-4 w-4" />
                    {{ $t('aiView.form.generate') }}
                  </button>
                </div>
              </div>

              <!-- Meta Results -->
              <div v-else class="space-y-4">
                <div
                  v-for="(result, idx) in aiStore.metaResult.platforms"
                  :key="idx"
                  class="rounded-lg border border-gray-200 dark:border-gray-700 p-4"
                  :style="{ animationDelay: `${idx * 150}ms` }"
                  style="animation: ai-item-fade-in 500ms ease-out backwards"
                >
                  <h4 class="mb-3 font-medium text-gray-900 dark:text-gray-100">
                    {{ platformLabel(result.platform) }}
                  </h4>
                  <div class="mb-3">
                    <p class="mb-1 text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('aiView.results.titleCandidates') }}</p>
                    <ul class="space-y-1">
                      <li
                        v-for="(title, ti) in result.titleCandidates"
                        :key="ti"
                        class="flex items-start gap-2 rounded-md bg-gray-50 dark:bg-gray-900 px-3 py-2 text-sm text-gray-800 dark:text-gray-200"
                      >
                        <span class="mt-0.5 shrink-0 text-xs font-medium text-primary-600">{{ ti + 1 }}.</span>
                        <AiTypingEffect v-if="idx === 0 && ti === 0" :text="title" :speed="20" />
                        <span v-else>{{ title }}</span>
                      </li>
                    </ul>
                  </div>
                  <div class="mb-3">
                    <p class="mb-1 text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('aiView.results.description') }}</p>
                    <p class="whitespace-pre-wrap rounded-md bg-gray-50 dark:bg-gray-900 px-3 py-2 text-sm text-gray-700 dark:text-gray-300">
                      {{ result.description }}
                    </p>
                  </div>
                  <div>
                    <p class="mb-1 text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('aiView.results.tags') }}</p>
                    <div class="flex flex-wrap gap-1">
                      <span
                        v-for="tag in result.hashtags"
                        :key="tag"
                        class="badge-blue"
                      >
                        #{{ tag }}
                      </span>
                    </div>
                  </div>
                </div>

                <div v-if="commonHashtags.length > 0">
                  <p class="mb-1 text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('aiView.results.commonHashtags') }}</p>
                  <div class="flex flex-wrap gap-1">
                    <span
                      v-for="tag in commonHashtags"
                      :key="tag"
                      class="badge-blue"
                    >
                      #{{ tag }}
                    </span>
                  </div>
                </div>

                <div class="flex items-center justify-between border-t dark:border-gray-700 pt-4">
                  <p class="text-xs text-gray-500 dark:text-gray-400">
                    사용 크레딧: {{ aiStore.metaResult.creditsUsed }} / 잔여: {{ aiStore.metaResult.creditsRemaining }}
                  </p>
                  <div class="flex gap-3">
                    <button class="btn-secondary" @click="resetAndClose">{{ $t('aiView.results.close') }}</button>
                    <button class="btn-primary" @click="resetTool">{{ $t('aiView.results.regenerate') }}</button>
                  </div>
                </div>
              </div>
            </template>

            <!-- 2. 해시태그 추천 -->
            <template v-else-if="selectedTool.id === 'hashtags'">
              <div v-if="!aiStore.hashtagResult" class="relative space-y-4">
                <AiLoadingOverlay
                  :visible="aiStore.loading"
                  stage="generating"
                  type="hashtags"
                />

                <div>
                  <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('aiView.videoTitle') }}</label>
                  <input
                    v-model="hashtagForm.title"
                    type="text"
                    class="input-field"
                    :placeholder="$t('aiView.videoTitlePlaceholder')"
                    :disabled="aiStore.loading"
                  />
                </div>
                <div>
                  <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('aiView.form.category') }}</label>
                  <select v-model="hashtagForm.category" class="input-field" :disabled="aiStore.loading">
                    <option value="">{{ $t('aiView.form.select') }}</option>
                    <option v-for="cat in categories" :key="cat" :value="cat">
                      {{ cat }}
                    </option>
                  </select>
                </div>
                <div>
                  <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('aiView.form.platform') }}</label>
                  <div class="flex flex-wrap gap-2">
                    <label
                      v-for="p in platforms"
                      :key="p.value"
                      class="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm transition-colors"
                      :class="[
                        hashtagForm.platforms.includes(p.value)
                          ? 'border-primary-300 dark:border-primary-600 bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300'
                          : 'border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700',
                        aiStore.loading ? 'opacity-50 pointer-events-none' : ''
                      ]"
                    >
                      <input
                        v-model="hashtagForm.platforms"
                        type="checkbox"
                        :value="p.value"
                        class="sr-only"
                        :disabled="aiStore.loading"
                      />
                      {{ p.label }}
                    </label>
                  </div>
                </div>
                <div class="flex justify-end gap-3 pt-2">
                  <button class="btn-secondary" @click="closeTool" :disabled="aiStore.loading">{{ $t('aiView.form.cancel') }}</button>
                  <button
                    class="btn-primary inline-flex items-center gap-2"
                    :disabled="!hashtagForm.title || !hashtagForm.category || hashtagForm.platforms.length === 0 || aiStore.loading"
                    @click="submitHashtags"
                  >
                    <HashtagIcon class="h-4 w-4" />
                    {{ $t('aiView.form.recommend') }}
                  </button>
                </div>
              </div>

              <!-- Hashtag Results -->
              <div v-else class="space-y-4">
                <div
                  v-for="(item, idx) in aiStore.hashtagResult.platforms"
                  :key="item.platform"
                  class="rounded-lg border border-gray-200 dark:border-gray-700 p-4"
                  :style="{ animationDelay: `${idx * 150}ms` }"
                  style="animation: ai-item-fade-in 500ms ease-out backwards"
                >
                  <h4 class="mb-2 font-medium text-gray-900 dark:text-gray-100">{{ platformLabel(item.platform) }}</h4>
                  <div class="flex flex-wrap gap-1.5">
                    <span
                      v-for="tag in item.hashtags"
                      :key="tag"
                      class="cursor-pointer rounded-full bg-blue-50 dark:bg-blue-900/20 px-2.5 py-1 text-xs font-medium text-blue-700 dark:text-blue-400 transition-colors hover:bg-blue-100 dark:hover:bg-blue-900/30"
                      @click="copyToClipboard(tag)"
                    >
                      #{{ tag }}
                    </span>
                  </div>
                </div>

                <div class="flex items-center justify-between border-t dark:border-gray-700 pt-4">
                  <p class="text-xs text-gray-500 dark:text-gray-400">
                    사용 크레딧: {{ aiStore.hashtagResult.creditsUsed }} / 잔여: {{ aiStore.hashtagResult.creditsRemaining }}
                  </p>
                  <div class="flex gap-3">
                    <button class="btn-secondary" @click="resetAndClose">{{ $t('aiView.results.close') }}</button>
                    <button class="btn-primary" @click="resetTool">{{ $t('aiView.results.reRecommend') }}</button>
                  </div>
                </div>
              </div>
            </template>

            <!-- 7. 콘텐츠 아이디어 -->
            <template v-else-if="selectedTool.id === 'ideas'">
              <div v-if="!aiStore.ideasResult" class="relative space-y-4">
                <AiLoadingOverlay
                  :visible="aiStore.loading"
                  stage="generating"
                  type="insight"
                />

                <div>
                  <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('aiView.form.category') }}</label>
                  <select v-model="ideasForm.category" class="input-field" :disabled="aiStore.loading">
                    <option value="">{{ $t('aiView.form.select') }}</option>
                    <option v-for="cat in categories" :key="cat" :value="cat">
                      {{ cat }}
                    </option>
                  </select>
                </div>
                <div class="flex justify-end gap-3 pt-2">
                  <button class="btn-secondary" @click="closeTool" :disabled="aiStore.loading">{{ $t('aiView.form.cancel') }}</button>
                  <button
                    class="btn-primary inline-flex items-center gap-2"
                    :disabled="!ideasForm.category || aiStore.loading"
                    @click="submitIdeas"
                  >
                    <LightBulbIcon class="h-4 w-4" />
                    {{ $t('aiView.form.generateIdea') }}
                  </button>
                </div>
              </div>

              <!-- Ideas Results -->
              <div v-else class="space-y-3">
                <div
                  v-for="(idea, idx) in aiStore.ideasResult.ideas"
                  :key="idx"
                  class="rounded-lg border border-gray-200 dark:border-gray-700 p-4"
                  :style="{ animationDelay: `${idx * 150}ms` }"
                  style="animation: ai-item-fade-in 500ms ease-out backwards"
                >
                  <div class="mb-2 flex items-start justify-between">
                    <h4 class="font-medium text-gray-900 dark:text-gray-100">
                      <AiTypingEffect v-if="idx === 0" :text="idea.title" :speed="25" />
                      <span v-else>{{ idea.title }}</span>
                    </h4>
                    <span
                      class="ml-2 shrink-0 rounded-full px-2 py-0.5 text-xs font-medium"
                      :class="idea.difficulty === 'EASY'
                        ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400'
                        : idea.difficulty === 'MEDIUM'
                          ? 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400'
                          : 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400'"
                    >
                      {{ idea.difficulty }}
                    </span>
                  </div>
                  <p class="mb-2 text-sm text-gray-600 dark:text-gray-300">{{ idea.description }}</p>
                  <p class="text-xs text-gray-400 dark:text-gray-500">
                    예상 반응: {{ idea.expectedReaction }}
                  </p>
                </div>

                <div class="flex items-center justify-between border-t dark:border-gray-700 pt-4">
                  <p class="text-xs text-gray-500 dark:text-gray-400">
                    사용 크레딧: {{ aiStore.ideasResult.creditsUsed }} / 잔여: {{ aiStore.ideasResult.creditsRemaining }}
                  </p>
                  <div class="flex gap-3">
                    <button class="btn-secondary" @click="resetAndClose">{{ $t('aiView.results.close') }}</button>
                    <button class="btn-primary" @click="resetTool">{{ $t('aiView.results.regenerate') }}</button>
                  </div>
                </div>
              </div>
            </template>

            <!-- 8. 성과 리포트 -->
            <template v-else-if="selectedTool.id === 'report'">
              <div v-if="!aiStore.reportResult" class="relative space-y-4">
                <AiLoadingOverlay
                  :visible="aiStore.loading"
                  stage="analyzing"
                  type="insight"
                />

                <div>
                  <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">분석 기간</label>
                  <div class="flex gap-3">
                    <button
                      v-for="p in reportPeriods"
                      :key="p.value"
                      class="flex-1 rounded-lg border px-4 py-3 text-center text-sm font-medium transition-colors"
                      :class="[
                        reportForm.period === p.value
                          ? 'border-primary-300 dark:border-primary-600 bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300'
                          : 'border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700',
                        aiStore.loading ? 'opacity-50 pointer-events-none' : ''
                      ]"
                      @click="reportForm.period = p.value"
                      :disabled="aiStore.loading"
                    >
                      {{ p.label }}
                    </button>
                  </div>
                </div>
                <div class="flex justify-end gap-3 pt-2">
                  <button class="btn-secondary" @click="closeTool" :disabled="aiStore.loading">{{ $t('aiView.form.cancel') }}</button>
                  <button
                    class="btn-primary inline-flex items-center gap-2"
                    :disabled="aiStore.loading"
                    @click="submitReport"
                  >
                    <ChartBarIcon class="h-4 w-4" />
                    {{ $t('aiView.form.generateReport') }}
                  </button>
                </div>
              </div>

              <!-- Report Results -->
              <div v-else class="space-y-4">
                <div
                  class="prose prose-sm dark:prose-invert max-w-none rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900 p-4"
                  v-html="renderMarkdown(aiStore.reportResult.reportMarkdown)"
                />

                <div class="flex items-center justify-between border-t dark:border-gray-700 pt-4">
                  <p class="text-xs text-gray-500 dark:text-gray-400">
                    사용 크레딧: {{ aiStore.reportResult.creditsUsed }} / 잔여: {{ aiStore.reportResult.creditsRemaining }}
                  </p>
                  <div class="flex gap-3">
                    <button class="btn-secondary" @click="resetAndClose">{{ $t('aiView.results.close') }}</button>
                    <button class="btn-primary" @click="resetTool">{{ $t('aiView.results.regenerate') }}</button>
                  </div>
                </div>
              </div>
            </template>

            <!-- 9. AI 전략 코치 -->
            <template v-else-if="selectedTool.id === 'strategy-coach'">
              <div v-if="!aiStore.strategyCoachResult" class="relative space-y-4">
                <AiLoadingOverlay
                  :visible="aiStore.loading"
                  stage="analyzing"
                  type="insight"
                />

                <div>
                  <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('aiView.strategyCoach.focusArea') }}</label>
                  <select v-model="strategyCoachForm.focusArea" class="input-field" :disabled="aiStore.loading">
                    <option value="">{{ $t('aiView.strategyCoach.focusOptions.all') }}</option>
                    <option value="콘텐츠">{{ $t('aiView.strategyCoach.focusOptions.content') }}</option>
                    <option value="성장">{{ $t('aiView.strategyCoach.focusOptions.growth') }}</option>
                    <option value="수익">{{ $t('aiView.strategyCoach.focusOptions.revenue') }}</option>
                    <option value="플랫폼">{{ $t('aiView.strategyCoach.focusOptions.platform') }}</option>
                  </select>
                </div>
                <div>
                  <label class="flex items-center gap-2 text-sm font-medium text-gray-700 dark:text-gray-300">
                    <input
                      v-model="strategyCoachForm.includeCompetitors"
                      type="checkbox"
                      class="rounded border-gray-300 dark:border-gray-600 text-primary-600 focus:ring-primary-500"
                      :disabled="aiStore.loading"
                    />
                    {{ $t('aiView.strategyCoach.includeCompetitors') }}
                  </label>
                </div>
                <div class="flex justify-end gap-3 pt-2">
                  <button class="btn-secondary" @click="closeTool" :disabled="aiStore.loading">{{ $t('aiView.form.cancel') }}</button>
                  <button
                    class="btn-primary inline-flex items-center gap-2"
                    :disabled="aiStore.loading"
                    @click="submitStrategyCoach"
                  >
                    <RocketLaunchIcon class="h-4 w-4" />
                    {{ $t('aiView.form.strategyAnalysis') }}
                  </button>
                </div>
              </div>

              <!-- Strategy Coach Results -->
              <div v-else class="space-y-4">
                <!-- 종합 전략 -->
                <div class="rounded-lg border border-primary-200 dark:border-primary-800 bg-primary-50 dark:bg-primary-900/20 p-4">
                  <h4 class="mb-2 font-semibold text-primary-700 dark:text-primary-300">{{ $t('aiView.strategyCoach.overallStrategy') }}</h4>
                  <p class="text-sm text-primary-800 dark:text-primary-200">{{ aiStore.strategyCoachResult.overallStrategy }}</p>
                </div>

                <!-- 콘텐츠 추천 -->
                <div>
                  <h4 class="mb-2 font-semibold text-gray-900 dark:text-gray-100">{{ $t('aiView.strategyCoach.contentRecommendations') }}</h4>
                  <div class="space-y-2">
                    <div
                      v-for="(rec, idx) in aiStore.strategyCoachResult.contentRecommendations"
                      :key="idx"
                      class="rounded-lg border border-gray-200 dark:border-gray-700 p-3"
                      :style="{ animationDelay: `${idx * 100}ms` }"
                      style="animation: ai-item-fade-in 500ms ease-out backwards"
                    >
                      <div class="mb-1 flex items-center justify-between">
                        <span class="font-medium text-gray-900 dark:text-gray-100">{{ rec.topic }}</span>
                        <span
                          class="rounded-full px-2 py-0.5 text-xs font-medium"
                          :class="rec.priority === 'HIGH'
                            ? 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400'
                            : rec.priority === 'MEDIUM'
                              ? 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400'
                              : 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400'"
                        >
                          {{ rec.priority }}
                        </span>
                      </div>
                      <p class="text-xs text-gray-500 dark:text-gray-400">{{ rec.targetPlatform }} | {{ rec.reason }}</p>
                      <p class="mt-1 text-xs text-primary-600 dark:text-primary-400">예상 영향: {{ rec.expectedImpact }}</p>
                    </div>
                  </div>
                </div>

                <!-- 플랫폼 전략 -->
                <div>
                  <h4 class="mb-2 font-semibold text-gray-900 dark:text-gray-100">{{ $t('aiView.strategyCoach.platformStrategy') }}</h4>
                  <div class="space-y-2">
                    <div
                      v-for="(ps, idx) in aiStore.strategyCoachResult.platformStrategy"
                      :key="idx"
                      class="rounded-lg border border-gray-200 dark:border-gray-700 p-3"
                      :style="{ animationDelay: `${idx * 100}ms` }"
                      style="animation: ai-item-fade-in 500ms ease-out backwards"
                    >
                      <h5 class="mb-1 font-medium text-gray-900 dark:text-gray-100">{{ ps.platform }}</h5>
                      <div class="grid gap-1 text-xs">
                        <p class="text-gray-600 dark:text-gray-300"><span class="font-medium">{{ $t('aiView.strategyCoach.strength') }}</span> {{ ps.strength }}</p>
                        <p class="text-gray-600 dark:text-gray-300"><span class="font-medium">{{ $t('aiView.strategyCoach.opportunity') }}</span> {{ ps.opportunity }}</p>
                        <p class="text-primary-600 dark:text-primary-400"><span class="font-medium">{{ $t('aiView.strategyCoach.action') }}</span> {{ ps.action }}</p>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 타이밍 조언 -->
                <div>
                  <h4 class="mb-2 font-semibold text-gray-900 dark:text-gray-100">{{ $t('aiView.strategyCoach.timingAdvice') }}</h4>
                  <div class="space-y-2">
                    <div
                      v-for="(ta, idx) in aiStore.strategyCoachResult.timingAdvice"
                      :key="idx"
                      class="rounded-lg border border-gray-200 dark:border-gray-700 p-3"
                    >
                      <p class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ ta.recommendation }}</p>
                      <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">{{ ta.reason }}</p>
                      <p class="mt-1 text-xs text-emerald-600 dark:text-emerald-400">예상 향상: {{ ta.expectedBoost }}</p>
                    </div>
                  </div>
                </div>

                <div class="flex justify-end gap-3 border-t dark:border-gray-700 pt-4">
                  <button class="btn-secondary" @click="resetAndClose">{{ $t('aiView.results.close') }}</button>
                  <button class="btn-primary" @click="resetTool">{{ $t('aiView.form.reAnalyze') }}</button>
                </div>
              </div>
            </template>

            <!-- 10. 수익 분석 리포트 -->
            <template v-else-if="selectedTool.id === 'revenue-report'">
              <div v-if="!aiStore.revenueReportResult" class="relative space-y-4">
                <AiLoadingOverlay
                  :visible="aiStore.loading"
                  stage="analyzing"
                  type="insight"
                />

                <div>
                  <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">분석 기간</label>
                  <div class="flex gap-3">
                    <button
                      v-for="p in reportPeriods"
                      :key="p.value"
                      class="flex-1 rounded-lg border px-4 py-3 text-center text-sm font-medium transition-colors"
                      :class="[
                        revenueReportForm.period === p.value
                          ? 'border-primary-300 dark:border-primary-600 bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300'
                          : 'border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700',
                        aiStore.loading ? 'opacity-50 pointer-events-none' : ''
                      ]"
                      @click="revenueReportForm.period = p.value"
                      :disabled="aiStore.loading"
                    >
                      {{ p.label }}
                    </button>
                  </div>
                </div>
                <div class="flex justify-end gap-3 pt-2">
                  <button class="btn-secondary" @click="closeTool" :disabled="aiStore.loading">{{ $t('aiView.form.cancel') }}</button>
                  <button
                    class="btn-primary inline-flex items-center gap-2"
                    :disabled="aiStore.loading"
                    @click="submitRevenueReport"
                  >
                    <CurrencyDollarIcon class="h-4 w-4" />
                    {{ $t('aiView.form.generateReport') }}
                  </button>
                </div>
              </div>

              <!-- Revenue Report Results -->
              <div v-else class="space-y-4">
                <div
                  class="prose prose-sm dark:prose-invert max-w-none rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900 p-4"
                  v-html="renderMarkdown(aiStore.revenueReportResult.reportMarkdown)"
                />

                <!-- 수익 최적화 팁 -->
                <div v-if="aiStore.revenueReportResult.optimizationTips.length > 0">
                  <h4 class="mb-2 font-semibold text-gray-900 dark:text-gray-100">{{ $t('aiView.revenueReport.optimizationTips') }}</h4>
                  <ul class="space-y-1">
                    <li
                      v-for="(tip, idx) in aiStore.revenueReportResult.optimizationTips"
                      :key="idx"
                      class="flex items-start gap-2 rounded-md bg-emerald-50 dark:bg-emerald-900/20 px-3 py-2 text-sm text-emerald-800 dark:text-emerald-200"
                    >
                      <CurrencyDollarIcon class="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
                      {{ tip }}
                    </li>
                  </ul>
                </div>

                <!-- 플랫폼별 분석 -->
                <div v-if="aiStore.revenueReportResult.platformBreakdown.length > 0">
                  <h4 class="mb-2 font-semibold text-gray-900 dark:text-gray-100">{{ $t('aiView.revenueReport.platformBreakdown') }}</h4>
                  <div class="space-y-2">
                    <div
                      v-for="(pb, idx) in aiStore.revenueReportResult.platformBreakdown"
                      :key="idx"
                      class="rounded-lg border border-gray-200 dark:border-gray-700 p-3"
                    >
                      <h5 class="mb-1 font-medium text-gray-900 dark:text-gray-100">{{ pb.platform }}</h5>
                      <div class="grid gap-1 text-xs">
                        <p class="text-gray-600 dark:text-gray-300"><span class="font-medium">{{ $t('aiView.revenueReport.contribution') }}</span> {{ pb.contribution }}</p>
                        <p class="text-gray-600 dark:text-gray-300"><span class="font-medium">{{ $t('aiView.revenueReport.trend') }}</span> {{ pb.trend }}</p>
                        <p class="text-primary-600 dark:text-primary-400"><span class="font-medium">{{ $t('aiView.revenueReport.suggestion') }}</span> {{ pb.suggestion }}</p>
                      </div>
                    </div>
                  </div>
                </div>

                <div class="flex justify-end gap-3 border-t dark:border-gray-700 pt-4">
                  <button class="btn-secondary" @click="resetAndClose">{{ $t('aiView.results.close') }}</button>
                  <button class="btn-primary" @click="resetTool">{{ $t('aiView.results.regenerate') }}</button>
                </div>
              </div>
            </template>

            <!-- Placeholder forms for other tools -->
            <template v-else>
              <div class="space-y-4">
                <div class="rounded-lg border border-dashed border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-900 px-6 py-12 text-center">
                  <component
                    :is="selectedTool.icon"
                    class="mx-auto mb-3 h-10 w-10 text-gray-400 dark:text-gray-500"
                  />
                  <h3 class="mb-1 text-sm font-medium text-gray-900 dark:text-gray-100">{{ selectedTool.name }}</h3>
                  <p class="text-sm text-gray-500 dark:text-gray-400">
                    {{ $t('aiView.placeholder.preparing') }}
                  </p>
                </div>
                <div class="flex justify-end">
                  <button class="btn-secondary" @click="closeTool">{{ $t('aiView.results.close') }}</button>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Insufficient Credit Modal -->
    <Teleport to="body">
      <div v-if="showCreditModal" class="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-label="크레딧 부족">
        <div class="fixed inset-0 bg-black/50" @click="showCreditModal = false" />
        <div class="relative max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl bg-white dark:bg-gray-800 shadow-xl">
          <div class="flex items-center justify-between border-b dark:border-gray-700 px-6 py-4">
            <div>
              <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ $t('aiView.creditModal.title') }}</h2>
              <p class="mt-0.5 text-sm text-gray-500 dark:text-gray-400">
                이 기능에는 <span class="font-medium text-primary-600">{{ requiredCredits }} 크레딧</span>이 필요합니다.
                (잔여: {{ balance.toLocaleString() }})
              </p>
            </div>
            <button
              class="rounded-lg p-2 text-gray-400 dark:text-gray-500 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-gray-600 dark:hover:text-gray-300"
              @click="showCreditModal = false"
            >
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <div class="p-6">
            <div class="grid gap-3 tablet:grid-cols-2">
              <div
                v-for="pkg in creditPackages"
                :key="pkg.name"
                class="cursor-pointer rounded-lg border-2 p-4 transition-all"
                :class="selectedPackage === pkg.name
                  ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                  : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'"
                @click="selectedPackage = pkg.name"
              >
                <h4 class="mb-1 font-semibold text-gray-900 dark:text-gray-100">{{ pkg.name }}</h4>
                <div class="mb-2 flex items-baseline gap-1">
                  <span class="text-2xl font-bold text-primary-600">
                    {{ pkg.price.toLocaleString() }}
                  </span>
                  <span class="text-sm text-gray-500 dark:text-gray-400">원</span>
                </div>
                <ul class="space-y-1 text-xs text-gray-600 dark:text-gray-300">
                  <li class="flex items-center gap-1.5">
                    <SparklesIcon class="h-3.5 w-3.5 text-primary-500" />
                    {{ pkg.credits.toLocaleString() }} 크레딧
                  </li>
                  <li class="flex items-center gap-1.5">
                    <span class="inline-block h-3.5 w-3.5 text-center text-primary-500">~</span>
                    크레딧당 {{ pkg.pricePerCredit }}원
                  </li>
                  <li class="flex items-center gap-1.5">
                    <ClockIcon class="h-3.5 w-3.5 text-primary-500" />
                    {{ pkg.validDays }}일 유효
                  </li>
                </ul>
              </div>
            </div>

            <div class="mt-6 flex justify-end gap-3">
              <button class="btn-secondary" @click="showCreditModal = false">{{ $t('aiView.form.cancel') }}</button>
              <button
                class="btn-primary"
                :disabled="!selectedPackage"
                @click="handlePurchase"
              >
                {{ $t('aiView.creditModal.purchase') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  SparklesIcon,
  DocumentTextIcon,
  HashtagIcon,
  MicrophoneIcon,
  DocumentMagnifyingGlassIcon,
  ChatBubbleLeftRightIcon,
  ClockIcon,
  LightBulbIcon,
  ChartBarIcon,
  XMarkIcon,
  RocketLaunchIcon,
  CurrencyDollarIcon,
} from '@heroicons/vue/24/outline'
import AiLoadingOverlay from '@/components/ai/AiLoadingOverlay.vue'
import AiTypingEffect from '@/components/ai/AiTypingEffect.vue'
import AiPresetList from '@/components/ai/AiPresetList.vue'
import AiUsageHistory from '@/components/ai/AiUsageHistory.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import { useAiStore } from '@/stores/ai'
import { useAiHistoryStore, type AiPreset } from '@/stores/aiHistory'
import { useCredit } from '@/composables/useCredit'
import { CREDIT_PACKAGES } from '@/types/credit'
import type { Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'
import type { AiTone } from '@/types/ai'

// --- Stores & Composables ---
const { t } = useI18n({ useScope: 'global' })
const router = useRouter()
const aiStore = useAiStore()
const aiHistoryStore = useAiHistoryStore()
const { balance, isLow, checkAndUse, fetchBalance } = useCredit()

// Common hashtags across all platforms
const commonHashtags = computed(() => {
  if (!aiStore.metaResult?.platforms) return []
  const allTags = aiStore.metaResult.platforms.flatMap(p => p.hashtags)
  return [...new Set(allTags)]
})

// --- Types ---
interface AiTool {
  id: string
  name: string
  credits: number
  description: string
  icon: Component
  iconBg: string
  iconColor: string
}

// --- AI Tools definition ---
const aiTools: AiTool[] = [
  {
    id: 'meta',
    name: '제목/설명 생성',
    credits: 5,
    description: '영상 스크립트로 플랫폼별 최적 제목 5안 + 설명 + 태그 자동 생성',
    icon: DocumentTextIcon,
    iconBg: 'bg-blue-100 dark:bg-blue-900/30',
    iconColor: 'text-blue-600 dark:text-blue-400',
  },
  {
    id: 'hashtags',
    name: '해시태그 추천',
    credits: 3,
    description: '트렌드 기반 플랫폼별 해시태그 30개 추천',
    icon: HashtagIcon,
    iconBg: 'bg-purple-100 dark:bg-purple-900/30',
    iconColor: 'text-purple-600 dark:text-purple-400',
  },
  {
    id: 'stt',
    name: '영상 STT 변환',
    credits: 10,
    description: '영상 음성을 텍스트로 변환 (최대 30분)',
    icon: MicrophoneIcon,
    iconBg: 'bg-red-100 dark:bg-red-900/30',
    iconColor: 'text-red-600 dark:text-red-400',
  },
  {
    id: 'analyze',
    name: '스크립트 분석',
    credits: 5,
    description: '핵심 키워드, 타겟 시청자, 카테고리 자동 추출',
    icon: DocumentMagnifyingGlassIcon,
    iconBg: 'bg-amber-100 dark:bg-amber-900/30',
    iconColor: 'text-amber-600 dark:text-amber-400',
  },
  {
    id: 'reply',
    name: '댓글 답변 생성',
    credits: 2,
    description: '정중/친근/유머 3가지 톤으로 답변 초안 생성',
    icon: ChatBubbleLeftRightIcon,
    iconBg: 'bg-green-100 dark:bg-green-900/30',
    iconColor: 'text-green-600 dark:text-green-400',
  },
  {
    id: 'schedule',
    name: '업로드 시간 추천',
    credits: 3,
    description: '채널 데이터 기반 요일별 최적 게시 시간 분석',
    icon: ClockIcon,
    iconBg: 'bg-cyan-100 dark:bg-cyan-900/30',
    iconColor: 'text-cyan-600 dark:text-cyan-400',
  },
  {
    id: 'ideas',
    name: '콘텐츠 아이디어',
    credits: 5,
    description: '트렌드 기반 다음 영상 주제 5개 + 예상 반응',
    icon: LightBulbIcon,
    iconBg: 'bg-yellow-100 dark:bg-yellow-900/30',
    iconColor: 'text-yellow-600 dark:text-yellow-400',
  },
  {
    id: 'report',
    name: '성과 리포트',
    credits: 8,
    description: '주간/월간 성과 AI 인사이트 리포트 생성',
    icon: ChartBarIcon,
    iconBg: 'bg-indigo-100 dark:bg-indigo-900/30',
    iconColor: 'text-indigo-600 dark:text-indigo-400',
  },
  {
    id: 'strategy-coach',
    name: 'AI 전략 코치',
    credits: 10,
    description: '채널 성과·경쟁자 분석 기반 맞춤형 성장 전략 제안',
    icon: RocketLaunchIcon,
    iconBg: 'bg-rose-100 dark:bg-rose-900/30',
    iconColor: 'text-rose-600 dark:text-rose-400',
  },
  {
    id: 'revenue-report',
    name: '수익 분석 리포트',
    credits: 8,
    description: '수익 트렌드·플랫폼별 비교·최적화 전략 리포트',
    icon: CurrencyDollarIcon,
    iconBg: 'bg-emerald-100 dark:bg-emerald-900/30',
    iconColor: 'text-emerald-600 dark:text-emerald-400',
  },
]

// --- Shared data ---
const platforms: { value: Platform; label: string }[] = [
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'TIKTOK', label: 'TikTok' },
  { value: 'INSTAGRAM', label: 'Instagram' },
  { value: 'NAVER_CLIP', label: 'Naver Clip' },
]

const categories = [
  '엔터테인먼트',
  '게임',
  '음악',
  '교육',
  '뉴스/정치',
  '과학기술',
  '스포츠',
  '여행/이벤트',
  '하우투/스타일',
  '비영리/사회운동',
  '반려동물/동물',
  '코미디',
  '자동차',
  '영화/애니메이션',
  '푸드',
  '뷰티/패션',
  '일상/브이로그',
]

const reportPeriods = [
  { value: '7d' as const, label: t('aiView.report.last7d') },
  { value: '30d' as const, label: t('aiView.report.last30d') },
]

// --- State ---
const activeTab = ref<'tools' | 'presets' | 'history'>('tools')
const selectedTool = ref<AiTool | null>(null)
const showCreditModal = ref(false)
const requiredCredits = ref(0)
const selectedPackage = ref<string | null>(null)
const creditPackages = CREDIT_PACKAGES

// --- Computed ---
const creditsUsedToday = computed(() => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return aiHistoryStore.history
    .filter(record => new Date(record.createdAt) >= today)
    .reduce((sum, record) => sum + record.creditsUsed, 0)
})

// --- Form states ---
const metaForm = ref({
  script: '',
  platforms: [] as Platform[],
  tone: 'FRIENDLY' as AiTone,
  category: '',
})

const hashtagForm = ref({
  title: '',
  category: '',
  platforms: [] as Platform[],
})

const ideasForm = ref({
  category: '',
})

const reportForm = ref({
  period: '7d' as '7d' | '30d',
})

const strategyCoachForm = ref({
  includeCompetitors: true,
  focusArea: '',
})

const revenueReportForm = ref({
  period: '30d' as '7d' | '30d',
})

// --- Lifecycle ---
onMounted(() => {
  fetchBalance()
})

// --- Helpers ---
function platformLabel(platform: string): string {
  const key = platform as Platform
  return PLATFORM_CONFIG[key]?.label ?? platform
}

function renderMarkdown(md: string): string {
  // Basic markdown rendering: headings, bold, lists, line breaks
  return md
    .replace(/^### (.+)$/gm, '<h3 class="text-base font-semibold text-gray-900 dark:text-gray-100 mt-4 mb-2">$1</h3>')
    .replace(/^## (.+)$/gm, '<h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100 mt-4 mb-2">$1</h2>')
    .replace(/^# (.+)$/gm, '<h1 class="text-xl font-bold text-gray-900 dark:text-gray-100 mt-4 mb-2">$1</h1>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/^\- (.+)$/gm, '<li class="ml-4 list-disc text-gray-700 dark:text-gray-300">$1</li>')
    .replace(/^\d+\. (.+)$/gm, '<li class="ml-4 list-decimal text-gray-700 dark:text-gray-300">$1</li>')
    .replace(/\n\n/g, '<br/><br/>')
    .replace(/\n/g, '<br/>')
}

async function copyToClipboard(text: string) {
  try {
    await navigator.clipboard.writeText(`#${text}`)
  } catch {
    // Clipboard API not available
  }
}

// --- Tool interaction ---
function handleToolClick(tool: AiTool) {
  // Check credit balance before opening tool
  if (!checkCreditBalance(tool.credits)) {
    requiredCredits.value = tool.credits
    selectedPackage.value = null
    showCreditModal.value = true
    return
  }

  aiStore.clearResults()
  selectedTool.value = tool
}

function checkCreditBalance(credits: number): boolean {
  return balance.value >= credits
}

function closeTool() {
  selectedTool.value = null
  aiStore.clearResults()
  resetForms()
}

function resetAndClose() {
  closeTool()
  fetchBalance()
}

function resetTool() {
  aiStore.clearResults()
  resetForms()
}

function resetForms() {
  metaForm.value = { script: '', platforms: [], tone: 'FRIENDLY', category: '' }
  hashtagForm.value = { title: '', category: '', platforms: [] }
  ideasForm.value = { category: '' }
  reportForm.value = { period: '7d' }
  strategyCoachForm.value = { includeCompetitors: true, focusArea: '' }
  revenueReportForm.value = { period: '30d' }
}

// --- Preset handler ---
function handlePresetSelected(preset: AiPreset) {
  // Switch to tools tab
  activeTab.value = 'tools'

  // Find and open the corresponding tool
  const tool = aiTools.find(t => t.id === preset.toolType)
  if (tool) {
    handleToolClick(tool)

    // Pre-fill the form based on tool type
    if (preset.toolType === 'meta') {
      metaForm.value.script = preset.prompt
    } else if (preset.toolType === 'hashtags') {
      hashtagForm.value.title = preset.prompt
    } else if (preset.toolType === 'ideas') {
      ideasForm.value.category = preset.prompt
    }
  }
}

// --- Submit handlers ---
async function submitMeta() {
  const canUse = await checkAndUse(5, '제목/설명 생성')
  if (!canUse) {
    selectedTool.value = null
    requiredCredits.value = 5
    selectedPackage.value = null
    showCreditModal.value = true
    return
  }

  try {
    const result = await aiStore.generateMeta({
      script: metaForm.value.script,
      useStt: false,
      targetPlatforms: metaForm.value.platforms,
      tone: metaForm.value.tone,
      category: metaForm.value.category,
    })
    await fetchBalance()

    // Add to history
    if (result) {
      aiHistoryStore.addRecord({
        toolType: '제목/설명 생성',
        prompt: metaForm.value.script,
        result: `플랫폼 ${metaForm.value.platforms.length}개에 대한 제목과 설명이 생성되었습니다`,
        creditsUsed: 5,
      })
    }
  } catch {
    // Error is handled by the store
  }
}

async function submitHashtags() {
  const canUse = await checkAndUse(3, '해시태그 추천')
  if (!canUse) {
    selectedTool.value = null
    requiredCredits.value = 3
    selectedPackage.value = null
    showCreditModal.value = true
    return
  }

  try {
    const result = await aiStore.generateHashtags({
      title: hashtagForm.value.title,
      category: hashtagForm.value.category,
      targetPlatforms: hashtagForm.value.platforms,
    })
    await fetchBalance()

    // Add to history
    if (result) {
      const firstPlatformItem = result.platforms[0]
      const sampleTags = firstPlatformItem?.hashtags.slice(0, 5).join(', ') || ''
      aiHistoryStore.addRecord({
        toolType: '해시태그 추천',
        prompt: hashtagForm.value.title,
        result: `#${sampleTags} 외 다수`,
        creditsUsed: 3,
      })
    }
  } catch {
    // Error is handled by the store
  }
}

async function submitIdeas() {
  const canUse = await checkAndUse(5, '콘텐츠 아이디어')
  if (!canUse) {
    selectedTool.value = null
    requiredCredits.value = 5
    selectedPackage.value = null
    showCreditModal.value = true
    return
  }

  try {
    const result = await aiStore.generateIdeas(ideasForm.value.category)
    await fetchBalance()

    // Add to history
    if (result) {
      const ideaTitles = result.ideas.map(idea => idea.title).join(' / ')
      aiHistoryStore.addRecord({
        toolType: '콘텐츠 아이디어',
        prompt: ideasForm.value.category,
        result: ideaTitles,
        creditsUsed: 5,
      })
    }
  } catch {
    // Error is handled by the store
  }
}

async function submitReport() {
  const canUse = await checkAndUse(8, '성과 리포트')
  if (!canUse) {
    selectedTool.value = null
    requiredCredits.value = 8
    selectedPackage.value = null
    showCreditModal.value = true
    return
  }

  try {
    const result = await aiStore.generateReport(reportForm.value.period)
    await fetchBalance()

    // Add to history
    if (result) {
      aiHistoryStore.addRecord({
        toolType: '성과 리포트',
        prompt: reportForm.value.period === '7d' ? '최근 7일' : '최근 30일',
        result: result.reportMarkdown.substring(0, 200) + '...',
        creditsUsed: 8,
      })
    }
  } catch {
    // Error is handled by the store
  }
}

async function submitStrategyCoach() {
  const canUse = await checkAndUse(10, 'AI 전략 코치')
  if (!canUse) {
    selectedTool.value = null
    requiredCredits.value = 10
    selectedPackage.value = null
    showCreditModal.value = true
    return
  }

  try {
    const result = await aiStore.generateStrategyCoach({
      includeCompetitors: strategyCoachForm.value.includeCompetitors,
      focusArea: strategyCoachForm.value.focusArea || undefined,
    })
    await fetchBalance()

    if (result) {
      aiHistoryStore.addRecord({
        toolType: 'AI 전략 코치',
        prompt: strategyCoachForm.value.focusArea || '전체 전략',
        result: result.overallStrategy.substring(0, 200) + '...',
        creditsUsed: 10,
      })
    }
  } catch {
    // Error is handled by the store
  }
}

async function submitRevenueReport() {
  const canUse = await checkAndUse(8, '수익 분석 리포트')
  if (!canUse) {
    selectedTool.value = null
    requiredCredits.value = 8
    selectedPackage.value = null
    showCreditModal.value = true
    return
  }

  const days = revenueReportForm.value.period === '30d' ? 30 : 7
  try {
    const result = await aiStore.generateRevenueReport(days)
    await fetchBalance()

    if (result) {
      aiHistoryStore.addRecord({
        toolType: '수익 분석 리포트',
        prompt: revenueReportForm.value.period === '7d' ? '최근 7일' : '최근 30일',
        result: result.reportMarkdown.substring(0, 200) + '...',
        creditsUsed: 8,
      })
    }
  } catch {
    // Error is handled by the store
  }
}

function handlePurchase() {
  if (!selectedPackage.value) return
  showCreditModal.value = false
  // Navigate to subscription/payment page with the selected package
  router.push({ path: '/subscription', query: { package: selectedPackage.value } })
}
</script>

<style scoped>
@keyframes ai-item-fade-in {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
