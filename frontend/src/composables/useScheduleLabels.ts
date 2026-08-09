import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { RecurrenceConfig, ScheduleStatus } from '@/types/schedule'

/**
 * 예약(스케줄) 관련 i18n 라벨 헬퍼.
 * 캘린더/리스트/모달 컴포넌트가 공통으로 사용한다.
 */
export function useScheduleLabels() {
  const { t } = useI18n({ useScope: 'global' })

  const dayLabels = computed(() => (t('scheduleView.dayLabels') as string).split(','))

  function getStatusLabel(status: ScheduleStatus): string {
    const map: Record<ScheduleStatus, string> = {
      SCHEDULED: t('scheduleView.status.scheduled'),
      PROCESSING: t('scheduleView.status.processing'),
      PUBLISHED: t('scheduleView.status.published'),
      PARTIALLY_PUBLISHED: t('scheduleView.status.partiallyPublished'),
      UNCONFIRMED: t('scheduleView.status.unconfirmed'),
      FAILED: t('scheduleView.status.failed'),
      CANCELLED: t('scheduleView.status.cancelled'),
    }
    return map[status]
  }

  function formatRecurrence(recurrence: RecurrenceConfig): string {
    const typeLabels: Record<string, string> = {
      DAILY: t('scheduleView.recurrence.daily'),
      WEEKLY: t('scheduleView.recurrence.weekly'),
      MONTHLY: t('scheduleView.recurrence.monthly'),
      INTERVAL: 'N일마다',
    }
    const unitMap: Record<string, string> = {
      DAILY: t('scheduleView.recurrence.dayUnit'),
      WEEKLY: t('scheduleView.recurrence.weekUnit'),
      MONTHLY: t('scheduleView.recurrence.monthUnit'),
      INTERVAL: '일',
    }

    const base =
      recurrence.interval > 1
        ? t('scheduleView.recurrence.every', {
            interval: recurrence.interval,
            unit: unitMap[recurrence.type] ?? '',
          })
        : (typeLabels[recurrence.type] ?? recurrence.type)

    if (recurrence.type === 'WEEKLY' && recurrence.daysOfWeek?.length) {
      const days = recurrence.daysOfWeek.map((d) => dayLabels.value[d]).join(', ')
      return `${base} (${days})`
    }
    return base
  }

  return { dayLabels, getStatusLabel, formatRecurrence }
}
