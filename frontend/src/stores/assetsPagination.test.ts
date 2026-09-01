import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { assetsApi } from '@/api/assets'
import { subscriptionApi } from '@/api/subscription'
import { useAssetsStore } from './assets'

/**
 * 에셋 목록의 **페이지네이션과 필터**를 고정한다.
 *
 * ## 무엇이 깨져 있었나
 *
 * `page 0, size 100` 으로 한 번만 읽고 끝냈다. 101 번째 에셋부터는 화면에 아예 나오지
 * 않아 재사용(콘텐츠 만들기)도 삭제도 할 수 없었다 — 방금 만든 판매 기능이 그 지점부터
 * 존재하지 않는 것과 같다.
 *
 * 거르기도 화면에서 했다. 그러면 검색이 "라이브러리에 없다"와 "이 페이지에 없다"를
 * 구분하지 못하고, 총계는 조건과 무관한 숫자가 된다.
 */

vi.mock('@/api/assets', () => ({
  assetsApi: { list: vi.fn(), upload: vi.fn(), update: vi.fn(), delete: vi.fn() },
}))
vi.mock('@/api/subscription', () => ({ subscriptionApi: { getUsage: vi.fn() } }))

function page(count: number, total: number) {
  return {
    assets: Array.from({ length: count }, (_, i) => ({
      id: i + 1,
      filename: `f${i}.mp4`,
      originalFilename: `원본 ${i}.mp4`,
      fileUrl: `https://r2.test/${i}`,
      fileType: 'VIDEO',
      fileSizeBytes: 100,
      mimeType: 'video/mp4',
      tags: [],
      folder: 'default',
      width: null,
      height: null,
      durationSeconds: null,
      createdAt: `2026-08-${String(i + 1).padStart(2, '0')}T00:00:00Z`,
    })),
    totalCount: total,
  }
}

/** 스토어는 생성 시 자동으로 한 번 읽는다. 그 호출을 소진하고 시작한다. */
async function freshStore() {
  const store = useAssetsStore()
  await Promise.resolve()
  await Promise.resolve()
  vi.mocked(assetsApi.list).mockClear()
  return store
}

