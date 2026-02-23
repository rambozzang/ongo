<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ t('settings.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ t('settings.description') }}
        </p>
      </div>
    </div>

    <PageGuide :title="t('settings.pageGuideTitle')" :items="(tm('settings.pageGuide') as string[])" />

    <!-- Tab Navigation -->
    <div class="mb-6 overflow-x-auto border-b border-gray-200 dark:border-gray-700">
      <nav class="-mb-px flex space-x-6 desktop:space-x-8" aria-label="Settings tabs">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="flex shrink-0 items-center gap-2 whitespace-nowrap border-b-2 px-1 py-3 text-sm font-medium transition-colors"
          :class="
            activeTab === tab.key
              ? 'border-primary-600 text-primary-600'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:border-gray-300 dark:hover:border-gray-600 hover:text-gray-700 dark:hover:text-gray-300'
          "
          @click="activeTab = tab.key"
        >
          <component :is="tab.icon" class="h-5 w-5" />
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- Profile Tab -->
    <div v-if="activeTab === 'profile'" class="space-y-6">
      <div class="card">
        <h2 class="mb-6 text-lg font-semibold text-gray-900 dark:text-gray-100">{{ t('settings.profile.title') }}</h2>

        <!-- Profile Image -->
        <div class="mb-6 flex items-center gap-6">
          <div class="relative">
            <div
              class="flex h-20 w-20 items-center justify-center overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700"
            >
              <img
                v-if="profileForm.profileImageUrl"
                :src="profileForm.profileImageUrl"
                :alt="t('settings.profile.profileImage')"
                class="h-full w-full object-cover"
              />
              <UserIcon v-else class="h-10 w-10 text-gray-400" />
            </div>
          </div>
          <div>
            <label
              for="profile-image-upload"
              class="btn-secondary cursor-pointer"
            >
              {{ t('settings.profile.changeImage') }}
            </label>
            <input
              id="profile-image-upload"
              type="file"
              accept="image/*"
              class="hidden"
              @change="handleImageUpload"
            />
            <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">{{ t('settings.profile.imageHint') }}</p>
          </div>
        </div>

        <!-- Nickname -->
        <div class="mb-6">
          <label for="nickname" class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ t('settings.profile.nickname') }}
          </label>
          <input
            id="nickname"
            v-model="profileForm.nickname"
            type="text"
            maxlength="20"
            :placeholder="t('settings.profile.nicknamePlaceholder')"
            class="input-field max-w-md"
          />
          <div class="mt-1 flex items-center justify-between max-w-md">
            <span class="text-xs text-gray-400 dark:text-gray-500">{{ t('settings.profile.nicknameLength') }}</span>
            <span class="text-xs text-gray-400 dark:text-gray-500">{{ profileForm.nickname.length }}/20</span>
          </div>
        </div>

        <!-- Creator Category -->
        <div class="mb-6">
          <label for="category" class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ t('settings.profile.category') }}
          </label>
          <select
            id="category"
            v-model="profileForm.category"
            class="input-field max-w-md"
          >
            <option value="">{{ t('settings.profile.selectCategory') }}</option>
            <option
              v-for="cat in categories"
              :key="cat.value"
              :value="cat.value"
            >
              {{ cat.label }}
            </option>
          </select>
        </div>

        <!-- Email (read-only) -->
        <div class="mb-6">
          <label class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ t('settings.profile.email') }}
          </label>
          <input
            type="email"
            :value="authStore.user?.email ?? ''"
            disabled
            class="input-field max-w-md cursor-not-allowed bg-gray-50 dark:bg-gray-900 text-gray-500 dark:text-gray-400"
          />
          <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">{{ t('settings.profile.emailHint') }}</p>
        </div>

        <!-- Connected Social Account (read-only) -->
        <div class="mb-6">
          <label class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ t('settings.profile.connectedSocial') }}
          </label>
          <div class="flex max-w-md items-center gap-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900 px-4 py-3">
            <!-- Google Icon -->
            <svg v-if="socialProvider === 'GOOGLE'" class="h-5 w-5" viewBox="0 0 24 24">
              <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"/>
              <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
              <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
              <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
            </svg>
            <!-- Kakao Icon -->
            <svg v-else-if="socialProvider === 'KAKAO'" class="h-5 w-5" viewBox="0 0 24 24">
              <path fill="#3C1E1E" d="M12 3C6.477 3 2 6.463 2 10.691c0 2.726 1.8 5.117 4.512 6.467-.197.735-1.273 4.716-1.31 5.015 0 0-.025.208.11.287.134.079.292.036.292.036.386-.054 4.472-2.926 5.178-3.424.39.056.793.085 1.218.085 5.523 0 10-3.463 10-7.691C22 6.463 17.523 3 12 3z"/>
            </svg>
            <div>
              <p class="text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ socialProvider === 'GOOGLE' ? 'Google' : 'Kakao' }} {{ t('settings.profile.account') }}
              </p>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ authStore.user?.email }}</p>
            </div>
            <span class="badge-success ml-auto">{{ t('settings.profile.connected') }}</span>
          </div>
        </div>

        <div class="flex justify-end border-t border-gray-100 dark:border-gray-700 pt-6">
          <button
            :disabled="isSavingProfile"
            class="btn-primary"
            @click="saveProfile"
          >
            <LoadingSpinner v-if="isSavingProfile" size="sm" class="mr-2" />
            {{ t('settings.save') }}
          </button>
        </div>
      </div>
    </div>

    <!-- Notifications Tab -->
    <div v-if="activeTab === 'notifications'" class="space-y-6">
      <div class="card">
        <h2 class="mb-6 text-lg font-semibold text-gray-900 dark:text-gray-100">{{ t('settings.notifications.title') }}</h2>

        <!-- Upload Notifications -->
        <div class="mb-8">
          <h3 class="mb-4 text-sm font-semibold text-gray-800 dark:text-gray-200">{{ t('settings.notifications.uploadAlerts') }}</h3>
          <div class="space-y-4">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm text-gray-700 dark:text-gray-300">{{ t('settings.notifications.emailAlert') }}</p>
                <p class="text-xs text-gray-400 dark:text-gray-500">{{ t('settings.notifications.emailAlertDesc') }}</p>
              </div>
              <button
                type="button"
                role="switch"
                :aria-checked="notificationForm.uploadEmail"
                class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"
                :class="notificationForm.uploadEmail ? 'bg-primary-600' : 'bg-gray-200 dark:bg-gray-700'"
                @click="notificationForm.uploadEmail = !notificationForm.uploadEmail"
              >
                <span
                  class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
                  :class="notificationForm.uploadEmail ? 'translate-x-5' : 'translate-x-0'"
                />
              </button>
            </div>
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm text-gray-700 dark:text-gray-300">{{ t('settings.notifications.pushAlert') }}</p>
                <p class="text-xs text-gray-400 dark:text-gray-500">{{ t('settings.notifications.pushAlertDesc') }}</p>
              </div>
              <button
                type="button"
                role="switch"
                :aria-checked="notificationForm.uploadPush"
                class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"
                :class="notificationForm.uploadPush ? 'bg-primary-600' : 'bg-gray-200 dark:bg-gray-700'"
                @click="notificationForm.uploadPush = !notificationForm.uploadPush"
              >
                <span
                  class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
                  :class="notificationForm.uploadPush ? 'translate-x-5' : 'translate-x-0'"
                />
              </button>
            </div>
          </div>
        </div>

        <!-- Comment Notifications -->
        <div class="mb-8">
          <h3 class="mb-4 text-sm font-semibold text-gray-800 dark:text-gray-200">{{ t('settings.notifications.commentAlerts') }}</h3>
          <div class="space-y-3">
            <label
              v-for="option in commentOptions"
              :key="option.value"
              class="flex cursor-pointer items-center gap-3 rounded-lg border px-4 py-3 transition-colors"
              :class="
                notificationForm.commentFrequency === option.value
                  ? 'border-primary-500 bg-primary-50 dark:border-primary-400 dark:bg-primary-900/20'
                  : 'border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700'
              "
            >
              <input
                v-model="notificationForm.commentFrequency"
                type="radio"
                name="commentFrequency"
                :value="option.value"
                class="h-4 w-4 border-gray-300 dark:border-gray-600 text-primary-600 focus:ring-primary-500"
              />
              <div>
                <p class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ option.label }}</p>
                <p class="text-xs text-gray-400 dark:text-gray-500">{{ option.description }}</p>
              </div>
            </label>
          </div>
        </div>

        <!-- Credit Remaining Alert -->
        <div class="mb-8">
          <h3 class="mb-4 text-sm font-semibold text-gray-800 dark:text-gray-200">{{ t('settings.notifications.creditAlert') }}</h3>
          <div class="flex items-center gap-3">
            <label for="creditThreshold" class="text-sm text-gray-700 dark:text-gray-300">
              {{ t('settings.notifications.creditThresholdPrefix') }}
            </label>
            <div class="relative w-24">
              <input
                id="creditThreshold"
                v-model.number="notificationForm.creditThreshold"
                type="number"
                min="5"
                max="50"
                step="5"
                class="input-field pr-7 text-center"
              />
              <span class="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-gray-400 dark:text-gray-500">%</span>
            </div>
            <span class="text-sm text-gray-700 dark:text-gray-300">{{ t('settings.notifications.creditThresholdSuffix') }}</span>
          </div>
          <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">{{ t('settings.notifications.creditThresholdHint') }}</p>
        </div>

        <!-- Schedule Reminder -->
        <div class="mb-8">
          <h3 class="mb-4 text-sm font-semibold text-gray-800 dark:text-gray-200">{{ t('settings.notifications.scheduleReminder') }}</h3>
          <div class="space-y-4">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm text-gray-700 dark:text-gray-300">{{ t('settings.notifications.reminder1h') }}</p>
                <p class="text-xs text-gray-400 dark:text-gray-500">{{ t('settings.notifications.reminder1hDesc') }}</p>
              </div>
              <button
                type="button"
                role="switch"
                :aria-checked="notificationForm.scheduleReminder1h"
                class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"
                :class="notificationForm.scheduleReminder1h ? 'bg-primary-600' : 'bg-gray-200 dark:bg-gray-700'"
                @click="notificationForm.scheduleReminder1h = !notificationForm.scheduleReminder1h"
              >
                <span
                  class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
                  :class="notificationForm.scheduleReminder1h ? 'translate-x-5' : 'translate-x-0'"
                />
              </button>
            </div>
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm text-gray-700 dark:text-gray-300">{{ t('settings.notifications.reminder30m') }}</p>
                <p class="text-xs text-gray-400 dark:text-gray-500">{{ t('settings.notifications.reminder30mDesc') }}</p>
              </div>
              <button
                type="button"
                role="switch"
                :aria-checked="notificationForm.scheduleReminder30m"
                class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"
                :class="notificationForm.scheduleReminder30m ? 'bg-primary-600' : 'bg-gray-200 dark:bg-gray-700'"
                @click="notificationForm.scheduleReminder30m = !notificationForm.scheduleReminder30m"
              >
                <span
                  class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
                  :class="notificationForm.scheduleReminder30m ? 'translate-x-5' : 'translate-x-0'"
                />
              </button>
            </div>
          </div>
        </div>

        <div class="flex justify-end border-t border-gray-100 dark:border-gray-700 pt-6">
          <button
            :disabled="isSavingNotifications"
            class="btn-primary"
            @click="saveNotifications"
          >
            <LoadingSpinner v-if="isSavingNotifications" size="sm" class="mr-2" />
            {{ t('settings.save') }}
          </button>
        </div>
      </div>
    </div>

    <!-- Default Settings Tab -->
    <div v-if="activeTab === 'defaults'" class="space-y-6">
      <div class="card">
        <h2 class="mb-6 text-lg font-semibold text-gray-900 dark:text-gray-100">{{ t('settings.defaults.title') }}</h2>

        <!-- Default Visibility -->
        <div class="mb-8">
          <h3 class="mb-4 text-sm font-semibold text-gray-800 dark:text-gray-200">{{ t('settings.defaults.visibility') }}</h3>
          <p class="mb-3 text-xs text-gray-400 dark:text-gray-500">{{ t('settings.defaults.visibilityDesc') }}</p>
          <div class="flex flex-wrap gap-3">
            <label
              v-for="option in visibilityOptions"
              :key="option.value"
              class="flex cursor-pointer items-center gap-2 rounded-lg border px-4 py-3 transition-colors"
              :class="
                defaultsForm.visibility === option.value
                  ? 'border-primary-500 bg-primary-50 dark:border-primary-400 dark:bg-primary-900/20'
                  : 'border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700'
              "
            >
              <input
                v-model="defaultsForm.visibility"
                type="radio"
                name="visibility"
                :value="option.value"
                class="h-4 w-4 border-gray-300 dark:border-gray-600 text-primary-600 focus:ring-primary-500"
              />
              <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ option.label }}</span>
            </label>
          </div>
        </div>

        <!-- Default Upload Platforms -->
        <div class="mb-8">
          <h3 class="mb-4 text-sm font-semibold text-gray-800 dark:text-gray-200">{{ t('settings.defaults.platforms') }}</h3>
          <p class="mb-3 text-xs text-gray-400 dark:text-gray-500">{{ t('settings.defaults.platformsDesc') }}</p>
          <div class="space-y-3">
            <label
              v-for="platform in platformOptions"
              :key="platform.value"
              class="flex cursor-pointer items-center gap-3 rounded-lg border px-4 py-3 transition-colors"
              :class="
                defaultsForm.platforms.includes(platform.value)
                  ? 'border-primary-500 bg-primary-50 dark:border-primary-400 dark:bg-primary-900/20'
                  : 'border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700'
              "
            >
              <input
                v-model="defaultsForm.platforms"
                type="checkbox"
                :value="platform.value"
                class="h-4 w-4 rounded border-gray-300 dark:border-gray-600 text-primary-600 focus:ring-primary-500"
              />
              <div
                class="flex h-8 w-8 items-center justify-center rounded-lg"
                :style="{ backgroundColor: platform.color }"
              >
                <span class="text-xs font-bold text-white">{{ platform.shortIcon }}</span>
              </div>
              <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ platform.label }}</span>
            </label>
          </div>
        </div>

        <!-- Default AI Tone -->
        <div class="mb-8">
          <h3 class="mb-4 text-sm font-semibold text-gray-800 dark:text-gray-200">{{ t('settings.defaults.aiTone') }}</h3>
          <p class="mb-3 text-xs text-gray-400 dark:text-gray-500">{{ t('settings.defaults.aiToneDesc') }}</p>
          <div class="flex flex-wrap gap-3">
            <label
              v-for="option in toneOptions"
              :key="option.value"
              class="flex cursor-pointer items-center gap-2 rounded-lg border px-4 py-3 transition-colors"
              :class="
                defaultsForm.aiTone === option.value
                  ? 'border-primary-500 bg-primary-50 dark:border-primary-400 dark:bg-primary-900/20'
                  : 'border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700'
              "
            >
              <input
                v-model="defaultsForm.aiTone"
                type="radio"
                name="aiTone"
                :value="option.value"
                class="h-4 w-4 border-gray-300 dark:border-gray-600 text-primary-600 focus:ring-primary-500"
              />
              <div>
                <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ option.label }}</span>
                <p class="text-xs text-gray-400 dark:text-gray-500">{{ option.description }}</p>
              </div>
            </label>
          </div>
        </div>

        <!-- Default AI Provider -->
        <div class="mb-8">
          <h3 class="mb-4 text-sm font-semibold text-gray-800 dark:text-gray-200">{{ $t('settings.aiProvider.title') }}</h3>
          <p class="mb-3 text-xs text-gray-400 dark:text-gray-500">{{ $t('settings.aiProvider.description') }}</p>
          <div class="space-y-3">
            <label
              v-for="option in aiProviderOptions"
              :key="option.value"
              class="flex cursor-pointer items-center gap-3 rounded-lg border px-4 py-3 transition-colors"
              :class="
                defaultsForm.aiProvider === option.value
                  ? 'border-primary-500 bg-primary-50 dark:border-primary-400 dark:bg-primary-900/20'
                  : 'border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700'
              "
            >
              <input
                v-model="defaultsForm.aiProvider"
                type="radio"
                name="aiProvider"
                :value="option.value"
                class="h-4 w-4 border-gray-300 dark:border-gray-600 text-primary-600 focus:ring-primary-500"
              />
              <div>
                <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ option.label }}</span>
                <p class="text-xs text-gray-400 dark:text-gray-500">{{ option.description }}</p>
              </div>
            </label>
          </div>
          <p class="mt-3 text-xs text-gray-400 dark:text-gray-500">{{ $t('settings.aiProvider.sttNote') }}</p>
        </div>

        <!-- Language Selection -->
        <div class="mb-8">
          <h3 class="mb-4 text-sm font-semibold text-gray-800 dark:text-gray-200">{{ t('settings.defaults.language') }}</h3>
          <p class="mb-3 text-xs text-gray-400 dark:text-gray-500">{{ t('settings.defaults.languageDesc') }}</p>
          <div class="flex flex-wrap gap-3">
            <label
              v-for="option in languageOptions"
              :key="option.value"
              class="flex cursor-pointer items-center gap-2 rounded-lg border px-4 py-3 transition-colors"
              :class="
                currentLocale === option.value
                  ? 'border-primary-500 bg-primary-50 dark:border-primary-400 dark:bg-primary-900/20'
                  : 'border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700'
              "
            >
              <input
                type="radio"
                name="language"
                :value="option.value"
                :checked="currentLocale === option.value"
                class="h-4 w-4 border-gray-300 dark:border-gray-600 text-primary-600 focus:ring-primary-500"
                @change="switchLocale(option.value)"
              />
              <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ option.label }}</span>
            </label>
          </div>
        </div>

        <div class="flex justify-end border-t border-gray-100 dark:border-gray-700 pt-6">
          <button
            :disabled="isSavingDefaults"
            class="btn-primary"
            @click="saveDefaults"
          >
            <LoadingSpinner v-if="isSavingDefaults" size="sm" class="mr-2" />
            {{ t('settings.save') }}
          </button>
        </div>
      </div>
    </div>

    <!-- Activity Log Tab -->
    <div v-if="activeTab === 'activity'" class="space-y-6">
      <div class="card">
        <h2 class="mb-6 text-lg font-semibold text-gray-900 dark:text-gray-100">{{ t('settings.activity.title') }}</h2>

        <!-- Filter -->
        <div class="mb-6">
          <ActivityFilter
            :selected-filters="selectedActivityFilters"
            :event-counts="activityStore.eventCounts"
            @update="updateActivityFilters"
          />
        </div>

        <!-- Timeline -->
        <ActivityTimeline :selected-filters="selectedActivityFilters" />
      </div>
    </div>

    <!-- Account Tab -->
    <div v-if="activeTab === 'account'" class="space-y-6">
      <!-- Account Info -->
      <div class="card">
        <h2 class="mb-6 text-lg font-semibold text-gray-900 dark:text-gray-100">{{ t('settings.account.title') }}</h2>

        <div class="space-y-4">
          <div class="flex items-center justify-between">
            <span class="text-sm text-gray-500 dark:text-gray-400">{{ t('settings.account.email') }}</span>
            <span class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ authStore.user?.email }}</span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-sm text-gray-500 dark:text-gray-400">{{ t('settings.account.socialLogin') }}</span>
            <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
              {{ socialProvider === 'GOOGLE' ? 'Google' : 'Kakao' }}
            </span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-sm text-gray-500 dark:text-gray-400">{{ t('settings.account.currentPlan') }}</span>
            <span class="badge-blue">{{ authStore.user?.planType ?? 'FREE' }}</span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-sm text-gray-500 dark:text-gray-400">{{ t('settings.account.joinDate') }}</span>
            <span class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ formattedCreatedAt }}</span>
          </div>
        </div>
      </div>

      <!-- Danger Zone -->
      <div class="card border-red-200 dark:border-red-800">
        <h2 class="mb-2 text-lg font-semibold text-red-600">{{ t('settings.account.dangerZone') }}</h2>
        <p class="mb-6 text-sm text-gray-500 dark:text-gray-400">
          {{ t('settings.account.dangerDesc') }}
        </p>

        <button class="btn-danger" @click="showDeleteModal = true">
          <ShieldExclamationIcon class="mr-2 h-4 w-4" />
          {{ t('settings.account.deleteAccount') }}
        </button>
      </div>
    </div>

    <!-- Delete Account Confirmation Modal -->
    <ConfirmModal
      v-model="showDeleteModal"
      :title="t('settings.account.deleteAccount')"
      :message="t('settings.account.deleteConfirm')"
      :confirm-text="t('settings.account.deleteBtn')"
      :cancel-text="t('settings.cancel')"
      :danger="true"
      @confirm="handleDeleteAccount"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import {
  UserIcon,
  BellIcon,
  Cog6ToothIcon,
  ShieldExclamationIcon,
  ClockIcon,
} from '@heroicons/vue/24/outline'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import ActivityFilter from '@/components/activity/ActivityFilter.vue'
import ActivityTimeline from '@/components/activity/ActivityTimeline.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'
import { useActivityLogStore } from '@/stores/activityLog'
import { useI18n } from 'vue-i18n'
import { useLocale } from '@/composables/useLocale'

