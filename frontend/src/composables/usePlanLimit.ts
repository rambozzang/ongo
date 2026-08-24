/**
 * 서버가 내려준 **안정 코드**를 읽고 대조한다.
 *
 * 서버는 거절 사유를 두 조각으로 나눠 보낸다 — 사람이 읽을 문장은 `ResData.message`(이미
 * client.ts 가 `error.message` 로 올려준다), 기계가 판단할 값은 `ResData.error` 의 안정 코드다.
 * 판단은 **코드로만** 한다. 문구는 번역되거나 다듬어지므로 문자열로 분기하면 조용히 깨진다.
 *
 * 판별을 한 곳에 모으되 **어떤 코드를 받아들일지는 화면이 정한다.** 화면마다 문맥이 다르기
 * 때문이다 — 채널 연결 실패 화면은 채널 한도 문구를 보여주므로 저장 공간 오류까지 받아들이면
 * 엉뚱한 안내가 붙는다. 공통 유틸이 "업그레이드로 풀리는 모든 것"을 한 덩어리로 판단하면
 * 그 불일치가 조용히 생긴다.
 */

/** 플랜 한도(채널 수·월 업로드·예약 등)를 넘겨 막힌 경우. */
export const PLAN_LIMIT_EXCEEDED = 'PLAN_LIMIT_EXCEEDED'

/** 저장 공간 한도를 넘겨 막힌 경우. */
export const STORAGE_QUOTA_EXCEEDED = 'STORAGE_QUOTA_EXCEEDED'

/**
 * 쇼츠 실행을 **시작하기도 전에** 완주 크레딧이 모자란 경우.
 *
 * 일반 `CREDIT_INSUFFICIENT` 와 구분되는 코드다. 그쪽은 작업 도중 어느 시점에서든 날 수
 * 있고 원인도 여러 가지라 결제를 권할 자리가 아니지만, 이 코드는 서버가 "이 요청은 지금
 * 잔액으로 절대 완주할 수 없다"고 생성 전에 판정한 경우다. 그때는 업그레이드가 실제 해법이다.
 */
export const SHORTS_INSUFFICIENT_CREDIT_FOR_RUN = 'SHORTS_INSUFFICIENT_CREDIT_FOR_RUN'

/** 서버가 내려준 안정 코드. 없거나 형태가 다르면 null. */
export function readStableCode(error: unknown): string | null {
  const code = (error as { response?: { data?: { error?: unknown } } })?.response?.data?.error
  return typeof code === 'string' && code ? code : null
}

/**
 * 이 오류가 호출부가 허용한 코드 중 하나인지.
 *
 * 허용 목록을 인자로 받는 이유는 오탐을 막기 위해서다. 크레딧 부족·인증 실패·검증 오류처럼
 * **돈을 내도 풀리지 않는 문제**에 결제를 권하면 사용자를 오도하는 것이고, 한 번 그러면
 * 정작 필요한 순간의 안내도 믿지 않게 된다.
 */
export function matchesCode(error: unknown, ...allowed: string[]): boolean {
  const code = readStableCode(error)
  return code != null && allowed.includes(code)
}

/** 업그레이드 안내가 향하는 곳. 화면마다 다른 경로를 쓰지 않도록 여기서 정한다. */
export const PLAN_UPGRADE_PATH = '/subscription'
