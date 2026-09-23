import { describe, expect, it } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import UploadQueueItem from './UploadQueueItem.vue'
import type { UploadQueueItem as Item } from '@/types/uploadQueue'

function item(overrides: Partial<Item> = {}): Item {
  return {
    id: 'q1',
    file: { name: 'a.mp4', type: 'video/mp4', size: 10 } as File,
    fileName: 'a.mp4',
    fileSize: 10,
    mimeType: 'video/mp4',
    progress: 0,
    status: 'failed',
    platforms: [],
    error: '월간 업로드 한도(5)를 초과했습니다',
    addedAt: new Date().toISOString(),
    ...overrides,
  }
}

const mountItem = (i: Item) =>
  mount(UploadQueueItem, {
    props: { item: i, index: 0, total: 1 },
    global: { stubs: { RouterLink: RouterLinkStub } },
  })

/*
 * 한도에 걸린 실패는 **돈이 되는 순간**이다. "업로드 실패" 만 보여주면 사용자는 떠난다.
 * 반대로 결제로 풀리지 않는 실패(네트워크·파일 형식)에 결제를 권하면 정작 필요할 때 믿지 않는다.
 */
describe('UploadQueueItem — 업그레이드 안내', () => {
  it('월 업로드 한도에 걸리면 요금제 화면으로 안내한다', () => {
    const w = mountItem(item({ errorCode: 'PLAN_LIMIT_EXCEEDED' }))
    const link = w.find('[data-testid="upload-upgrade-link"]')
    expect(link.exists()).toBe(true)
    expect(w.findComponent(RouterLinkStub).props('to')).toBe('/subscription')
  })

  it('저장 공간 한도에 걸려도 안내한다', () => {
    const w = mountItem(item({ errorCode: 'STORAGE_QUOTA_EXCEEDED' }))
    expect(w.find('[data-testid="upload-upgrade-link"]').exists()).toBe(true)
  })

  it('결제로 풀리지 않는 실패에는 결제를 권하지 않는다', () => {
    expect(mountItem(item({ errorCode: undefined })).find('[data-testid="upload-upgrade-link"]').exists()).toBe(false)
    expect(mountItem(item({ errorCode: 'CREDIT_INSUFFICIENT' })).find('[data-testid="upload-upgrade-link"]').exists()).toBe(false)
  })
})