import { settingsApi } from '@/api/settings'
import { authApi } from '@/api/auth'
import type { CreatorCategory } from '@/types/user'
import type { Platform } from '@/types/channel'
import type { ActivityEventType } from '@/stores/activityLog'

const authStore = useAuthStore()
const userStore = useUserStore()
const notify = useNotificationStore()
const activityStore = useActivityLogStore()
const { t, tm } = useI18n({ useScope: 'global' })
const { currentLocale, switchLocale } = useLocale()

// --- Tab State ---

type SettingsTab = 'profile' | 'notifications' | 'defaults' | 'account' | 'activity'

const activeTab = ref<SettingsTab>('profile')

const tabs = computed<{ key: SettingsTab; label: string; icon: typeof UserIcon }[]>(() => [
  { key: 'profile', label: t('settings.tabs.profile'), icon: UserIcon },
  { key: 'notifications', label: t('settings.tabs.notifications'), icon: BellIcon },
  { key: 'defaults', label: t('settings.tabs.defaults'), icon: Cog6ToothIcon },
  { key: 'activity', label: t('settings.tabs.activity'), icon: ClockIcon },
  { key: 'account', label: t('settings.tabs.account'), icon: ShieldExclamationIcon },
])

// --- Social Provider ---

const socialProvider = computed(() => {
  // Infer provider from user email domain or store data
  // Fallback to GOOGLE if not available
  const email = authStore.user?.email ?? ''
  if (email.endsWith('@kakao.com') || email.includes('kakao')) return 'KAKAO'
  return 'GOOGLE'
})

