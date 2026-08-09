<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900">
    <!-- Header -->
    <div class="border-b dark:border-gray-700 bg-white dark:bg-gray-800 px-4 py-4">
      <div class="flex max-w-3xl items-center justify-between">
        <OnGoLogo size="md" />
        <span v-if="currentStep > 0 && currentStep <= 4" class="text-body text-gray-400 dark:text-gray-500">
          {{ t('onboarding.stepProgress', { current: currentStep, total: steps.length }) }}
        </span>
      </div>
    </div>

    <!-- Step Indicator -->
    <div v-if="currentStep > 0 && currentStep <= 4" class="border-b dark:border-gray-700 bg-white dark:bg-gray-800 px-4 py-6">
      <div class="max-w-2xl px-4">
        <OnboardingStepIndicator :current-step="currentStep" :steps="steps" />
      </div>
    </div>

    <!-- Content -->
    <div class="max-w-2xl px-4 py-8">
      <!-- Step transitions -->
      <Transition :name="transitionName" mode="out-in">
        <div :key="currentStep">
          <!-- Step 0: Welcome Screen -->
          <div v-if="currentStep === 0" class="max-w-lg text-center">
            <div class="mb-8">
              <OnGoLogo size="lg" />
              <p class="mt-4 whitespace-pre-line text-h2 text-gray-900 dark:text-gray-100">
                {{ t('onboarding.welcome.tagline') }}
              </p>
            </div>

            <div class="space-y-6">
              <div class="flex items-center gap-4 rounded-xl border border-gray-200 bg-white p-4 text-left dark:border-gray-700 dark:bg-gray-800">
                <div class="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-primary-100 dark:bg-primary-900/30">
                  <ArrowUpTrayIcon class="h-6 w-6 text-primary-600 dark:text-primary-400" />
                </div>
                <div>
                  <p class="font-semibold text-gray-900 dark:text-gray-100">{{ t('onboarding.welcome.features.multiUpload.title') }}</p>
                  <p class="text-body text-gray-500 dark:text-gray-400">{{ t('onboarding.welcome.features.multiUpload.description') }}</p>
                </div>
              </div>

              <div class="flex items-center gap-4 rounded-xl border border-gray-200 bg-white p-4 text-left dark:border-gray-700 dark:bg-gray-800">
                <div class="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-info-subtle">
                  <SparklesIcon class="h-6 w-6 text-info-strong" />
                </div>
                <div>
                  <p class="font-semibold text-gray-900 dark:text-gray-100">{{ t('onboarding.welcome.features.aiMetadata.title') }}</p>
                  <p class="text-body text-gray-500 dark:text-gray-400">{{ t('onboarding.welcome.features.aiMetadata.description') }}</p>
                </div>
              </div>

              <div class="flex items-center gap-4 rounded-xl border border-gray-200 bg-white p-4 text-left dark:border-gray-700 dark:bg-gray-800">
                <div class="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-success-subtle">
                  <ChartBarIcon class="h-6 w-6 text-success-strong" />
                </div>
                <div>
                  <p class="font-semibold text-gray-900 dark:text-gray-100">{{ t('onboarding.welcome.features.analytics.title') }}</p>
                  <p class="text-body text-gray-500 dark:text-gray-400">{{ t('onboarding.welcome.features.analytics.description') }}</p>
                </div>
              </div>
            </div>

            <button class="btn-primary btn-press mt-8 w-full py-3 text-body-lg" @click="startOnboarding">
              {{ t('onboarding.welcome.start') }}
            </button>
            <p class="mt-3 text-caption text-gray-400">{{ t('onboarding.welcome.duration') }}</p>
          </div>

          <!-- Step 1: Profile -->
          <div v-else-if="currentStep === 1">
            <div class="mb-6 text-center">
              <h2 class="text-h1 font-bold text-gray-900 dark:text-gray-100">{{ t('onboarding.profile.title') }}</h2>
              <p class="mt-2 text-body text-gray-500 dark:text-gray-400">{{ t('onboarding.profile.description') }}</p>
            </div>

            <div class="rounded-2xl bg-white dark:bg-gray-800 p-6 shadow-sm tablet:p-8">
              <!-- Nickname -->
              <div class="mb-6">
                <label for="nickname" class="mb-2 block text-body font-medium text-gray-700 dark:text-gray-300">
                  {{ t('onboarding.profile.nickname') }} <span class="text-error-strong">*</span>
                </label>
                <input
                  id="nickname"
                  v-model="profile.nickname"
                  type="text"
                  maxlength="20"
                  :placeholder="t('onboarding.profile.nicknamePlaceholder')"
                  class="input-field w-full"
                  :class="{ 'border-error focus:border-error focus:ring-error': nicknameError }"
                  @input="nicknameError = ''"
                />
                <div class="mt-1 flex items-center justify-between">
                  <p v-if="nicknameError" class="text-body-xs text-error-strong">{{ nicknameError }}</p>
                  <span v-else class="text-body-xs text-gray-400 dark:text-gray-500">{{ t('onboarding.profile.nicknameLength') }}</span>
                  <span class="text-body-xs text-gray-400 dark:text-gray-500">{{ profile.nickname.length }}/20</span>
                </div>
              </div>

              <!-- Category -->
              <div>
                <label class="mb-2 block text-body font-medium text-gray-700 dark:text-gray-300">
                  {{ t('onboarding.profile.category') }} <span class="text-error-strong">*</span>
                </label>
                <p class="mb-3 text-body-xs text-gray-400">{{ t('onboarding.profile.categoryHint') }}</p>
                <div class="grid grid-cols-2 gap-2 tablet:grid-cols-4">
                  <button
                    v-for="cat in categories"
                    :key="cat.value"
                    type="button"
                    class="rounded-xl border-2 px-3 py-3 text-body font-medium transition-all"
                    :class="
                      profile.category === cat.value
                        ? 'border-primary-500 bg-primary-50 text-primary-700'
                        : 'border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-700'
                    "
                    @click="profile.category = cat.value; categoryError = ''"
                  >
                    <span class="mr-1">{{ cat.icon }}</span>
                    {{ cat.label }}
                  </button>
                </div>
                <p v-if="categoryError" class="mt-2 text-body-xs text-error-strong">{{ categoryError }}</p>
              </div>
            </div>
          </div>

          <!-- Step 2: Channel Connect -->
          <div v-else-if="currentStep === 2">
            <div class="mb-6 text-center">
              <h2 class="text-h1 font-bold text-gray-900 dark:text-gray-100">{{ t('onboarding.channels.title') }}</h2>
              <p class="mt-2 text-body text-gray-500 dark:text-gray-400">{{ t('onboarding.channels.description') }}</p>
            </div>

            <div class="space-y-3">
              <div v-if="platformLoadError" class="rounded-xl border border-error/30 bg-error-subtle px-4 py-3 text-body text-error-strong">
                {{ platformLoadError }}
                <button type="button" class="btn-secondary mt-2" @click="loadPlatformCapabilities">
                  {{ t('common.retry') }}
                </button>
              </div>
              <div v-else-if="platforms.length === 0" class="rounded-xl border border-gray-200 bg-white px-4 py-6 text-center text-body text-gray-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-400">
                {{ t('onboarding.channels.loadingPlatforms') }}
              </div>
              <div
                v-for="platform in platforms"
                :key="platform.key"
                class="flex items-center justify-between rounded-2xl bg-white dark:bg-gray-800 p-5 shadow-sm transition-all"
                :class="connectedPlatforms.has(platform.key) ? 'ring-2 ring-primary-500/30' : ''"
              >
                <div class="flex items-center gap-4">
                  <div
                    class="flex h-12 w-12 items-center justify-center rounded-xl"
                    :style="{ backgroundColor: platform.bgColor }"
                  >
                    <svg v-if="platform.key === 'YOUTUBE'" class="h-6 w-6 text-white" viewBox="0 0 24 24" fill="currentColor">
                      <path d="M23.498 6.186a3.016 3.016 0 00-2.122-2.136C19.505 3.545 12 3.545 12 3.545s-7.505 0-9.377.505A3.017 3.017 0 00.502 6.186C0 8.07 0 12 0 12s0 3.93.502 5.814a3.016 3.016 0 002.122 2.136c1.871.505 9.376.505 9.376.505s7.505 0 9.377-.505a3.015 3.015 0 002.122-2.136C24 15.93 24 12 24 12s0-3.93-.502-5.814zM9.545 15.568V8.432L15.818 12l-6.273 3.568z"/>
                    </svg>
                    <svg v-else-if="platform.key === 'TIKTOK'" class="h-6 w-6 text-white" viewBox="0 0 24 24" fill="currentColor">
                      <path d="M19.59 6.69a4.83 4.83 0 01-3.77-4.25V2h-3.45v13.67a2.89 2.89 0 01-2.88 2.5 2.89 2.89 0 01-2.89-2.89 2.89 2.89 0 012.89-2.89c.28 0 .54.04.79.1v-3.5a6.37 6.37 0 00-.79-.05A6.34 6.34 0 003.15 15.2a6.34 6.34 0 0010.86-4.43v-7a8.16 8.16 0 004.77 1.52v-3.4a4.85 4.85 0 01-.81.06l-.38-.26z"/>
                    </svg>
                    <svg v-else-if="platform.key === 'INSTAGRAM'" class="h-6 w-6 text-white" viewBox="0 0 24 24" fill="currentColor">
                      <path d="M12 2.163c3.204 0 3.584.012 4.85.07 3.252.148 4.771 1.691 4.919 4.919.058 1.265.069 1.645.069 4.849 0 3.205-.012 3.584-.069 4.849-.149 3.225-1.664 4.771-4.919 4.919-1.266.058-1.644.07-4.85.07-3.204 0-3.584-.012-4.849-.07-3.26-.149-4.771-1.699-4.919-4.92-.058-1.265-.07-1.644-.07-4.849 0-3.204.013-3.583.07-4.849.149-3.227 1.664-4.771 4.919-4.919 1.266-.057 1.645-.069 4.849-.069zM12 0C8.741 0 8.333.014 7.053.072 2.695.272.273 2.69.073 7.052.014 8.333 0 8.741 0 12c0 3.259.014 3.668.072 4.948.2 4.358 2.618 6.78 6.98 6.98C8.333 23.986 8.741 24 12 24c3.259 0 3.668-.014 4.948-.072 4.354-.2 6.782-2.618 6.979-6.98.059-1.28.073-1.689.073-4.948 0-3.259-.014-3.667-.072-4.947-.196-4.354-2.617-6.78-6.979-6.98C15.668.014 15.259 0 12 0zm0 5.838a6.162 6.162 0 100 12.324 6.162 6.162 0 000-12.324zM12 16a4 4 0 110-8 4 4 0 010 8zm6.406-11.845a1.44 1.44 0 100 2.881 1.44 1.44 0 000-2.881z"/>
                    </svg>
                    <svg v-else-if="platform.key === 'NAVER_CLIP'" class="h-6 w-6 text-white" viewBox="0 0 24 24" fill="currentColor">
                      <path d="M16.273 12.845L7.376 0H0v24h7.727V11.155L16.624 24H24V0h-7.727v12.845z"/>
                    </svg>
                    <svg v-else-if="platform.key === 'TWITTER'" class="h-6 w-6 text-white" viewBox="0 0 24 24" fill="currentColor">
                      <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"/>
                    </svg>
                    <svg v-else-if="platform.key === 'FACEBOOK'" class="h-6 w-6 text-white" viewBox="0 0 24 24" fill="currentColor">
                      <path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z"/>
                    </svg>
                    <svg v-else-if="platform.key === 'THREADS'" class="h-6 w-6 text-white" viewBox="0 0 24 24" fill="currentColor">
                      <path d="M12.186 24h-.007c-3.581-.024-6.334-1.205-8.184-3.509C2.35 18.44 1.5 15.586 1.472 12.01v-.017c.03-3.579.879-6.43 2.525-8.482C5.845 1.205 8.6.024 12.18 0h.014c2.746.02 5.043.725 6.826 2.098 1.677 1.29 2.858 3.13 3.509 5.467l-2.04.569c-1.104-3.96-3.898-5.984-8.304-6.015-2.91.022-5.11.936-6.54 2.717C4.307 6.504 3.616 8.914 3.589 12c.027 3.086.718 5.496 2.057 7.164 1.43 1.783 3.631 2.698 6.54 2.717 2.623-.02 4.358-.631 5.8-2.045 1.647-1.613 1.618-3.593 1.09-4.798-.31-.71-.873-1.3-1.634-1.75-.192 1.352-.622 2.446-1.284 3.272-.886 1.102-2.14 1.704-3.73 1.79-1.202.065-2.361-.218-3.259-.801-1.063-.689-1.685-1.74-1.752-2.96-.065-1.187.408-2.26 1.33-3.017.88-.724 2.10-1.14 3.531-1.205 1.07-.049 2.07.058 2.986.318-.076-1.382-.603-2.417-1.58-3.084-.837-.573-1.947-.864-3.298-.864h-.038c-1.107.008-2.072.258-2.867.74l-1.02-1.775c1.07-.648 2.396-.999 3.895-1.012h.05c1.78 0 3.263.451 4.41 1.34 1.223.946 1.93 2.328 2.098 4.107.585.26 1.116.586 1.586.978 1.07.893 1.802 2.127 2.119 3.573.434 1.98.065 4.396-1.98 6.399-1.77 1.736-3.97 2.498-7.109 2.523zm-1.478-7.889c-.236 0-.47.014-.7.04-.96.097-2.255.444-2.201 1.735.034.77.497 1.292 1.379 1.583.344.115.73.17 1.148.17.617 0 1.282-.135 1.814-.557.623-.493.99-1.282 1.092-2.345-.692-.235-1.476-.532-2.242-.582-.1-.008-.194-.012-.29-.044z"/>
                    </svg>
                    <span v-else class="text-body-xs font-bold text-white">{{ platform.label.charAt(0) }}</span>
                  </div>
                  <div>
                    <p class="font-semibold text-gray-900 dark:text-gray-100">{{ platform.label }}</p>
                    <p v-if="connectedPlatforms.has(platform.key)" class="text-body-xs text-success-strong">{{ t('onboarding.channels.connected') }}</p>
                    <p v-else class="text-body-xs text-gray-400 dark:text-gray-500">{{ platform.description }}</p>
                  </div>
                </div>
                <button
                  v-if="connectedPlatforms.has(platform.key)"
                  :disabled="disconnectingPlatform === platform.key"
                  class="rounded-lg border border-gray-200 dark:border-gray-700 px-4 py-2 text-caption text-gray-500 dark:text-gray-400 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
                  @click="disconnectPlatform(platform.key)"
                >
                  {{ disconnectingPlatform === platform.key ? t('onboarding.channels.disconnecting') : t('onboarding.channels.disconnect') }}
                </button>
                <button
                  v-else
                  :disabled="isConnecting"
                  class="btn-primary text-body-xs"
                  @click="connectPlatform(platform.key)"
                >
                  {{ t('onboarding.channels.connect') }}
                </button>
              </div>
            </div>

            <p v-if="channelError" class="mt-4 text-center text-body text-error-strong">{{ channelError }}</p>
          </div>

          <!-- Step 3: Plan Selection -->
          <div v-else-if="currentStep === 3">
            <div class="mb-6 text-center">
              <h2 class="text-h1 font-bold text-gray-900 dark:text-gray-100">{{ t('onboarding.plan.title') }}</h2>
              <p class="mt-2 text-body text-gray-500 dark:text-gray-400">{{ t('onboarding.plan.description') }}</p>
            </div>

            <div class="space-y-4">
              <PlanSelectionCard
                v-for="plan in displayPlans"
                :key="plan.type"
                :plan="plan"
                :is-selected="selectedPlan === plan.type"
                :is-recommended="plan.type === 'STARTER'"
                @select="selectedPlan = $event"
              />
            </div>
          </div>

          <!-- Step 4: AI Trial -->
          <div v-else-if="currentStep === 4">
            <div class="mb-6 text-center">
              <h2 class="text-h1 font-bold text-gray-900 dark:text-gray-100">{{ t('onboarding.aiTrial.title') }}</h2>
              <p class="mt-2 text-body text-gray-500 dark:text-gray-400">
                {{ t('onboarding.aiTrial.description') }}
              </p>
            </div>

            <div class="rounded-2xl bg-white dark:bg-gray-800 p-6 shadow-sm tablet:p-8">
              <!-- AI Demo -->
              <div v-if="!aiTrialResult" class="text-center">
                <div class="mx-auto mb-6 flex h-20 w-20 items-center justify-center rounded-2xl bg-primary-100">
                  <svg class="h-10 w-10 text-primary-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 00-2.455 2.456z" />
                  </svg>
                </div>
                <h3 class="mb-2 text-title font-semibold text-gray-900 dark:text-gray-100">{{ t('onboarding.aiTrial.demoTitle') }}</h3>
                <p class="mb-6 whitespace-pre-line text-body text-gray-500 dark:text-gray-400">
                  {{ t('onboarding.aiTrial.demoDescription') }}
                </p>
                <div class="mb-4 inline-flex items-center gap-1 rounded-full bg-warning-subtle px-3 py-1 text-body-xs text-warning-strong">
                  <svg class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  {{ t('onboarding.aiTrial.creditCost', { count: AI_TRIAL_CREDIT_COST }) }}
                </div>
                <div>
                  <button
                    :disabled="isAiLoading"
                    class="btn-primary rounded-xl px-8 py-3 text-body disabled:opacity-50"
                    @click="tryAiGeneration"
                  >
                    <span v-if="isAiLoading" class="flex items-center gap-2">
                      <span class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></span>
                      {{ t('onboarding.aiTrial.generating') }}
                    </span>
                    <span v-else>{{ t('onboarding.aiTrial.tryIt') }}</span>
                  </button>
                </div>
                <p v-if="aiTrialError" class="mt-3 text-body text-error-strong">{{ aiTrialError }}</p>
              </div>

              <!-- AI Result -->
              <div v-else>
                <div class="mb-4 flex items-center gap-2">
                  <svg class="h-5 w-5 text-success-strong" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  <h3 class="font-semibold text-gray-900 dark:text-gray-100">{{ t('onboarding.aiTrial.resultTitle') }}</h3>
                </div>

                <div class="space-y-4">
                  <div class="rounded-xl bg-gray-50 dark:bg-gray-900 p-4">
                    <p class="mb-2 text-caption text-gray-500 dark:text-gray-400">{{ t('onboarding.aiTrial.suggestedTitles') }}</p>
                    <ul class="space-y-1">
                      <li v-for="(title, i) in aiTrialResult.titles" :key="i" class="text-body text-gray-800 dark:text-gray-200">
                        {{ i + 1 }}. {{ title }}
                      </li>
                    </ul>
                  </div>
                  <div class="rounded-xl bg-gray-50 dark:bg-gray-900 p-4">
                    <p class="mb-2 text-caption text-gray-500 dark:text-gray-400">{{ t('onboarding.aiTrial.suggestedTags') }}</p>
                    <div class="flex flex-wrap gap-1.5">
                      <span
                        v-for="tag in aiTrialResult.tags"
                        :key="tag"
                        class="rounded-full bg-primary-100 dark:bg-primary-900/30 px-2.5 py-1 text-body-xs text-primary-700"
                      >
                        #{{ tag }}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Step 5: Completion -->
          <div v-else-if="currentStep === 5" class="text-center">
            <div class="mx-auto mb-8 flex h-24 w-24 items-center justify-center rounded-full bg-success-subtle">
              <svg class="h-12 w-12 text-success-strong" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <h2 class="mb-3 text-display font-bold text-gray-900 dark:text-gray-100">
              {{ t('onboarding.complete.title', { nickname: profile.nickname }) }}
            </h2>
            <p class="mb-2 text-gray-500 dark:text-gray-400">
              {{ t('onboarding.complete.description') }}
            </p>
            <p class="mb-8 text-body text-gray-400 dark:text-gray-500">
              {{ t('onboarding.complete.hint') }}
            </p>

            <div class="space-y-3">
              <button
                class="btn-primary w-full tablet:w-auto px-10 py-3.5"
                @click="goToUpload"
              >
                {{ t('onboarding.complete.goToUpload') }}
              </button>
              <div>
                <button
                  class="text-body font-medium text-gray-500 dark:text-gray-400 transition-colors hover:text-gray-700 dark:hover:text-gray-300"
                  @click="goToDashboard"
                >
                  {{ t('onboarding.complete.goToDashboard') }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </Transition>

      <!-- Navigation Buttons -->
      <div v-if="currentStep > 0 && currentStep <= 4" class="mt-8 flex items-center justify-between">
        <button
          v-if="currentStep > 1"
          class="btn-secondary flex items-center gap-1"
          @click="prevStep"
        >
          <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
          </svg>
          {{ t('action.previous') }}
        </button>
        <div v-else></div>

        <div class="flex items-center gap-3">
          <button
            v-if="currentStep === 4"
            class="rounded-xl px-5 py-2.5 text-body font-medium text-gray-500 dark:text-gray-400 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700"
            @click="skipAiTrial"
          >
            {{ t('onboarding.nav.skip') }}
          </button>
          <button
            :disabled="isSubmitting"
            class="btn-primary flex items-center gap-1"
            @click="nextStep"
          >
            <span v-if="isSubmitting" class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></span>
            <span v-else>
              {{ currentStep === 4 ? t('onboarding.nav.finish') : t('action.next') }}
            </span>
            <svg v-if="!isSubmitting" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import type { CreatorCategory } from '@/types/user'
import type { Channel, Platform } from '@/types/channel'
import type { PlatformUploadCapability } from '@/types/video'
import type { PlanType } from '@/types/subscription'
import { authApi } from '@/api/auth'
import { aiApi } from '@/api/ai'
import { channelApi } from '@/api/channel'
import { videoApi } from '@/api/video'
import { subscriptionApi } from '@/api/subscription'
import { buildOAuthUrl, generateOAuthStateNonce, generatePKCE } from '@/utils/oauth'
import { ArrowUpTrayIcon, SparklesIcon, ChartBarIcon } from '@heroicons/vue/24/outline'
import OnboardingStepIndicator from '@/components/onboarding/OnboardingStepIndicator.vue'
import PlanSelectionCard from '@/components/onboarding/PlanSelectionCard.vue'
import { PLANS } from '@/types/subscription'
import OnGoLogo from '@/components/brand/OnGoLogo.vue'

const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n({ useScope: 'global' })

/** AI 체험 1회당 차감되는 크레딧 */
const AI_TRIAL_CREDIT_COST = 5

const currentStep = ref(0)
const isSubmitting = ref(false)
const isConnecting = ref(false)
const isAiLoading = ref(false)
const transitionName = ref('slide-left')

// Step 1: Profile
const profile = reactive({
  nickname: authStore.user?.nickname || '',
  category: (authStore.user?.category || '') as CreatorCategory | '',
})
const nicknameError = ref('')
const categoryError = ref('')

// Step 2: Channels
const connectedPlatforms = ref<Set<Platform>>(new Set())
const connectedChannelIds = ref<Partial<Record<Platform, number>>>({})
const channelError = ref('')
const platformCapabilities = ref<PlatformUploadCapability[]>([])
const platformLoadError = ref('')
const disconnectingPlatform = ref<Platform | null>(null)

// Step 3: Plan Selection
const selectedPlan = ref<PlanType>('FREE')
const displayPlans = computed(() => PLANS.slice(0, 3))

// Step 4: AI trial
const aiTrialResult = ref<{ titles: string[]; tags: string[] } | null>(null)
const aiTrialError = ref('')

const steps = computed<{ number: number; label: string }[]>(() => [
  { number: 1, label: t('onboarding.steps.profile') },
  { number: 2, label: t('onboarding.steps.channels') },
  { number: 3, label: t('onboarding.steps.plan') },
  { number: 4, label: t('onboarding.steps.aiTrial') },
])

const categories = computed<{ value: CreatorCategory; label: string; icon: string }[]>(() => [
  { value: 'BEAUTY', label: t('settings.categories.beauty'), icon: '💄' },
  { value: 'FOOD', label: t('settings.categories.food'), icon: '🍔' },
  { value: 'GAME', label: t('settings.categories.game'), icon: '🎮' },
  { value: 'DAILY', label: t('settings.categories.daily'), icon: '📸' },
  { value: 'EDUCATION', label: t('settings.categories.education'), icon: '📚' },
  { value: 'IT', label: t('settings.categories.it'), icon: '💻' },
  { value: 'TRAVEL', label: t('settings.categories.travel'), icon: '✈️' },
  { value: 'MUSIC', label: t('settings.categories.music'), icon: '🎵' },
])

const platformDescriptions: Record<Platform, string> = {
  YOUTUBE: 'youtube',
  TIKTOK: 'tiktok',
  INSTAGRAM: 'instagram',
  NAVER_CLIP: 'naverClip',
  TWITTER: 'twitter',
  FACEBOOK: 'facebook',
  THREADS: 'threads',
  PINTEREST: 'pinterest',
  LINKEDIN: 'linkedin',
  WORDPRESS: 'wordpress',
  DAILYMOTION: 'dailymotion',
  VIMEO: 'vimeo',
  TUMBLR: 'tumblr',
}

const platforms = computed<{ key: Platform; label: string; description: string; bgColor: string }[]>(() =>
  platformCapabilities.value
    .filter((capability) => capability.directVideoUpload || capability.cloudVideoUpload)
    .map((capability) => {
      const key = capability.platform
      const config = {
        YOUTUBE: ['YouTube', '#FF0000'], TIKTOK: ['TikTok', '#000000'], INSTAGRAM: ['Instagram Reels', '#E1306C'],
        NAVER_CLIP: ['Naver Clip', '#03C75A'], TWITTER: ['X (Twitter)', '#000000'], FACEBOOK: ['Facebook', '#1877F2'],
        THREADS: ['Threads', '#000000'], PINTEREST: ['Pinterest', '#E60023'], LINKEDIN: ['LinkedIn', '#0A66C2'],
        WORDPRESS: ['WordPress.com', '#21759B'], DAILYMOTION: ['Dailymotion', '#00D2F3'], VIMEO: ['Vimeo', '#1AB7EA'],
        TUMBLR: ['Tumblr', '#36465D'],
      } as Record<Platform, [string, string]>
      return {
        key,
        label: config[key][0],
        description: t(`onboarding.channels.descriptions.${platformDescriptions[key]}`),
        bgColor: config[key][1],
      }
    }),
)

function startOnboarding() {
  currentStep.value = 1
}

function validateStep1(): boolean {
  let valid = true

  if (!profile.nickname.trim()) {
    nicknameError.value = t('onboarding.profile.nicknameRequired')
    valid = false
  } else if (profile.nickname.trim().length < 2) {
    nicknameError.value = t('onboarding.profile.nicknameMinLength')
    valid = false
  }

  if (!profile.category) {
    categoryError.value = t('onboarding.profile.categoryRequired')
    valid = false
  }

  return valid
}

function validateStep2(): boolean {
  if (connectedPlatforms.value.size === 0) {
    channelError.value = t('onboarding.channels.required')
    return false
  }
  channelError.value = ''
  return true
}

async function nextStep() {
  transitionName.value = 'slide-left'

  if (currentStep.value === 1) {
    if (!validateStep1()) return

    isSubmitting.value = true
    try {
      await authApi.updateProfile({
        nickname: profile.nickname.trim(),
        category: profile.category as CreatorCategory,
      })
      currentStep.value = 2
    } catch {
      nicknameError.value = t('onboarding.profile.saveError')
    } finally {
      isSubmitting.value = false
    }
    return
  }

  if (currentStep.value === 2) {
    if (!validateStep2()) return
    currentStep.value = 3
    return
  }

  if (currentStep.value === 3) {
    currentStep.value = 4
    return
  }

  if (currentStep.value === 4) {
    await completeOnboarding()
  }
}

function prevStep() {
  transitionName.value = 'slide-right'
  if (currentStep.value > 1) {
    currentStep.value--
  }
}

async function connectPlatform(platform: Platform) {
  isConnecting.value = true
  channelError.value = ''
  try {
    // Twitter 는 PKCE 가 필수라 code_challenge 없이 URL 을 만들면 예외가 난다.
    // 예전에는 이 함수가 동기였고 try/catch 도 없어서, X 연동을 누르면 isConnecting 이
    // true 로 굳어 나머지 플랫폼 버튼까지 전부 비활성화됐다(온보딩 이탈 불가).
    const challenge = platform === 'TWITTER'
      ? (await generatePKCE('twitter_code_verifier')).challenge
      : undefined
    window.location.href = buildOAuthUrl(platform, '/onboarding', challenge, generateOAuthStateNonce())
  } catch (e) {
    isConnecting.value = false
    channelError.value = e instanceof Error ? e.message : t('onboarding.channels.connectFailed')
  }
}

function disconnectPlatform(platform: Platform) {
  const channelId = connectedChannelIds.value[platform]
  if (!channelId || disconnectingPlatform.value) return
  disconnectingPlatform.value = platform
  channelError.value = ''
  void channelApi.disconnect(channelId)
    .then(() => {
      const nextPlatforms = new Set(connectedPlatforms.value)
      nextPlatforms.delete(platform)
      connectedPlatforms.value = nextPlatforms
      const nextIds = { ...connectedChannelIds.value }
      delete nextIds[platform]
      connectedChannelIds.value = nextIds
    })
    .catch((error) => {
      channelError.value = error instanceof Error ? error.message : t('onboarding.channels.disconnectFailed')
    })
    .finally(() => {
      disconnectingPlatform.value = null
    })
}

async function tryAiGeneration() {
  isAiLoading.value = true
  aiTrialError.value = ''
  try {
    const result = await aiApi.demoGenerate(profile.category || 'DEFAULT')
    aiTrialResult.value = result
  } catch (e: unknown) {
    aiTrialError.value = e instanceof Error ? e.message : t('onboarding.aiTrial.error')
  } finally {
    isAiLoading.value = false
  }
}

async function skipAiTrial() {
  transitionName.value = 'slide-left'
  await completeOnboarding()
}

async function completeOnboarding() {
  isSubmitting.value = true
  try {
    await authApi.completeOnboarding()
    if (authStore.user) {
      authStore.user.onboardingCompleted = true
    }
    // 무료가 아닌 플랜 선택 시 구독 변경 (실패해도 온보딩 완료 진행)
    if (selectedPlan.value !== 'FREE') {
      try {
        await subscriptionApi.changePlan({ targetPlan: selectedPlan.value })
      } catch {
        // 플랜 변경 실패 시 무시 - 나중에 구독 페이지에서 변경 가능
      }
    }
    currentStep.value = 5
  } catch {
    // Still proceed to completion screen
    currentStep.value = 5
  } finally {
    isSubmitting.value = false
  }
}

function goToUpload() {
  router.push(authStore.consumePostLoginRedirect() ?? '/upload')
}

function goToDashboard() {
  router.push(authStore.consumePostLoginRedirect() ?? '/dashboard')
}

// Load existing connected channels
async function loadConnectedChannels() {
  try {
    const { channels } = await channelApi.list()
    const nextIds: Partial<Record<Platform, number>> = {}
    channels.forEach((ch: Channel) => {
      connectedPlatforms.value.add(ch.platform)
      nextIds[ch.platform] = ch.id
    })
    connectedChannelIds.value = nextIds
  } catch {
    // No channels yet, that's fine
  }
}

async function loadPlatformCapabilities() {
  try {
    platformCapabilities.value = await videoApi.getUploadCapabilities()
    platformLoadError.value = ''
  } catch (error) {
    platformCapabilities.value = []
    platformLoadError.value = error instanceof Error ? error.message : '플랫폼 목록을 불러오지 못했습니다.'
  }
}

// 서버가 실제로 활성화한 플랫폼과 연결 상태를 함께 로드한다.
void Promise.all([loadConnectedChannels(), loadPlatformCapabilities()])
</script>

<style scoped>
.slide-left-enter-active,
.slide-left-leave-active,
.slide-right-enter-active,
.slide-right-leave-active {
  transition: all 0.3s ease-out;
}

.slide-left-enter-from {
  opacity: 0;
  transform: translateX(30px);
}

.slide-left-leave-to {
  opacity: 0;
  transform: translateX(-30px);
}

.slide-right-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}

.slide-right-leave-to {
  opacity: 0;
  transform: translateX(30px);
}
</style>
