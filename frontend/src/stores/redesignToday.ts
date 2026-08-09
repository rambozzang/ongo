import { defineStore } from 'pinia'
import { ref } from 'vue'
import { channelApi } from '@/api/channel'
import { scheduleApi } from '@/api/schedule'
import type { Channel, Platform, TokenStatus } from '@/types/channel'
import type { Schedule, ScheduleStatus } from '@/types/schedule'
import { useRedesignShellStore } from './redesignShell'
import { kstDateString, kstTimeString } from '@/utils/kst'

type PillVariant = 'success' | 'warning' | 'error' | 'muted'
type ChipCode = 'YT' | 'IG' | 'TT' | 'FB' | 'NV' | 'TH' | 'TW'

/** Platform → 플랫폼 칩 코드. 칩이 없는 플랫폼은 가장 가까운 중립 칩(TH)으로 떨어뜨린다. */
const CHIP: Partial<Record<Platform, ChipCode>> = {
  YOUTUBE: 'YT',
  INSTAGRAM: 'IG',
  TIKTOK: 'TT',
  FACEBOOK: 'FB',
  NAVER_CLIP: 'NV',
  THREADS: 'TH',
  TWITTER: 'TW',
}
const toChip = (p: Platform): ChipCode => CHIP[p] ?? 'TH'

const SCHEDULE_STATUS: Record<ScheduleStatus, { label: string; variant: PillVariant }> = {
  SCHEDULED: { label: '예약 완료', variant: 'success' },
  PROCESSING: { label: '발행 중', variant: 'warning' },
  PUBLISHED: { label: '발행 완료', variant: 'success' },
  PARTIALLY_PUBLISHED: { label: '일부 발행', variant: 'warning' },
  UNCONFIRMED: { label: '확인 필요', variant: 'warning' },
  FAILED: { label: '발행 실패', variant: 'error' },
  CANCELLED: { label: '취소됨', variant: 'muted' },
}

const TOKEN_STATUS: Record<TokenStatus, { label: string; variant: PillVariant }> = {
  ACTIVE: { label: '정상', variant: 'success' },
  EXPIRING_SOON: { label: '만료 임박', variant: 'warning' },
  EXPIRED: { label: '토큰 만료', variant: 'error' },
  DISCONNECTED: { label: '연결 끊김', variant: 'error' },
}

export interface QueueRow {
  id: number
  videoId: number | null
  time: string
  title: string
  thumbnailUrl: string | null
  duration: string | null
  platforms: ChipCode[]
  meta: string
  statusLabel: string
  statusVariant: PillVariant
}

export interface AttentionItem {
  id: string
  severity: 'error' | 'warning' | 'info'
  message: string
  meta: string
  cta: string
  to: string
}

/**
 * 오늘 화면 데이터.
 *
 * 실패를 숨기지 않는 것이 이 화면의 핵심이라, 토큰 만료·발행 실패는 반드시 '확인 필요'로
 * 올라온다. 아직 API 가 없는 지표(어제 조회수 등)는 0/빈 문자열로 두고 화면에서 빈 상태로 처리한다.
 */
