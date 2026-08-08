const KST = 'Asia/Seoul'

/** 서버의 KST LocalDateTime 계약에 맞는 오늘 날짜. */
export function kstDateString(date = new Date()): string {
  return formatKstParts(date).slice(0, 10)
}

/** KST LocalDateTime 응답을 브라우저 시간대와 무관한 Date로 읽는다. */
export function parseKstLocalDateTime(value: string): Date {
  const normalized = value.length === 16 ? `${value}:00` : value
  return new Date(`${normalized}+09:00`)
}

/** 서버 LocalDateTime의 시각만 표시한다. */
export function kstTimeString(value: string): string {
  return value.slice(11, 16)
}

function formatKstParts(date: Date): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: KST,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date)
  const value = (type: string) => parts.find((part) => part.type === type)?.value ?? '00'
  return `${value('year')}-${value('month')}-${value('day')}`
}
