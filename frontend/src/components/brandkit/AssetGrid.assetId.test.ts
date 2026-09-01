import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia, setActivePinia } from 'pinia'
import AssetGrid from './AssetGrid.vue'
import { assetsApi } from '@/api/assets'
import { brandKitApi } from '@/api/brandkit'
import { useBrandKitStore } from '@/stores/brandkit'
import koMessages from '@/locales/ko/common.json'

/**
 * 브랜드킷이 **URL 문자열이 아니라 원본 에셋 id 를 들고 다니는지** 고정한다.
 *
 * 업로드 응답의 `fileUrl` 만 복사해 저장하면 운영(S3/R2)에서 7 일 뒤 서명이 만료돼 로고와
 * 워터마크가 통째로 깨진다. 회귀가 **일주일 뒤에** 나타나므로 눈으로는 잡히지 않는다.
 *
 * `assetId` 가 있으면 서버가 조회할 때마다 소유권을 확인하고 저장 키로 URL 을 새로 발급한다.
 */

vi.mock('@/api/assets', () => ({
  assetsApi: { upload: vi.fn(), list: vi.fn(), update: vi.fn(), delete: vi.fn() },
}))
vi.mock('@/api/brandkit', () => ({
  brandKitApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(), setDefault: vi.fn() },
}))

function uploadResponse(id = 42) {
  return {
    id,
    filename: 'uuid_logo.png',
    originalFilename: '로고.png',
    fileUrl: 'https://r2.test/assets/100/uuid_logo.png?sig=fresh',
    fileType: 'IMAGE',
    fileSizeBytes: 1024,
    mimeType: 'image/png',
    tags: [],
    folder: 'brand-kit',
    width: null,
    height: null,
    durationSeconds: null,
    createdAt: '2026-08-01T00:00:00Z',
  }
}

function renderGrid() {
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  return mount(AssetGrid, {
    props: { assets: [] },
    global: { plugins: [createPinia(), i18n] },
  })
}

describe('브랜드킷 에셋 업로드', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  /** **핵심.** id 가 빠지면 서버가 URL 을 새로 발급할 근거를 잃는다. */
  it('업로드하면 원본 에셋 id 를 함께 올린다', async () => {
    vi.mocked(assetsApi.upload).mockResolvedValue(uploadResponse(42) as never)
    const wrapper = renderGrid()

    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', {
      value: [new File(['x'], 'logo.png', { type: 'image/png' })],
    })
    await input.trigger('change')
    await flushPromises()

    const added = wrapper.emitted('add')
    expect(added).toBeDefined()
    expect(added![0][0]).toMatchObject({
      assetId: 42,
      url: 'https://r2.test/assets/100/uuid_logo.png?sig=fresh',
    })
  })

  /** 브랜드킷 폴더로 올려야 목록에서 구분된다 — 기존 동작을 지킨다. */
  it('브랜드킷 폴더로 업로드한다', async () => {
    vi.mocked(assetsApi.upload).mockResolvedValue(uploadResponse() as never)
    const wrapper = renderGrid()

    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', {
      value: [new File(['x'], 'logo.png', { type: 'image/png' })],
    })
    await input.trigger('change')
    await flushPromises()

    expect(assetsApi.upload).toHaveBeenCalledWith(expect.any(File), 'brand-kit')
  })
})

describe('브랜드킷 저장 요청', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(brandKitApi.list).mockResolvedValue([] as never)
    // 서버는 언제나 완전한 브랜드킷을 돌려준다. 저장 뒤 상태를 그 응답으로 다시 세운다.
    const saved = {
      id: 1, name: '기본', primaryColor: '#000', secondaryColor: '#111', accentColor: '#222',
      fontFamily: 'Pretendard', logoUrl: 'https://r2.test/fresh', introTemplateUrl: null,
      outroTemplateUrl: null, watermarkUrl: null, guidelines: null,
      colors: [], fonts: [],
      assets: [{
        id: 1, name: '로고.png', type: 'logo', url: 'https://r2.test/fresh',
        format: 'PNG', size: '1 KB', uploadedAt: '2026-08-01T00:00:00Z', assetId: 42,
      }],
      isDefault: true, createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-01T00:00:00Z',
    }
    vi.mocked(brandKitApi.create).mockResolvedValue(saved as never)
    vi.mocked(brandKitApi.update).mockResolvedValue(saved as never)
  })

  /** 화면이 들고 있던 `assetId` 가 저장 요청까지 살아 있어야 서버가 검증·재발급할 수 있다. */
  it('저장 요청의 assets 에 assetId 가 포함된다', async () => {
    const store = useBrandKitStore()
    store.addAsset({
      name: '로고.png',
      type: 'logo',
      url: 'https://r2.test/assets/100/uuid_logo.png?sig=fresh',
      format: 'PNG',
      size: '1 KB',
      uploadedAt: '2026-08-01T00:00:00Z',
      assetId: 42,
    })

    await store.saveBrandKit()

    const request = vi.mocked(brandKitApi.create).mock.calls[0][0] as { assets: Array<{ assetId?: number }> }
    expect(request.assets.some((a) => a.assetId === 42)).toBe(true)
  })

  /**
   * 서버 응답의 `assetId` 는 화면 상태까지 그대로 와야 한다 — 다음 저장에서 다시 보내야
   * 참조가 끊기지 않는다.
   */
  it('서버 응답의 assetId 를 화면 상태로 가져온다', async () => {
    vi.mocked(brandKitApi.list).mockResolvedValue([
      {
        id: 1, name: '기본', primaryColor: '#000', secondaryColor: '#111', accentColor: '#222',
        fontFamily: 'Pretendard', logoUrl: 'https://r2.test/fresh', introTemplateUrl: null,
        outroTemplateUrl: null, watermarkUrl: null, guidelines: null,
        colors: [], fonts: [],
        assets: [{
          id: 1, name: '로고.png', type: 'logo', url: 'https://r2.test/fresh',
          format: 'PNG', size: '1 KB', uploadedAt: '2026-08-01T00:00:00Z', assetId: 42,
        }],
        isDefault: true, createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-01T00:00:00Z',
      },
    ] as never)
    const store = useBrandKitStore()

    await store.fetchBrandKits()

    expect(store.brandKit.assets[0].assetId).toBe(42)
    expect(store.brandKit.assets[0].url).toBe('https://r2.test/fresh')
  })

  /**
   * `assetId` 가 없는 예전 항목도 그대로 읽혀야 한다. 못 읽으면 기존 브랜드킷이
   * 통째로 빈 목록이 된다.
   */
  it('assetId 가 없는 예전 항목도 그대로 읽는다', async () => {
    vi.mocked(brandKitApi.list).mockResolvedValue([
      {
        id: 1, name: '기본', primaryColor: '#000', secondaryColor: '#111', accentColor: '#222',
        fontFamily: 'Pretendard', logoUrl: 'https://r2.test/legacy', introTemplateUrl: null,
        outroTemplateUrl: null, watermarkUrl: null, guidelines: null,
        colors: [], fonts: [],
        assets: [{
          id: 1, name: '로고.png', type: 'logo', url: 'https://r2.test/legacy',
          format: 'PNG', size: '1 KB', uploadedAt: '2026-08-01T00:00:00Z',
        }],
        isDefault: true, createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-01T00:00:00Z',
      },
    ] as never)
    const store = useBrandKitStore()

    await store.fetchBrandKits()

    expect(store.brandKit.assets).toHaveLength(1)
    expect(store.brandKit.assets[0].assetId).toBeUndefined()
  })
})
