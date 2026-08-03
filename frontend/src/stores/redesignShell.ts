import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

/**
 * 리디자인 셸의 공용 상태 — 레일 배지, 상단바 제목, 이번 달 업로드 쿼터.
 *
 * 배지는 "무엇이 막혀 있는지"를 상시 노출하기 위한 것이다(핸드오프의 세 번째 목표:
 * 실패를 숨기지 않기). 값이 0이면 배지를 그리지 않는다.
 */
export const useRedesignShellStore = defineStore('redesignShell', () => {
  const todayQueueCount = ref(0)
  const unansweredCount = ref(0)
  const scheduledCount = ref(0)
  const channelErrorCount = ref(0)

  const uploadQuota = ref({ used: 0, limit: 0 })

  const loading = ref(false)

  /** 레일에 표시할 배지 문자열. 0이면 빈 문자열로 두어 렌더하지 않는다. */
  const badges = computed(() => ({
    today: todayQueueCount.value ? String(todayQueueCount.value) : '',
    inbox: unansweredCount.value ? String(unansweredCount.value) : '',
    calendar: scheduledCount.value ? String(scheduledCount.value) : '',
    // 채널은 개수가 아니라 이상 신호다. 하나라도 있으면 느낌표.
    channels: channelErrorCount.value ? '!' : '',
  }))

  function setCounts(next: {
    todayQueue?: number
    unanswered?: number
    scheduled?: number
    channelErrors?: number
  }) {
    if (next.todayQueue !== undefined) todayQueueCount.value = next.todayQueue
    if (next.unanswered !== undefined) unansweredCount.value = next.unanswered
    if (next.scheduled !== undefined) scheduledCount.value = next.scheduled
    if (next.channelErrors !== undefined) channelErrorCount.value = next.channelErrors
  }

  function setUploadQuota(used: number, limit: number) {
    uploadQuota.value = { used, limit }
  }

  return {
    todayQueueCount,
    unansweredCount,
    scheduledCount,
    channelErrorCount,
    uploadQuota,
    loading,
    badges,
    setCounts,
    setUploadQuota,
  }
})
