import axios, { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { readStableCode } from '@/composables/usePlanLimit'
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

  /**
   * **413 은 본문이 우리 것이 아닐 수 있다.**
   *
   * 리버스 프록시가 한도를 넘긴 요청을 잘라내면 응답 본문은 nginx 의 HTML 오류 페이지다.
   * JSON 경로가 전부 건너뛰어져 `error.message` 에는 Axios 기본 문구만 남고, 사용자는
   * 파일이 큰 것이 문제인지 서버가 죽은 것인지 구분할 수 없다.
   */
  it('413 은 본문이 HTML 이어도 크기 문제임을 알려 준다', async () => {
    apiClient.defaults.adapter = rejectWith(
      413,
      '<html><head><title>413 Request Entity Too Large</title></head></html>' as never,
    )

    const error = await apiClient.post('/assets').catch((e) => e)

    expect(error.message).toContain('파일이 너무 커서')
    expect(error.message).not.toContain('413')
  })

  /** 서버가 JSON 으로 더 구체적인 이유를 주면 그 문장이 이긴다 — 바닥값은 없을 때만 쓴다. */
  it('413 이어도 서버 JSON 메시지가 있으면 그것을 쓴다', async () => {
    const reason = '업로드 파일은 2GB 이하여야 합니다.'
    apiClient.defaults.adapter = rejectWith(413, {
      success: false,
      message: reason,
      data: null,
      error: 'FILE_TOO_LARGE',
    })

    const error = await apiClient.post('/assets').catch((e) => e)

    expect(error.message).toBe(reason)
  })

  /**
   * 413 에는 업그레이드 안내를 붙이지 않는다. 요금제를 올려도 프록시 한도는 그대로라
   * 권해서는 안 되는 자리다 — 플랜·저장공간 한도는 서버가 코드를 함께 주는 다른 층이다.
   */
  it('413 은 플랜·저장공간 한도 코드를 만들어 내지 않는다', async () => {
    apiClient.defaults.adapter = rejectWith(413, '<html>413</html>' as never)

    const error = await apiClient.post('/assets').catch((e) => e)

    expect(readStableCode(error)).toBeNull()
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

/**
 * 갱신 상한 초과와 인증 실패를 가르는 계약.
 *
 * 서버는 로그인·토큰 갱신에 요청 상한을 걸어 두고, 넘기면 400 + `AUTH_RATE_LIMIT_EXCEEDED`
 * 를 준다. 그런데 갱신 실패를 전부 한 덩어리로 처리하던 동안에는 이 응답도 세션 만료로 읽혀
 * **로그아웃**으로 이어졌다. 상한은 서버 전체에 걸리므로, 남이 상한을 채운 탓에 잠깐 막혔을
 * 뿐인 사용자의 멀쩡한 세션까지 우리가 지운 셈이다.
 *
 * 그래서 여기서 고정하는 사실은 두 가지다 — 상한 초과는 **세션을 건드리지 않는다**,
 * 그리고 그 밖의 갱신 실패는 **예전 그대로 로그아웃한다**. 둘 중 하나만 지키면 의미가 없다.
 */
describe('토큰 갱신 상한 초과', () => {
  /** 서버(`AuthRateLimiter`)가 실제로 내려보내는 문구. */
  const REFRESH_LIMIT_MESSAGE = '토큰 갱신 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.'
  const LOGIN_LIMIT_MESSAGE = '로그인 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.'

  /**
   * 모듈을 새로 읽어 테스트끼리 격리한다.
   *
   * `forceLogout` 은 모듈 수준 `isLoggingOut` 플래그로 **한 번만** 동작한다. 모듈 인스턴스를
   * 공유하면 앞선 테스트가 그 플래그를 켜 버려, 뒤 테스트의 "로그아웃했는가" 판정이 조용히
   * 항상 참이 된다 — 회귀를 못 잡는 테스트가 된다.
   *
   * axios 도 같은 세대에서 함께 가져온다. `resetModules` 후의 client 는 새 axios 인스턴스를
   * 잡으므로, 바깥에서 정적으로 import 한 axios 에 스파이를 걸면 갱신 요청을 가로채지 못한다.
   */
  async function freshModules() {
    vi.resetModules()
    const freshAxios = (await import('axios')).default
    const freshClient = (await import('./client')).default
    return { apiClient: freshClient, axios: freshAxios }
  }

  /** 보호된 요청이 401 을 받아 갱신 분기로 들어가게 한다. */
  const expiredSession = async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
    throw new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, {
      data: { success: false, message: null, data: null, error: 'TOKEN_EXPIRED' },
      status: 401,
      statusText: 'Unauthorized',
      headers: {},
      config,
    })
  }

  /** 갱신 요청이 받는 응답. `AuthRateLimiter` → `handleBusiness` 의 실제 형태다. */
  function refreshRejection(status: number, data: unknown) {
    const error = new AxiosError(
      `Request failed with status code ${status}`,
      'ERR_BAD_REQUEST',
      undefined,
      undefined,
    )
    error.response = { data, status, statusText: '', headers: {}, config: {} } as never
    return error
  }

  beforeEach(() => {
    localStorage.setItem('accessToken', 'stale-access-token')
    localStorage.setItem('refreshToken', 'live-refresh-token')
  })

  afterEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    vi.restoreAllMocks()
  })

  it('상한에 걸린 갱신은 토큰과 세션을 그대로 둔다', async () => {
    const { apiClient, axios } = await freshModules()
    apiClient.defaults.adapter = expiredSession
    vi.spyOn(axios, 'post').mockRejectedValue(
      refreshRejection(400, {
        success: false,
        message: REFRESH_LIMIT_MESSAGE,
        data: null,
        error: 'AUTH_RATE_LIMIT_EXCEEDED',
      }),
    )

    await expect(apiClient.get('/channels')).rejects.toBeDefined()

    // 세션이 살아 있어야 상한이 풀린 뒤 그대로 이어서 쓸 수 있다.
    expect(localStorage.getItem('accessToken')).toBe('stale-access-token')
    expect(localStorage.getItem('refreshToken')).toBe('live-refresh-token')
    expect(sessionStorage.getItem('sessionExpired')).toBeNull()
  })

  it('상한 초과 오류는 재시도 사유와 안정 코드를 그대로 전달한다', async () => {
    const { apiClient, axios } = await freshModules()
    apiClient.defaults.adapter = expiredSession
    vi.spyOn(axios, 'post').mockRejectedValue(
      refreshRejection(400, {
        success: false,
        message: REFRESH_LIMIT_MESSAGE,
        data: null,
        error: 'AUTH_RATE_LIMIT_EXCEEDED',
      }),
    )

    const error = await apiClient.get('/channels').catch((e) => e)

    // 화면이 "잠시 후 다시" 를 말할 수 있어야 한다. 보강이 없으면 상태문구만 남는다.
    expect(error.message).toBe(REFRESH_LIMIT_MESSAGE)
    // 형태를 바꾸면 useErrorHandler 의 상태코드 분기와 matchesCode 판정이 조용히 깨진다.
    expect(error.isAxiosError).toBe(true)
    expect(error.response.status).toBe(400)
    expect(error.response.data.error).toBe('AUTH_RATE_LIMIT_EXCEEDED')
  })

  /** **대조군.** 이게 없으면 "전부 로그아웃 안 함" 으로 퇴화해도 테스트가 통과한다. */
  it('상한이 아닌 갱신 실패는 예전대로 세션을 지운다', async () => {
    const { apiClient, axios } = await freshModules()
    apiClient.defaults.adapter = expiredSession
    vi.spyOn(axios, 'post').mockRejectedValue(new Error('refresh failed'))

    await expect(apiClient.get('/channels')).rejects.toBeDefined()

    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(sessionStorage.getItem('sessionExpired')).toBe('1')
  })

  /**
   * **상태코드가 아니라 안정 코드로 가른다.**
   *
   * 400 만 보고 봐주면, 갱신 경로의 다른 400(형식 오류·폐기된 토큰 등)까지 세션을 남긴 채
   * 무한히 실패하게 된다. 그건 상한과 달리 기다려도 풀리지 않는다.
   */
  it('상한 코드가 아닌 400 갱신 실패는 세션을 지운다', async () => {
    const { apiClient, axios } = await freshModules()
    apiClient.defaults.adapter = expiredSession
    vi.spyOn(axios, 'post').mockRejectedValue(
      refreshRejection(400, {
        success: false,
        message: '유효하지 않은 리프레시 토큰입니다',
        data: null,
        error: 'INVALID_REFRESH_TOKEN',
      }),
    )

    await expect(apiClient.get('/channels')).rejects.toBeDefined()

    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(sessionStorage.getItem('sessionExpired')).toBe('1')
  })

  /** 상한은 시간이 지나면 풀린다. 그때 아무 조치 없이 정상 복구되어야 의미가 있다. */
  it('상한이 풀리면 다음 요청의 갱신이 그대로 성공한다', async () => {
    const { apiClient, axios } = await freshModules()
    apiClient.defaults.adapter = expiredSession
    const refresh = vi.spyOn(axios, 'post').mockRejectedValue(
      refreshRejection(400, {
        success: false,
        message: REFRESH_LIMIT_MESSAGE,
        data: null,
        error: 'AUTH_RATE_LIMIT_EXCEEDED',
      }),
    )

    await expect(apiClient.get('/channels')).rejects.toBeDefined()

    // 상한이 풀린 뒤 — 사용자가 다시 로그인할 필요가 없어야 한다.
    let attempts = 0
    apiClient.defaults.adapter = async (config) => {
      if (attempts++ === 0) return expiredSession(config)
      return {
        data: { success: true, message: null, data: { id: 1 }, error: null },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      }
    }
    refresh.mockResolvedValue({
      data: {
        success: true,
        message: null,
        data: { accessToken: 'fresh-access-token', refreshToken: 'fresh-refresh-token' },
        error: null,
      },
    } as AxiosResponse)

    const response = await apiClient.get('/channels')

    expect(response.status).toBe(200)
    expect(localStorage.getItem('accessToken')).toBe('fresh-access-token')
  })

  /**
   * 로그인 요청 자체가 상한에 걸려도 **갱신 재귀에 들어가지 않는다.**
   *
   * 상한 응답은 400 이라 401 분기 자체를 타지 않지만, 그 계약을 여기서 함께 못 박는다.
   * 로그인 화면은 서버가 준 사유를 그대로 보여줄 수 있어야 한다.
   */
  it('로그인 상한 초과는 갱신을 시도하지 않고 사유를 그대로 보여준다', async () => {
    const { apiClient, axios } = await freshModules()
    apiClient.defaults.adapter = rejectWith(400, {
      success: false,
      message: LOGIN_LIMIT_MESSAGE,
      data: null,
      error: 'AUTH_RATE_LIMIT_EXCEEDED',
    })
    const refresh = vi.spyOn(axios, 'post')

    const error = await apiClient.post('/auth/login/google', { code: 'x' }).catch((e) => e)

    expect(refresh).not.toHaveBeenCalled()
    expect(error.message).toBe(LOGIN_LIMIT_MESSAGE)
    expect(sessionStorage.getItem('sessionExpired')).toBeNull()
  })
})
