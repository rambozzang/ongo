<script setup lang="ts">
import { ref, computed } from 'vue'
import { XMarkIcon, PaperAirplaneIcon } from '@heroicons/vue/24/outline'
import type { TeamRole } from '@/types/team'
import { useTeamStore } from '@/stores/team'

interface Props {
  show: boolean
}

interface Emits {
  (e: 'close'): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

const teamStore = useTeamStore()

const emailInput = ref('')
const selectedRole = ref<TeamRole>('editor')
const customMessage = ref('')
const emailError = ref('')

const emails = computed(() => {
  return emailInput.value
    .split(',')
    .map((e) => e.trim())
    .filter((e) => e.length > 0)
})

const isValid = computed(() => {
  if (emails.value.length === 0) return false

  for (const email of emails.value) {
    if (!validateEmail(email)) {
      return false
    }
  }
  return true
})

const validateEmail = (email: string): boolean => {
  const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return re.test(email)
}

const handleEmailInput = () => {
  emailError.value = ''
  if (emailInput.value && !isValid.value) {
    emailError.value = '유효하지 않은 이메일 주소가 포함되어 있습니다'
  }
}

const handleInvite = () => {
  if (!isValid.value) return

  for (const email of emails.value) {
    teamStore.inviteMember(email, selectedRole.value)
  }

  emailInput.value = ''
  selectedRole.value = 'editor'
  customMessage.value = ''
  emailError.value = ''
  emit('close')
}

const handleClose = () => {
  emailInput.value = ''
  selectedRole.value = 'editor'
  customMessage.value = ''
  emailError.value = ''
  emit('close')
}
</script>

<template>
  <transition
    enter-active-class="transition ease-out duration-200"
    enter-from-class="opacity-0"
    enter-to-class="opacity-100"
    leave-active-class="transition ease-in duration-150"
    leave-from-class="opacity-100"
    leave-to-class="opacity-0"
  >
    <div
      v-if="show"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="invite-member-title"
      @click.self="handleClose"
      @keydown.escape="handleClose"
    >
      <transition
        enter-active-class="transition ease-out duration-200"
        enter-from-class="opacity-0 scale-95"
        enter-to-class="opacity-100 scale-100"
        leave-active-class="transition ease-in duration-150"
        leave-from-class="opacity-100 scale-100"
        leave-to-class="opacity-0 scale-95"
      >
        <div
          v-if="show"
          class="w-full max-w-lg rounded-lg bg-white p-6 shadow-xl dark:bg-gray-800"
        >
          <div class="flex items-center justify-between border-b border-gray-200 pb-4 dark:border-gray-700">
            <h3 id="invite-member-title" class="text-lg font-semibold text-gray-900 dark:text-white">
              팀 멤버 초대
            </h3>
            <button
              @click="handleClose"
              aria-label="모달 닫기"
              class="rounded-md p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
            >
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <div class="mt-6 space-y-4">
            <!-- Email Input -->
            <div>
              <label
                for="email"
                class="block text-sm font-medium text-gray-700 dark:text-gray-300"
              >
                이메일 주소
              </label>
              <input
                id="email"
                v-model="emailInput"
                @input="handleEmailInput"
                type="text"
                placeholder="example@email.com (여러 개는 쉼표로 구분)"
                class="mt-1 block w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white dark:placeholder-gray-500 dark:focus:border-indigo-400 dark:focus:ring-indigo-400"
              />
              <p v-if="emailError" class="mt-1 text-xs text-red-600 dark:text-red-400">
                {{ emailError }}
              </p>
              <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                여러 이메일을 초대하려면 쉼표로 구분하세요
              </p>
            </div>

            <!-- Role Selector -->
            <div>
              <label
                for="role"
                class="block text-sm font-medium text-gray-700 dark:text-gray-300"
              >
                역할
              </label>
              <select
                id="role"
                v-model="selectedRole"
                class="mt-1 block w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white dark:focus:border-indigo-400 dark:focus:ring-indigo-400"
              >
                <option value="admin">관리자 - 모든 권한 및 멤버 관리</option>
                <option value="editor">에디터 - 콘텐츠 업로드 및 수정</option>
                <option value="viewer">뷰어 - 읽기 전용 접근</option>
              </select>
            </div>

            <!-- Custom Message -->
            <div>
              <label
                for="message"
                class="block text-sm font-medium text-gray-700 dark:text-gray-300"
              >
                초대 메시지 (선택사항)
              </label>
              <textarea
                id="message"
                v-model="customMessage"
                rows="3"
                placeholder="초대 메시지를 입력하세요..."
                class="mt-1 block w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white dark:placeholder-gray-500 dark:focus:border-indigo-400 dark:focus:ring-indigo-400"
              ></textarea>
            </div>

            <!-- Preview -->
            <div
              v-if="emails.length > 0 && isValid"
              class="rounded-md bg-indigo-50 p-3 dark:bg-indigo-900/20"
            >
              <p class="text-sm font-medium text-indigo-800 dark:text-indigo-300">
                초대할 멤버: {{ emails.length }}명
              </p>
              <div class="mt-2 flex flex-wrap gap-2">
                <span
                  v-for="email in emails"
                  :key="email"
                  class="inline-flex items-center rounded-full bg-indigo-100 px-2.5 py-0.5 text-xs font-medium text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-300"
                >
                  {{ email }}
                </span>
              </div>
            </div>
          </div>

          <div class="mt-6 flex justify-end space-x-3">
            <button
              @click="handleClose"
              class="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200 dark:hover:bg-gray-600"
            >
              취소
            </button>
            <button
              @click="handleInvite"
              :disabled="!isValid"
              class="inline-flex items-center rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-gray-400 dark:bg-indigo-500 dark:hover:bg-indigo-600 dark:disabled:bg-gray-600"
            >
              <PaperAirplaneIcon class="mr-2 h-4 w-4" />
              초대 보내기
            </button>
          </div>
        </div>
      </transition>
    </div>
  </transition>
</template>
