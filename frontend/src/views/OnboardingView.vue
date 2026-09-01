<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900">
    <!-- Header -->
    <div class="border-b dark:border-gray-700 bg-white dark:bg-gray-800 px-4 py-4">
      <div class="flex max-w-3xl items-center justify-between">
        <OnGoLogo size="md" />
        <div class="flex items-center gap-4">
          <span v-if="currentStep > 0 && currentStep <= 4" class="text-body text-gray-400 dark:text-gray-500">
            {{ t('onboarding.stepProgress', { current: currentStep, total: steps.length }) }}
          </span>
          <!-- 온보딩은 AppLayout 밖이라 TopBar 가 없다. 여기서 나갈 방법이 없으면
               다른 계정으로 로그인하거나 중단할 수가 없다. -->
          <button type="button" class="btn-secondary" @click="authStore.logout()">
            <ArrowRightOnRectangleIcon class="h-4 w-4" />
            {{ t('nav.logout') }}
          </button>
        </div>
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
              <div v-else-if="platforms.length === 0 && unavailablePlatforms.length === 0" class="rounded-xl border border-gray-200 bg-white px-4 py-6 text-center text-body text-gray-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-400">
                {{ t('onboarding.channels.loadingPlatforms') }}
              </div>
              <div
                v-if="unavailablePlatforms.length"
                class="rounded-xl border border-warning-subtle bg-warning-subtle px-4 py-3 text-body text-warning-strong"
                role="status"
              >
                <p class="font-semibold">{{ t('onboarding.channels.unavailableTitle') }}</p>
                <p class="mt-1 text-body-xs leading-5">{{ t('onboarding.channels.unavailableDescription') }}</p>
                <p class="mt-1 text-body-xs font-medium">{{ unavailablePlatformLabels }}</p>
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
            <p v-else class="mt-4 text-center text-body-xs text-gray-500 dark:text-gray-400">
              {{ t('onboarding.channels.optionalHint') }}
            </p>
          </div>

          <!-- Step 3: Plan Selection -->
          <div v-else-if="currentStep === 3">
            <div class="mb-6 text-center">
              <h2 class="text-h1 font-bold text-gray-900 dark:text-gray-100">{{ t('onboarding.plan.title') }}</h2>
              <p class="mt-2 text-body text-gray-500 dark:text-gray-400">{{ t('onboarding.plan.description') }}</p>
            </div>

            <!--
              결제를 시작할 수 없으면 유료 플랜을 고를 수 없게 만든다. 고른 뒤 '다음'에서
              막으면 사용자는 이미 결정을 내린 뒤에 되돌려지고, 왜인지도 모른다.
            -->
            <p
              v-if="!paymentEnabled"
              class="mb-4 rounded-lg border border-warning-strong/40 bg-warning-subtle p-3 text-body-xs text-warning-strong"
              role="status"
            >
              {{ paymentUnavailableCopy }}
            </p>

            <!--
              조회 실패를 빈 화면으로 두지 않는다. 카드가 없는 이유를 말해 주지 않으면
              사용자는 플랜이 사라진 줄 안다. 무료로 계속 갈 수 있다는 점까지 같이 알린다.
            -->
            <p
              v-if="isLoadingPlans"
              class="mb-4 text-body-xs text-gray-500 dark:text-gray-400"
              role="status"
            >
              플랜 정보를 불러오는 중…
            </p>
            <div
              v-else-if="plansError"
              class="mb-4 rounded-lg border border-warning-strong/40 bg-warning-subtle p-3 text-body-xs text-warning-strong"
              role="status"
              data-testid="onboarding-plans-error"
            >
              <p>{{ plansError }} 무료 플랜으로 계속 진행할 수 있습니다.</p>
              <button type="button" class="btn-secondary mt-2 text-body-xs" @click="loadPlans">
                다시 시도
              </button>
            </div>

            <div class="space-y-4">
              <PlanSelectionCard
                v-for="plan in displayPlans"
                :key="plan.type"
                :plan="plan"
                :is-selected="selectedPlan === plan.type"
                :is-recommended="plan.type === 'STARTER'"
                :disabled="isPlanUnavailable(plan.type)"
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
                <p class="mb-4 whitespace-pre-line text-body text-gray-500 dark:text-gray-400">
                  {{ t('onboarding.aiTrial.demoDescription') }}
                </p>

                <!--
                  샘플이 아니라 **사용자 자기 스크립트**를 받는다. 남의 영상으로 만든 제목은
                  AI 가 돈다는 증명일 뿐이라, 첫 가치가 되지 못한다.
                -->
                <div class="mb-4 text-left">
                  <label for="ai-trial-script" class="mb-1.5 block text-body font-medium text-gray-700 dark:text-gray-300">
                    {{ t('onboarding.aiTrial.scriptLabel') }}
                  </label>
                  <textarea
                    id="ai-trial-script"
                    v-model="aiTrialScript"
                    rows="4"
                    class="input-field w-full"
                    :placeholder="t('onboarding.aiTrial.scriptPlaceholder')"
                    :disabled="isAiLoading"
                  />
                </div>
                <div class="mb-4 inline-flex items-center gap-1 rounded-full bg-warning-subtle px-3 py-1 text-body-xs text-warning-strong">
                  <svg class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  <span v-if="aiTrialCreditCost != null">
                    {{ t('onboarding.aiTrial.creditCost', { count: aiTrialCreditCost }) }}
                  </span>
                  <span v-else>{{ t('onboarding.aiTrial.creditChecking') }}</span>
                </div>
                <p v-if="aiTrialPricingError" class="mb-4 text-body-xs text-error-strong" role="alert">
                  {{ aiTrialPricingError }}
                  <button type="button" class="ml-1 underline" @click="loadAiFeaturePricing">
                    {{ t('common.retry') }}
                  </button>
                </p>
                <div>
                  <button
                    :disabled="isAiLoading || !hasAiTrialScript || aiTrialCreditCost == null"
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
                <!--
                  실패는 사유 그대로 보여주고 다음 행동을 남긴다. AI 가 안 되는 것과 온보딩을
                  못 끝내는 것은 다른 문제라, 여기서 막으면 안 된다.
                -->
                <p v-if="aiTrialError" class="mt-3 text-body text-error-strong">{{ aiTrialError }}</p>
                <p v-if="aiTrialError" class="mt-1 text-body-xs text-gray-500 dark:text-gray-400">
                  {{ t('onboarding.aiTrial.failureHint') }}
                </p>
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
                @click="goToFirstValue"
              >
                {{ t('onboarding.complete.goToFirstValue') }}
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

      <!-- 온보딩 완료 저장 실패. 완료 화면으로 넘기지 않고 여기서 멈춰 재시도를 받는다. -->
      <div
        v-if="completeError"
        role="alert"
        class="mt-8 rounded-lg bg-error-subtle p-3 text-center text-body text-error-strong"
      >
        {{ completeError }}
      </div>

      <!-- 유료 플랜은 온보딩 완료와 분리해, 실제 결제 검증이 끝난 뒤에만 다음 단계로 간다.
           선택만으로 구독이 바뀌거나 결제 실패가 숨겨지면 가격표는 있어도 매출이 생기지 않는다. -->
      <PaymentModal
        v-model="showPaymentModal"
        :target-plan="selectedPlan"
        :price="selectedPlanInfo?.price ?? 0"
        :plan="selectedPlanInfo ?? null"
        @confirm="handlePlanPaymentSuccess"
      />

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
            v-if="currentStep === 2 && connectedPlatforms.size === 0"
            class="rounded-xl px-5 py-2.5 text-body font-medium text-gray-500 dark:text-gray-400 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700"
            @click="skipChannels"
          >
            {{ t('onboarding.channels.skip') }}
          </button>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { usePaymentAvailability } from '@/composables/usePaymentAvailability'