describe('에셋 목록 페이지네이션', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(assetsApi.list).mockResolvedValue(page(24, 240) as never)
    vi.mocked(subscriptionApi.getUsage).mockResolvedValue({
      uploadsThisMonth: 0, storageUsedMb: 0, storageLimitBytes: 50 * 1024 ** 3,
    } as never)
  })

  /* ── 총계 ─────────────────────────────────────────────────────────── */

  /** 총계는 서버가 조건과 함께 센 값이다. 화면이 받은 건수로 대신하면 거짓이 된다. */
  it('서버가 센 총계를 그대로 쓴다', async () => {
    const store = await freshStore()
    await store.fetchAssets()

    expect(store.totalCount).toBe(240)
    expect(store.totalPages).toBe(10)
    expect(store.hasNextPage).toBe(true)
    expect(store.hasPrevPage).toBe(false)
  })

  /* ── 101 번째 이후 접근 ───────────────────────────────────────────── */

  /**
   * **핵심 회귀.** 예전에는 첫 100 개에서 끝나 그 뒤 에셋에 닿을 방법이 없었다.
   */
  it('다음 페이지로 101 번째 이후 에셋에 닿는다', async () => {
    const store = await freshStore()
    await store.fetchAssets()

    store.nextPage()
    await Promise.resolve()
    expect(store.page).toBe(1)
    expect(assetsApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 24 }))

    store.nextPage()
    store.nextPage()
    store.nextPage()
    await Promise.resolve()
    // 5 페이지(0-indexed 4)면 97~120 번째 — 예전 100 개 창을 넘어선다.
    expect(store.page).toBe(4)
  })

  it('첫 페이지에서는 이전으로 가지 않는다', async () => {
    const store = await freshStore()
    await store.fetchAssets()
    vi.mocked(assetsApi.list).mockClear()

    store.prevPage()

    expect(store.page).toBe(0)
    expect(assetsApi.list).not.toHaveBeenCalled()
  })

  it('마지막 페이지에서는 다음으로 가지 않는다', async () => {
    vi.mocked(assetsApi.list).mockResolvedValue(page(4, 4) as never)
    const store = await freshStore()
    await store.fetchAssets()
    vi.mocked(assetsApi.list).mockClear()

    store.nextPage()

    expect(store.page).toBe(0)
    expect(assetsApi.list).not.toHaveBeenCalled()
  })

  /* ── CRUD 후 서버 정합성 ──────────────────────────────────────────── */

  /**
   * **배열에 밀어 넣으면 페이지 창이 깨진다.**
   *
   * 24개짜리 페이지가 25개가 되고, 그 한 칸만큼 다음 페이지가 밀려 같은 에셋이 두 번
   * 보인다. `totalCount` 도 그대로라 마지막 페이지에 닿지 못한다.
   */
  it('업로드 뒤에는 서버에서 현재 목록을 다시 읽는다', async () => {
    vi.mocked(assetsApi.upload).mockResolvedValue({
      id: 999, filename: 'new.mp4', originalFilename: '새 파일.mp4',
      fileUrl: 'https://r2.test/new', fileType: 'VIDEO', fileSizeBytes: 100,
      mimeType: 'video/mp4', tags: [], folder: 'default',
      width: null, height: null, durationSeconds: null, createdAt: '2026-08-30T00:00:00Z',
    } as never)
    const store = await freshStore()
    await store.fetchAssets()
    vi.mocked(assetsApi.list).mockClear()
    vi.mocked(assetsApi.list).mockResolvedValue(page(24, 241) as never)

    await store.uploadAsset(new File(['x'], 'new.mp4'), [])

    expect(assetsApi.list).toHaveBeenCalled()
    expect(store.totalCount).toBe(241)
    // 페이지 크기를 넘겨 담지 않는다.
    expect(store.filteredAssets).toHaveLength(24)
  })

  /** 새 에셋은 최신순의 맨 앞이다 — 3페이지에 머무르면 방금 올린 파일이 보이지 않는다. */
  it('업로드 뒤에는 첫 페이지로 이동한다', async () => {
    vi.mocked(assetsApi.upload).mockResolvedValue({
      id: 999, filename: 'new.mp4', originalFilename: '새 파일.mp4',
      fileUrl: 'https://r2.test/new', fileType: 'VIDEO', fileSizeBytes: 100,
      mimeType: 'video/mp4', tags: [], folder: 'default',
      width: null, height: null, durationSeconds: null, createdAt: '2026-08-30T00:00:00Z',
    } as never)
    const store = await freshStore()
    await store.fetchAssets()
    store.nextPage()
    store.nextPage()
    await Promise.resolve()
    expect(store.page).toBe(2)

    await store.uploadAsset(new File(['x'], 'new.mp4'), [])

    expect(store.page).toBe(0)
    expect(assetsApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0 }))
  })

  /**
   * **삭제도 마찬가지다.** splice 만 하면 페이지가 23개가 되고 총계는 그대로라
   * 다음 페이지의 첫 건이 건너뛰어진다.
   */
  it('삭제 뒤에는 총계를 서버 값으로 갱신한다', async () => {
    vi.mocked(assetsApi.delete).mockResolvedValue(undefined as never)
    const store = await freshStore()
    await store.fetchAssets()
    expect(store.totalCount).toBe(240)
    vi.mocked(assetsApi.list).mockResolvedValue(page(24, 239) as never)

    await store.deleteAsset(1)

    expect(store.totalCount).toBe(239)
    expect(store.filteredAssets).toHaveLength(24)
  })

  /**
   * 마지막 페이지의 마지막 건을 지우면 그 페이지는 빈다. 그대로 두면 "에셋이 없습니다"
   * 가 뜨고 사용자는 라이브러리가 비었다고 읽는다 — 실제로는 앞 페이지에 있다.
   */
  it('삭제로 현재 페이지가 비면 이전 페이지로 옮긴다', async () => {
    vi.mocked(assetsApi.delete).mockResolvedValue(undefined as never)
    vi.mocked(assetsApi.list).mockResolvedValue(page(24, 25) as never)
    const store = await freshStore()
    await store.fetchAssets()
    store.nextPage()
    await Promise.resolve()
    // 2페이지에 1건만 있는 상태
    vi.mocked(assetsApi.list).mockResolvedValue(page(1, 25) as never)
    await store.fetchAssets()
    expect(store.page).toBe(1)

    // 그 1건을 지우면 2페이지가 빈다.
    vi.mocked(assetsApi.list).mockResolvedValueOnce(page(0, 24) as never)
    vi.mocked(assetsApi.list).mockResolvedValue(page(24, 24) as never)
    await store.deleteAsset(1)

    expect(store.page).toBe(0)
    expect(store.filteredAssets).toHaveLength(24)
  })

  /** 첫 페이지가 비는 것은 정상이다 — 라이브러리가 실제로 비었다는 뜻이라 옮기지 않는다. */
  it('첫 페이지가 비어도 페이지를 옮기지 않는다', async () => {
    vi.mocked(assetsApi.delete).mockResolvedValue(undefined as never)
    vi.mocked(assetsApi.list).mockResolvedValue(page(1, 1) as never)
    const store = await freshStore()
    await store.fetchAssets()

    vi.mocked(assetsApi.list).mockResolvedValue(page(0, 0) as never)
    await store.deleteAsset(1)

    expect(store.page).toBe(0)
    expect(store.totalCount).toBe(0)
  })

  /**
   * **재조회가 실패하면 페이지를 옮기지 않는다.**
   *
   * 실패해도 목록은 마지막으로 확인된 내용을 지키므로 보통은 비어 있지 않다. 그런데
   * 직전 조회가 **성공적으로 빈 페이지**를 돌려준 뒤(총계가 낡아 있던 경우) 재조회가
   * 실패하면, 비어 있음과 실패가 겹친다. 그때 페이지를 옮기면 **오류가 이동으로 둔갑해**
   * 사용자는 조회가 실패했다는 사실을 모른 채 다른 페이지를 보게 된다.
   */
  it('빈 페이지에서 재조회가 실패하면 페이지를 옮기지 않는다', async () => {
    vi.mocked(assetsApi.delete).mockResolvedValue(undefined as never)
    const store = await freshStore()
    await store.fetchAssets()
    store.nextPage()
    await Promise.resolve()

    // 2페이지가 성공적으로 비어 있는 상태를 만든다.
    vi.mocked(assetsApi.list).mockResolvedValue(page(0, 240) as never)
    await store.fetchAssets()
    expect(store.page).toBe(1)
    expect(store.filteredAssets).toHaveLength(0)
    expect(store.loadError).toBeNull()

    // 그 상태에서 재조회가 실패한다.
    vi.mocked(assetsApi.list).mockRejectedValue(new Error('네트워크 오류'))
    await store.deleteAsset(1)

    expect(store.page).toBe(1)
    expect(store.loadError).toBe('네트워크 오류')
  })

  /** 목록이 남아 있는 평범한 실패에서도 마찬가지다. */
  it('삭제 후 재조회가 실패해도 페이지를 옮기지 않는다', async () => {
    vi.mocked(assetsApi.delete).mockResolvedValue(undefined as never)
    const store = await freshStore()
    await store.fetchAssets()
    store.nextPage()
    await Promise.resolve()
    expect(store.page).toBe(1)

    vi.mocked(assetsApi.list).mockRejectedValue(new Error('네트워크 오류'))
    await store.deleteAsset(1)

    expect(store.page).toBe(1)
    expect(store.loadError).toBe('네트워크 오류')
  })

  /* ── 일괄 삭제 ────────────────────────────────────────────────────── */

  /**
   * 예전에는 건마다 `deleteAsset` 을 병렬로 불러 사용량 조회가 N 번 나갔고, 응답 순서가
   * 보장되지 않아 **더 오래된 측정치가 마지막에 도착해** 남는 일이 있었다.
   */
  it('일괄 삭제는 재조회와 사용량 갱신을 한 번만 한다', async () => {
    vi.mocked(assetsApi.delete).mockResolvedValue(undefined as never)
    const store = await freshStore()
    await store.fetchAssets()
    vi.mocked(assetsApi.list).mockClear()
    vi.mocked(subscriptionApi.getUsage).mockClear()
    vi.mocked(assetsApi.list).mockResolvedValue(page(21, 237) as never)

    await store.bulkDelete([1, 2, 3])

    expect(assetsApi.delete).toHaveBeenCalledTimes(3)
    expect(assetsApi.list).toHaveBeenCalledTimes(1)
    expect(subscriptionApi.getUsage).toHaveBeenCalledTimes(1)
    expect(store.totalCount).toBe(237)
  })

  /**
   * **일부가 거절돼도 서버와 다시 맞춘다.**
   *
   * 브랜드 키트가 쓰고 있는 에셋은 서버가 거절한다(`ASSET_IN_USE`). 그때 `Promise.all` 이
   * 곧바로 실패하는데, 이미 지워진 건들이 있으므로 거기서 멈추면 총계와 페이지가 어긋난
   * 채 남는다.
   */
  it('일괄 삭제 중 일부가 거절돼도 목록과 총계를 다시 읽는다', async () => {
    vi.mocked(assetsApi.delete).mockImplementation(async (id: number) => {
      if (id === 2) throw new Error('브랜드 키트에서 사용 중이라 삭제할 수 없습니다: 여름 브랜드.')
      return undefined as never
    })
    const store = await freshStore()
    await store.fetchAssets()
    vi.mocked(assetsApi.list).mockClear()
    vi.mocked(subscriptionApi.getUsage).mockClear()
    vi.mocked(assetsApi.list).mockResolvedValue(page(22, 238) as never)

    await expect(store.bulkDelete([1, 2, 3])).rejects.toThrow('브랜드 키트에서 사용 중')

    expect(assetsApi.list).toHaveBeenCalledTimes(1)
    expect(subscriptionApi.getUsage).toHaveBeenCalledTimes(1)
    expect(store.totalCount).toBe(238)
  })

  /**
   * 선택은 **통째로 비우지 않는다.** 지운 건만 빠지므로 남은 선택이 정확히
   * "지우지 못한 것들"이 된다 — 사용자가 무엇을 처리해야 하는지 그대로 보인다.
   */
  it('거절된 항목만 선택에 남는다', async () => {
    vi.mocked(assetsApi.delete).mockImplementation(async (id: number) => {
      if (id === 2) throw new Error('브랜드 키트에서 사용 중')
      return undefined as never
    })
    const store = await freshStore()
    await store.fetchAssets()
    store.selectAll([1, 2, 3])

    await store.bulkDelete([1, 2, 3]).catch(() => {})

    expect([...store.selectedAssets]).toEqual([2])
  })

  /** 일괄 삭제 뒤 선택은 비운다 — 지워진 id 가 남으면 "3개 선택됨" 이 거짓이 된다. */
  it('일괄 삭제는 선택 상태를 비운다', async () => {
    vi.mocked(assetsApi.delete).mockResolvedValue(undefined as never)
    const store = await freshStore()
    await store.fetchAssets()
    store.selectAll([1, 2, 3])
    expect(store.selectedAssets.size).toBe(3)

    await store.bulkDelete([1, 2, 3])

    expect(store.selectedAssets.size).toBe(0)
  })

  /** 단건 삭제는 **그 항목만** 선택에서 뺀다 — 나머지 선택은 지켜야 한다. */
  it('단건 삭제는 다른 선택을 지우지 않는다', async () => {
    vi.mocked(assetsApi.delete).mockResolvedValue(undefined as never)
    const store = await freshStore()
    await store.fetchAssets()
    store.selectAll([1, 2, 3])

    await store.deleteAsset(2)

    expect([...store.selectedAssets].sort()).toEqual([1, 3])
  })

  /* ── 필터 ─────────────────────────────────────────────────────────── */

  /** 거르기는 서버가 한다 — 그래야 검색이 전체 라이브러리를 본다. */
  it('필터를 서버 파라미터로 넘긴다', async () => {
    const store = await freshStore()
    store.filter = { type: 'VIDEO', search: '여름', tags: ['logo'] }
    await nextTick()
    await Promise.resolve()

    expect(assetsApi.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ fileType: 'VIDEO', search: '여름', tag: 'logo' }),
    )
  })

  /**
   * **조건이 바뀌면 첫 페이지로 되돌린다.**
   *
   * 3 페이지를 보다가 조건을 좁히면 결과가 한 페이지뿐일 수 있다. 페이지를 그대로 두면
   * 빈 화면이 나오고 사용자는 "검색 결과가 없다"로 읽는다 — 실제로는 있는데 3 페이지에
   * 없을 뿐이다.
   */
  it('필터가 바뀌면 첫 페이지로 되돌린다', async () => {
    const store = await freshStore()
    await store.fetchAssets()
    store.nextPage()
    store.nextPage()
    await Promise.resolve()
    expect(store.page).toBe(2)

    store.filter = { type: 'IMAGE' }
    await nextTick()
    await Promise.resolve()

    expect(store.page).toBe(0)
    expect(assetsApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, fileType: 'IMAGE' }))
  })

  /** 조건이 없으면 조건 파라미터를 보내지 않는다 — 빈 문자열은 서버에서 다른 뜻이 된다. */
  it('조건이 없으면 필터 파라미터를 보내지 않는다', async () => {
    const store = await freshStore()
    await store.fetchAssets()

    const params = vi.mocked(assetsApi.list).mock.calls[0][0]
    expect(params).not.toHaveProperty('fileType')
    expect(params).not.toHaveProperty('search')
    expect(params).not.toHaveProperty('tag')
  })

  /**
   * 서버가 이미 걸렀으므로 화면이 다시 거르면 안 된다 — 총계와 화면 건수가 어긋난다.
   */
  it('받은 목록을 화면에서 다시 거르지 않는다', async () => {
    vi.mocked(assetsApi.list).mockResolvedValue(page(3, 3) as never)
    const store = await freshStore()
    store.filter = { type: 'AUDIO' }
    await nextTick()
    await Promise.resolve()
    await Promise.resolve()

    // 서버가 VIDEO 를 돌려줬지만(테스트 픽스처) 화면은 그것을 그대로 그린다.
    expect(store.filteredAssets).toHaveLength(3)
  })

  /** 실패한 요청은 빈 라이브러리가 아니다 — 마지막으로 확인된 목록을 지키는 기존 정책. */
  it('조회 실패는 목록을 비우지 않는다', async () => {
    const store = await freshStore()
    await store.fetchAssets()
    expect(store.filteredAssets).toHaveLength(24)

    vi.mocked(assetsApi.list).mockRejectedValue(new Error('네트워크 오류'))
    await store.fetchAssets()

    expect(store.filteredAssets).toHaveLength(24)
    expect(store.loadError).toBe('네트워크 오류')
  })
})
