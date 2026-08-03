import { defineStore } from 'pinia'
import { ref } from 'vue'
import { channelApi } from '@/api/channel'
import { scheduleApi } from '@/api/schedule'
import type { Channel, Platform, TokenStatus } from '@/types/channel'
import type { Schedule, ScheduleStatus } from '@/types/schedule'
import { useRedesignShellStore } from './redesignShell'

type PillVariant = 'success' | 'warning' | 'error' | 'muted'
type ChipCode = 'YT' | 'IG' | 'TT' | 'FB' | 'NV' | 'TH'

/** Platform → 플랫폼 칩 코드. 칩이 없는 플랫폼은 가장 가까운 중립 칩(TH)으로 떨어뜨린다. */
const CHIP: Partial<Record<Platform, ChipCode>> = {
  YOUTUBE: 'YT',
  INSTAGRAM: 'IG',
  TIKTOK: 'TT',
  FACEBOOK: 'FB',
  NAVER_CLIP: 'NV',
  THREADS: 'TH',
}
const toChip = (p: Platform): ChipCode => CHIP[p] ?? 'TH'

const SCHEDULE_STATUS: Record<ScheduleStatus, { label: string; variant: PillVariant }> = {
  SCHEDULED: { label: '예약 완료', variant: 'success' },
  PUBLISHED: { label: '발행 완료', variant: 'success' },
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
    const at = new Date(s.scheduledAt)
    const status = SCHEDULE_STATUS[s.status] ?? SCHEDULE_STATUS.SCHEDULED
    return {
      id: s.id,
      videoId: s.videoId ?? null,
      time: at.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false }),
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
    try {
      const today = new Date()
      const day = today.toISOString().slice(0, 10)

      const [scheduleRes, channelRes] = await Promise.allSettled([
        scheduleApi.list({ startDate: day, endDate: day }),
        channelApi.list(),
      ])

      const scheduleList: Schedule[] = scheduleRes.status === 'fulfilled' ? (scheduleRes.value ?? []) : []
      // channelApi.list() 는 배열이 아니라 { channels, maxAllowed, currentCount } 를 돌려준다
      const channelList: Channel[] =
        channelRes.status === 'fulfilled' ? (channelRes.value?.channels ?? []) : []

      queue.value = [...scheduleList]
        .sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt))
        .map(toQueueRow)

      attention.value = buildAttention(scheduleList, channelList)

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

      kpi.value = {
        ...kpi.value,
        scheduled: scheduleList.filter((s) => s.status === 'SCHEDULED').length,
        pending: scheduleList.filter((s) => s.status === 'SCHEDULED').length,
        failed: scheduleList.filter((s) => s.status === 'FAILED').length,
      }

      // 레일 배지에 반영 — 막힌 것이 어디에 있는지 항상 보이게 한다
      const shell = useRedesignShellStore()
      shell.setCounts({
        todayQueue: queue.value.length,
        channelErrors: channelList.filter(
          (c) => c.tokenStatus === 'EXPIRED' || c.tokenStatus === 'DISCONNECTED',
        ).length,
      })
    } finally {
      loading.value = false
    }
  }

  return { loading, queue, attention, channels, kpi, load }
})
