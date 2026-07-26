<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  UserGroupIcon,
  EnvelopeIcon,
  ClockIcon,
  UserPlusIcon,
  UserMinusIcon,
  CheckCircleIcon,
  XCircleIcon,
  ArrowPathIcon,
  ShieldCheckIcon,
  ViewColumnsIcon,
  MagnifyingGlassIcon,
} from '@heroicons/vue/24/outline'
import { useTeamStore } from '@/stores/team'
import { useApprovalStore } from '@/stores/approval'
import { useNotification } from '@/composables/useNotification'
import { useListControls, type ListSortOption } from '@/composables/useListControls'
import OTabs from '@/components/ui/OTabs.vue'
import TeamMemberCard from '@/components/team/TeamMemberCard.vue'
import InviteMemberModal from '@/components/team/InviteMemberModal.vue'
import TeamActivityFeed from '@/components/team/TeamActivityFeed.vue'
import RoleBadge from '@/components/team/RoleBadge.vue'
import PermissionMatrix from '@/components/team/PermissionMatrix.vue'
import WorkflowBoard from '@/components/team/WorkflowBoard.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ListToolbar from '@/components/common/ListToolbar.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import type { TeamMember, TeamInvite, TeamRole, InviteStatus } from '@/types/team'

const { t } = useI18n({ useScope: 'global' })

const teamStore = useTeamStore()
const approvalStore = useApprovalStore()
const notification = useNotification()

/** 체크박스 공통 스타일 — 목록 확산 시 그대로 복사해 쓴다. */
const CHECKBOX_CLASS =
  'h-4 w-4 shrink-0 cursor-pointer rounded border-gray-300 accent-primary-600 focus:ring-2 focus:ring-primary-500 disabled:cursor-not-allowed disabled:opacity-40 dark:border-gray-600'

type TabType = 'members' | 'invites' | 'activity' | 'permissions' | 'workflow'
const activeTab = ref<TabType>('members')
const showInviteModal = ref(false)
const showCancelInviteModal = ref(false)
const cancelInviteTargetId = ref<number | null>(null)
const showBulkRemoveModal = ref(false)
const showBulkCancelModal = ref(false)
const bulkWorking = ref(false)

const currentUser = computed(() => teamStore.members[0])

const canManage = computed(() => {
  return currentUser.value?.role === 'owner' || currentUser.value?.role === 'admin'
})

/**
 * 선택 가능한 항목만 담은 새 선택 집합.
 *
 * `useListControls.toggleAll`은 "보이는 항목 전부"를 선택하므로,
 * 소유자·본인·이미 수락된 초대처럼 **대상에서 빠져야 하는 항목**이 섞인 목록에는 쓸 수 없다.
 * (전체 선택 한 번으로 마지막 소유자가 선택되면 그대로 팀이 무너진다.)
 */
const nextSelection = <T extends { id: number }>(
  targets: T[],
  current: Set<number>,
  deselect: boolean,
): Set<number> => {
  const next = new Set(current)
  targets.forEach((item) => (deselect ? next.delete(item.id) : next.add(item.id)))
  return next
}

// ─── 멤버 목록 제어 ───────────────────────────────────────
/** 역할 정렬용 서열. 내림차순이 기본이라 소유자가 맨 위로 온다. */
const ROLE_RANK: Record<TeamRole, number> = { owner: 4, admin: 3, editor: 2, viewer: 1 }

const memberSortOptions = computed<ListSortOption<TeamMember>[]>(() => [
  { key: 'role', label: t('team.sortRole'), accessor: (m) => ROLE_RANK[m.role], kind: 'number', defaultDir: 'desc' },
  { key: 'name', label: t('team.sortName'), accessor: 'name', kind: 'string', defaultDir: 'asc' },
  { key: 'joined', label: t('team.sortJoined'), accessor: 'joinedAt', kind: 'date', defaultDir: 'desc' },
  {
    key: 'lastActive',
    label: t('team.sortLastActive'),
    accessor: 'lastActiveAt',
    kind: 'date',
    defaultDir: 'desc',
  },
])

