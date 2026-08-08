/** 서버가 반환하는 최적 게시 시간의 최소 계약. */
export interface OptimalScheduleSlot {
  dayOfWeek: number
  hour: number
  score?: number
}

/**
 * 분석 슬롯을 다음 실행 가능한 로컬 wall-clock 시각으로 변환한다.
 *
 * dayOfWeek는 백엔드와 JavaScript 모두 일요일=0 규칙을 사용한다. 날짜는
 * UTC instant로 보내지 않고 LocalDateTime 문자열로 직렬화해야 한다.
 */
export function nextOptimalDateTime(
  now: Date,
  slot: OptimalScheduleSlot,
  minimumLeadMinutes = 5,
  timeZone = 'Asia/Seoul',
): string {
  const nowParts = zonedParts(now, timeZone)
  const targetDay = clampInteger(slot.dayOfWeek, 0, 6, 0)
  const currentDay = new Date(Date.UTC(nowParts.year, nowParts.month - 1, nowParts.day)).getUTCDay()
  const daysUntil = (targetDay - currentDay + 7) % 7
  const candidate = new Date(Date.UTC(nowParts.year, nowParts.month - 1, nowParts.day + daysUntil,
    clampInteger(slot.hour, 0, 23, 0), 0, 0))

  const earliest = Date.UTC(nowParts.year, nowParts.month - 1, nowParts.day,
    nowParts.hour, nowParts.minute + Math.max(0, minimumLeadMinutes), 0)
  if (candidate.getTime() <= earliest) candidate.setUTCDate(candidate.getUTCDate() + 7)
  return formatParts({
    year: candidate.getUTCFullYear(),
    month: candidate.getUTCMonth() + 1,
    day: candidate.getUTCDate(),
    hour: candidate.getUTCHours(),
    minute: candidate.getUTCMinutes(),
  })
}

/** 분석 이력이 없는 새 채널을 위한 명시적인 일반 기본 슬롯. */
export function fallbackOptimalSlot(index: number, now = new Date()): OptimalScheduleSlot {
  const hours = [9, 12, 19]
  const hour = hours[Math.abs(index) % hours.length]
  const parts = zonedParts(now, 'Asia/Seoul')
  return {
    dayOfWeek: new Date(Date.UTC(parts.year, parts.month - 1, parts.day)).getUTCDay(),
    hour,
  }
}

/** 백엔드 쇼츠 API가 요구하는 KST wall-clock 문자열을 Instant로 변환한다. */
export function kstWallClockToInstant(value: string): Date {
  const normalized = value.length === 16 ? `${value}:00` : value
  return new Date(`${normalized}+09:00`)
}

function zonedParts(date: Date, timeZone: string) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date)
  const value = (type: string) => Number(parts.find((part) => part.type === type)?.value ?? 0)
  return { year: value('year'), month: value('month'), day: value('day'), hour: value('hour'), minute: value('minute') }
}

function formatParts(parts: { year: number; month: number; day: number; hour: number; minute: number }) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${parts.year}-${pad(parts.month)}-${pad(parts.day)}T${pad(parts.hour)}:${pad(parts.minute)}`
}

function clampInteger(value: number, minimum: number, maximum: number, fallback: number) {
  if (!Number.isFinite(value)) return fallback
  return Math.min(maximum, Math.max(minimum, Math.trunc(value)))
}
