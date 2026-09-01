<script setup lang="ts">
import { ref, computed } from 'vue'
import { XMarkIcon, PaperAirplaneIcon } from '@heroicons/vue/24/outline'
import type { TeamRole } from '@/types/team'
import { useTeamStore } from '@/stores/team'
import { useNotification } from '@/composables/useNotification'
import { PLAN_LIMIT_EXCEEDED, PLAN_UPGRADE_PATH, matchesCode } from '@/composables/usePlanLimit'

interface Props {
  show: boolean
}

interface Emits {
  (e: 'close'): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

const teamStore = useTeamStore()
const notification = useNotification()

const emailInput = ref('')
const selectedRole = ref<TeamRole>('editor')
const customMessage = ref('')
const emailError = ref('')
/**
 * 서버가 초대를 거절한 사유. **우리가 지어내지 않고 서버 문장을 그대로 쓴다.**
 *
 * 예전에는 `catch` 가 오류를 통째로 버리고 "입력한 주소와 권한을 확인해 주세요" 라는 고정
 * 문구만 띄웠다. 서버가 팀 좌석 한도로 막기 시작하면서 그 문구는 **틀린 안내**가 됐다 —
 * 주소도 권한도 멀쩡한데 주소를 고치라고 하니, 사용자는 될 때까지 이메일만 다시 친다.
 */
const inviteError = ref('')
/** 업그레이드로 풀리는 거절일 때만 true. 안정 코드로만 판단한다. */
const showUpgradeLink = ref(false)

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

const handleInvite = async () => {
  if (!isValid.value) return

  inviteError.value = ''
  showUpgradeLink.value = false

  try {
    await Promise.all(emails.value.map((email) => teamStore.inviteMember(email, selectedRole.value)))
  } catch (error) {
    /*
     * **서버가 준 사유를 그대로 보여준다.**
     *
     * `client.ts` 의 `enrichWithServerMessage` 가 ResData.message 를 `error.message` 로
     * 올려주므로, 여기서 문구를 다시 만들 이유가 없다. 지어낸 고정 문구는 서버가 막는 이유가
     * 늘어날 때마다 조용히 틀린 안내가 된다 — 팀 좌석 한도가 정확히 그 경우였다.
     *
     * 업그레이드 안내는 **안정 코드로만** 판단한다. 문구로 분기하면 번역·수정에 깨지고,
     * 돈을 내도 풀리지 않는 오류(주소 형식·중복)에 결제를 권하게 된다.
     */
    inviteError.value = error instanceof Error && error.message
      ? error.message
      : '초대를 보내지 못했습니다. 잠시 후 다시 시도해 주세요.'
    showUpgradeLink.value = matchesCode(error, PLAN_LIMIT_EXCEEDED)
    notification.error(inviteError.value)
    return
  }

  emailInput.value = ''
  selectedRole.value = 'editor'
  customMessage.value = ''
  emailError.value = ''
  inviteError.value = ''
  showUpgradeLink.value = false
  emit('close')
}

const handleClose = () => {
  emailInput.value = ''
  selectedRole.value = 'editor'
  customMessage.value = ''
  emailError.value = ''
  inviteError.value = ''
  showUpgradeLink.value = false
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
            <h3 id="invite-member-title" class="text-title font-semibold text-gray-900 dark:text-white">
              팀 멤버 초대
            </h3>
            <button
              aria-label="모달 닫기"
              class="rounded-md p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
              @click="handleClose"
            >
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <div class="mt-6 space-y-4">
            <!-- Email Input -->
            <div>
              <label
                for="email"
                class="block text-body font-medium text-gray-700 dark:text-gray-300"
              >
                이메일 주소
              </label>
              <input
                id="email"
                v-model="emailInput"
                type="text"
                placeholder="example@email.com (여러 개는 쉼표로 구분)"
                class="mt-1 block w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-body text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white dark:placeholder-gray-500 dark:focus:border-primary-400 dark:focus:ring-primary-400"
                @input="handleEmailInput"
              />
              <p v-if="emailError" class="mt-1 text-body-xs text-error-strong">
                {{ emailError }}
              </p>
              <!--
                서버가 거절한 사유. 모달 안에 남겨 두어야 사용자가 무엇을 고쳐야 하는지
                보면서 입력을 수정할 수 있다. 토스트는 사라진다.
              -->
              <p v-if="inviteError" class="mt-1 text-body-xs text-error-strong">
                {{ inviteError }}
              </p>
              <router-link
                v-if="showUpgradeLink"
                :to="PLAN_UPGRADE_PATH"
                class="mt-2 inline-flex text-body-sm font-semibold underline"
              >
                {{ $t('subscription.changePlan') }}
              </router-link>
              <p class="mt-1 text-body-xs text-gray-500 dark:text-gray-400">
                여러 이메일을 초대하려면 쉼표로 구분하세요
              </p>
            </div>

            <!-- Role Selector -->
            <div>
              <label
                for="role"
                class="block text-body font-medium text-gray-700 dark:text-gray-300"
              >
                역할
              </label>
              <select
                id="role"
                v-model="selectedRole"
                class="mt-1 block w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-body text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white dark:focus:border-primary-400 dark:focus:ring-primary-400"
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
                class="block text-body font-medium text-gray-700 dark:text-gray-300"
              >
                초대 메시지 (선택사항)
              </label>
              <textarea
                id="message"
                v-model="customMessage"
                rows="3"
                placeholder="초대 메시지를 입력하세요..."
                class="mt-1 block w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-body text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white dark:placeholder-gray-500 dark:focus:border-primary-400 dark:focus:ring-primary-400"
              ></textarea>
            </div>

            <!-- Preview -->
            <div
              v-if="emails.length > 0 && isValid"
              class="rounded-md bg-primary-50 p-3 dark:bg-primary-900"
            >
              <p class="text-body font-medium text-primary-800 dark:text-primary-300">
                초대할 멤버: {{ emails.length }}명
              </p>
              <div class="mt-2 flex flex-wrap gap-2">
                <span
                  v-for="email in emails"
                  :key="email"
                  class="inline-flex items-center rounded-full bg-primary-100 px-2.5 py-0.5 text-caption text-primary-700 dark:bg-primary-900 dark:text-primary-300"
                >
                  {{ email }}
                </span>
              </div>
            </div>
          </div>

          <div class="mt-6 flex justify-end space-x-3">
            <button
              class="rounded-md border border-gray-300 bg-white px-4 py-2 text-body font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200 dark:hover:bg-gray-600"
              @click="handleClose"
            >
              취소
            </button>
            <button
              :disabled="!isValid"
              class="inline-flex items-center rounded-md bg-primary-600 px-4 py-2 text-body font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:bg-gray-400 dark:bg-primary-500 dark:hover:bg-primary-600 dark:disabled:bg-gray-600"
              @click="handleInvite"
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