const {
  query: memberQuery,
  sortKey: memberSortKey,
  sortDir: memberSortDir,
  filtered: filteredMembers,
  visibleCount: visibleMemberCount,
  isSourceEmpty: isMemberSourceEmpty,
  isResultEmpty: isMemberResultEmpty,
  resetFilters: resetMemberFilters,
  selectedIds: selectedMemberIds,
  selectedCount: selectedMemberCount,
  isSelected: isMemberSelected,
  toggle: toggleMemberSelection,
  clearSelection: clearMemberSelection,
} = useListControls<TeamMember>(() => teamStore.members, {
  searchFields: ['name', 'email', (member) => t(`team.roles.${member.role}`)],
  sortOptions: memberSortOptions,
  defaultSortKey: 'role',
})

/**
 * 일괄 제거 대상에서 빠지는 멤버.
 * - **소유자**: 기존 `TeamMemberCard`의 가드(`canManage && member.role !== 'owner'`)를 그대로 따른다.
 * - **본인**: 스스로를 팀에서 빼면 관리 권한까지 함께 사라져 되돌릴 수 없다.
 *   (`currentUser`는 이 화면이 예전부터 쓰던 `members[0]` 규칙을 그대로 사용한다.)
 */
const isRemovableMember = (member: TeamMember): boolean =>
  member.role !== 'owner' && member.id !== currentUser.value?.id

const removableMembers = computed(() => filteredMembers.value.filter(isRemovableMember))

const allRemovableSelected = computed(
  () =>
    removableMembers.value.length > 0 &&
    removableMembers.value.every((member) => isMemberSelected(member.id)),
)

const someRemovableSelected = computed(
  () => selectedMemberCount.value > 0 && !allRemovableSelected.value,
)

const toggleAllRemovableMembers = () => {
  selectedMemberIds.value = nextSelection(
    removableMembers.value,
    selectedMemberIds.value,
    allRemovableSelected.value,
  )
}

const handleBulkRemoveMembers = async () => {
  const ids = [...selectedMemberIds.value]
  if (ids.length === 0) return
  bulkWorking.value = true
  try {
    await Promise.all(ids.map((id) => teamStore.removeMember(id)))
    notification.success(t('team.bulkRemoveDone', { count: ids.length }))
  } catch {
    notification.error(t('team.removeFailed'))
  } finally {
    bulkWorking.value = false
    clearMemberSelection()
  }
}

// ─── 초대 목록 제어 ───────────────────────────────────────
/** 조치가 필요한 초대(대기 중)를 위로 올리는 서열. */
const INVITE_STATUS_RANK: Record<InviteStatus, number> = { pending: 3, expired: 2, accepted: 1 }

const inviteSortOptions = computed<ListSortOption<TeamInvite>[]>(() => [
  {
    key: 'status',
    label: t('team.sortInviteStatus'),
    accessor: (invite) => INVITE_STATUS_RANK[invite.status],
    kind: 'number',
    defaultDir: 'desc',
  },
  { key: 'invited', label: t('team.sortInvitedAt'), accessor: 'invitedAt', kind: 'date', defaultDir: 'desc' },
  { key: 'expires', label: t('team.sortExpires'), accessor: 'expiresAt', kind: 'date', defaultDir: 'asc' },
  { key: 'email', label: t('team.sortEmail'), accessor: 'email', kind: 'string', defaultDir: 'asc' },
])

/**
 * 멤버와 완전히 독립된 인스턴스다.
 * 멤버 id와 초대 id는 서로 다른 시퀀스라 값이 겹칠 수 있어,
 * 선택 상태를 공유하면 엉뚱한 멤버가 제거되는 사고가 난다.
 */