export const useRedesignTodayStore = defineStore('redesignToday', () => {
  const loading = ref(false)
  const loadError = ref<'loadFailed' | 'loadPartial' | null>(null)
  const queue = ref<QueueRow[]>([])
  const attention = ref<AttentionItem[]>([])
  const channels = ref<
    { id: number; platform: ChipCode; name: string; sub: string; statusLabel: string; statusVariant: PillVariant }[]
  >([])

  const kpi = ref({
    scheduled: 0,
    pending: 0,
    failed: 0,
    unanswered: 0,
    unansweredDelta: '',
    avgResponse: '',
    viewsLabel: '—',
    viewsDelta: '',
    shortsShare: '',
    weeklyPublished: 0,
    weeklyGoal: 0,
  })

  function toQueueRow(s: Schedule): QueueRow {
    const status = SCHEDULE_STATUS[s.status] ?? SCHEDULE_STATUS.SCHEDULED
    return {
      id: s.id,
      videoId: s.videoId ?? null,
      time: kstTimeString(s.scheduledAt),
      title: s.videoTitle,
      thumbnailUrl: s.thumbnailUrl,
      duration: null,
      platforms: (s.platforms ?? []).map((p) => toChip(p.platform)),
      meta: '',
      statusLabel: status.label,
      statusVariant: status.variant,
    }
  }

  function buildAttention(scheduleList: Schedule[], channelList: Channel[]): AttentionItem[] {
    const out: AttentionItem[] = []

    // 토큰이 끊긴 채널은 예약이 조용히 밀리므로 가장 먼저 올린다
    for (const ch of channelList) {
      if (ch.tokenStatus === 'EXPIRED' || ch.tokenStatus === 'DISCONNECTED') {
        out.push({
          id: `token-${ch.id}`,
          severity: 'error',
          message: `${ch.channelName} 연결이 만료되었습니다`,
          meta: '재연결하면 대기 중인 예약이 자동으로 발행됩니다',
          cta: '재연결',
          to: '/channels-v2',
        })
      } else if (ch.tokenStatus === 'EXPIRING_SOON') {
        out.push({
          id: `token-soon-${ch.id}`,
          severity: 'warning',
          message: `${ch.channelName} 연결이 곧 만료됩니다`,
          meta: '미리 재연결해 두면 발행이 중단되지 않습니다',
          cta: '확인',
          to: '/channels-v2',
        })
      }
    }

    const attentionStatuses = scheduleList.filter(
      (s) => s.status === 'UNCONFIRMED' || s.status === 'PARTIALLY_PUBLISHED',
    )
    for (const schedule of attentionStatuses) {
      const isPartial = schedule.status === 'PARTIALLY_PUBLISHED'
      out.push({
        id: `publish-${schedule.id}`,
        severity: 'warning',
        message: isPartial
          ? `${schedule.videoTitle} 일부 채널만 발행되었습니다`
          : `${schedule.videoTitle} 게시 결과 확인이 필요합니다`,
        meta: isPartial
          ? '성공한 채널은 유지되고 실패한 채널만 상세 화면에서 확인할 수 있습니다'
          : '중복 게시를 막기 위해 자동 재전송하지 않았습니다',
        cta: '상세 확인',
        to: schedule.videoId ? `/videos/${schedule.videoId}` : '/calendar-v2',
      })
    }

    const failed = scheduleList.filter((s) => s.status === 'FAILED')
    if (failed.length > 0) {
      out.push({
        id: 'failed',
        severity: 'error',
        message: `발행 실패 ${failed.length}건`,
        meta: '자동 재시도 후에도 실패한 항목입니다',
        cta: '검토',
        to: '/calendar-v2',
      })
    }

    return out
  }

  async function load() {
    loading.value = true
    loadError.value = null
    try {
      const day = kstDateString()

      const [scheduleRes, channelRes, unreadRes] = await Promise.allSettled([
        scheduleApi.list({ startDate: day, endDate: day }),
        channelApi.list(),
        // KPI와 레일 배지를 같은 서버 값으로 맞춘다. 댓글 목록을 다시 내려받지 않는다.
        import('@/api/inbox').then(({ inboxApi }) => inboxApi.getUnreadCount()),
      ])

      const scheduleList: Schedule[] | null = scheduleRes.status === 'fulfilled' ? (scheduleRes.value ?? []) : null
      // channelApi.list() 는 배열이 아니라 { channels, maxAllowed, currentCount } 를 돌려준다
      const channelList: Channel[] | null =
        channelRes.status === 'fulfilled' ? (channelRes.value?.channels ?? []) : null
      const unreadCount = unreadRes.status === 'fulfilled' ? unreadRes.value.count : kpi.value.unanswered
      const rejectedCount = [scheduleRes, channelRes, unreadRes].filter((result) => result.status === 'rejected').length
      loadError.value = rejectedCount === 3 ? 'loadFailed' : rejectedCount > 0 ? 'loadPartial' : null

      // 부분 실패 시 마지막 정상 데이터를 유지한다. 실패를 빈 상태로 바꾸면
      // 예약/채널이 사라진 것으로 오인해 중복 예약이나 잘못된 재연결을 유발할 수 있다.
      if (scheduleList) {
        queue.value = [...scheduleList]
          .sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt))
          .map(toQueueRow)
        if (channelList) attention.value = buildAttention(scheduleList, channelList)
        kpi.value = {
          ...kpi.value,
          scheduled: scheduleList.filter((s) => s.status === 'SCHEDULED').length,
          pending: scheduleList.filter((s) => s.status === 'PROCESSING').length,
          failed: scheduleList.filter((s) => s.status === 'FAILED').length,
        }
      }

      if (channelList) {
        channels.value = channelList.map((ch) => {
          const status = TOKEN_STATUS[ch.tokenStatus] ?? TOKEN_STATUS.ACTIVE
          return {
            id: ch.id,
            platform: toChip(ch.platform),
            name: ch.channelName,
            sub: ch.subscriberCount ? `구독자 ${new Intl.NumberFormat('ko-KR').format(ch.subscriberCount)}` : '',
            statusLabel: status.label,
            statusVariant: status.variant,
          }
        })
      }

      if (unreadRes.status === 'fulfilled') {
        kpi.value = { ...kpi.value, unanswered: unreadCount }
      }

      // 레일 배지에 반영 — 막힌 것이 어디에 있는지 항상 보이게 한다
      const shell = useRedesignShellStore()
      shell.setCounts({
        todayQueue: queue.value.length,
        unanswered: kpi.value.unanswered,
        scheduled: kpi.value.scheduled,
        channelErrors: (channelList ?? []).filter(
          (c) => c.tokenStatus === 'EXPIRED' || c.tokenStatus === 'DISCONNECTED',
        ).length,
      })
    } finally {
      loading.value = false
    }
  }

  return { loading, loadError, queue, attention, channels, kpi, load }
})
