// describe/it은 vitest에서 가져와야 러너가 테스트를 수집한다.
// node:test에서 가져오면 Node 러너에 등록되어 vitest가 "No test suite found"로 실패한다.
// 단언은 node:assert/strict 그대로 쓴다 — AssertionError를 vitest가 실패로 정확히 보고한다.
import { describe, it } from 'vitest'
import assert from 'node:assert/strict'
import type * as PortOne from '@portone/browser-sdk/v2'
import { isPortOnePaymentError } from './usePortOne'

type PaymentResponse = NonNullable<Awaited<ReturnType<typeof PortOne.requestPayment>>>

const response = (overrides: Partial<PaymentResponse> = {}): PaymentResponse => ({
  transactionType: 'PAYMENT',
  txId: 'tx-1',
  paymentId: 'ongo-1',
  ...overrides,
})

describe('isPortOnePaymentError', () => {
  it('treats an omitted result as a user closing the payment UI', () => {
    assert.equal(isPortOnePaymentError(undefined), false)
  })

  it('treats any present error code as a failure, including an empty string', () => {
    assert.equal(isPortOnePaymentError(response({ code: '' })), true)
    assert.equal(isPortOnePaymentError(response({ code: 'PAYMENT_FAILED' })), true)
  })

  it('allows a response without an error code to continue to completion', () => {
    assert.equal(isPortOnePaymentError(response()), false)
  })
})
