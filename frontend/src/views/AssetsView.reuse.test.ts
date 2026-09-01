import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import AssetsView from './AssetsView.vue'
import AssetCard from '@/components/assets/AssetCard.vue'
import { assetsApi } from '@/api/assets'
import { subscriptionApi } from '@/api/subscription'
import { videoApi } from '@/api/video'
import koMessages from '@/locales/ko/common.json'

/**
 * 라이브러리에서 **작성 화면으로 이어지는 한 걸음**을 고정한다.
 *
 * 이 동선이 없으면 에셋은 유료 저장공간만 쓰고 아무 데도 쓰이지 못한다. 반대로 잘못
 * 연결하면 실패했는데 작성 화면으로 보내 버려, 사용자는 빈 화면에서 원인을 알 수 없다.
 * 그래서 **성공했을 때만** 이동하는지를 실제 렌더와 라우터로 확인한다.
 */

vi.mock('@/api/assets', () => ({
  assetsApi: { list: vi.fn(), upload: vi.fn(), update: vi.fn(), delete: vi.fn() },
}))
vi.mock('@/api/subscription', () => ({ subscriptionApi: { getUsage: vi.fn() } }))
vi.mock('@/api/video', () => ({ videoApi: { createFromAsset: vi.fn() } }))

const notifyError = vi.fn()
vi.mock('@/composables/useNotification', () => ({
  useNotification: () => ({ success: vi.fn(), error: notifyError, info: vi.fn(), warning: vi.fn() }),
}))

function assetRow(overrides: Record<string, unknown> = {}) {
  return {
    id: 7,
    filename: 'uuid_clip.mp4',
    originalFilename: '여름 브이로그.mp4',
    fileUrl: 'https://r2.test/assets/100/uuid_clip.mp4?sig=fresh',
    fileType: 'VIDEO',
    fileSizeBytes: 50_000_000,
    mimeType: 'video/mp4',
    tags: [],
    folder: 'default',
    width: null,
    height: null,
    durationSeconds: null,
    createdAt: '2026-08-01T00:00:00Z',
    ...overrides,
  }
}

async function renderAssets(
  rows: Array<Record<string, unknown>> = [assetRow()],
  totalCount = rows.length,
) {
  setActivePinia(createPinia())
  vi.mocked(assetsApi.list).mockResolvedValue({ assets: rows, totalCount } as never)

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/assets', component: { template: '<div />' } },
      { path: '/compose', component: { template: '<div />' } },
    ],
  })
  await router.push('/assets')
  await router.isReady()

  const wrapper = mount(AssetsView, {
    global: {
      plugins: [
        createPinia(),
        router,
        createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } }),
      ],
      stubs: {
        PageGuide: true,
        SectionCard: { template: '<section><slot /></section>' },
        AssetUploadModal: true,
        AssetPreviewModal: true,
        ConfirmModal: true,
      },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

/** 카드가 내보내는 이벤트로 확인한다 — 아이콘 마크업이 바뀌어도 계약은 남는다. */
function firstCard(wrapper: Awaited<ReturnType<typeof renderAssets>>['wrapper']) {
  return wrapper.findAllComponents(AssetCard)[0]
}

