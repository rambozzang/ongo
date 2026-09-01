<template>
  <div class="min-h-full py-5 text-content tablet:py-6">
    <!-- Header -->
    <PageHeader :title="$t('aiView.title')" :description="$t('aiView.description')">
      <template #actions>
        <div
          class="flex items-center gap-2 rounded-lg border px-3 py-2 text-body-sm"
          :class="isLow ? 'border-error bg-error-subtle' : 'border-line-control bg-surface-card'"
        >
          <SparklesIcon class="h-4 w-4" :class="isLow ? 'text-error-strong' : 'text-accent'" />
          <span class="text-content-secondary">{{ $t('aiView.usedToday') }}</span>
          <span class="font-mono font-bold text-accent">
            {{ creditsUsedToday.toLocaleString() }}
          </span>
          <span class="mx-1 text-content-quaternary">|</span>
          <span class="text-content-secondary">{{ $t('aiView.remaining') }}</span>
          <span class="font-mono font-bold" :class="isLow ? 'text-error-strong' : 'text-accent'">
            {{ balance.toLocaleString() }}
          </span>
        </div>
      </template>
    </PageHeader>

    <PageGuide :title="$t('aiView.pageGuideTitle')" :items="($tm('aiView.pageGuide') as string[])" />

    <div class="mt-6">
    <SectionCard :title="$t('aiView.tabs.tools')" :meta="`${aiTools.length}`" body-class="p-4">
      <div
        v-if="aiFeatureCosts"
        class="page-grid page-grid--cards"
      >
        <article
          v-for="tool in aiTools"
          :key="tool.id"
          class="card-interactive group"
          :class="isToolUnavailable(tool) ? 'cursor-not-allowed opacity-60' : 'cursor-pointer'"
          :role="isToolUnavailable(tool) ? undefined : 'button'"
          :tabindex="isToolUnavailable(tool) ? undefined : 0"
          :aria-disabled="isToolUnavailable(tool) ? 'true' : undefined"
          :data-testid="`ai-tool-${tool.id}`"
          @click="handleToolClick(tool)"
          @keydown.enter="handleToolClick(tool)"
          @keydown.space.prevent="handleToolClick(tool)"
        >
          <div class="mb-3 flex items-start justify-between">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-accent-dim">
              <component :is="tool.icon" class="h-5 w-5 text-accent" />
            </div>
            <span v-if="isToolUnavailable(tool)" class="badge-gray">{{ $t('aiView.unsupported.badge') }}</span>
            <span v-else class="badge-blue">{{ tool.credits }} {{ $t('aiView.credits') }}</span>
          </div>
          <h3
            class="mb-1 text-h3 text-content transition-colors"
            :class="isToolUnavailable(tool) ? '' : 'group-hover:text-accent'"
          >
            {{ tool.name }}
          </h3>
          <p class="mb-4 text-body-sm leading-relaxed text-content-secondary">{{ tool.description }}</p>
          <p
            v-if="isToolUnavailable(tool)"
            class="mb-4 rounded-lg border border-warning-subtle bg-warning-subtle px-3 py-2 text-body-sm text-warning-strong"
            role="status"
          >
            {{ toolUnavailableReason(tool) }}
          </p>
          <button
            class="btn-primary w-full"
            :disabled="isToolUnavailable(tool)"
            @click.stop="handleToolClick(tool)"
          >
            {{ isToolUnavailable(tool) ? $t('aiView.unsupported.button') : $t('aiView.useButton') }}
          </button>
        </article>
      </div>
      <div
        v-else
        class="rounded-xl border border-warning-subtle bg-warning-subtle px-4 py-5 text-body text-warning-strong"
        role="status"
        aria-live="polite"
        data-testid="ai-feature-pricing-status"
      >
        <p>{{ aiFeaturePricingError ?? $t('aiView.pricing.loading') }}</p>
        <button
          v-if="aiFeaturePricingError"
          type="button"
          class="btn-secondary mt-3 text-body-sm"
          @click="loadAiFeaturePricing"
        >
          {{ $t('common.retry') }}
        </button>
      </div>
    </SectionCard>
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
                <h2 class="text-title font-semibold text-gray-900 dark:text-gray-100">{{ selectedTool.name }}</h2>
                <p class="text-body-xs text-gray-500 dark:text-gray-400">{{ selectedTool.credits }} {{ $t('aiView.creditsUsed') }}</p>
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
            <!-- 크레딧 부족 CTA: 실제 API 가 차감 단계에서 CREDIT_INSUFFICIENT 를 돌려줄 때만 노출 -->
            <div
              v-if="aiStore.creditBlocked"
              class="mb-4 flex flex-col gap-2 rounded-lg border border-warning bg-warning-subtle px-4 py-3"
              role="alert"
            >
              <p class="text-body text-warning-strong">{{ $t('aiView.creditModal.insufficientMessage') }}</p>
              <button
                type="button"
                data-testid="ai-credit-cta"
                class="btn-primary inline-flex w-full items-center justify-center gap-2"
                @click="openCreditModal(selectedTool?.credits ?? 0)"
              >
                {{ $t('aiView.creditModal.ctaText') }}
              </button>
            </div>

            <!-- 일반 오류 문구 (크레딧 부족 CTA 와 분리) -->
            <div
              v-else-if="aiStore.error"
              class="mb-4 rounded-lg border border-error bg-error-subtle px-4 py-3 text-body text-error-strong"
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
                  <label class="mb-1.5 block text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('aiView.form.script') }}</label>
                  <textarea
                    v-model="metaForm.script"
                    class="input-field min-h-[120px] resize-y"
                    :placeholder="$t('aiView.form.scriptPlaceholder')"
                    :disabled="aiStore.loading"
                  />
                </div>
                <div>
                  <label class="mb-1.5 block text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('aiView.form.platform') }}</label>
                  <div class="flex flex-wrap gap-2">
                    <label
                      v-for="p in platforms"
                      :key="p.value"
                      class="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-body transition-colors"
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
                    <label class="mb-1.5 block text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('aiView.form.tone') }}</label>
                    <select v-model="metaForm.tone" class="input-field" :disabled="aiStore.loading">
                      <option value="FRIENDLY">{{ $t('aiView.tones.friendly') }}</option>
                      <option value="PROFESSIONAL">{{ $t('aiView.tones.professional') }}</option>
                      <option value="HUMOROUS">{{ $t('aiView.tones.humorous') }}</option>
                    </select>
                  </div>
                  <div>
                    <label class="mb-1.5 block text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('aiView.form.category') }}</label>
                    <select v-model="metaForm.category" class="input-field" :disabled="aiStore.loading">
                      <option value="">{{ $t('aiView.form.select') }}</option>
                      <option v-for="cat in categories" :key="cat" :value="cat">
                        {{ cat }}
                      </option>
                    </select>
                  </div>
                </div>
                <div class="flex justify-end gap-3 pt-2">
                  <button class="btn-secondary" :disabled="aiStore.loading" @click="closeTool">{{ $t('aiView.form.cancel') }}</button>
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
                    <p class="mb-1 text-caption text-gray-500 dark:text-gray-400">{{ $t('aiView.results.titleCandidates') }}</p>
                    <ul class="space-y-1">
                      <li
                        v-for="(title, ti) in result.titleCandidates"
                        :key="ti"
                        class="flex items-start gap-2 rounded-md bg-gray-50 dark:bg-gray-900 px-3 py-2 text-body text-gray-800 dark:text-gray-200"
                      >
                        <span class="mt-0.5 shrink-0 text-caption text-primary-600">{{ ti + 1 }}.</span>
                        <AiTypingEffect v-if="idx === 0 && ti === 0" :text="title" :speed="20" />
                        <span v-else>{{ title }}</span>
                      </li>
                    </ul>
                  </div>
                  <div class="mb-3">
                    <p class="mb-1 text-caption text-gray-500 dark:text-gray-400">{{ $t('aiView.results.description') }}</p>
                    <p class="whitespace-pre-wrap rounded-md bg-gray-50 dark:bg-gray-900 px-3 py-2 text-body text-gray-700 dark:text-gray-300">
                      {{ result.description }}
                    </p>
                  </div>
                  <div>
                    <p class="mb-1 text-caption text-gray-500 dark:text-gray-400">{{ $t('aiView.results.tags') }}</p>
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
                  <p class="mb-1 text-caption text-gray-500 dark:text-gray-400">{{ $t('aiView.results.commonHashtags') }}</p>
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
                  <p class="text-body-xs text-gray-500 dark:text-gray-400">
                    {{ $t('aiView.creditsUsedLabel') }}: {{ resultCreditsUsed(aiStore.metaResult.creditsUsed) }} / {{ $t('aiView.remainingLabel') }}: {{ resultCreditsRemaining(aiStore.metaResult.creditsRemaining) }}
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
                  <label class="mb-1.5 block text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('aiView.videoTitle') }}</label>
                  <input
                    v-model="hashtagForm.title"
                    type="text"
                    class="input-field"
                    :placeholder="$t('aiView.videoTitlePlaceholder')"
                    :disabled="aiStore.loading"
                  />
                </div>
                <div>
                  <label class="mb-1.5 block text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('aiView.form.category') }}</label>
                  <select v-model="hashtagForm.category" class="input-field" :disabled="aiStore.loading">
                    <option value="">{{ $t('aiView.form.select') }}</option>
                    <option v-for="cat in categories" :key="cat" :value="cat">
                      {{ cat }}
                    </option>
                  </select>
                </div>
                <div>
                  <label class="mb-1.5 block text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('aiView.form.platform') }}</label>
                  <div class="flex flex-wrap gap-2">
                    <label
                      v-for="p in platforms"
                      :key="p.value"
                      class="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-body transition-colors"
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
                  <button class="btn-secondary" :disabled="aiStore.loading" @click="closeTool">{{ $t('aiView.form.cancel') }}</button>
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
                      class="cursor-pointer rounded-full bg-info-subtle px-2.5 py-1 text-caption text-info-strong transition hover:opacity-80"
                      @click="copyToClipboard(tag)"
                    >
                      #{{ tag }}
                    </span>
                  </div>
                </div>

                <div class="flex items-center justify-between border-t dark:border-gray-700 pt-4">
                  <p class="text-body-xs text-gray-500 dark:text-gray-400">
                    {{ $t('aiView.creditsUsedLabel') }}: {{ resultCreditsUsed(aiStore.hashtagResult.creditsUsed) }} / {{ $t('aiView.remainingLabel') }}: {{ resultCreditsRemaining(aiStore.hashtagResult.creditsRemaining) }}
                  </p>
                  <div class="flex gap-3">
                    <button class="btn-secondary" @click="resetAndClose">{{ $t('aiView.results.close') }}</button>
                    <button class="btn-primary" @click="resetTool">{{ $t('aiView.results.reRecommend') }}</button>
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
                  <label class="mb-1.5 block text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('aiView.analysisPeriod') }}</label>
                  <div class="flex gap-3">
                    <button
                      v-for="p in reportPeriods"
                      :key="p.value"
                      class="flex-1 rounded-lg border px-4 py-3 text-center text-body font-medium transition-colors"
                      :class="[
                        reportForm.period === p.value
                          ? 'border-primary-300 dark:border-primary-600 bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300'
                          : 'border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700',
                        aiStore.loading ? 'opacity-50 pointer-events-none' : ''
                      ]"
                      :disabled="aiStore.loading"
                      @click="reportForm.period = p.value"
                    >
                      {{ p.label }}
                    </button>
                  </div>
                </div>
                <div class="flex justify-end gap-3 pt-2">
                  <button class="btn-secondary" :disabled="aiStore.loading" @click="closeTool">{{ $t('aiView.form.cancel') }}</button>
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
                  <p class="text-body-xs text-gray-500 dark:text-gray-400">
                    {{ $t('aiView.creditsUsedLabel') }}: {{ resultCreditsUsed(aiStore.reportResult.creditsUsed) }} / {{ $t('aiView.remainingLabel') }}: {{ resultCreditsRemaining(aiStore.reportResult.creditsRemaining) }}
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
                  <label class="mb-1.5 block text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('aiView.strategyCoach.focusArea') }}</label>
                  <select v-model="strategyCoachForm.focusArea" class="input-field" :disabled="aiStore.loading">
                    <option value="">{{ $t('aiView.strategyCoach.focusOptions.all') }}</option>
                    <option value="콘텐츠">{{ $t('aiView.strategyCoach.focusOptions.content') }}</option>
                    <option value="성장">{{ $t('aiView.strategyCoach.focusOptions.growth') }}</option>
                    <option value="수익">{{ $t('aiView.strategyCoach.focusOptions.revenue') }}</option>
                    <option value="플랫폼">{{ $t('aiView.strategyCoach.focusOptions.platform') }}</option>
                  </select>
                </div>
                <div>
                  <label class="flex items-center gap-2 text-body font-medium text-gray-700 dark:text-gray-300">
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
                  <button class="btn-secondary" :disabled="aiStore.loading" @click="closeTool">{{ $t('aiView.form.cancel') }}</button>
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
                  <p class="text-body text-primary-800 dark:text-primary-200">{{ aiStore.strategyCoachResult.overallStrategy }}</p>
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
                          class="rounded-full px-2 py-0.5 text-caption"
                          :class="rec.priority === 'HIGH'
                            ? 'bg-error-subtle text-error-strong'
                            : rec.priority === 'MEDIUM'
                              ? 'bg-warning-subtle text-warning-strong'
                              : 'bg-success-subtle text-success-strong'"
                        >
                          {{ rec.priority }}
                        </span>
                      </div>
                      <p class="text-body-xs text-gray-500 dark:text-gray-400">{{ rec.targetPlatform }} | {{ rec.reason }}</p>
                      <p class="mt-1 text-body-xs text-primary-600 dark:text-primary-400">{{ $t('aiView.expectedImpact') }}: {{ rec.expectedImpact }}</p>
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
                      <div class="grid gap-1 text-body-xs">
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
                      <p class="text-body font-medium text-gray-900 dark:text-gray-100">{{ ta.recommendation }}</p>
                      <p class="mt-1 text-body-xs text-gray-500 dark:text-gray-400">{{ ta.reason }}</p>
                      <p class="mt-1 text-body-xs text-success-strong">{{ $t('aiView.expectedBoost') }}: {{ ta.expectedBoost }}</p>
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
                  <label class="mb-1.5 block text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('aiView.analysisPeriod') }}</label>
                  <div class="flex gap-3">
                    <button
                      v-for="p in reportPeriods"
                      :key="p.value"
                      class="flex-1 rounded-lg border px-4 py-3 text-center text-body font-medium transition-colors"
                      :class="[
                        revenueReportForm.period === p.value
                          ? 'border-primary-300 dark:border-primary-600 bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300'
                          : 'border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700',
                        aiStore.loading ? 'opacity-50 pointer-events-none' : ''
                      ]"
                      :disabled="aiStore.loading"
                      @click="revenueReportForm.period = p.value"
                    >
                      {{ p.label }}
                    </button>
                  </div>
                </div>
                <div class="flex justify-end gap-3 pt-2">
                  <button class="btn-secondary" :disabled="aiStore.loading" @click="closeTool">{{ $t('aiView.form.cancel') }}</button>
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
                      class="flex items-start gap-2 rounded-md bg-success-subtle px-3 py-2 text-body text-success-strong"
                    >
                      <CurrencyDollarIcon class="mt-0.5 h-4 w-4 shrink-0 text-success-strong" />
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
                      <div class="grid gap-1 text-body-xs">
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

            <!-- Guard for a tool that is not enabled in this release. -->
            <template v-else>
              <div class="space-y-4">
                <div class="rounded-lg border border-dashed border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-900 px-6 py-12 text-center">
                  <component
                    :is="selectedTool.icon"
                    class="mx-auto mb-3 h-10 w-10 text-gray-400 dark:text-gray-500"
                  />
                  <h3 class="mb-1 text-body font-medium text-gray-900 dark:text-gray-100">{{ selectedTool.name }}</h3>
                  <p class="text-body text-gray-500 dark:text-gray-400">
                    {{ $t('aiView.toolUnavailable') }}
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

    <!-- AI 도구에서 여는 결제도 구독 화면과 같은 실제 PortOne 흐름을 사용한다. -->
    <CreditPurchaseModal
      v-model="showCreditModal"
      :required-credits="requiredCredits"
      :current-balance="balance"
      @purchase="handleCreditPurchase"
    />
  </div>
