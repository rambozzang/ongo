import axios, { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from './client'

const unauthorizedAdapter = async (
  config: InternalAxiosRequestConfig,
): Promise<AxiosResponse> => {
  throw new AxiosError(
    'Unauthorized',
    'ERR_BAD_REQUEST',
    config,
    undefined,
    {
      data: { success: false, message: null, data: null, error: 'login failed' },
      status: 401,
      statusText: 'Unauthorized',
      headers: {},
      config,
    },
  )
}

describe('API authentication retry policy', () => {
  let originalAdapter: typeof apiClient.defaults.adapter

  beforeEach(() => {
    originalAdapter = apiClient.defaults.adapter
    apiClient.defaults.adapter = unauthorizedAdapter
    localStorage.setItem('accessToken', 'stale-access-token')
    localStorage.setItem('refreshToken', 'refresh-token')
  })

  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter
    localStorage.clear()
    sessionStorage.clear()
    vi.restoreAllMocks()
  })

  it('does not refresh a failed social login request', async () => {
    const refresh = vi.spyOn(axios, 'post')

    await expect(apiClient.post('/auth/login/google', { code: 'bad-code' })).rejects.toMatchObject({
      response: { status: 401 },
    })

    expect(refresh).not.toHaveBeenCalled()
  })

  it('does not refresh a failed dev login request', async () => {
    const refresh = vi.spyOn(axios, 'post')

    await expect(apiClient.post('/auth/dev-login', { email: 'dev@example.com' })).rejects.toMatchObject({
      response: { status: 401 },
    })

    expect(refresh).not.toHaveBeenCalled()
  })

  it.each([
    ['/auth/google/state', 'Google OAuth state request'],
    ['/auth/kakao/state', 'Kakao OAuth state request'],
    ['/auth/refresh', 'token refresh request'],
  ])('does not recursively refresh a failed %s', async (path) => {
    const refresh = vi.spyOn(axios, 'post')

    await expect(apiClient.get(path)).rejects.toMatchObject({
      response: { status: 401 },
    })

    expect(refresh).not.toHaveBeenCalled()
  })

  it('still refreshes a protected request after a 401 response', async () => {
    let attempts = 0
    apiClient.defaults.adapter = async (config) => {
      if (attempts++ === 0) {
        return unauthorizedAdapter(config)
      }
      return {
        data: { success: true, message: null, data: { id: 1 }, error: null },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      }
    }
    const refresh = vi.spyOn(axios, 'post').mockResolvedValue({
      data: {
        success: true,
        message: null,
        data: { accessToken: 'new-access-token', refreshToken: 'new-refresh-token' },
        error: null,
      },
    } as AxiosResponse)

    const response = await apiClient.get('/auth/profile')

    expect(response.status).toBe(200)
    expect(refresh).toHaveBeenCalledOnce()
    expect(localStorage.getItem('accessToken')).toBe('new-access-token')
  })
})

/**
 * 서버 거절 사유를 사용자에게 전달하는 계약.
 *
 * 백엔드는 한도 초과·크레딧 부족을 400 + ResData{message, error} 로 내려보내지만, 화면 다수가
 * `error.message` 를 그대로 렌더하기 때문에 보강 전에는 전부 `Request failed with status code 400`
 * 으로 보였다. 정작 결제해야 할 이유를 설명하는 자리가 통째로 비어 있었다.
 */
function rejectWith(status: number, data: unknown) {
  return async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
    throw new AxiosError(
      `Request failed with status code ${status}`,
      'ERR_BAD_REQUEST',
      config,
      undefined,
      { data, status, statusText: 'Bad Request', headers: {}, config },
    )
  }
}

