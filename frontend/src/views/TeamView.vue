<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import {
  UserGroupIcon,
  EnvelopeIcon,
  ClockIcon,
  UserPlusIcon,
  CheckCircleIcon,
  XCircleIcon,
  ArrowPathIcon,
  ShieldCheckIcon,
  ViewColumnsIcon,
} from '@heroicons/vue/24/outline'
import { useTeamStore } from '@/stores/team'
import { useApprovalStore } from '@/stores/approval'
import TeamMemberCard from '@/components/team/TeamMemberCard.vue'
import InviteMemberModal from '@/components/team/InviteMemberModal.vue'
import TeamActivityFeed from '@/components/team/TeamActivityFeed.vue'
import RoleBadge from '@/components/team/RoleBadge.vue'
import PermissionMatrix from '@/components/team/PermissionMatrix.vue'
import WorkflowBoard from '@/components/team/WorkflowBoard.vue'
import PageGuide from '@/components/common/PageGuide.vue'

const teamStore = useTeamStore()
const approvalStore = useApprovalStore()

type TabType = 'members' | 'invites' | 'activity' | 'permissions' | 'workflow'
const activeTab = ref<TabType>('members')
const showInviteModal = ref(false)

const currentUser = computed(() => teamStore.members[0])

const canManage = computed(() => {
  return currentUser.value?.role === 'owner' || currentUser.value?.role === 'admin'
})

const roleStats = computed(() => {
  return [
    {
      role: 'owner',
      count: teamStore.membersByRole('owner').length,
      label: '소유자',
      color: 'text-purple-600 dark:text-purple-400',
    },
    {
      role: 'admin',
      count: teamStore.membersByRole('admin').length,
      label: '관리자',
      color: 'text-blue-600 dark:text-blue-400',
    },
    {
      role: 'editor',
      count: teamStore.membersByRole('editor').length,
      label: '에디터',
      color: 'text-green-600 dark:text-green-400',
    },
    {
      role: 'viewer',
      count: teamStore.membersByRole('viewer').length,
      label: '뷰어',
      color: 'text-gray-600 dark:text-gray-400',
    },
  ]
})

const inviteStatus = computed(() => {
  const pending = teamStore.invites.filter((i) => i.status === 'pending').length
  const expired = teamStore.invites.filter((i) => i.status === 'expired').length
  return { pending, expired }
})

const handleCancelInvite = (inviteId: number) => {
  if (confirm('초대를 취소하시겠습니까?')) {
    teamStore.cancelInvite(inviteId)
  }
}

const handleResendInvite = (inviteId: number) => {
  teamStore.resendInvite(inviteId)
}

const relativeTime = (dateString: string): string => {
  const diff = Date.now() - new Date(dateString).getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days === 0) return '오늘'
  if (days === 1) return '어제'
  if (days < 7) return `${days}일 전`
  return new Date(dateString).toLocaleDateString('ko-KR')
}

const expiresIn = (dateString: string): string => {
  const diff = new Date(dateString).getTime() - Date.now()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days < 0) return '만료됨'
  if (days === 0) return '오늘 만료'
  if (days === 1) return '내일 만료'
  return `${days}일 후 만료`
}

