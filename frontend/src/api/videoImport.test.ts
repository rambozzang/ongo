import { beforeEach, describe, expect, it, vi } from 'vitest'

const http = vi.hoisted(() => ({ post: vi.fn(), get: vi.fn() }))

vi.mock('./client', () => ({
  default: { post: http.post, get: http.get },
  unwrapResponse: (res: { data: { data: unknown } }) => res.data.data,
}))

import { videoApi } from './video'

const ok = (data: unknown) => Promise.resolve({ data: { success: true, data } })
const noSleep = () => Promise.resolve()

/*
 * URL 가져오기는 서버 작업이다. nginx 가 1분에 연결을 끊어, 한 요청으로 기다리던 때는 1분 넘게 걸리는
 * 가져오기가 서버에서는 성공하는데 화면은 실패를 보였다.
 */
describe('videoApi.importUrl', () => {
  beforeEach(() => {
    http.post.mockReset()
    http.get.mockReset()
  })

  it('작업을 시작하고 끝날 때까지 상태를 물어 결과를 돌려준다', async () => {
    http.post.mockReturnValue(ok({ jobId: 'j1', status: 'QUEUED' }))
    http.get
      .mockReturnValueOnce(ok({ jobId: 'j1', status: 'RUNNING' }))
      .mockReturnValueOnce(ok({ jobId: 'j1', status: 'SUCCEEDED', result: { videoId: 7, title: '라이브', provider: 'YOUTUBE' } }))

    const result = await videoApi.importUrl({ url: 'https://youtu.be/x' }, { sleep: noSleep })

    expect(result.videoId).toBe(7)
    expect(http.post).toHaveBeenCalledWith('/videos/import-url', { url: 'https://youtu.be/x' })
    expect(http.get).toHaveBeenCalledWith('/videos/import-url/jobs/j1')
    expect(http.get).toHaveBeenCalledTimes(2)
  })

  it('실패한 작업은 서버 문구를 그대로 보여준다', async () => {
    http.post.mockReturnValue(ok({ jobId: 'j2', status: 'QUEUED' }))
    http.get.mockReturnValue(ok({ jobId: 'j2', status: 'FAILED', errorCode: 'VIDEO_DOWNLOAD_SIZE_INVALID', errorMessage: '최대 10GB 까지 가져올 수 있습니다.' }))

    await expect(videoApi.importUrl({ url: 'https://youtu.be/x' }, { sleep: noSleep })).rejects.toThrow('최대 10GB')
  })

  it('잠깐의 네트워크 오류는 넘기고 계속 묻는다', async () => {
    http.post.mockReturnValue(ok({ jobId: 'j3', status: 'RUNNING' }))
    http.get
      .mockRejectedValueOnce(new Error('Network Error'))
      .mockReturnValueOnce(ok({ jobId: 'j3', status: 'SUCCEEDED', result: { videoId: 3, title: 't', provider: 'TIKTOK' } }))

    const result = await videoApi.importUrl({ url: 'https://tiktok.com/x' }, { sleep: noSleep })

    expect(result.videoId).toBe(3)
  })

  /** 서버가 재기동하면 작업 상태가 사라진다. 영상은 이미 만들어졌을 수 있다 — 실패라고 단정하지 않는다. */
  it('작업이 사라졌으면 내 영상 목록을 확인하라고 안내한다', async () => {
    http.post.mockReturnValue(ok({ jobId: 'j4', status: 'RUNNING' }))
    http.get.mockRejectedValue({ response: { status: 404 } })

    await expect(videoApi.importUrl({ url: 'https://youtu.be/x' }, { sleep: noSleep })).rejects.toThrow('내 영상 목록')
  })

  it('최대 대기 시간을 넘기면 기다리기를 멈춘다', async () => {
    http.post.mockReturnValue(ok({ jobId: 'j5', status: 'RUNNING' }))
    http.get.mockReturnValue(ok({ jobId: 'j5', status: 'RUNNING' }))

    await expect(
      videoApi.importUrl({ url: 'https://youtu.be/x' }, { sleep: noSleep, pollIntervalMs: 1_000, maxWaitMs: 3_000 }),
    ).rejects.toThrow('너무 오래')
    expect(http.get).toHaveBeenCalledTimes(3)
  })
})
