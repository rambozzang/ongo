<template>
  <div>
    <h1 class="mb-6 text-2xl font-bold text-gray-900 dark:text-gray-100">설정</h1>

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
        <h2 class="mb-6 text-lg font-semibold text-gray-900 dark:text-gray-100">프로필 설정</h2>

        <!-- Profile Image -->
        <div class="mb-6 flex items-center gap-6">
          <div class="relative">
            <div
              class="flex h-20 w-20 items-center justify-center overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700"
            >
              <img
                v-if="profileForm.profileImageUrl"
                :src="profileForm.profileImageUrl"
                alt="프로필 이미지"
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
              이미지 변경
            </label>
            <input
              id="profile-image-upload"
              type="file"
              accept="image/*"
              class="hidden"
              @change="handleImageUpload"
            />
            <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">JPG, PNG 파일. 최대 5MB</p>
          </div>
        </div>

        <!-- Nickname -->
        <div class="mb-6">
          <label for="nickname" class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">
            닉네임
          </label>
          <input
            id="nickname"
            v-model="profileForm.nickname"
            type="text"
            maxlength="20"
            placeholder="닉네임을 입력하세요"
            class="input-field max-w-md"
          />
          <div class="mt-1 flex items-center justify-between max-w-md">
            <span class="text-xs text-gray-400">2~20자</span>
            <span class="text-xs text-gray-400">{{ profileForm.nickname.length }}/20</span>
          </div>
        </div>

        <!-- Creator Category -->
        <div class="mb-6">
          <label for="category" class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">
            크리에이터 카테고리
          </label>
          <select
            id="category"
            v-model="profileForm.category"
            class="input-field max-w-md"
          >
            <option value="">카테고리 선택</option>
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
            이메일
          </label>
          <input
            type="email"
            :value="authStore.user?.email ?? ''"
            disabled
            class="input-field max-w-md cursor-not-allowed bg-gray-50 dark:bg-gray-900 text-gray-500 dark:text-gray-400"
          />
          <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">소셜 계정 이메일은 변경할 수 없습니다.</p>
        </div>

        <!-- Connected Social Account (read-only) -->
        <div class="mb-6">
          <label class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">
            연동된 소셜 계정
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
                {{ socialProvider === 'GOOGLE' ? 'Google' : 'Kakao' }} 계정
              </p>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ authStore.user?.email }}</p>
            </div>
            <span class="badge-success ml-auto">연동됨</span>
          </div>
        </div>

        <div class="flex justify-end border-t border-gray-100 dark:border-gray-700 pt-6">
          <button
            :disabled="isSavingProfile"
            class="btn-primary"
            @click="saveProfile"
          >
            <LoadingSpinner v-if="isSavingProfile" size="sm" class="mr-2" />
            저장
          </button>
        </div>
      </div>
    </div>

    <!-- Notifications Tab -->
    <div v-if="activeTab === 'notifications'" class="space-y-6">
      <div class="card">
        <h2 class="mb-6 text-lg font-semibold text-gray-900 dark:text-gray-100">알림 설정</h2>

        <!-- Upload Notifications -->
        <div class="mb-8">
          <h3 class="mb-4 text-sm font-semibold text-gray-800 dark:text-gray-200">업로드 완료/실패 알림</h3>
          <div class="space-y-4">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm text-gray-700 dark:text-gray-300">이메일 알림</p>
                <p class="text-xs text-gray-400">업로드 완료 또는 실패 시 이메일로 알림</p>
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
                <p class="text-sm text-gray-700 dark:text-gray-300">푸시 알림</p>
                <p class="text-xs text-gray-400">브라우저 푸시 알림으로 즉시 전달</p>
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
          <h3 class="mb-4 text-sm font-semibold text-gray-800 dark:text-gray-200">댓글 알림</h3>
          <div class="space-y-3">
            <label
              v-for="option in commentOptions"
              :key="option.value"
              class="flex cursor-pointer items-center gap-3 rounded-lg border px-4 py-3 transition-colors"
              :class="
                notificationForm.commentFrequency === option.value
                  ? 'border-primary-500 bg-primary-50'
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
                <p class="text-sm font-medium text-gray-700">{{ option.label }}</p>
                <p class="text-xs text-gray-400">{{ option.description }}</p>
              </div>
            </label>
          </div>
        </div>

        <!-- Credit Remaining Alert -->
        <div class="mb-8">
          <h3 class="mb-4 text-sm font-semibold text-gray-800 dark:text-gray-200">크레딧 잔여 알림</h3>
          <div class="flex items-center gap-3">
            <label for="creditThreshold" class="text-sm text-gray-700 dark:text-gray-300">
              잔여 크레딧이
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
            <span class="text-sm text-gray-700 dark:text-gray-300">이하일 때 알림</span>
          </div>
          <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">5~50% 사이의 값을 설정하세요. (기본값: 20%)</p>
        </div>

        <!-- Schedule Reminder -->
        <div class="mb-8">
          <h3 class="mb-4 text-sm font-semibold text-gray-800 dark:text-gray-200">예약 업로드 리마인더</h3>
          <div class="space-y-4">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm text-gray-700 dark:text-gray-300">1시간 전 알림</p>
                <p class="text-xs text-gray-400">예약 업로드 1시간 전에 알림</p>
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
                <p class="text-sm text-gray-700 dark:text-gray-300">30분 전 알림</p>
                <p class="text-xs text-gray-400">예약 업로드 30분 전에 알림</p>
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
            저장
          </button>
        </div>
      </div>
    </div>

    <!-- Default Settings Tab -->
    <div v-if="activeTab === 'defaults'" class="space-y-6">
      <div class="card">
        <h2 class="mb-6 text-lg font-semibold text-gray-900 dark:text-gray-100">기본 설정</h2>

        <!-- Default Visibility -->
        <div class="mb-8">
          <h3 class="mb-4 text-sm font-semibold text-gray-800 dark:text-gray-200">기본 공개 설정</h3>
          <p class="mb-3 text-xs text-gray-400 dark:text-gray-500">영상 업로드 시 기본으로 적용되는 공개 설정입니다.</p>
          <div class="flex flex-wrap gap-3">
            <label
              v-for="option in visibilityOptions"
              :key="option.value"
              class="flex cursor-pointer items-center gap-2 rounded-lg border px-4 py-3 transition-colors"
              :class="
                defaultsForm.visibility === option.value
                  ? 'border-primary-500 bg-primary-50'
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
          <h3 class="mb-4 text-sm font-semibold text-gray-800 dark:text-gray-200">기본 업로드 플랫폼</h3>
          <p class="mb-3 text-xs text-gray-400 dark:text-gray-500">영상 업로드 시 기본으로 선택되는 플랫폼입니다.</p>
          <div class="space-y-3">
            <label
              v-for="platform in platformOptions"
              :key="platform.value"
              class="flex cursor-pointer items-center gap-3 rounded-lg border px-4 py-3 transition-colors"
              :class="
                defaultsForm.platforms.includes(platform.value)
                  ? 'border-primary-500 bg-primary-50'
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
          <h3 class="mb-4 text-sm font-semibold text-gray-800 dark:text-gray-200">기본 AI 톤</h3>
          <p class="mb-3 text-xs text-gray-400 dark:text-gray-500">AI가 제목, 설명을 생성할 때 사용하는 기본 톤입니다.</p>
          <div class="flex flex-wrap gap-3">
            <label
              v-for="option in toneOptions"
              :key="option.value"
              class="flex cursor-pointer items-center gap-2 rounded-lg border px-4 py-3 transition-colors"
              :class="
                defaultsForm.aiTone === option.value
                  ? 'border-primary-500 bg-primary-50'
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
                <p class="text-xs text-gray-400">{{ option.description }}</p>
              </div>
            </label>
          </div>
        </div>

        <!-- Language Selection -->
        <div class="mb-8">
          <h3 class="mb-4 text-sm font-semibold text-gray-800 dark:text-gray-200">언어 / Language</h3>
          <p class="mb-3 text-xs text-gray-400 dark:text-gray-500">앱에서 사용할 언어를 선택하세요</p>
          <div class="flex flex-wrap gap-3">
            <label
              v-for="option in languageOptions"
              :key="option.value"
              class="flex cursor-pointer items-center gap-2 rounded-lg border px-4 py-3 transition-colors"
              :class="
                currentLocale === option.value
                  ? 'border-primary-500 bg-primary-50'
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
            저장
          </button>
        </div>
      </div>
    </div>

    <!-- Activity Log Tab -->
    <div v-if="activeTab === 'activity'" class="space-y-6">
      <div class="card">
        <h2 class="mb-6 text-lg font-semibold text-gray-900 dark:text-gray-100">활동 로그</h2>

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
        <h2 class="mb-6 text-lg font-semibold text-gray-900 dark:text-gray-100">계정 정보</h2>

        <div class="space-y-4">
          <div class="flex items-center justify-between">
            <span class="text-sm text-gray-500 dark:text-gray-400">이메일</span>
            <span class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ authStore.user?.email }}</span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-sm text-gray-500 dark:text-gray-400">소셜 로그인</span>
            <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
              {{ socialProvider === 'GOOGLE' ? 'Google' : 'Kakao' }}
            </span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-sm text-gray-500 dark:text-gray-400">현재 플랜</span>
            <span class="badge-blue">{{ authStore.user?.planType ?? 'FREE' }}</span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-sm text-gray-500 dark:text-gray-400">가입일</span>
            <span class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ formattedCreatedAt }}</span>
          </div>
        </div>
      </div>

      <!-- Danger Zone -->
      <div class="card border-red-200 dark:border-red-800">
        <h2 class="mb-2 text-lg font-semibold text-red-600">위험 구역</h2>
        <p class="mb-6 text-sm text-gray-500 dark:text-gray-400">
          회원 탈퇴 시 모든 데이터가 즉시 삭제되며, 이 작업은 되돌릴 수 없습니다.
        </p>

        <button class="btn-danger" @click="showDeleteModal = true">
          <ShieldExclamationIcon class="mr-2 h-4 w-4" />
          회원 탈퇴
        </button>
      </div>
    </div>

    <!-- Delete Account Confirmation Modal -->
    <ConfirmModal
      v-model="showDeleteModal"
      title="회원 탈퇴"
      message="모든 데이터가 삭제되며 복구할 수 없습니다. 정말로 탈퇴하시겠습니까?"
      confirm-text="탈퇴하기"
      cancel-text="취소"
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
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'
import { useActivityLogStore } from '@/stores/activityLog'
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
const { currentLocale, switchLocale } = useLocale()

