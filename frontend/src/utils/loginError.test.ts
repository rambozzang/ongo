import { describe, expect, it } from 'vitest'
import { loginErrorMessage } from './loginError'

describe('loginErrorMessage', () => {
  it.each([
    new Error('Network Error'),
    new Error('Request failed with status code 503'),
    new Error(''),
    'not an error',
  ])('uses the localized fallback for transport errors: %p', (error) => {
    expect(loginErrorMessage(error, '로그인에 실패했습니다.')).toBe('로그인에 실패했습니다.')
  })

  it('preserves actionable domain errors from the server', () => {
    expect(loginErrorMessage(new Error('Google OAuth 설정이 필요합니다.'), 'fallback'))
      .toBe('Google OAuth 설정이 필요합니다.')
  })
})