const {
  query: inviteQuery,
  sortKey: inviteSortKey,
  sortDir: inviteSortDir,
  filtered: filteredInvites,
  visibleCount: visibleInviteCount,
  isSourceEmpty: isInviteSourceEmpty,
  isResultEmpty: isInviteResultEmpty,
  resetFilters: resetInviteFilters,
  selectedIds: selectedInviteIds,
  selectedCount: selectedInviteCount,
  isSelected: isInviteSelected,
  toggle: toggleInviteSelection,
  clearSelection: clearInviteSelection,
} = useListControls<TeamInvite>(() => teamStore.invites, {
  searchFields: [
    'email',
    (invite) => t(`team.roles.${invite.role}`),
    (invite) => t(`team.inviteStatus.${invite.status}`),
  ],
  sortOptions: inviteSortOptions,
  defaultSortKey: 'status',
})

/** 취소할 수 있는 초대는 대기 중인 것뿐 — 개별 취소 버튼의 기존 조건과 동일하다. */
const isCancellableInvite = (invite: TeamInvite): boolean => invite.status === 'pending'

const cancellableInvites = computed(() => filteredInvites.value.filter(isCancellableInvite))

const allCancellableSelected = computed(
  () =>
    cancellableInvites.value.length > 0 &&
    cancellableInvites.value.every((invite) => isInviteSelected(invite.id)),
)

const someCancellableSelected = computed(
  () => selectedInviteCount.value > 0 && !allCancellableSelected.value,
)

const toggleAllCancellableInvites = () => {
  selectedInviteIds.value = nextSelection(
    cancellableInvites.value,
    selectedInviteIds.value,
    allCancellableSelected.value,
  )
}

const handleBulkCancelInvites = () => {
  const ids = [...selectedInviteIds.value]
  if (ids.length === 0) return
  ids.forEach((id) => teamStore.cancelInvite(id))
  notification.success(t('team.bulkCancelInvitesDone', { count: ids.length }))
  clearInviteSelection()
}