</template>

<script setup lang="ts">
import { escapeHtml } from '@/utils/html'
import { ref, computed, onMounted, type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  SparklesIcon,
  DocumentTextIcon,
  HashtagIcon,
  ChartBarIcon,
  XMarkIcon,
  RocketLaunchIcon,
  CurrencyDollarIcon,
} from '@heroicons/vue/24/outline'
import AiLoadingOverlay from '@/components/ai/AiLoadingOverlay.vue'
import AiTypingEffect from '@/components/ai/AiTypingEffect.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SectionCard from '@/components/redesign/SectionCard.vue'
import { useAiStore } from '@/stores/ai'
import { useCredit } from '@/composables/useCredit'
import { useAiFeaturePricing } from '@/composables/useAiFeaturePricing'
import { useRevenueDataAvailability } from '@/composables/useRevenueDataAvailability'
import CreditPurchaseModal from '@/components/subscription/CreditPurchaseModal.vue'
import type { Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'
import type { AiTone } from '@/types/ai'

// --- Stores & Composables ---
const { t } = useI18n({ useScope: 'global' })
const aiStore = useAiStore()
const { balance, isLow, usedToday, checkAndUse, fetchBalance, fetchTransactions } = useCredit()
const { revenueDataAvailable, revenueDataUnavailableReason, loadRevenueDataAvailability } =
  useRevenueDataAvailability()

// Common hashtags across all platforms
const commonHashtags = computed(() => {
  if (!aiStore.metaResult?.platforms) return []
  const allTags = aiStore.metaResult.platforms.flatMap(p => p.hashtags)
  return [...new Set(allTags)]
})

// --- Types ---
interface AiTool {
  id: string
  featureKey: string
  name: string
  credits: number
  description: string
  icon: Component
  iconBg: string
  iconColor: string
  /**
   * 플랫폼 광고 수익 실측치가 있어야 동작하는 도구. 서버가 수익을 수집하지 않는 동안
   * 이 도구들은 `REVENUE_DATA_UNAVAILABLE` 로 항상 거절되므로 열지 않는다.
   */
  requiresPlatformRevenue?: boolean
}

// --- AI Tools definition ---
// 비용은 여기서 복제하지 않는다. 실제 차감 단가는 서버 `AiFeature`에서 받는다.
const aiToolDefinitions: Omit<AiTool, 'credits'>[] = [
  {
    id: 'meta',
    featureKey: 'META_GENERATION',
    name: '제목/설명 생성',
    description: '영상 스크립트로 플랫폼별 최적 제목 5안 + 설명 + 태그 자동 생성',
    icon: DocumentTextIcon,
    iconBg: 'bg-blue-100 dark:bg-blue-900/30',
    iconColor: 'text-blue-600 dark:text-blue-400',
  },
  {
    id: 'hashtags',
    featureKey: 'HASHTAG_RECOMMENDATION',
    name: '해시태그 추천',
    description: '트렌드 기반 플랫폼별 해시태그 30개 추천',
    icon: HashtagIcon,
    iconBg: 'bg-purple-100 dark:bg-purple-900/30',
    iconColor: 'text-purple-600 dark:text-purple-400',
  },
  {
    id: 'report',
    featureKey: 'PERFORMANCE_REPORT',
    name: '성과 리포트',
    description: '주간/월간 성과 AI 인사이트 리포트 생성',
    icon: ChartBarIcon,
    iconBg: 'bg-primary-100 dark:bg-primary-900/30',
    iconColor: 'text-primary-600 dark:text-primary-400',
  },
  {
    id: 'strategy-coach',
    featureKey: 'STRATEGY_COACH',
    name: 'AI 전략 코치',
    description: '채널 성과·경쟁자 분석 기반 맞춤형 성장 전략 제안',
    icon: RocketLaunchIcon,
    iconBg: 'bg-rose-100 dark:bg-rose-900/30',
    iconColor: 'text-rose-600 dark:text-rose-400',
  },
  {
    id: 'revenue-report',
    featureKey: 'REVENUE_REPORT',
    name: '수익 분석 리포트',
    description: '수익 트렌드·플랫폼별 비교·최적화 전략 리포트',
    icon: CurrencyDollarIcon,
    iconBg: 'bg-emerald-100 dark:bg-emerald-900/30',
    iconColor: 'text-emerald-600 dark:text-emerald-400',
    requiresPlatformRevenue: true,
  },
]

// --- Shared data ---
const platforms: { value: Platform; label: string }[] = [
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'TIKTOK', label: 'TikTok' },
  { value: 'INSTAGRAM', label: 'Instagram' },
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
const selectedTool = ref<AiTool | null>(null)
const showCreditModal = ref(false)
const requiredCredits = ref(0)
const {
  costs: aiFeatureCosts,
  error: aiFeaturePricingError,
  load: loadAiFeaturePricing,
  costOf: aiFeatureCostOf,
} = useAiFeaturePricing()

const aiTools = computed<AiTool[]>(() => aiToolDefinitions.map((definition) => ({
  ...definition,
  credits: aiFeatureCostOf(definition.featureKey) ?? 0,
})))

// --- Computed ---
const creditsUsedToday = usedToday

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
  fetchTransactions(0, 100)
  loadRevenueDataAvailability()
  void loadAiFeaturePricing()
})