// --- Profile Tab ---

const categories = computed<{ value: CreatorCategory; label: string }[]>(() => [
  { value: 'BEAUTY', label: t('settings.categories.beauty') },
  { value: 'FOOD', label: t('settings.categories.food') },
  { value: 'GAME', label: t('settings.categories.game') },
  { value: 'DAILY', label: t('settings.categories.daily') },
  { value: 'EDUCATION', label: t('settings.categories.education') },
  { value: 'IT', label: t('settings.categories.it') },
  { value: 'TRAVEL', label: t('settings.categories.travel') },
  { value: 'MUSIC', label: t('settings.categories.music') },
  { value: 'FASHION', label: t('settings.categories.fashion') },
  { value: 'SPORTS', label: t('settings.categories.sports') },
  { value: 'OTHER', label: t('settings.categories.other') },
])

const profileForm = reactive({
  nickname: authStore.user?.nickname ?? '',
  profileImageUrl: authStore.user?.profileImageUrl ?? '',
  category: (authStore.user?.category ?? '') as CreatorCategory | '',
})

const isSavingProfile = ref(false)

function handleImageUpload(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  if (file.size > 5 * 1024 * 1024) {
    notify.warning(t('settings.profile.imageSizeError'))
    return
  }

  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp']
  if (!allowedTypes.includes(file.type)) {
    notify.warning(t('settings.profile.imageTypeError'))
    return
  }

  // Create preview URL
  const reader = new FileReader()
  reader.onload = (e) => {
    profileForm.profileImageUrl = (e.target?.result as string) ?? ''
  }
  reader.readAsDataURL(file)
}