const roleStats = computed(() => {
  return [
    {
      role: 'owner',
      count: teamStore.membersByRole('owner').length,
      label: t('team.roles.owner'),
      color: 'text-info-strong',
    },
    {
      role: 'admin',
      count: teamStore.membersByRole('admin').length,
      label: t('team.roles.admin'),
      color: 'text-info-strong',
    },
    {
      role: 'editor',
      count: teamStore.membersByRole('editor').length,
      label: t('team.roles.editor'),
      color: 'text-success-strong',
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
  cancelInviteTargetId.value = inviteId
  showCancelInviteModal.value = true
}

const confirmCancelInvite = () => {
  const inviteId = cancelInviteTargetId.value
  cancelInviteTargetId.value = null
  if (inviteId === null) return
  teamStore.cancelInvite(inviteId)
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
    <div class="page-grid page-grid--metrics mb-6">
      <div class="card">
        <div class="flex items-center">
          <UserGroupIcon class="h-8 w-8 text-primary-600 dark:text-primary-400" />
          <div class="ml-3">
            <p class="text-body text-gray-600 dark:text-gray-400">{{ $t('team.stats.totalMembers') }}</p>
            <p class="text-h1 font-semibold text-gray-900 dark:text-gray-100">
              {{ teamStore.members.length }}
            </p>
          </div>
        </div>
      </div>
      <div class="card">
        <div class="flex items-center">
          <div
            class="flex h-8 w-8 items-center justify-center rounded-full bg-success-subtle"
          >
            <span class="h-3 w-3 rounded-full bg-success"></span>
          </div>
          <div class="ml-3">
            <p class="text-body text-gray-600 dark:text-gray-400">{{ $t('team.stats.online') }}</p>
            <p class="text-h1 font-semibold text-gray-900 dark:text-gray-100">
              {{ teamStore.onlineMembers.length }}
            </p>
          </div>
        </div>
      </div>
      <div class="card">
        <div class="flex items-center">
          <EnvelopeIcon class="h-8 w-8 text-info-strong" />
          <div class="ml-3">
            <p class="text-body text-gray-600 dark:text-gray-400">{{ $t('team.stats.pendingInvites') }}</p>
            <p class="text-h1 font-semibold text-gray-900 dark:text-gray-100">
              {{ inviteStatus.pending }}
            </p>
          </div>
        </div>
      </div>
      <div class="card">
        <div class="flex items-center">
          <ClockIcon class="h-8 w-8 text-info-strong" />
          <div class="ml-3">
            <p class="text-body text-gray-600 dark:text-gray-400">{{ $t('team.stats.recentActivity') }}</p>
            <p class="text-h1 font-semibold text-gray-900 dark:text-gray-100">
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
            <h3 class="text-body font-medium text-gray-900 dark:text-gray-100">
              {{ $t('team.roleDistribution') }}
            </h3>
            <div class="mt-4 grid grid-cols-2 gap-4 mobile:grid-cols-4">
              <div
                v-for="stat in roleStats"
                :key="stat.role"
                class="text-center"
              >
                <p :class="[stat.color, 'text-h1 font-semibold']">
                  {{ stat.count }}
                </p>
                <p class="text-body text-gray-600 dark:text-gray-400">
                  {{ stat.label }}
                </p>
              </div>
            </div>
          </div>

          <!-- 검색 · 정렬 · 일괄 작업 -->
          <div>
            <ListToolbar
              v-if="!isMemberSourceEmpty"
              v-model="memberQuery"
              v-model:sort-key="memberSortKey"
              v-model:sort-dir="memberSortDir"
              :sort-options="memberSortOptions"
              :selected-count="selectedMemberCount"
              :total-count="visibleMemberCount"
              :search-placeholder="$t('team.searchMembersPlaceholder')"
              :search-label="$t('team.searchMembersLabel')"
              @clear-selection="clearMemberSelection"
            >
              <template #bulk-actions>
                <button
                  type="button"
                  class="btn-danger inline-flex items-center gap-1.5"
                  :disabled="bulkWorking"
                  @click="showBulkRemoveModal = true"
                >
                  <UserMinusIcon class="h-4 w-4" aria-hidden="true" />
                  {{ $t('team.removeSelectedMembers') }}
                </button>
              </template>
            </ListToolbar>

            <!-- 전체 선택 — 소유자와 본인은 애초에 대상에서 빠진다 -->
            <div
              v-if="canManage && removableMembers.length > 0"
              class="mb-3 flex items-center gap-2"
            >
              <input
                id="team-members-select-all"
                type="checkbox"
                :class="CHECKBOX_CLASS"
                :checked="allRemovableSelected"
                :indeterminate="someRemovableSelected"
                @change="toggleAllRemovableMembers"
              />
              <label
                for="team-members-select-all"
                class="cursor-pointer text-body text-gray-500 dark:text-gray-400"
              >
                {{ $t('list.selectAll', { count: removableMembers.length }) }}
              </label>
            </div>

            <!-- 팀에 멤버가 아예 없을 때 -->
            <EmptyState
              v-if="isMemberSourceEmpty"
              :icon="UserGroupIcon"
              :title="$t('team.emptyMembersTitle')"
              :description="$t('team.emptyMembersDesc')"
              :action-label="canManage ? $t('team.inviteMember') : undefined"
              @action="showInviteModal = true"
            />

            <!-- 검색 결과만 없을 때 -->
            <EmptyState
              v-else-if="isMemberResultEmpty"
              :icon="MagnifyingGlassIcon"
              :title="$t('list.noResultsTitle')"
              :description="$t('list.noResultsDescription')"
              :action-label="$t('list.resetFilters')"
              @action="resetMemberFilters"
            />

            <!-- Members Grid -->
            <div v-else class="page-grid page-grid--cards">
              <div
                v-for="member in filteredMembers"
                :key="member.id"
                class="flex items-start gap-3"
              >
                <input
                  v-if="canManage"
                  type="checkbox"
                  :class="[CHECKBOX_CLASS, 'mt-6']"
                  :checked="isMemberSelected(member.id)"
                  :disabled="!isRemovableMember(member)"
                  :title="isRemovableMember(member) ? undefined : $t('team.memberNotRemovable')"
                  :aria-label="$t('list.selectItem', { name: member.name })"
                  @change="toggleMemberSelection(member.id)"
                />
                <TeamMemberCard
                  :member="member"
                  :can-manage="canManage"
                  class="min-w-0 flex-1"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- Invites Tab -->
        <div v-if="activeTab === 'invites'">
          <!-- 검색 · 정렬 · 일괄 작업 -->
          <ListToolbar
            v-if="!isInviteSourceEmpty"
            v-model="inviteQuery"
            v-model:sort-key="inviteSortKey"
            v-model:sort-dir="inviteSortDir"
            :sort-options="inviteSortOptions"
            :selected-count="selectedInviteCount"
            :total-count="visibleInviteCount"
            :search-placeholder="$t('team.searchInvitesPlaceholder')"
            :search-label="$t('team.searchInvitesLabel')"
            @clear-selection="clearInviteSelection"
          >
            <template #bulk-actions>
              <button
                type="button"
                class="btn-danger inline-flex items-center gap-1.5"
                @click="showBulkCancelModal = true"
              >
                <XCircleIcon class="h-4 w-4" aria-hidden="true" />
                {{ $t('team.cancelSelectedInvites') }}
              </button>
            </template>
          </ListToolbar>

          <!-- 전체 선택 — 취소할 수 있는 "대기 중" 초대만 대상이다 -->
          <div
            v-if="canManage && cancellableInvites.length > 0"
            class="mb-3 flex items-center gap-2"
          >
            <input
              id="team-invites-select-all"
              type="checkbox"
              :class="CHECKBOX_CLASS"
              :checked="allCancellableSelected"
              :indeterminate="someCancellableSelected"
              @change="toggleAllCancellableInvites"
            />
            <label
              for="team-invites-select-all"
              class="cursor-pointer text-body text-gray-500 dark:text-gray-400"
            >
              {{ $t('list.selectAll', { count: cancellableInvites.length }) }}
            </label>
          </div>

          <!-- 초대가 아예 없을 때 -->
          <EmptyState
            v-if="isInviteSourceEmpty"
            :icon="EnvelopeIcon"
            :title="$t('team.noInvites')"
            :description="$t('team.noInvitesDescription')"
            :action-label="canManage ? $t('team.inviteMember') : undefined"
            @action="showInviteModal = true"
          />

          <!-- 검색 결과만 없을 때 -->
          <EmptyState
            v-else-if="isInviteResultEmpty"
            :icon="MagnifyingGlassIcon"
            :title="$t('list.noResultsTitle')"
            :description="$t('list.noResultsDescription')"
            :action-label="$t('list.resetFilters')"
            @action="resetInviteFilters"
          />

          <div v-else class="space-y-4">
            <div
              v-for="invite in filteredInvites"
              :key="invite.id"
              class="card"
            >
              <div class="flex items-start justify-between gap-3">
                <input
                  v-if="canManage"
                  type="checkbox"
                  :class="[CHECKBOX_CLASS, 'mt-1']"
                  :checked="isInviteSelected(invite.id)"
                  :disabled="!isCancellableInvite(invite)"
                  :title="isCancellableInvite(invite) ? undefined : $t('team.inviteNotCancellable')"
                  :aria-label="$t('list.selectItem', { name: invite.email })"
                  @change="toggleInviteSelection(invite.id)"
                />
                <div class="flex-1">
                  <div class="flex items-center space-x-3">
                    <p class="text-body font-medium text-gray-900 dark:text-gray-100">
                      {{ invite.email }}
                    </p>
                    <RoleBadge :role="invite.role" />
                    <span
                      v-if="invite.status === 'pending'"
                      class="inline-flex items-center rounded-full bg-warning-subtle px-2.5 py-0.5 text-body-xs font-medium text-warning-strong"
                    >
                      <ClockIcon class="mr-1 h-3 w-3" />
                      {{ $t('team.inviteStatus.pending') }}
                    </span>
                    <span
                      v-else-if="invite.status === 'expired'"
                      class="inline-flex items-center rounded-full bg-error-subtle px-2.5 py-0.5 text-body-xs font-medium text-error-strong"
                    >
                      <XCircleIcon class="mr-1 h-3 w-3" />
                      {{ $t('team.inviteStatus.expired') }}
                    </span>
                    <span
                      v-else
                      class="inline-flex items-center rounded-full bg-success-subtle px-2.5 py-0.5 text-body-xs font-medium text-success-strong"
                    >
                      <CheckCircleIcon class="mr-1 h-3 w-3" />
                      {{ $t('team.inviteStatus.accepted') }}
                    </span>
                  </div>
                  <div class="mt-2 flex items-center space-x-4 text-body text-gray-500 dark:text-gray-400">
                    <span>{{ $t('team.invitedAt') }}: {{ relativeTime(invite.invitedAt) }}</span>
                    <span>•</span>
                    <span>{{ expiresIn(invite.expiresAt) }}</span>
                  </div>
                </div>

                <div v-if="canManage && invite.status === 'pending'" class="flex space-x-2">
                  <button
                    class="rounded-md p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
                    :title="$t('team.resendInvite')"
                    @click="handleResendInvite(invite.id)"
                  >
                    <ArrowPathIcon class="h-5 w-5" />
                  </button>
                  <button
                    class="rounded-md p-2 text-gray-400 hover:bg-error-subtle hover:text-error-strong"
                    :title="$t('team.cancelInvite')"
                    @click="handleCancelInvite(invite.id)"
                  >
                    <XCircleIcon class="h-5 w-5" />
                  </button>
                </div>
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
            <h3 class="mb-4 text-title font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('team.workflowBoard') }}
            </h3>
            <WorkflowBoard />
          </div>

          <!-- My Tasks & Pending Reviews -->
          <div class="page-grid page-grid--split">
            <!-- 내 작업 -->
            <div class="card">
              <h3 class="mb-4 text-body-lg font-semibold text-gray-900 dark:text-gray-100">
                {{ $t('team.myTasks') }}
              </h3>
              <div v-if="approvalStore.myTasks">
                <div v-if="approvalStore.myTasks.assignedToMe.length > 0" class="mb-4">
                  <p class="mb-2 text-body-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    {{ $t('team.assignedToMe') }}
                  </p>
                  <div class="space-y-2">
                    <div
                      v-for="task in approvalStore.myTasks.assignedToMe"
                      :key="task.approvalId"
                      class="flex items-center justify-between rounded-md border border-gray-100 p-3 dark:border-gray-700"
                    >
                      <div class="min-w-0 flex-1">
                        <p class="truncate text-body font-medium text-gray-900 dark:text-gray-100">{{ task.videoTitle }}</p>
                        <p class="text-body-xs text-gray-500 dark:text-gray-400">{{ task.requesterName }}</p>
                      </div>
                      <span
                        class="ml-2 inline-flex rounded-full px-2 py-0.5 text-[10px] font-medium"
                        :class="{
                          'bg-warning-subtle text-warning-strong': task.status === 'PENDING',
                          'bg-success-subtle text-success-strong': task.status === 'APPROVED',
                          'bg-error-subtle text-error-strong': task.status === 'REJECTED',
                          'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300': !['PENDING', 'APPROVED', 'REJECTED'].includes(task.status),
                        }"
                      >
                        {{ task.status }}
                      </span>
                    </div>
                  </div>
                </div>
                <div v-if="approvalStore.myTasks.requestedByMe.length > 0">
                  <p class="mb-2 text-body-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    {{ $t('team.requestedByMe') }}
                  </p>
                  <div class="space-y-2">
                    <div
                      v-for="task in approvalStore.myTasks.requestedByMe"
                      :key="task.approvalId"
                      class="flex items-center justify-between rounded-md border border-gray-100 p-3 dark:border-gray-700"
                    >
                      <div class="min-w-0 flex-1">
                        <p class="truncate text-body font-medium text-gray-900 dark:text-gray-100">{{ task.videoTitle }}</p>
                        <p class="text-body-xs text-gray-500 dark:text-gray-400">{{ task.reviewerName ?? $t('team.noReviewer') }}</p>
                      </div>
                      <span
                        class="ml-2 inline-flex rounded-full px-2 py-0.5 text-[10px] font-medium"
                        :class="{
                          'bg-warning-subtle text-warning-strong': task.status === 'PENDING',
                          'bg-success-subtle text-success-strong': task.status === 'APPROVED',
                          'bg-error-subtle text-error-strong': task.status === 'REJECTED',
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
                  class="py-8 text-center text-body text-gray-400 dark:text-gray-500"
                >
                  {{ $t('team.noTasks') }}
                </div>
              </div>
              <div v-else class="py-8 text-center text-body text-gray-400 dark:text-gray-500">
                {{ $t('team.loading') }}
              </div>
            </div>

            <!-- 대기 중인 검토 -->
            <div class="card">
              <div class="mb-4 flex items-center justify-between">
                <h3 class="text-body-lg font-semibold text-gray-900 dark:text-gray-100">
                  {{ $t('team.pendingReviews') }}
                </h3>
                <span
                  v-if="approvalStore.pendingReviews && approvalStore.pendingReviews.overdueCount > 0"
                  class="inline-flex items-center rounded-full bg-error-subtle px-2.5 py-0.5 text-body-xs font-medium text-error-strong"
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
                      <p class="truncate text-body font-medium text-gray-900 dark:text-gray-100">{{ review.videoTitle }}</p>
                      <p class="text-body-xs text-gray-500 dark:text-gray-400">{{ review.requesterName }}</p>
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
                <div v-else class="py-8 text-center text-body text-gray-400 dark:text-gray-500">
                  {{ $t('team.noPendingReviews') }}
                </div>
              </div>
              <div v-else class="py-8 text-center text-body text-gray-400 dark:text-gray-500">
                {{ $t('team.loading') }}
              </div>
            </div>
          </div>
        </div>
      </div>

    <!-- Invite Member Modal -->
    <InviteMemberModal :show="showInviteModal" @close="showInviteModal = false" />

    <!-- 초대 취소 확인 -->
    <ConfirmModal
      v-model="showCancelInviteModal"
      :title="$t('team.cancelInvite')"
      :message="$t('team.confirmCancelInvite')"
      :confirm-text="$t('team.cancelInvite')"
      danger
      @confirm="confirmCancelInvite"
      @cancel="cancelInviteTargetId = null"
    />

    <!-- 선택 멤버 일괄 제거 확인 -->
    <ConfirmModal
      v-model="showBulkRemoveModal"
      :title="$t('team.bulkRemoveTitle')"
      :message="$t('team.bulkRemoveMessage', { count: selectedMemberCount })"
      :confirm-text="$t('team.removeSelectedMembers')"
      danger
      @confirm="handleBulkRemoveMembers"
    />

    <!-- 선택 초대 일괄 취소 확인 -->
    <ConfirmModal
      v-model="showBulkCancelModal"
      :title="$t('team.bulkCancelInvitesTitle')"
      :message="$t('team.bulkCancelInvitesMessage', { count: selectedInviteCount })"
      :confirm-text="$t('team.cancelSelectedInvites')"
      danger
      @confirm="handleBulkCancelInvites"
    />
  </div>
</template>
