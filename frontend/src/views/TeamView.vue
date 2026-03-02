<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
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
import OTabs from '@/components/ui/OTabs.vue'
import TeamMemberCard from '@/components/team/TeamMemberCard.vue'
import InviteMemberModal from '@/components/team/InviteMemberModal.vue'
import TeamActivityFeed from '@/components/team/TeamActivityFeed.vue'
import RoleBadge from '@/components/team/RoleBadge.vue'
import PermissionMatrix from '@/components/team/PermissionMatrix.vue'
import WorkflowBoard from '@/components/team/WorkflowBoard.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'

const { t } = useI18n({ useScope: 'global' })

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
      label: t('team.roles.owner'),
      color: 'text-purple-600 dark:text-purple-400',
    },
    {
      role: 'admin',
      count: teamStore.membersByRole('admin').length,
      label: t('team.roles.admin'),
      color: 'text-blue-600 dark:text-blue-400',
    },
    {
      role: 'editor',
      count: teamStore.membersByRole('editor').length,
      label: t('team.roles.editor'),
      color: 'text-green-600 dark:text-green-400',
    },
    {
      role: 'viewer',
      count: teamStore.membersByRole('viewer').length,
      label: t('team.roles.viewer'),
      color: 'text-gray-600 dark:text-gray-400',
    },
  ]
})

const inviteStatus = computed(() => {
  const pending = teamStore.invites.filter((i) => i.status === 'pending').length
  const expired = teamStore.invites.filter((i) => i.status === 'expired').length
  return { pending, expired }
})

const tabs = computed(() => {
  const base = [
    { key: 'members', label: t('team.tabs.members'), icon: UserGroupIcon, count: teamStore.members.length },
    { key: 'invites', label: t('team.tabs.invites'), icon: EnvelopeIcon, count: inviteStatus.value.pending > 0 ? inviteStatus.value.pending : undefined },
    { key: 'activity', label: t('team.tabs.activity'), icon: ClockIcon },
  ]
  if (canManage.value) {
    base.push({ key: 'permissions', label: t('team.tabs.permissions'), icon: ShieldCheckIcon })
  }
  base.push({ key: 'workflow', label: t('team.tabs.workflow'), icon: ViewColumnsIcon })
  return base
})

const handleCancelInvite = (inviteId: number) => {
  if (confirm(t('team.confirmCancelInvite'))) {
    teamStore.cancelInvite(inviteId)
  }
}

const handleResendInvite = (inviteId: number) => {
  teamStore.resendInvite(inviteId)
}

const relativeTime = (dateString: string): string => {
  const diff = Date.now() - new Date(dateString).getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days === 0) return t('team.time.today')
  if (days === 1) return t('team.time.yesterday')
  if (days < 7) return t('team.time.daysAgo', { days })
  return new Date(dateString).toLocaleDateString('ko-KR')
}