async function saveProfile() {
  if (!profileForm.nickname.trim()) {
    notify.warning(t('settings.profile.nicknameRequired'))
    return
  }
  if (profileForm.nickname.trim().length < 2) {
    notify.warning(t('settings.profile.nicknameMinLength'))
    return
  }

  isSavingProfile.value = true
  try {
    await userStore.updateProfile({
      nickname: profileForm.nickname.trim(),
      profileImageUrl: profileForm.profileImageUrl || null,
      category: (profileForm.category as CreatorCategory) || null,
    })
    notify.success(t('settings.profile.saveSuccess'))
  } catch {
    notify.error(t('settings.profile.saveError'))
  } finally {
    isSavingProfile.value = false
  }
}

// --- Notifications Tab ---

const commentOptions = computed(() => [
  { value: 'realtime', label: t('settings.notifications.commentRealtime'), description: t('settings.notifications.commentRealtimeDesc') },
  { value: 'hourly', label: t('settings.notifications.commentHourly'), description: t('settings.notifications.commentHourlyDesc') },
  { value: 'daily', label: t('settings.notifications.commentDaily'), description: t('settings.notifications.commentDailyDesc') },
])

const notificationForm = reactive({
  uploadEmail: true,
  uploadPush: true,
  commentFrequency: 'realtime',
  creditThreshold: 20,
  scheduleReminder1h: true,
  scheduleReminder30m: false,
})

