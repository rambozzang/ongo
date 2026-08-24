import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * 구독 화면에 쿠폰 UX 가 남아 있지 않은지 고정한다.
 *
 * 예전 화면은 쿠폰 입력란과 "검증"·"적용" 버튼을 보여주고 `쿠폰 유효: 20% 할인` 까지
 * 표시했다. 그런데 결제는 쿠폰을 읽지 않아 정가로 청구됐다 — 고객은 할인을 약속받고
 * 정가를 냈다.
 *
 * 화면을 마운트하지 않고 소스를 읽는 이유: 이 뷰에는 테스트 하네스가 없고 스토어 의존이
 * 많아 마운트 비용이 크다. 반면 여기서 막아야 하는 것은 "쿠폰 입력 UI 가 다시 생기는
 * 것"이고, 그건 소스에 그 문자열이 다시 나타나는 것과 같다.
 */
describe('SubscriptionView coupon UX', () => {
  // vitest 의 cwd 는 frontend 루트다. import.meta.url 은 Vite 변환을 거쳐 file: 스킴이 아니다.
  const source = readFileSync(resolve(process.cwd(), 'src/views/SubscriptionView.vue'), 'utf-8')

  /** 제거 이유를 적은 주석은 남긴다. 그 주석을 걸러낸 본문에서만 검사한다. */
  const withoutComments = source
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')

  it('renders no coupon input or action button', () => {
    expect(withoutComments).not.toContain('couponCode')
    expect(withoutComments).not.toContain('couponValidation')
    expect(withoutComments).not.toContain('쿠폰 코드를 입력하세요')
  })

  it('never promises a discount', () => {
    expect(withoutComments).not.toContain('쿠폰 유효')
    expect(withoutComments).not.toContain('할인')
  })

  it('calls no coupon store action', () => {
    expect(withoutComments).not.toContain('validateCoupon')
    expect(withoutComments).not.toContain('applyCoupon')
  })
})
