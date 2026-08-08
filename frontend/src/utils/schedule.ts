import type { Schedule, ScheduleStatus } from '@/types/schedule'
import { PLATFORM_CONFIG, type Platform } from '@/types/channel'

/** Date → 'YYYY-MM-DD' (로컬 타임존 기준) */
export function toDateStr(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** Date → 'YYYY-MM-DDTHH:mm' (input[type=datetime-local] 용) */
export function toDateTimeLocal(date: Date): string {
  const y = date.getFullYear()
  const mo = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const mi = String(date.getMinutes()).padStart(2, '0')
  return `${y}-${mo}-${d}T${h}:${mi}`
}

/** ISO 문자열 → 'YYYY.MM.DD' */
export function formatScheduleDate(dateStr: string): string {
  const d = new Date(dateStr)
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`
}

/** ISO 문자열 → 'HH:mm' */
export function formatScheduleTime(dateStr: string): string {
  const d = new Date(dateStr)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** 0~23 → 'HH:00' */
export function formatHourLabel(hour: number): string {
  return `${String(hour).padStart(2, '0')}:00`
}

/** 해당 날짜가 속한 주의 일요일 00:00 */
export function getWeekStart(date: Date): Date {
  const d = new Date(date)
  d.setDate(d.getDate() - d.getDay())
  d.setHours(0, 0, 0, 0)
  return d
}

export function getPlatformColor(platform: Platform): string {
  return PLATFORM_CONFIG[platform]?.color ?? '#6B7280'
}

export function getScheduleBgColor(schedule: Schedule): string {
  if (schedule.platforms.length === 0) return '#F3F4F6'
  return getPlatformColor(schedule.platforms[0].platform) + '15'
}

export function getScheduleBorderColor(schedule: Schedule): string {
  if (schedule.platforms.length === 0) return '#D1D5DB'
  return getPlatformColor(schedule.platforms[0].platform)
}

const STATUS_BADGE_CLASS: Record<ScheduleStatus, string> = {
  SCHEDULED: 'badge-blue',
  PROCESSING: 'badge-warning',
  PUBLISHED: 'badge-success',
  PARTIALLY_PUBLISHED: 'badge-warning',
  UNCONFIRMED: 'badge-warning',
  FAILED: 'badge-danger',
  CANCELLED: 'badge-gray',
}

export function getStatusBadgeClass(status: ScheduleStatus): string {
  return `badge ${STATUS_BADGE_CLASS[status]}`
}

/** 반복 예약 여부 */
export function isRecurringSchedule(schedule: Schedule): boolean {
  return !!schedule.recurrence && schedule.recurrence.type !== 'NONE'
}