const isSavingNotifications = ref(false)

async function saveNotifications() {
  if (notificationForm.creditThreshold < 5 || notificationForm.creditThreshold > 50) {
    notify.warning(t('settings.notifications.creditThresholdError'))
    return
  }

  isSavingNotifications.value = true
  try {
    await settingsApi.updateNotifications(notificationForm)
    notify.success(t('settings.notifications.saveSuccess'))
  } catch {
    notify.error(t('settings.notifications.saveError'))
  } finally {
    isSavingNotifications.value = false
  }
}

// --- Default Settings Tab ---

const visibilityOptions = computed(() => [
  { value: 'PUBLIC', label: t('settings.defaults.visibilityPublic') },
  { value: 'PRIVATE', label: t('settings.defaults.visibilityPrivate') },
  { value: 'UNLISTED', label: t('settings.defaults.visibilityUnlisted') },
])

const platformOptions: { value: Platform; label: string; color: string; shortIcon: string }[] = [
  { value: 'YOUTUBE', label: 'YouTube', color: '#FF0000', shortIcon: 'YT' },
  { value: 'TIKTOK', label: 'TikTok', color: '#000000', shortIcon: 'TT' },
  { value: 'INSTAGRAM', label: 'Instagram Reels', color: '#E1306C', shortIcon: 'IG' },
  { value: 'NAVER_CLIP', label: 'Naver Clip', color: '#03C75A', shortIcon: 'N' },
]

