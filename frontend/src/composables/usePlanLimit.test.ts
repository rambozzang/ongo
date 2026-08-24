import { describe, expect, it } from 'vitest'
import {
  PLAN_LIMIT_EXCEEDED,
  PLAN_UPGRADE_PATH,
  STORAGE_QUOTA_EXCEEDED,
  matchesCode,
  readStableCode,
} from './usePlanLimit'

/**
 * 업그레이드를 권할 자격이 있는 오류만 통과해야 한다.
 *
 * 돈을 내도 풀리지 않는 문제(검증 실패, 인증 만료, 서버 장애)에 결제를 권하면 사용자를
 * 오도하는 것이고, 한 번 그러면 진짜 필요한 순간의 안내도 믿지 않게 된다.
 */
describe('플랜 업그레이드 판별', () => {
  /** 두 한도를 모두 허용하는 화면(에셋 업로드)의 관점. */
  const anyLimit = (e: unknown) => matchesCode(e, PLAN_LIMIT_EXCEEDED, STORAGE_QUOTA_EXCEEDED)
  /** 플랜 한도만 허용하는 화면(채널·댓글)의 관점. */
  const planOnly = (e: unknown) => matchesCode(e, PLAN_LIMIT_EXCEEDED)
  const withCode = (code: unknown) => ({ response: { data: { error: code } } })

  it.each([
    ['플랜 한도', 'PLAN_LIMIT_EXCEEDED'],
    ['저장 공간 한도', 'STORAGE_QUOTA_EXCEEDED'],
  ])('%s 는 업그레이드로 풀리므로 통과시킨다', (_label, code) => {
    expect(anyLimit(withCode(code))).toBe(true)
  })

  /*
   * 크레딧은 플랜이 아니라 별도 구매로 푸는 문제라 목적지가 다르다. 같은 링크로 보내면
   * 구독 화면에서 "크레딧은 어디서 사지"로 한 번 더 헤매게 된다.
   */
  it.each([
    ['크레딧 부족', 'CREDIT_INSUFFICIENT'],
    ['중복 구독 결제', 'SUBSCRIPTION_ALREADY_ACTIVE'],
    ['인증 실패', 'UNAUTHORIZED'],
    ['검증 실패', 'BAD_REQUEST'],
    ['권한 없음', 'FORBIDDEN'],
    ['찾을 수 없음', 'NOT_FOUND'],
    ['요청 제한', 'RATE_LIMIT_EXCEEDED'],
  ])('%s 에는 결제를 권하지 않는다', (_label, code) => {
    expect(anyLimit(withCode(code))).toBe(false)
  })

  it.each([
    ['코드가 없는 오류', {}],
    ['본문이 문자열(HTML 오류 페이지)', { response: { data: '<html>502</html>' } }],
    ['본문이 null', { response: { data: null } }],
    ['응답 없는 네트워크 오류', new Error('Network Error')],
    ['null', null],
    ['문자열', 'boom'],
    ['코드가 문자열이 아님', { response: { data: { error: 42 } } }],
  ])('%s 는 판별 불가로 보고 결제를 권하지 않는다', (_label, error) => {
    expect(anyLimit(error)).toBe(false)
  })

  /*
   * 사용자 문구로 분기하면 번역·문구 수정에 조용히 깨진다. 코드가 근거여야 한다.
   */
  it('사유 문구에 한도라는 말이 있어도 코드가 다르면 권하지 않는다', () => {
    const error = Object.assign(new Error('저장 공간 한도를 초과했습니다'), withCode('BAD_REQUEST'))
    expect(anyLimit(error)).toBe(false)
  })

  /*
   * 허용 코드를 화면이 정하는 이유. 채널 연결 실패 화면은 '채널을 더 연결하려면…' 이라고
   * 안내하므로, 저장 공간 한도까지 받아들이면 사유와 안내가 어긋난다.
   */
  it('플랜 한도만 허용하는 화면은 저장 공간 오류에 CTA 를 띄우지 않는다', () => {
    expect(planOnly(withCode(PLAN_LIMIT_EXCEEDED))).toBe(true)
    expect(planOnly(withCode(STORAGE_QUOTA_EXCEEDED))).toBe(false)
  })

  it('두 한도를 모두 허용하는 화면은 저장 공간 오류도 받아들인다', () => {
    expect(anyLimit(withCode(STORAGE_QUOTA_EXCEEDED))).toBe(true)
  })

  it('허용 목록이 비면 아무것도 통과시키지 않는다', () => {
    expect(matchesCode(withCode(PLAN_LIMIT_EXCEEDED))).toBe(false)
  })

  it('안정 코드를 그대로 읽어 온다', () => {
    expect(readStableCode(withCode('PLAN_LIMIT_EXCEEDED'))).toBe('PLAN_LIMIT_EXCEEDED')
    expect(readStableCode(withCode(''))).toBeNull()
    expect(readStableCode(null)).toBeNull()
  })

  it('안내 목적지는 한 곳으로 고정한다', () => {
    // 화면마다 다른 경로를 쓰면 capability 게이팅·라우트 변경이 일부만 반영된다.
    expect(PLAN_UPGRADE_PATH).toBe('/subscription')
  })
})