import { useAiFeaturePricing } from '@/composables/useAiFeaturePricing'
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
import { buildOAuthState, generateOAuthStateNonce, generatePKCE, getOAuthRedirectUri, storeChannelOAuthContext } from '@/utils/oauth'
import { ArrowUpTrayIcon, SparklesIcon, ChartBarIcon, ArrowRightOnRectangleIcon } from '@heroicons/vue/24/outline'
import OnboardingStepIndicator from '@/components/onboarding/OnboardingStepIndicator.vue'
import PlanSelectionCard from '@/components/onboarding/PlanSelectionCard.vue'
import PaymentModal from '@/components/subscription/PaymentModal.vue'
import { useSubscriptionStore } from '@/stores/subscription'
import OnGoLogo from '@/components/brand/OnGoLogo.vue'

const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n({ useScope: 'global' })

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
/** 온보딩 완료 저장 실패 사유. 값이 있으면 완료 화면으로 넘어가지 않는다. */
const completeError = ref('')
const platformCapabilities = ref<PlatformUploadCapability[]>([])
const platformLoadError = ref('')
const disconnectingPlatform = ref<Platform | null>(null)

// Step 3: Plan Selection

/**
 * 이미 결제가 끝난 유료 플랜. 무료·미인증 상태에서는 null 이다.
 *
 * 온보딩을 끝내기 전에 새로고침하거나 이탈하면 라우터 가드가 다시 이 화면으로 돌려보내는데,
 * 화면 상태는 전부 초기화된다. 그런데 결제는 서버에 이미 남아 있어, 같은 플랜을 다시 고르면
 * 서버의 중복 결제 가드가 400 으로 막아 결제한 사용자가 진행하지 못했다.
 * 그래서 인증 프로필의 planType 을 초기 상태의 정본으로 삼는다.
 */