// 워크플로우 탭 전환 시 데이터 로드
watch(activeTab, (tab) => {
  if (tab === 'workflow') {
    approvalStore.fetchMyTasks()
    approvalStore.fetchPendingReviews()
  }
})
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900">
    <div class="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <!-- Header -->
      <div class="mb-8">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-3xl font-bold text-gray-900 dark:text-white">
              {{ teamStore.teamName }}
            </h1>
            <p class="mt-2 text-sm text-gray-600 dark:text-gray-400">
              팀 멤버 관리 및 협업 활동 추적
            </p>
          </div>
          <button
            v-if="canManage"
            @click="showInviteModal = true"
            class="inline-flex items-center rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 dark:bg-indigo-500 dark:hover:bg-indigo-600"
          >
            <UserPlusIcon class="mr-2 h-5 w-5" />
            멤버 초대
          </button>
        </div>

        <!-- Stats -->
        <div class="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
          <div
            class="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800"
          >
            <div class="flex items-center">
              <UserGroupIcon class="h-8 w-8 text-indigo-600 dark:text-indigo-400" />
              <div class="ml-3">
                <p class="text-sm text-gray-600 dark:text-gray-400">전체 멤버</p>
                <p class="text-2xl font-semibold text-gray-900 dark:text-white">
                  {{ teamStore.members.length }}
                </p>
              </div>
            </div>
          </div>
          <div
            class="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800"
          >
            <div class="flex items-center">
              <div
                class="flex h-8 w-8 items-center justify-center rounded-full bg-green-100 dark:bg-green-900/30"
              >
                <span class="h-3 w-3 rounded-full bg-green-500"></span>
              </div>
              <div class="ml-3">
                <p class="text-sm text-gray-600 dark:text-gray-400">온라인</p>
                <p class="text-2xl font-semibold text-gray-900 dark:text-white">
                  {{ teamStore.onlineMembers.length }}
                </p>
              </div>
            </div>
          </div>
          <div
            class="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800"
          >
            <div class="flex items-center">
              <EnvelopeIcon class="h-8 w-8 text-blue-600 dark:text-blue-400" />
              <div class="ml-3">
                <p class="text-sm text-gray-600 dark:text-gray-400">대기 중 초대</p>
                <p class="text-2xl font-semibold text-gray-900 dark:text-white">
                  {{ inviteStatus.pending }}
                </p>
              </div>
            </div>
          </div>
          <div
            class="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800"
          >
            <div class="flex items-center">
              <ClockIcon class="h-8 w-8 text-purple-600 dark:text-purple-400" />
              <div class="ml-3">
                <p class="text-sm text-gray-600 dark:text-gray-400">최근 활동</p>
                <p class="text-2xl font-semibold text-gray-900 dark:text-white">
                  {{ teamStore.activities.length }}
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <PageGuide title="팀 관리" :items="[
        '멤버 탭에서 팀원 목록을 확인하고, 역할 배지(Owner/Admin/Editor/Viewer)로 권한 수준을 파악하세요',
        '초대 탭에서 이메일로 새 멤버를 초대하고, 대기 중/만료된 초대를 관리(재발송/취소)할 수 있습니다',
        '활동 탭에서 팀원들의 최근 작업 이력을 모니터링하세요',
        '권한 탭에서 역할별 기능 접근 권한을 세밀하게 설정하여 팀 보안을 관리하세요',
        '상단 역할 통계에서 Owner·Admin·Editor·Viewer 인원 수를 한눈에 확인하세요',
      ]" />

      <!-- Tabs -->
      <div class="border-b border-gray-200 dark:border-gray-700">
        <nav class="-mb-px flex space-x-8">
          <button
            @click="activeTab = 'members'"
            :class="[
              activeTab === 'members'
                ? 'border-indigo-500 text-indigo-600 dark:border-indigo-400 dark:text-indigo-400'
                : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400 dark:hover:border-gray-600 dark:hover:text-gray-300',
              'flex items-center whitespace-nowrap border-b-2 px-1 py-4 text-sm font-medium',
            ]"
          >
            <UserGroupIcon class="mr-2 h-5 w-5" />
            멤버
            <span
              :class="[
                activeTab === 'members'
                  ? 'bg-indigo-100 text-indigo-600 dark:bg-indigo-900/30 dark:text-indigo-400'
                  : 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400',
                'ml-2 rounded-full px-2.5 py-0.5 text-xs font-medium',
              ]"
            >
              {{ teamStore.members.length }}
            </span>
          </button>
          <button
            @click="activeTab = 'invites'"
            :class="[
              activeTab === 'invites'
                ? 'border-indigo-500 text-indigo-600 dark:border-indigo-400 dark:text-indigo-400'
                : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400 dark:hover:border-gray-600 dark:hover:text-gray-300',
              'flex items-center whitespace-nowrap border-b-2 px-1 py-4 text-sm font-medium',
            ]"
          >
            <EnvelopeIcon class="mr-2 h-5 w-5" />
            초대
            <span
              v-if="inviteStatus.pending > 0"
              :class="[
                activeTab === 'invites'
                  ? 'bg-indigo-100 text-indigo-600 dark:bg-indigo-900/30 dark:text-indigo-400'
                  : 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400',
                'ml-2 rounded-full px-2.5 py-0.5 text-xs font-medium',
              ]"
            >
              {{ inviteStatus.pending }}
            </span>
          </button>
          <button
            @click="activeTab = 'activity'"
            :class="[
              activeTab === 'activity'
                ? 'border-indigo-500 text-indigo-600 dark:border-indigo-400 dark:text-indigo-400'
                : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400 dark:hover:border-gray-600 dark:hover:text-gray-300',
              'flex items-center whitespace-nowrap border-b-2 px-1 py-4 text-sm font-medium',
            ]"
          >
            <ClockIcon class="mr-2 h-5 w-5" />
            활동
          </button>
          <button
            v-if="canManage"
            @click="activeTab = 'permissions'"
            :class="[
              activeTab === 'permissions'
                ? 'border-indigo-500 text-indigo-600 dark:border-indigo-400 dark:text-indigo-400'
                : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400 dark:hover:border-gray-600 dark:hover:text-gray-300',
              'flex items-center whitespace-nowrap border-b-2 px-1 py-4 text-sm font-medium',
            ]"
          >
            <ShieldCheckIcon class="mr-2 h-5 w-5" />
            권한
          </button>
          <button
            @click="activeTab = 'workflow'"
            :class="[
              activeTab === 'workflow'
                ? 'border-indigo-500 text-indigo-600 dark:border-indigo-400 dark:text-indigo-400'
                : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400 dark:hover:border-gray-600 dark:hover:text-gray-300',
              'flex items-center whitespace-nowrap border-b-2 px-1 py-4 text-sm font-medium',
            ]"
          >
            <ViewColumnsIcon class="mr-2 h-5 w-5" />
            워크플로우
          </button>
        </nav>
      </div>

      <!-- Tab Content -->
      <div class="mt-8">
        <!-- Members Tab -->
        <div v-if="activeTab === 'members'" class="space-y-6">
          <!-- Role Distribution -->
          <div
            class="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800"
          >
            <h3 class="text-sm font-medium text-gray-900 dark:text-white">
              역할별 분포
            </h3>
            <div class="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
              <div
                v-for="stat in roleStats"
                :key="stat.role"
                class="text-center"
              >
                <p :class="[stat.color, 'text-2xl font-semibold']">
                  {{ stat.count }}
                </p>
                <p class="text-sm text-gray-600 dark:text-gray-400">
                  {{ stat.label }}
                </p>
              </div>
            </div>
          </div>

          <!-- Members Grid -->
          <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <TeamMemberCard
              v-for="member in teamStore.members"
              :key="member.id"
              :member="member"
              :can-manage="canManage"
            />
          </div>
        </div>

        <!-- Invites Tab -->
        <div v-if="activeTab === 'invites'" class="space-y-4">
          <div
            v-if="teamStore.invites.length === 0"
            class="text-center py-12"
          >
            <EnvelopeIcon class="mx-auto h-12 w-12 text-gray-400 dark:text-gray-600" />
            <h3 class="mt-2 text-sm font-medium text-gray-900 dark:text-white">
              초대 내역 없음
            </h3>
            <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
              팀 멤버를 초대하여 협업을 시작하세요.
            </p>
            <button
              v-if="canManage"
              @click="showInviteModal = true"
              class="mt-4 inline-flex items-center rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 dark:bg-indigo-500 dark:hover:bg-indigo-600"
            >
              <UserPlusIcon class="mr-2 h-5 w-5" />
              멤버 초대
            </button>
          </div>

          <div
            v-for="invite in teamStore.invites"
            :key="invite.id"
            class="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800"
          >
            <div class="flex items-start justify-between">
              <div class="flex-1">
                <div class="flex items-center space-x-3">
                  <p class="text-sm font-medium text-gray-900 dark:text-white">
                    {{ invite.email }}
                  </p>
                  <RoleBadge :role="invite.role" />
                  <span
                    v-if="invite.status === 'pending'"
                    class="inline-flex items-center rounded-full bg-yellow-100 px-2.5 py-0.5 text-xs font-medium text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300"
                  >
                    <ClockIcon class="mr-1 h-3 w-3" />
                    대기 중
                  </span>
                  <span
                    v-else-if="invite.status === 'expired'"
                    class="inline-flex items-center rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-medium text-red-800 dark:bg-red-900/30 dark:text-red-300"
                  >
                    <XCircleIcon class="mr-1 h-3 w-3" />
                    만료됨
                  </span>
                  <span
                    v-else
                    class="inline-flex items-center rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-800 dark:bg-green-900/30 dark:text-green-300"
                  >
                    <CheckCircleIcon class="mr-1 h-3 w-3" />
                    수락됨
                  </span>
                </div>
                <div class="mt-2 flex items-center space-x-4 text-sm text-gray-500 dark:text-gray-400">
                  <span>초대: {{ relativeTime(invite.invitedAt) }}</span>
                  <span>•</span>
                  <span>{{ expiresIn(invite.expiresAt) }}</span>
                </div>
              </div>

              <div v-if="canManage && invite.status === 'pending'" class="flex space-x-2">
                <button
                  @click="handleResendInvite(invite.id)"
                  class="rounded-md p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
                  title="초대 재전송"
                >
                  <ArrowPathIcon class="h-5 w-5" />
                </button>
                <button
                  @click="handleCancelInvite(invite.id)"
                  class="rounded-md p-2 text-gray-400 hover:bg-red-100 hover:text-red-600 dark:hover:bg-red-900/20 dark:hover:text-red-400"
                  title="초대 취소"
                >
                  <XCircleIcon class="h-5 w-5" />
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Activity Tab -->
        <div v-if="activeTab === 'activity'">
          <div
            class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800"
          >
            <TeamActivityFeed />
          </div>
        </div>

        <!-- Permissions Tab -->
        <div v-if="activeTab === 'permissions'">
          <div
            class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800"
          >
            <PermissionMatrix />
          </div>
        </div>

        <!-- Workflow Tab -->
        <div v-if="activeTab === 'workflow'" class="space-y-8">
          <!-- Kanban Board -->
          <div>
            <h3 class="mb-4 text-lg font-semibold text-gray-900 dark:text-white">
              워크플로우 보드
            </h3>
            <WorkflowBoard />
          </div>

          <!-- My Tasks & Pending Reviews -->
          <div class="grid gap-6 lg:grid-cols-2">
            <!-- 내 작업 -->
            <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
              <h3 class="mb-4 text-base font-semibold text-gray-900 dark:text-white">
                내 작업
              </h3>
              <div v-if="approvalStore.myTasks">
                <div v-if="approvalStore.myTasks.assignedToMe.length > 0" class="mb-4">
                  <p class="mb-2 text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    나에게 할당됨
                  </p>
                  <div class="space-y-2">
                    <div
                      v-for="task in approvalStore.myTasks.assignedToMe"
                      :key="task.approvalId"
                      class="flex items-center justify-between rounded-md border border-gray-100 p-3 dark:border-gray-700"
                    >
                      <div class="min-w-0 flex-1">
                        <p class="truncate text-sm font-medium text-gray-900 dark:text-white">{{ task.videoTitle }}</p>
                        <p class="text-xs text-gray-500 dark:text-gray-400">{{ task.requesterName }}</p>
                      </div>
                      <span
                        class="ml-2 inline-flex rounded-full px-2 py-0.5 text-[10px] font-medium"
                        :class="{
                          'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300': task.status === 'PENDING',
                          'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300': task.status === 'APPROVED',
                          'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300': task.status === 'REJECTED',
                          'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300': !['PENDING', 'APPROVED', 'REJECTED'].includes(task.status),
                        }"
                      >
                        {{ task.status }}
                      </span>
                    </div>
                  </div>
                </div>
                <div v-if="approvalStore.myTasks.requestedByMe.length > 0">
                  <p class="mb-2 text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    내가 요청함
                  </p>
                  <div class="space-y-2">
                    <div
                      v-for="task in approvalStore.myTasks.requestedByMe"
                      :key="task.approvalId"
                      class="flex items-center justify-between rounded-md border border-gray-100 p-3 dark:border-gray-700"
                    >
                      <div class="min-w-0 flex-1">
                        <p class="truncate text-sm font-medium text-gray-900 dark:text-white">{{ task.videoTitle }}</p>
                        <p class="text-xs text-gray-500 dark:text-gray-400">{{ task.reviewerName ?? '검토자 미정' }}</p>
                      </div>
                      <span
                        class="ml-2 inline-flex rounded-full px-2 py-0.5 text-[10px] font-medium"
                        :class="{
                          'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300': task.status === 'PENDING',
                          'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300': task.status === 'APPROVED',
                          'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300': task.status === 'REJECTED',
                          'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300': !['PENDING', 'APPROVED', 'REJECTED'].includes(task.status),
                        }"
                      >
                        {{ task.status }}
                      </span>
                    </div>
                  </div>
                </div>
                <div
                  v-if="approvalStore.myTasks.assignedToMe.length === 0 && approvalStore.myTasks.requestedByMe.length === 0"
                  class="py-8 text-center text-sm text-gray-400 dark:text-gray-500"
                >
                  작업이 없습니다
                </div>
              </div>
              <div v-else class="py-8 text-center text-sm text-gray-400 dark:text-gray-500">
                로딩 중...
              </div>
            </div>

            <!-- 대기 중인 검토 -->
            <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
              <div class="mb-4 flex items-center justify-between">
                <h3 class="text-base font-semibold text-gray-900 dark:text-white">
                  대기 중인 검토
                </h3>
                <span
                  v-if="approvalStore.pendingReviews && approvalStore.pendingReviews.overdueCount > 0"
                  class="inline-flex items-center rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-medium text-red-700 dark:bg-red-900/30 dark:text-red-300"
                >
                  {{ approvalStore.pendingReviews.overdueCount }}건 기한 초과
                </span>
              </div>
              <div v-if="approvalStore.pendingReviews">
                <div v-if="approvalStore.pendingReviews.reviews.length > 0" class="space-y-2">
                  <div
                    v-for="review in approvalStore.pendingReviews.reviews"
                    :key="review.approvalId"
                    class="flex items-center justify-between rounded-md border border-gray-100 p-3 dark:border-gray-700"
                  >
                    <div class="min-w-0 flex-1">
                      <p class="truncate text-sm font-medium text-gray-900 dark:text-white">{{ review.videoTitle }}</p>
                      <p class="text-xs text-gray-500 dark:text-gray-400">{{ review.requesterName }}</p>
                    </div>
                    <div class="ml-2 flex flex-wrap gap-1">
                      <span
                        v-for="p in review.platforms"
                        :key="p"
                        class="inline-flex rounded px-1.5 py-0.5 text-[10px] font-medium bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300"
                      >
                        {{ p }}
                      </span>
                    </div>
                  </div>
                </div>
                <div v-else class="py-8 text-center text-sm text-gray-400 dark:text-gray-500">
                  대기 중인 검토가 없습니다
                </div>
              </div>
              <div v-else class="py-8 text-center text-sm text-gray-400 dark:text-gray-500">
                로딩 중...
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Invite Member Modal -->
    <InviteMemberModal :show="showInviteModal" @close="showInviteModal = false" />
  </div>
</template>