// --- Helpers ---
function platformLabel(platform: string): string {
  const key = platform as Platform
  return PLATFORM_CONFIG[key]?.label ?? platform
}

function renderMarkdown(md: string): string {
  // Basic markdown rendering: headings, bold, lists, line breaks
  return escapeHtml(md)
    .replace(/^### (.+)$/gm, '<h3 class="text-h3 text-gray-900 dark:text-gray-100 mt-4 mb-2">$1</h3>')
    .replace(/^## (.+)$/gm, '<h2 class="text-title font-semibold text-gray-900 dark:text-gray-100 mt-4 mb-2">$1</h2>')
    .replace(/^# (.+)$/gm, '<h1 class="text-h2 font-bold text-gray-900 dark:text-gray-100 mt-4 mb-2">$1</h1>')
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
/**
 * UI 만 막지 않는다. 카드 전체가 클릭 대상이고 키보드 진입도 있어서, 여기서 한 번 더
 * 막지 않으면 비활성 표시와 실제 동작이 어긋난다.
 */
function isToolUnavailable(tool: AiTool): boolean {
  return aiFeatureCostOf(tool.featureKey) == null
    || (tool.requiresPlatformRevenue === true && !revenueDataAvailable.value)
}

function toolUnavailableReason(tool: AiTool): string {
  if (aiFeatureCostOf(tool.featureKey) == null) {
    return aiFeaturePricingError.value || t('aiView.pricing.loading')
  }
  if (!isToolUnavailable(tool)) return ''
  return revenueDataUnavailableReason.value || t('aiView.unsupported.revenueReason')
}

function handleToolClick(tool: AiTool) {
  if (isToolUnavailable(tool)) return

  // Check credit balance before opening tool
  if (!checkCreditBalance(tool.credits)) {
    requiredCredits.value = tool.credits
    showCreditModal.value = true
    return
  }

  aiStore.clearResults()
  selectedTool.value = tool
}

function checkCreditBalance(credits: number): boolean {
  return balance.value >= credits
}

/**
 * 크레딧 부족 모달을 엽니다. 사전 부족(handleToolClick/submit*) 과 서버 측
 * CREDIT_INSUFFICIENT(aiStore.creditBlocked CTA) 이 두 경로에서 동일하게 사용한다.
 * 실제 결제는 공통 CreditPurchaseModal 이 처리한다.
 */
function openCreditModal(credits: number) {
  requiredCredits.value = credits
  showCreditModal.value = true
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
  reportForm.value = { period: '7d' }
  strategyCoachForm.value = { includeCompetitors: true, focusArea: '' }
  revenueReportForm.value = { period: '30d' }
}

function toolCost(featureKey: string): number | null {
  return aiFeatureCostOf(featureKey)
}

/**
 * 일부 구버전 AI 응답에는 사용량 필드가 없다. 그 경우에도 결과 화면에 `undefined`를
 * 그리지 않는다. 사용 단가는 같은 화면에서 서버 `/ai/features`로 받은 값이고 잔액은
 * 요청 직후 서버에서 다시 조회한 값이다. 새 응답이 필드를 제공하면 그 실측값을 우선한다.
 */
function resultCreditsUsed(value?: number): number {
  return value ?? selectedTool.value?.credits ?? 0
}

function resultCreditsRemaining(value?: number): number {
  return value ?? balance.value
}

// --- Submit handlers ---
async function submitMeta() {
  const credits = toolCost('META_GENERATION')
  if (credits == null) return
  const canUse = await checkAndUse(credits, '제목/설명 생성')
  if (!canUse) {
    selectedTool.value = null
    requiredCredits.value = credits
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

    void result
  } catch {
    // Error is handled by the store
  }
}

async function submitHashtags() {
  const credits = toolCost('HASHTAG_RECOMMENDATION')
  if (credits == null) return
  const canUse = await checkAndUse(credits, '해시태그 추천')
  if (!canUse) {
    selectedTool.value = null
    requiredCredits.value = credits
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

    void result
  } catch {
    // Error is handled by the store
  }
}

async function submitReport() {
  const credits = toolCost('PERFORMANCE_REPORT')
  if (credits == null) return
  const canUse = await checkAndUse(credits, '성과 리포트')
  if (!canUse) {
    selectedTool.value = null
    requiredCredits.value = credits
    showCreditModal.value = true
    return
  }

  try {
    const result = await aiStore.generateReport(reportForm.value.period)
    await fetchBalance()

    void result
  } catch {
    // Error is handled by the store
  }
}

async function submitStrategyCoach() {
  const credits = toolCost('STRATEGY_COACH')
  if (credits == null) return
  const canUse = await checkAndUse(credits, 'AI 전략 코치')
  if (!canUse) {
    selectedTool.value = null
    requiredCredits.value = credits
    showCreditModal.value = true
    return
  }

  try {
    const result = await aiStore.generateStrategyCoach({
      includeCompetitors: strategyCoachForm.value.includeCompetitors,
      focusArea: strategyCoachForm.value.focusArea || undefined,
    })
    await fetchBalance()

    void result
  } catch {
    // Error is handled by the store
  }
}

async function submitRevenueReport() {
  const credits = toolCost('REVENUE_REPORT')
  if (credits == null) return
  const canUse = await checkAndUse(credits, '수익 분석 리포트')
  if (!canUse) {
    selectedTool.value = null
    requiredCredits.value = credits
    showCreditModal.value = true
    return
  }

  const days = revenueReportForm.value.period === '30d' ? 30 : 7
  try {
    const result = await aiStore.generateRevenueReport(days)
    await fetchBalance()

    void result
  } catch {
    // Error is handled by the store
  }
}

async function handleCreditPurchase() {
  // 공통 모달은 서버 검증·크레딧 지급이 끝난 뒤 이 이벤트를 발생시킨다.
  await fetchBalance()
  aiStore.clearResults()
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