function alreadyPaidPlan(): PlanType | null {
  const planType = authStore.user?.planType
  return planType && planType !== 'FREE' ? planType : null
}

const selectedPlan = ref<PlanType>(alreadyPaidPlan() ?? 'FREE')
/*
 * 플랜 목록은 **서버가 준다.** 가격과 한도는 서버가 결제 기준으로 삼는 값이라, 화면이
 * 자기 상수를 그리면 사용자가 본 금액과 청구액이 갈릴 수 있다.
 *
 * 노출 범위(앞 세 개)는 종전과 같다. 어떤 플랜을 온보딩에 보여줄지는 별도 판단이라
 * 이번에 바꾸지 않는다.
 *
 * 조회 전·실패 시에는 빈 목록이다 — 오래된 숫자를 대신 그리지 않는다. 무료 플랜은
 * 기본 선택으로 남아 있어 온보딩 자체는 계속 진행할 수 있다.
 */
const subscriptionStore = useSubscriptionStore()
const displayPlans = computed(() => subscriptionStore.plans.slice(0, 3))
const selectedPlanInfo = computed(() =>
  subscriptionStore.plans.find((plan) => plan.type === selectedPlan.value),
)
const showPaymentModal = ref(false)

/*
 * 결제를 마친 플랜. 4단계에서 '이전'으로 3단계에 돌아와 '다음'을 다시 누르면 같은 구독을
 * 한 번 더 결제할 수 있었다(complete 의 멱등성은 paymentId 단위라 새 체크아웃은 별건으로
 * 통과해 카드가 두 번 청구된다).
 *
 * 선택한 플랜을 바꾸면 다시 결제해야 하므로, 불리언이 아니라 **결제한 플랜**을 기억한다.
 * 새로고침으로 이 값이 사라져도 프로필에서 복원되며, 서버의 중복 결제 가드가 최종 방어선이다.
 */
const paidPlan = ref<PlanType | null>(alreadyPaidPlan())

/*
 * 결제 가능 여부는 서버가 정한다. SubscriptionView 와 같은 계약을 쓴다 — 온보딩만 따로
 * 판단하면 두 화면이 어긋나고, 결제 설정은 배포 환경에 있어 클라이언트가 볼 수 없다.
 */
const { paymentEnabled, paymentDisabledReason, loadPaymentAvailability } = usePaymentAvailability()
const {
  error: aiTrialPricingError,
  load: loadAiFeaturePricing,
  costOf: aiFeatureCostOf,
} = useAiFeaturePricing()
const aiTrialCreditCost = computed(() => aiFeatureCostOf('META_GENERATION'))

/** 서버가 이유를 주면 그것을 쓰고, 없으면 같은 뜻의 기본 문구를 쓴다. */
const paymentUnavailableCopy = computed(
  () => paymentDisabledReason.value
    ?? '온라인 결제를 일시적으로 사용할 수 없습니다. 지금은 무료 플랜으로 시작하고, 나중에 구독 화면에서 전환할 수 있습니다.',
)

/**
 * 지금 고를 수 없는 플랜인지.
 *
 * 무료는 언제나 고를 수 있다 — 결제가 막혔다고 가입 자체를 막을 이유가 없다.
 * 이미 결제한 플랜도 막지 않는다. 결제창을 다시 지나지 않으므로 깨질 흐름이 없다.
 */
function isPlanUnavailable(planType: PlanType): boolean {
  if (planType === 'FREE') return false
  if (paidPlan.value === planType) return false
  return !paymentEnabled.value
}

/*
 * 플랜 조회 상태는 이 화면이 따로 들고 있다. 스토어의 `error` 는 구독·결제 조회와
 * 공유하는 자리라, 그것으로 판단하면 다른 실패를 플랜 실패로 그리게 된다.
 */
const isLoadingPlans = ref(false)
const plansError = ref<string | null>(null)