// --- Tab State ---

type SettingsTab = 'profile' | 'notifications' | 'defaults' | 'account' | 'activity'

const activeTab = ref<SettingsTab>('profile')

const tabs: { key: SettingsTab; label: string; icon: typeof UserIcon }[] = [
  { key: 'profile', label: '프로필', icon: UserIcon },
  { key: 'notifications', label: '알림', icon: BellIcon },
  { key: 'defaults', label: '기본 설정', icon: Cog6ToothIcon },
  { key: 'activity', label: '활동 로그', icon: ClockIcon },
  { key: 'account', label: '계정', icon: ShieldExclamationIcon },
]

// --- Social Provider ---

const socialProvider = computed(() => {
  // Infer provider from user email domain or store data
  // Fallback to GOOGLE if not available
  const email = authStore.user?.email ?? ''
  if (email.endsWith('@kakao.com') || email.includes('kakao')) return 'KAKAO'
  return 'GOOGLE'
})

// --- Profile Tab ---

const categories: { value: CreatorCategory; label: string }[] = [
  { value: 'BEAUTY', label: '뷰티' },
  { value: 'FOOD', label: '먹방' },
  { value: 'GAME', label: '게임' },
  { value: 'DAILY', label: '일상' },
  { value: 'EDUCATION', label: '교육' },
  { value: 'IT', label: 'IT/테크' },
  { value: 'TRAVEL', label: '여행' },
  { value: 'MUSIC', label: '음악' },
  { value: 'FASHION', label: '패션' },
  { value: 'SPORTS', label: '스포츠' },
  { value: 'OTHER', label: '기타' },
]

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
    notify.warning('이미지 파일은 5MB 이하만 가능합니다.')
    return
  }

  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp']
  if (!allowedTypes.includes(file.type)) {
    notify.warning('JPG, PNG, WebP 형식의 이미지만 가능합니다.')
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
    notify.warning('닉네임을 입력해주세요.')
    return
  }
  if (profileForm.nickname.trim().length < 2) {
    notify.warning('닉네임은 2자 이상이어야 합니다.')
    return
  }

  isSavingProfile.value = true
  try {
    await userStore.updateProfile({
      nickname: profileForm.nickname.trim(),
      profileImageUrl: profileForm.profileImageUrl || null,
      category: (profileForm.category as CreatorCategory) || null,
    })
    notify.success('프로필이 저장되었습니다.')
  } catch {
    notify.error('프로필 저장에 실패했습니다. 다시 시도해주세요.')
  } finally {
    isSavingProfile.value = false
  }
}