describe('AssetsView 에셋 재사용', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    notifyError.mockClear()
    vi.mocked(subscriptionApi.getUsage).mockResolvedValue({
      uploadsThisMonth: 0, storageUsedMb: 0, storageLimitBytes: 50 * 1024 ** 3,
    } as never)
  })

  it('에셋 라이브러리 제목을 번역된 문구로 보여준다', async () => {
    const { wrapper } = await renderAssets()

    expect(wrapper.text()).toContain('에셋 라이브러리')
    expect(wrapper.text()).not.toContain('assets.title')
  })

  /* ── 액션 노출 ────────────────────────────────────────────────────── */

  /**
   * **영상 에셋에만 보인다.** 서버가 `fileType != VIDEO` 를 거절하므로, 다른 타입에
   * 버튼을 두면 눌러 봐야 실패하는 자리를 만드는 셈이다.
   */
  it('영상 에셋에는 콘텐츠 만들기 액션이 있다', async () => {
    const { wrapper } = await renderAssets()

    expect(wrapper.find('button[title="콘텐츠 만들기"]').exists()).toBe(true)
  })

  it('이미지·오디오·템플릿 에셋에는 액션이 없다', async () => {
    for (const fileType of ['IMAGE', 'AUDIO', 'TEMPLATE']) {
      const { wrapper } = await renderAssets([assetRow({ fileType })])

      expect(
        wrapper.find('button[title="콘텐츠 만들기"]').exists(),
        `${fileType} 에 승격 버튼이 보인다`,
      ).toBe(false)
    }
  })

  /* ── 성공 ─────────────────────────────────────────────────────────── */

  /** **핵심.** 서버가 만들어 준 videoId 로 작성 화면에 진입해야 이어서 편집·예약할 수 있다. */
  it('성공하면 서버가 만든 초안으로 작성 화면에 진입한다', async () => {
    vi.mocked(videoApi.createFromAsset).mockResolvedValue({ videoId: 91 } as never)
    const { wrapper, router } = await renderAssets()

    await firstCard(wrapper).vm.$emit('use', 7)
    await flushPromises()

    expect(videoApi.createFromAsset).toHaveBeenCalledWith(7)
    expect(router.currentRoute.value.path).toBe('/compose')
    expect(router.currentRoute.value.query.videoId).toBe('91')
  })

  /** 로컬 File 을 지어내 업로드를 흉내내지 않는다 — 이미 스토리지에 있는 파일이다. */
  it('승격은 업로드 API 를 다시 부르지 않는다', async () => {
    vi.mocked(videoApi.createFromAsset).mockResolvedValue({ videoId: 91 } as never)
    const { wrapper } = await renderAssets()

    await firstCard(wrapper).vm.$emit('use', 7)
    await flushPromises()

    expect(assetsApi.upload).not.toHaveBeenCalled()
  })

  /* ── 삭제 거절 안내 ───────────────────────────────────────────────── */

  /**
   * **핵심 회귀.** 예전에는 `assetsStore.deleteAsset(id)` 를 `await` 없이 불러, 거절되면
   * 처리되지 않은 프라미스가 되고 화면에는 아무것도 남지 않았다 — 모달은 닫히고 에셋은
   * 그대로라 사용자는 눌렀는지조차 알 수 없었다.
   */
  it('삭제가 거절되면 서버가 준 사유를 보여 준다', async () => {
    vi.mocked(assetsApi.delete).mockRejectedValue(
      new Error('브랜드 키트에서 사용 중이라 삭제할 수 없습니다: 여름 브랜드. 해당 브랜드 키트에서 먼저 교체하거나 제거해 주세요.'),
    )
    const { wrapper } = await renderAssets()

    await firstCard(wrapper).vm.$emit('delete', 7)
    await flushPromises()
    // 확인 모달의 확인을 누른다.
    wrapper.findComponent({ name: 'ConfirmModal' }).vm.$emit('confirm')
    await flushPromises()

    expect(notifyError).toHaveBeenCalledWith(
      expect.stringContaining('브랜드 키트에서 사용 중이라 삭제할 수 없습니다'),
    )
  })

  /** 일괄 삭제도 같은 문을 지나야 한다 — 단건만 고치면 일괄이 조용히 실패한다. */
  it('일괄 삭제 거절도 사유를 보여 준다', async () => {
    vi.mocked(assetsApi.delete).mockRejectedValue(new Error('브랜드 키트에서 사용 중이라 삭제할 수 없습니다: 여름 브랜드.'))
    const { wrapper } = await renderAssets()

    await firstCard(wrapper).vm.$emit('select', 7)
    await flushPromises()
    const bulkButton = wrapper.findAll('button').find((b) => b.text().includes('삭제'))
    expect(bulkButton).toBeDefined()
    await bulkButton!.trigger('click')
    await flushPromises()
    wrapper.findComponent({ name: 'ConfirmModal' }).vm.$emit('confirm')
    await flushPromises()

    expect(notifyError).toHaveBeenCalledWith(
      expect.stringContaining('브랜드 키트에서 사용 중이라 삭제할 수 없습니다'),
    )
  })

  /** 성공 경로는 그대로다 — 안내가 붙었다고 정상 삭제가 막히면 안 된다. */
  it('삭제에 성공하면 오류를 띄우지 않는다', async () => {
    vi.mocked(assetsApi.delete).mockResolvedValue(undefined as never)
    const { wrapper } = await renderAssets()

    await firstCard(wrapper).vm.$emit('delete', 7)
    await flushPromises()
    wrapper.findComponent({ name: 'ConfirmModal' }).vm.$emit('confirm')
    await flushPromises()

    expect(assetsApi.delete).toHaveBeenCalledWith(7)
    expect(notifyError).not.toHaveBeenCalled()
  })

  /* ── 페이지네이션 ─────────────────────────────────────────────────── */

  /**
   * **101 번째 이후 에셋에 닿는 유일한 수단이다.** 예전에는 첫 100 개에서 끝나
   * 그 뒤 자산은 승격도 삭제도 할 수 없었다.
   */
  it('여러 페이지가 있으면 이동 버튼을 보여 준다', async () => {
    const { wrapper } = await renderAssets([assetRow()], 240)

    const next = wrapper.findAll('button').find((b) => b.text() === '다음')
    const prev = wrapper.findAll('button').find((b) => b.text() === '이전')

    expect(next).toBeDefined()
    expect(prev).toBeDefined()
    // 첫 페이지에서는 이전이 막혀 있어야 한다.
    expect(prev!.attributes('disabled')).toBeDefined()
  })

  it('다음을 누르면 다음 페이지를 서버에 요청한다', async () => {
    const { wrapper } = await renderAssets([assetRow()], 240)

    await wrapper.findAll('button').find((b) => b.text() === '다음')!.trigger('click')
    await flushPromises()

    expect(assetsApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }))
  })

  /** 한 페이지뿐이면 이동 버튼을 만들지 않는다 — 누를 곳이 없는 버튼을 두지 않는다. */
  it('한 페이지뿐이면 이동 버튼이 없다', async () => {
    const { wrapper } = await renderAssets([assetRow()], 1)

    expect(wrapper.findAll('button').some((b) => b.text() === '다음')).toBe(false)
  })

  /**
   * 총계는 **서버가 센 값**이다. 화면이 받은 건수로 대신하면 24 개를 보여 주면서
   * "총 24 개"라고 말한다.
   */
  it('총계는 서버 값을 보여 준다', async () => {
    const { wrapper } = await renderAssets([assetRow()], 240)

    expect(wrapper.text()).toContain('총 240개')
  })

  /* ── 실패 ─────────────────────────────────────────────────────────── */

  /**
   * **실패하면 이동하지 않는다.** 초안이 없는데 작성 화면으로 보내면 사용자는 빈 화면에서
   * 무엇이 잘못됐는지 알 수 없다. 서버가 준 사유를 그대로 보여 준다.
   */
  it('실패하면 이동하지 않고 서버 사유를 알린다', async () => {
    vi.mocked(videoApi.createFromAsset).mockRejectedValue(
      new Error('영상 에셋만 콘텐츠로 만들 수 있습니다.'),
    )
    const { wrapper, router } = await renderAssets()

    await firstCard(wrapper).vm.$emit('use', 7)
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/assets')
    expect(notifyError).toHaveBeenCalledWith('영상 에셋만 콘텐츠로 만들 수 있습니다.')
  })

  /** 연타로 사본이 두 벌 만들어지면 쿼터가 두 배로 빠진다. */
  it('처리 중에는 다시 요청하지 않는다', async () => {
    let resolvePromotion: (value: unknown) => void = () => {}
    vi.mocked(videoApi.createFromAsset).mockReturnValue(
      new Promise((resolve) => { resolvePromotion = resolve }) as never,
    )
    const { wrapper } = await renderAssets()

    await firstCard(wrapper).vm.$emit('use', 7)
    await firstCard(wrapper).vm.$emit('use', 7)
    await flushPromises()

    expect(videoApi.createFromAsset).toHaveBeenCalledTimes(1)
    resolvePromotion({ videoId: 91 })
    await flushPromises()
  })
})