const toneOptions = computed(() => [
  { value: 'FRIENDLY', label: t('settings.defaults.toneFriendly'), description: t('settings.defaults.toneFriendlyDesc') },
  { value: 'PROFESSIONAL', label: t('settings.defaults.toneProfessional'), description: t('settings.defaults.toneProfessionalDesc') },
  { value: 'HUMOR', label: t('settings.defaults.toneHumor'), description: t('settings.defaults.toneHumorDesc') },
])

const languageOptions = [
  { value: 'ko' as const, label: '한국어' },
  { value: 'en' as const, label: 'English' },
]

const aiProviderOptions = computed(() => [
  { value: 'CLAUDE', label: 'Claude', description: t('settings.aiProvider.claude') },
  { value: 'GEMINI', label: 'Gemini', description: t('settings.aiProvider.gemini') },
  { value: 'OPENAI', label: 'OpenAI GPT', description: t('settings.aiProvider.openai') },
])

const defaultsForm = reactive({
  visibility: 'PUBLIC',
  platforms: ['YOUTUBE'] as Platform[],
  aiTone: 'FRIENDLY',
  aiProvider: 'CLAUDE',
})

const isSavingDefaults = ref(false)

async function saveDefaults() {
  isSavingDefaults.value = true
  try {
    await settingsApi.updateDefaults(defaultsForm)
    notify.success(t('settings.defaults.saveSuccess'))
  } catch {
    notify.error(t('settings.defaults.saveError'))
  } finally {
    isSavingDefaults.value = false
  }
}