const expiresIn = (dateString: string): string => {
  const diff = new Date(dateString).getTime() - Date.now()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days < 0) return t('team.time.expired')
  if (days === 0) return t('team.time.expiresToday')
  if (days === 1) return t('team.time.expiresTomorrow')
  return t('team.time.expiresInDays', { days })
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
  <div class="relative">
    <!-- Header -->
    <PageHeader :title="teamStore.teamName" :description="$t('team.description')">
      <template #actions>
        <button
          v-if="canManage"
          class="btn-primary inline-flex items-center gap-2"
          @click="showInviteModal = true"
        >
          <UserPlusIcon class="h-5 w-5" />
          {{ $t('team.inviteMember') }}
        </button>
      </template>
    </PageHeader>

    <PageGuide :title="$t('team.pageGuideTitle')" :items="($tm('team.pageGuide') as string[])" />

    <!-- Stats -->
    <div class="mb-6 grid grid-cols-2 gap-4 mobile:grid-cols-4">
      <div class="card">
        <div class="flex items-center">
          <UserGroupIcon class="h-8 w-8 text-primary-600 dark:text-primary-400" />
          <div class="ml-3">
            <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('team.stats.totalMembers') }}</p>
            <p class="text-2xl font-semibold text-gray-900 dark:text-gray-100">
              {{ teamStore.members.length }}
            </p>
          </div>
        </div>
      </div>
      <div class="card">
        <div class="flex items-center">
          <div
            class="flex h-8 w-8 items-center justify-center rounded-full bg-green-100 dark:bg-green-900/30"
          >
            <span class="h-3 w-3 rounded-full bg-green-500"></span>
          </div>
          <div class="ml-3">
            <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('team.stats.online') }}</p>
            <p class="text-2xl font-semibold text-gray-900 dark:text-gray-100">
              {{ teamStore.onlineMembers.length }}
            </p>
          </div>
        </div>
      </div>
      <div class="card">
        <div class="flex items-center">
          <EnvelopeIcon class="h-8 w-8 text-blue-600 dark:text-blue-400" />
          <div class="ml-3">
            <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('team.stats.pendingInvites') }}</p>
            <p class="text-2xl font-semibold text-gray-900 dark:text-gray-100">
              {{ inviteStatus.pending }}
            </p>
          </div>
        </div>
      </div>
      <div class="card">
        <div class="flex items-center">
          <ClockIcon class="h-8 w-8 text-purple-600 dark:text-purple-400" />
          <div class="ml-3">
            <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('team.stats.recentActivity') }}</p>
            <p class="text-2xl font-semibold text-gray-900 dark:text-gray-100">
              {{ teamStore.activities.length }}
            </p>
          </div>
        </div>
      </div>
    </div>

      <!-- Tabs -->
      <OTabs v-model="activeTab" :tabs="tabs" />

      <!-- Tab Content -->
      <div class="mt-8">
        <!-- Members Tab -->
        <div v-if="activeTab === 'members'" class="space-y-6">
          <!-- Role Distribution -->
          <div class="card">
            <h3 class="text-sm font-medium text-gray-900 dark:text-gray-100">
              {{ $t('team.roleDistribution') }}
            </h3>
            <div class="mt-4 grid grid-cols-2 gap-4 mobile:grid-cols-4">
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
          <div class="grid gap-4 mobile:grid-cols-2 desktop:grid-cols-3">
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
            <h3 class="mt-2 text-sm font-medium text-gray-900 dark:text-gray-100">
              {{ $t('team.noInvites') }}
            </h3>
            <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
              {{ $t('team.noInvitesDescription') }}
            </p>
            <button
              v-if="canManage"
              @click="showInviteModal = true"
              class="btn-primary mt-4 inline-flex items-center gap-2"
            >
              <UserPlusIcon class="h-5 w-5" />
              {{ $t('team.inviteMember') }}
            </button>
          </div>

          <div
            v-for="invite in teamStore.invites"
            :key="invite.id"
            class="card"
          >
            <div class="flex items-start justify-between">
              <div class="flex-1">
                <div class="flex items-center space-x-3">
                  <p class="text-sm font-medium text-gray-900 dark:text-gray-100">
                    {{ invite.email }}
                  </p>
                  <RoleBadge :role="invite.role" />
                  <span
                    v-if="invite.status === 'pending'"
                    class="inline-flex items-center rounded-full bg-yellow-100 px-2.5 py-0.5 text-xs font-medium text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300"
                  >
                    <ClockIcon class="mr-1 h-3 w-3" />
                    {{ $t('team.inviteStatus.pending') }}
                  </span>
                  <span
                    v-else-if="invite.status === 'expired'"
                    class="inline-flex items-center rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-medium text-red-800 dark:bg-red-900/30 dark:text-red-300"
                  >
                    <XCircleIcon class="mr-1 h-3 w-3" />
                    {{ $t('team.inviteStatus.expired') }}
                  </span>
                  <span
                    v-else
                    class="inline-flex items-center rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-800 dark:bg-green-900/30 dark:text-green-300"
                  >
                    <CheckCircleIcon class="mr-1 h-3 w-3" />
                    {{ $t('team.inviteStatus.accepted') }}
                  </span>
                </div>
                <div class="mt-2 flex items-center space-x-4 text-sm text-gray-500 dark:text-gray-400">
                  <span>{{ $t('team.invitedAt') }}: {{ relativeTime(invite.invitedAt) }}</span>
                  <span>•</span>
                  <span>{{ expiresIn(invite.expiresAt) }}</span>
                </div>
              </div>

              <div v-if="canManage && invite.status === 'pending'" class="flex space-x-2">
                <button
                  @click="handleResendInvite(invite.id)"
                  class="rounded-md p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
                  :title="$t('team.resendInvite')"
                >
                  <ArrowPathIcon class="h-5 w-5" />
                </button>
                <button
                  @click="handleCancelInvite(invite.id)"
                  class="rounded-md p-2 text-gray-400 hover:bg-red-100 hover:text-red-600 dark:hover:bg-red-900/20 dark:hover:text-red-400"
                  :title="$t('team.cancelInvite')"
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
            class="card"
          >
            <TeamActivityFeed />
          </div>
        </div>

        <!-- Permissions Tab -->
        <div v-if="activeTab === 'permissions'">
          <div
            class="card"
          >
            <PermissionMatrix />
          </div>
        </div>

        <!-- Workflow Tab -->
        <div v-if="activeTab === 'workflow'" class="space-y-8">
          <!-- Kanban Board -->
          <div>
            <h3 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('team.workflowBoard') }}
            </h3>
            <WorkflowBoard />
          </div>

          <!-- My Tasks & Pending Reviews -->
          <div class="grid gap-6 desktop:grid-cols-2">
            <!-- 내 작업 -->
            <div class="card">
              <h3 class="mb-4 text-base font-semibold text-gray-900 dark:text-gray-100">
                {{ $t('team.myTasks') }}
              </h3>
              <div v-if="approvalStore.myTasks">
                <div v-if="approvalStore.myTasks.assignedToMe.length > 0" class="mb-4">
                  <p class="mb-2 text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    {{ $t('team.assignedToMe') }}
                  </p>
                  <div class="space-y-2">
                    <div
                      v-for="task in approvalStore.myTasks.assignedToMe"
                      :key="task.approvalId"
                      class="flex items-center justify-between rounded-md border border-gray-100 p-3 dark:border-gray-700"
                    >
                      <div class="min-w-0 flex-1">
                        <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">{{ task.videoTitle }}</p>
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
                    {{ $t('team.requestedByMe') }}
                  </p>
                  <div class="space-y-2">
                    <div
                      v-for="task in approvalStore.myTasks.requestedByMe"
                      :key="task.approvalId"
                      class="flex items-center justify-between rounded-md border border-gray-100 p-3 dark:border-gray-700"
                    >
                      <div class="min-w-0 flex-1">
                        <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">{{ task.videoTitle }}</p>
                        <p class="text-xs text-gray-500 dark:text-gray-400">{{ task.reviewerName ?? $t('team.noReviewer') }}</p>
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
                  {{ $t('team.noTasks') }}
                </div>
              </div>
              <div v-else class="py-8 text-center text-sm text-gray-400 dark:text-gray-500">
                {{ $t('team.loading') }}
              </div>
            </div>

            <!-- 대기 중인 검토 -->
            <div class="card">
              <div class="mb-4 flex items-center justify-between">
                <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">
                  {{ $t('team.pendingReviews') }}
                </h3>
                <span
                  v-if="approvalStore.pendingReviews && approvalStore.pendingReviews.overdueCount > 0"
                  class="inline-flex items-center rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-medium text-red-700 dark:bg-red-900/30 dark:text-red-300"
                >
                  {{ $t('team.overdueCount', { count: approvalStore.pendingReviews.overdueCount }) }}
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
                      <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">{{ review.videoTitle }}</p>
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
                  {{ $t('team.noPendingReviews') }}
                </div>
              </div>
              <div v-else class="py-8 text-center text-sm text-gray-400 dark:text-gray-500">
                {{ $t('team.loading') }}
              </div>
            </div>
          </div>
        </div>
      </div>

    <!-- Invite Member Modal -->
    <InviteMemberModal :show="showInviteModal" @close="showInviteModal = false" />
  </div>
</template>