/** 실패해도 온보딩은 계속되어야 한다 — 무료 플랜 기본 선택은 그대로 남는다. */
async function loadPlans() {
  isLoadingPlans.value = true
  plansError.value = null
  try {
    await subscriptionStore.fetchPlans()
  } catch (cause) {
    // 오래된 가격을 대신 그리지 않는다. 못 불러왔다는 사실 자체를 보여 준다.
    plansError.value = cause instanceof Error ? cause.message : '플랜 정보를 불러오지 못했습니다.'
  } finally {
    isLoadingPlans.value = false
  }
}

onMounted(() => {
  // 실패해도 온보딩은 계속되어야 한다. 조회 실패는 composable 이 사용 불가로 처리한다.
  void loadPaymentAvailability()
  // 체험 비용도 서버 정본에서 읽는다. 비용을 모르면 체험 버튼만 닫고 온보딩은 계속한다.
  void loadAiFeaturePricing()
  /*
   * 플랜 목록도 서버에서 받는다. 스토어가 실패 시 목록을 비우므로 화면은 빈 상태가 되고,
   * 무료 플랜 기본 선택으로 온보딩은 계속된다 — 오래된 가격을 보여 주지 않는다.
   */
  void loadPlans()
})

// Step 4: AI trial
const aiTrialResult = ref<{ titles: string[]; tags: string[] } | null>(null)
const aiTrialScript = ref('')
/** 공백만 있는 입력도 호출 대상이 아니다. */
const hasAiTrialScript = computed(() => aiTrialScript.value.trim().length > 0)
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

const platformLabels: Record<Platform, string> = {
  YOUTUBE: 'YouTube', TIKTOK: 'TikTok', INSTAGRAM: 'Instagram', NAVER_CLIP: 'Naver Clip',
  TWITTER: 'X', FACEBOOK: 'Facebook', THREADS: 'Threads', PINTEREST: 'Pinterest',
  LINKEDIN: 'LinkedIn', WORDPRESS: 'WordPress.com', DAILYMOTION: 'Dailymotion',
  VIMEO: 'Vimeo', TUMBLR: 'Tumblr',
}

const platforms = computed<{ key: Platform; label: string; description: string; bgColor: string }[]>(() =>
  platformCapabilities.value
    .filter((capability) => (capability.directVideoUpload || capability.cloudVideoUpload) && capability.configurationAvailable !== false)
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

const unavailablePlatforms = computed(() => platformCapabilities.value
  .filter((capability) => (capability.directVideoUpload || capability.cloudVideoUpload) && capability.configurationAvailable === false)
  .map((capability) => capability.platform))

const unavailablePlatformLabels = computed(() => unavailablePlatforms.value
  .map((platform) => platformLabels[platform] ?? platform)
  .join(', '))

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
  channelError.value = ''
  return true
}