// --- Activity Log Tab ---

const selectedActivityFilters = ref<ActivityEventType[]>([])

function updateActivityFilters(filters: ActivityEventType[]) {
  selectedActivityFilters.value = filters
}

// --- Account Tab ---

const showDeleteModal = ref(false)

const formattedCreatedAt = computed(() => {
  const dateStr = authStore.user?.createdAt
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleDateString(currentLocale.value === 'ko' ? 'ko-KR' : 'en-US', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })
})

async function handleDeleteAccount() {
  try {
    await authApi.deleteAccount()
    notify.success(t('settings.account.deleteSuccess'))
    authStore.logout()
  } catch {
    notify.error(t('settings.account.deleteError'))
  }
}

// --- Load settings from API ---
onMounted(async () => {
  try {
    const settings = await settingsApi.getSettings()
    // Populate notification form
    notificationForm.uploadEmail = settings.notificationUpload
    notificationForm.commentFrequency = settings.notificationComment
    notificationForm.creditThreshold = settings.notificationCreditThreshold
    notificationForm.scheduleReminder1h = settings.notificationScheduleReminder >= 60
    notificationForm.scheduleReminder30m = settings.notificationScheduleReminder === 30

    // Populate defaults form
    defaultsForm.visibility = settings.defaultVisibility
    defaultsForm.platforms = settings.defaultPlatforms as Platform[]
    defaultsForm.aiTone = settings.defaultAiTone
    defaultsForm.aiProvider = settings.defaultAiProvider || 'CLAUDE'
  } catch {
    // Use form defaults if API unavailable
  }
})
</script>