// --- Notifications Tab ---

const commentOptions = [
  { value: 'realtime', label: '실시간', description: '댓글이 달릴 때마다 즉시 알림' },
  { value: 'hourly', label: '1시간 요약', description: '1시간마다 댓글 요약을 알림' },
  { value: 'daily', label: '일별 요약', description: '하루 한 번 댓글 요약을 알림' },
]

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
    notify.warning('크레딧 알림 기준은 5~50% 사이여야 합니다.')
    return
  }

  isSavingNotifications.value = true
  try {
    await settingsApi.updateNotifications(notificationForm)
    notify.success('알림 설정이 저장되었습니다.')
  } catch {
    notify.error('알림 설정 저장에 실패했습니다. 다시 시도해주세요.')
  } finally {
    isSavingNotifications.value = false
  }
}

// --- Default Settings Tab ---

const visibilityOptions = [
  { value: 'PUBLIC', label: '공개' },
  { value: 'PRIVATE', label: '비공개' },
  { value: 'UNLISTED', label: '미등록' },
]

const platformOptions: { value: Platform; label: string; color: string; shortIcon: string }[] = [
  { value: 'YOUTUBE', label: 'YouTube', color: '#FF0000', shortIcon: 'YT' },
  { value: 'TIKTOK', label: 'TikTok', color: '#000000', shortIcon: 'TT' },
  { value: 'INSTAGRAM', label: 'Instagram Reels', color: '#E1306C', shortIcon: 'IG' },
  { value: 'NAVER_CLIP', label: 'Naver Clip', color: '#03C75A', shortIcon: 'N' },
]