describe('서버 오류 메시지 보강', () => {
  let originalAdapter: typeof apiClient.defaults.adapter

  beforeEach(() => {
    originalAdapter = apiClient.defaults.adapter
  })

  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter
    localStorage.clear()
    sessionStorage.clear()
    vi.restoreAllMocks()
  })

  it('400 응답의 message 를 error.message 로 올리고 Axios 형태를 보존한다', async () => {
    const reason = '연동 플랫폼 한도를 초과했습니다. 현재 플랜 한도: 1'
    apiClient.defaults.adapter = rejectWith(400, {
      success: false,
      message: reason,
      data: null,
      error: 'PLAN_LIMIT_EXCEEDED',
    })

    const error = await apiClient.post('/channels/connect/youtube').catch((e) => e)

    expect(error.message).toBe(reason)
    // 형태를 바꾸면 useErrorHandler 의 상태코드 분기와 comments 스토어 판정이 조용히 깨진다.
    expect(error).toBeInstanceOf(AxiosError)
    expect(error.isAxiosError).toBe(true)
    expect(error.response.status).toBe(400)
    expect(error.response.data.error).toBe('PLAN_LIMIT_EXCEEDED')
    expect(error.config.url).toBe('/channels/connect/youtube')
  })

  /*
   * message 가 비어 있으면 error 로 내려간다.
   *
   * 백엔드는 handleBusiness 에서만 message 를 채우고, IllegalArgumentException·
   * IllegalStateException·검증 실패 등 나머지 4xx 는 message 를 비운 채 error 에 사람이 읽을
   * 문장을 넣는다. application 레이어의 require/check 가 200곳 가까이 되므로, error 를 읽지
   * 않으면 입력·예약·게시 오류가 전부 상태문구로만 보인다.
   */
  it('message 가 없으면 error 의 사유를 올린다', async () => {
    const reason = '게시 시간이 지난 예약 게시물은 수정할 수 없습니다'
    apiClient.defaults.adapter = rejectWith(400, { success: false, data: null, error: reason })

    const error = await apiClient.put('/schedules/1').catch((e) => e)

    expect(error.message).toBe(reason)
    expect(error.response.status).toBe(400)
    expect(error.isAxiosError).toBe(true)
  })

  it('message 와 error 가 함께 있으면 message 를 쓴다 — 기계 코드 노출 방지', async () => {
    // handleBusiness 는 error 에 코드를 넣는다. 우선순위가 뒤집히면 사용자가
    // 'PLAN_LIMIT_EXCEEDED' 를 그대로 보게 된다.
    const reason = '월간 업로드 한도를 초과했습니다. 현재 플랜 한도: 5'
    apiClient.defaults.adapter = rejectWith(400, {
      success: false,
      message: reason,
      data: null,
      error: 'PLAN_LIMIT_EXCEEDED',
    })

    const error = await apiClient.post('/videos').catch((e) => e)

    expect(error.message).toBe(reason)
    expect(error.message).not.toContain('PLAN_LIMIT_EXCEEDED')
  })

  /*
   * error 단독 값이 기계 코드면 무시한다.
   *
   * 사유를 못 보여주는 것보다 정체불명의 식별자를 보여주는 쪽이 나쁘다. 보여주려는 대상은
   * require/검증 실패가 담는 한국어 문장이지 코드가 아니다.
   */
  it.each([
    ['BAD_REQUEST'],
    ['PLAN_LIMIT_EXCEEDED'],
    ['DUPLICATE'],
  ])('error 가 기계 코드 %s 뿐이면 보강하지 않는다', async (code) => {
    apiClient.defaults.adapter = rejectWith(400, { success: false, data: null, error: code })

    const error = await apiClient.post('/channels/connect/youtube').catch((e) => e)

    expect(error.message).toBe('Request failed with status code 400')
    expect(error.message).not.toContain(code)
  })

  it('한국어 문장이 담긴 error 는 코드가 아니므로 보강한다', async () => {
    const reason = '닉네임은 2자 이상이어야 합니다'
    apiClient.defaults.adapter = rejectWith(400, { success: false, data: null, error: reason })

    const error = await apiClient.post('/users/profile').catch((e) => e)

    expect(error.message).toBe(reason)
  })

  it.each([
    ['필드 목록이 섞인 검증 문구', 'nickname: 필수 항목입니다, category: 필수 항목입니다'],
    ['코드 뒤에 설명이 붙은 문구', 'BAD_REQUEST 잘못된 요청입니다'],
    ['소문자가 섞인 값', 'Bad_Request'],
  ])('%s 는 코드가 아니므로 보강한다', async (_label, reason) => {
    apiClient.defaults.adapter = rejectWith(400, { success: false, data: null, error: reason })

    const error = await apiClient.post('/users/profile').catch((e) => e)

    expect(error.message).toBe(reason)
  })

  it('message 와 error 가 모두 없으면 기존 오류 메시지를 유지한다', async () => {
    apiClient.defaults.adapter = rejectWith(400, { success: false, data: null })

    const error = await apiClient.post('/videos').catch((e) => e)

    expect(error.message).toBe('Request failed with status code 400')
    expect(error.response.status).toBe(400)
  })

  it.each([
    ['공백만 있는 값', '   '],
    ['빈 문자열', ''],
    ['숫자', 42],
    ['객체', { detail: 'nope' }],
    ['배열(검증 오류 목록)', [{ field: 'nickname' }]],
    ['null', null],
  ])('message·error 가 %s 면 무시하고 기존 메시지를 유지한다', async (_label, value) => {
    apiClient.defaults.adapter = rejectWith(400, {
      success: false,
      message: value,
      data: null,
      error: value,
    })

    const error = await apiClient.post('/videos').catch((e) => e)

    expect(error.message).toBe('Request failed with status code 400')
  })

  /*
   * 401 은 어느 필드도 보강하지 않는다.
   *
   * 세션을 만드는 요청(소셜 로그인·dev-login·state·refresh)의 401 은 refresh 분기를 건너뛰고
   * 보강 코드까지 도달한다. handleUnauthorized/handleTokenExpired 가 error 에 예외 메시지를
   * 담으므로, 여기서 error 를 읽으면 로그인 화면 문구가 서버 내부 사유로 바뀐다.
   */
  it.each([
    ['/auth/login/google', 'error 만 있는 401'],
    ['/auth/dev-login', 'error 만 있는 401'],
  ])('%s 의 401 은 error 를 보강하지 않는다', async (path) => {
    apiClient.defaults.adapter = rejectWith(401, {
      success: false,
      data: null,
      error: '유효하지 않은 인가 코드입니다',
    })

    const error = await apiClient.post(path).catch((e) => e)

    expect(error.message).toBe('Request failed with status code 401')
    expect(error.response.data.error).toBe('유효하지 않은 인가 코드입니다')
  })

  it('401 은 message 가 있어도 보강하지 않는다', async () => {
    apiClient.defaults.adapter = rejectWith(401, {
      success: false,
      message: '세션이 만료되었습니다',
      data: null,
      error: 'UNAUTHORIZED',
    })

    const error = await apiClient.post('/auth/login/kakao').catch((e) => e)

    expect(error.message).toBe('Request failed with status code 401')
  })

  it.each([
    ['문자열 본문(HTML 오류 페이지)', '<html>502 Bad Gateway</html>'],
    ['null 본문', null],
    ['배열 본문', [{ field: 'nickname', message: '필수' }]],
    ['Blob 본문', new Blob(['bin'])],
  ])('%s 여도 throw 하지 않고 원본 오류를 그대로 reject 한다', async (_label, data) => {
    apiClient.defaults.adapter = rejectWith(400, data)

    const error = await apiClient.post('/videos').catch((e) => e)

    expect(error).toBeInstanceOf(AxiosError)
    expect(error.message).toBe('Request failed with status code 400')
    expect(error.response.data).toEqual(data)
  })

  it('응답 없는 네트워크 오류는 그대로 둔다', async () => {
    apiClient.defaults.adapter = async (config) => {
      throw new AxiosError('Network Error', 'ERR_NETWORK', config)
    }

    const error = await apiClient.get('/channels').catch((e) => e)

    expect(error.message).toBe('Network Error')
    expect(error.response).toBeUndefined()
  })

  it('보강해도 401 refresh 계약은 그대로다', async () => {
    // 보호된 요청의 401 은 refresh 분기가 먼저 처리해 보강 코드까지 가지도 않는다.
    // (세션 확립 요청의 401 은 보강 코드에 도달하므로, 거기서 401 을 명시적으로 걸러낸다.)
    localStorage.setItem('accessToken', 'stale')
    localStorage.setItem('refreshToken', 'refresh-token')
    apiClient.defaults.adapter = rejectWith(401, {
      success: false,
      message: '세션이 만료되었습니다',
      data: null,
      error: 'UNAUTHORIZED',
    })
    const refresh = vi.spyOn(axios, 'post').mockRejectedValue(new Error('refresh failed'))

    await expect(apiClient.get('/channels')).rejects.toBeDefined()

    expect(refresh).toHaveBeenCalledOnce()
  })
})
