import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  ApprovalRequest,
  ApprovalComment,
  ApprovalStats,
  ApprovalStatus,
} from '@/types/approval'
import { approvalApi } from '@/api/approval'
import { useNotificationStore } from '@/stores/notification'
import type { ApprovalResponse, ApprovalCommentResponse } from '@/api/approval'

function mapApiApproval(a: ApprovalResponse): ApprovalRequest {
  return {
    id: String(a.id),
    videoId: a.videoId,
    videoTitle: a.videoTitle,
    platforms: a.platforms ? a.platforms.split(',').map((p) => p.trim()) : [],
    scheduledAt: a.scheduledAt ?? undefined,
    requesterId: String(a.requesterId),
    requesterName: a.requesterName,
    reviewerId: a.reviewerId != null ? String(a.reviewerId) : undefined,
    reviewerName: a.reviewerName ?? undefined,
    status: a.status as ApprovalStatus,
    comment: a.comment ?? undefined,
    revisionNote: a.revisionNote ?? undefined,
    requestedAt: a.requestedAt,
    decidedAt: a.decidedAt ?? undefined,
  }
}

function mapApiComment(c: ApprovalCommentResponse): ApprovalComment {
  return {
    id: String(c.id),
    approvalId: String(c.approvalId),
    userId: String(c.userId),
    userName: c.userName,
    content: c.content,
    field: c.field ?? undefined,
    createdAt: c.createdAt ?? new Date().toISOString(),
  }
}