function skipChannels() {
  channelError.value = ''
  transitionName.value = 'slide-left'
  currentStep.value = 3
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
    // 이미 결제한 플랜이면 결제창을 다시 열지 않는다.
    if (selectedPlan.value !== 'FREE' && paidPlan.value !== selectedPlan.value) {
      /*
       * 결제를 시작할 수 없는 상태면 결제창을 열지 않는다. 카드는 이미 비활성이지만,
       * 이전 단계에서 고른 값이 남아 있거나 상태가 바뀌었을 수 있어 여기서 한 번 더 막는다.
       * 이미 결제한 플랜(위 조건에서 걸러짐)은 결제창을 지나지 않으므로 영향이 없다.
       */
      if (!paymentEnabled.value) return
      showPaymentModal.value = true
      return
    }
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
    const clientState = buildOAuthState(platform, '/onboarding', generateOAuthStateNonce())
    const { authorizationUrl } = await channelApi.authorizationUrl(platform, {
      redirectUri: getOAuthRedirectUri(),
      state: clientState,
      codeChallenge: challenge,
    })
    storeChannelOAuthContext(clientState)
    window.location.href = authorizationUrl
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

/**
 * 사용자가 쓴 스크립트로 **정식 AI 경로**를 부른다.
 *
 * 예전에는 하드코딩된 샘플("크림파스타 레시피…")로 데모를 돌렸다. AI 가 돈다는 증명은 됐지만
 * 사용자 자기 콘텐츠가 아니라 첫 가치가 되지 못했고, 인증 없는 공개 LLM 엔드포인트라
 * 남용 표면이기도 했다.
 *
 * 정식 경로는 인증·분당 제한·크레딧 차감·실패 시 환불을 이미 갖추고 있어 여기서 다시 만들 게 없다.
 */
async function tryAiGeneration() {
  if (aiTrialCreditCost.value == null) {
    aiTrialPricingError.value = 'AI 기능 비용을 확인한 뒤 다시 시도해 주세요.'
    return
  }
  // 빈 입력으로 호출하면 크레딧만 쓰고 의미 없는 결과가 나온다.
  if (!hasAiTrialScript.value) {
    aiTrialError.value = t('onboarding.aiTrial.scriptRequired')
    return
  }

  isAiLoading.value = true
  aiTrialError.value = ''
  try {
    const result = await aiApi.generateMeta({
      script: aiTrialScript.value.trim(),
      useStt: false,
      // 온보딩 시점에는 연결된 채널이 없을 수 있다. 한 플랫폼 결과만으로 가치는 충분히 전달된다.
      targetPlatforms: ['YOUTUBE'],
      tone: 'FRIENDLY',
      category: profile.category || 'DEFAULT',
    })
    const first = result.platforms?.[0]
    aiTrialResult.value = {
      titles: first?.titleCandidates ?? [],
      tags: first?.hashtags ?? [],
    }
  } catch (e: unknown) {
    // 사유를 그대로 보여준다. 크레딧 부족·AI 장애·파싱 실패가 서로 다른 행동을 부르기 때문이다.
    aiTrialError.value = e instanceof Error && e.message
      ? e.message
      : t('onboarding.aiTrial.error')
  } finally {
    isAiLoading.value = false
  }
}

async function skipAiTrial() {
  transitionName.value = 'slide-left'
  await completeOnboarding()
}

async function handlePlanPaymentSuccess() {
  // PaymentModal emits this only after PortOne's server-side completion check.
  // Keep the user on plan selection when the modal is closed or payment fails.
  showPaymentModal.value = false
  paidPlan.value = selectedPlan.value

  /*
   * 결제가 서버 검증까지 끝났으므로 authStore.user.planType 이 아직 FREE 다.
   * planType 을 읽는 화면이 19곳이라, 갱신하지 않으면 방금 결제한 사용자가
   * 세션 내내 무료 플랜으로 보인다.
   *
   * 다만 이 재조회는 **결제 성공의 후속 작업일 뿐**이다. 실패해도 되돌릴 결제가
   * 아니므로 사용자를 결제 화면에 가두면 안 된다. fetchProfile 은 현재 내부에서
   * 예외를 삼키지만(그리고 인증 실패 시 세션을 정리한다), 구현이 바뀌어 reject 하게
   * 되더라도 unhandled rejection 으로 흐름이 끊기지 않도록 여기서도 막는다.
   * 어느 경로든 다음 단계로는 반드시 진행한다.
   */
  try {
    await authStore.fetchProfile()
  } catch {
    // 갱신 실패는 결제 결과에 영향을 주지 않는다. 다음 진입 시 다시 조회된다.
  }

  currentStep.value = 4
}

async function completeOnboarding() {
  isSubmitting.value = true
  completeError.value = ''
  try {
    await authApi.completeOnboarding()
    if (authStore.user) {
      authStore.user.onboardingCompleted = true
    }
    currentStep.value = 5
  } catch (e: unknown) {
    // 완료 화면으로 넘기지 않는다.
    //
    // 서버가 onboarding_completed 를 기록하지 못했는데 화면만 완료로 보이면,
    // 사용자는 끝난 줄 알고 나가지만 다음 로그인에 온보딩으로 되돌아온다.
    // 그때는 이유를 알 수 없어 서비스가 고장난 것처럼 보인다.
    // 현재 단계에 머무르고 재시도할 수 있게 사유를 보여준다.
    completeError.value = e instanceof Error && e.message
      ? e.message
      : t('onboarding.completeFailed')
  } finally {
    isSubmitting.value = false
  }
}

/**
 * 온보딩을 마친 사용자를 **오늘 실제로 동작하는 가치**로 데려간다.
 *
 * 기본값이 /upload(→ /compose)였는데, 방금 온보딩을 끝낸 사용자는 채널이 0개다. 채널 연결은
 * 플랫폼 앱 심사에 묶여 있어 거기서 할 수 있는 일이 없다 — 첫 화면이 막다른 길이었다.
 * AI 메타 생성은 스크립트를 붙여넣기만 하면 되고 영상·채널·플랫폼 승인이 전부 불필요해서,
 * 가입 직후 바로 자기 콘텐츠로 결과를 볼 수 있는 유일한 경로다.
 *
 * 명시적으로 요청된 목적지(딥링크·보호된 경로에서 튕겨 온 경우)는 그대로 우선한다.
 * 사용자가 가려던 곳을 우리 판단으로 덮어쓰면 안 된다.
 */
function goToFirstValue() {
  router.push(authStore.consumePostLoginRedirect() ?? '/ai')
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