const toneOptions = [
  { value: 'FRIENDLY', label: '친근', description: '일상적이고 친근한 톤' },
  { value: 'PROFESSIONAL', label: '전문', description: '전문적이고 신뢰감 있는 톤' },
  { value: 'HUMOR', label: '유머', description: '재미있고 가벼운 톤' },
]

const languageOptions = [
  { value: 'ko' as const, label: '한국어' },
  { value: 'en' as const, label: 'English' },
]

const defaultsForm = reactive({
  visibility: 'PUBLIC',
  platforms: ['YOUTUBE'] as Platform[],
  aiTone: 'FRIENDLY',
})

const isSavingDefaults = ref(false)

async function saveDefaults() {
  isSavingDefaults.value = true
  try {
    await settingsApi.updateDefaults(defaultsForm)
    notify.success('기본 설정이 저장되었습니다.')
  } catch {
    notify.error('기본 설정 저장에 실패했습니다. 다시 시도해주세요.')
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
  return `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일`
})

async function handleDeleteAccount() {
  try {
    await authApi.deleteAccount()
    notify.success('회원 탈퇴가 완료되었습니다.')
    authStore.logout()
  } catch {
    notify.error('회원 탈퇴에 실패했습니다. 다시 시도해주세요.')
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
  } catch {
    // Use form defaults if API unavailable
  }
})
</script>