export const useApprovalStore = defineStore('approval', () => {
  const requests = ref<ApprovalRequest[]>([])
  const comments = ref<ApprovalComment[]>([])
  const loading = ref(false)

  // Getters
  const pendingRequests = computed(() =>
    requests.value.filter((r) => r.status === 'pending'),
  )

  const myRequests = computed(() =>
    requests.value.filter((r) => r.requesterId === String(currentUserId())),
  )

  const stats = computed<ApprovalStats>(() => {
    const pending = requests.value.filter((r) => r.status === 'pending').length
    const approved = requests.value.filter((r) => r.status === 'approved').length
    const rejected = requests.value.filter((r) => r.status === 'rejected').length
    const revisionRequested = requests.value.filter(
      (r) => r.status === 'revision_requested',
    ).length

    const decidedRequests = requests.value.filter((r) => r.decidedAt)
    let avgMs = 0
    if (decidedRequests.length > 0) {
      const totalMs = decidedRequests.reduce((sum, r) => {
        const requested = new Date(r.requestedAt).getTime()
        const decided = new Date(r.decidedAt!).getTime()
        return sum + (decided - requested)
      }, 0)
      avgMs = totalMs / decidedRequests.length
    }

    const avgHours = Math.floor(avgMs / (1000 * 60 * 60))
    const avgMinutes = Math.floor((avgMs % (1000 * 60 * 60)) / (1000 * 60))
    const avgResponseTime =
      avgHours > 0
        ? `${avgHours}시간 ${avgMinutes}분`
        : avgMinutes > 0
          ? `${avgMinutes}분`
          : '-'

    return {
      pending,
      approved,
      rejected,
      revisionRequested,
      avgResponseTime,
    }
  })

  function currentUserId(): string {
    // Extract from auth store or localStorage
    try {
      const token = localStorage.getItem('accessToken')
      if (token) {
        const payload = JSON.parse(atob(token.split('.')[1]))
        return String(payload.sub || payload.userId || '')
      }
    } catch {
      // ignore
    }
    return ''
  }

  function getRequestById(id: string): ApprovalRequest | undefined {
    return requests.value.find((r) => r.id === id)
  }

  function getCommentsByApprovalId(approvalId: string): ApprovalComment[] {
    return comments.value.filter((c) => c.approvalId === approvalId)
  }

  function getFilteredRequests(
    tab: 'pending_review' | 'my_requests' | 'all',
    statusFilter?: ApprovalStatus,
  ): ApprovalRequest[] {
    let filtered: ApprovalRequest[]
    switch (tab) {
      case 'pending_review':
        filtered = requests.value.filter((r) => r.status === 'pending')
        break
      case 'my_requests':
        filtered = requests.value.filter(
          (r) => r.requesterId === String(currentUserId()),
        )
        break
      case 'all':
      default:
        filtered = [...requests.value]
        break
    }
    if (statusFilter) {
      filtered = filtered.filter((r) => r.status === statusFilter)
    }
    return filtered.sort(
      (a, b) =>
        new Date(b.requestedAt).getTime() - new Date(a.requestedAt).getTime(),
    )
  }

  // Actions
  async function fetchRequests(status?: string) {
    loading.value = true
    try {
      const data = await approvalApi.list(status)
      requests.value = data.approvals.map(mapApiApproval)
    } catch (e) {
      useNotificationStore().error('승인 처리 중 오류가 발생했습니다')
    } finally {
      loading.value = false
    }
  }

  async function submitForApproval(
    videoId: number,
    videoTitle: string,
    platforms: string[],
    scheduledAt?: string,
    comment?: string,
    reviewerId?: string,
    reviewerName?: string,
  ) {
    try {
      const result = await approvalApi.create({
        videoId,
        videoTitle,
        platforms: platforms.join(','),
        scheduledAt,
        reviewerId: reviewerId ? Number(reviewerId) : undefined,
        reviewerName,
        comment,
      })
      const mapped = mapApiApproval(result)
      requests.value.unshift(mapped)
    } catch (e) {
      useNotificationStore().error('승인 처리 중 오류가 발생했습니다')
      throw e
    }
  }

  async function approveRequest(id: string, comment?: string) {
    try {
      const result = await approvalApi.updateStatus(Number(id), {
        status: 'approved',
        comment,
      })
      const index = requests.value.findIndex((r) => r.id === id)
      if (index !== -1) {
        requests.value[index] = mapApiApproval(result)
      }
    } catch (e) {
      useNotificationStore().error('승인 처리 중 오류가 발생했습니다')
      throw e
    }
  }

  async function rejectRequest(id: string, comment: string) {
    try {
      const result = await approvalApi.updateStatus(Number(id), {
        status: 'rejected',
        revisionNote: comment,
      })
      const index = requests.value.findIndex((r) => r.id === id)
      if (index !== -1) {
        requests.value[index] = mapApiApproval(result)
      }
    } catch (e) {
      useNotificationStore().error('승인 처리 중 오류가 발생했습니다')
      throw e
    }
  }

  async function requestRevision(id: string, revisionNote: string) {
    try {
      const result = await approvalApi.updateStatus(Number(id), {
        status: 'revision_requested',
        revisionNote,
      })
      const index = requests.value.findIndex((r) => r.id === id)
      if (index !== -1) {
        requests.value[index] = mapApiApproval(result)
      }
    } catch (e) {
      useNotificationStore().error('승인 처리 중 오류가 발생했습니다')
      throw e
    }
  }

  async function resubmitRequest(id: string, comment?: string) {
    try {
      const result = await approvalApi.updateStatus(Number(id), {
        status: 'pending',
        comment,
      })
      const index = requests.value.findIndex((r) => r.id === id)
      if (index !== -1) {
        requests.value[index] = mapApiApproval(result)
      }
    } catch (e) {
      useNotificationStore().error('승인 처리 중 오류가 발생했습니다')
      throw e
    }
  }

  async function fetchComments(approvalId: string) {
    try {
      const data = await approvalApi.getComments(Number(approvalId))
      // Remove old comments for this approval and add fresh ones
      comments.value = comments.value.filter((c) => c.approvalId !== approvalId)
      comments.value.push(...data.map(mapApiComment))
    } catch (e) {
      useNotificationStore().error('승인 처리 중 오류가 발생했습니다')
    }
  }

  async function addComment(approvalId: string, content: string, field?: string) {
    try {
      const result = await approvalApi.addComment(Number(approvalId), {
        content,
        field,
      })
      comments.value.push(mapApiComment(result))
    } catch (e) {
      useNotificationStore().error('승인 처리 중 오류가 발생했습니다')
      throw e
    }
  }

  return {
    // State
    requests,
    comments,
    loading,

    // Getters
    pendingRequests,
    myRequests,
    stats,

    // Functions
    getRequestById,
    getCommentsByApprovalId,
    getFilteredRequests,

    // Actions
    fetchRequests,
    fetchComments,
    submitForApproval,
    approveRequest,
    rejectRequest,
    requestRevision,
    resubmitRequest,
    addComment,
  }
})
