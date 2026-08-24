import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import AssetUploadModal from './AssetUploadModal.vue'
import { useAssetsStore } from '@/stores/assets'
import koMessages from '@/locales/ko/common.json'

/**
 * 에셋 업로드는 저장 공간 한도가 실제로 걸리는 화면이다.
 *
 * 업로드 init·에셋·URL 임포트 세 경로에 quota 가 붙으면서 자주 발생하는 마찰이 됐는데,
 * 사유만 보여주고 끝나면 사용자는 "그래서 뭘 해야 하나"를 알 수 없다. 반대로 돈을 내도
 * 풀리지 않는 오류(검증 실패·크레딧)에 결제를 권하면 오도가 된다 — 그 경계를 고정한다.
 */
describe('AssetUploadModal 업그레이드 안내', () => {
  const UPGRADE_LABEL = koMessages.subscription.changePlan

  function serverError(message: string, code: string) {
    return Object.assign(new Error(message), {
      response: { status: 400, data: { success: false, message, error: code } },
    })
  }

  async function uploadFailingWith(error: unknown) {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useAssetsStore()
    // 업로드만 실패시키고 나머지 스토어 동작은 건드리지 않는다.
    vi.spyOn(store, 'uploadAsset').mockRejectedValue(error)

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/subscription', component: { template: '<div />' } },
      ],
    })
    await router.push('/')
    await router.isReady()

    const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
    const wrapper = mount(AssetUploadModal, {
      props: { modelValue: true },
      global: { plugins: [pinia, router, i18n], stubs: { Teleport: true, teleport: true } },
    })

    // 파일을 고른 뒤 업로드를 누른 것과 같은 상태로 만든다.
    const vm = wrapper.vm as unknown as {
      selectedFiles: File[]
      handleUpload: () => Promise<void>
    }
    vm.selectedFiles = [new File(['x'], 'a.png', { type: 'image/png' })]
    await vm.handleUpload()
    await flushPromises()
    return wrapper
  }

  const upgradeLink = (wrapper: Awaited<ReturnType<typeof uploadFailingWith>>) =>
    wrapper.findAll('a').find((a) => a.text().includes(UPGRADE_LABEL))

  beforeEach(() => {
    vi.restoreAllMocks()
  })

  /*
   * 저장 공간 한도는 업그레이드로 풀린다. 사유와 함께 갈 곳을 보여줘야 한다.
   */
  it('저장 공간 한도 거절이면 사유와 함께 /subscription 링크를 보여준다', async () => {
    const reason = '저장 공간 한도를 초과했습니다. 현재 플랜 한도: 1GB'
    const wrapper = await uploadFailingWith(serverError(reason, 'STORAGE_QUOTA_EXCEEDED'))

    expect(wrapper.text()).toContain(reason)
    const link = upgradeLink(wrapper)
    expect(link).toBeDefined()
    expect(link!.attributes('href')).toBe('/subscription')
  })

  it('플랜 한도 거절도 같은 경로를 안내한다', async () => {
    const reason = '월간 업로드 한도를 초과했습니다. 현재 플랜 한도: 5'
    const wrapper = await uploadFailingWith(serverError(reason, 'PLAN_LIMIT_EXCEEDED'))

    expect(wrapper.text()).toContain(reason)
    expect(upgradeLink(wrapper)!.attributes('href')).toBe('/subscription')
  })

  /*
   * 돈을 내도 풀리지 않는 오류에 결제를 권하면 사용자를 오도하는 것이고,
   * 한 번 그러면 정작 필요한 순간의 안내도 믿지 않게 된다.
   */
  it.each([
    ['검증 실패', 'BAD_REQUEST', '파일 형식이 올바르지 않습니다'],
    ['크레딧 부족', 'CREDIT_INSUFFICIENT', '크레딧이 부족합니다'],
    ['인증 실패', 'UNAUTHORIZED', '로그인이 필요합니다'],
  ])('%s 에는 사유만 보여주고 링크를 붙이지 않는다', async (_label, code, reason) => {
    const wrapper = await uploadFailingWith(serverError(reason, code))

    expect(wrapper.text()).toContain(reason)
    expect(upgradeLink(wrapper)).toBeUndefined()
    expect(wrapper.find('a[href="/subscription"]').exists()).toBe(false)
  })

  it('안정 코드가 없는 오류에도 링크를 붙이지 않는다', async () => {
    const wrapper = await uploadFailingWith(new Error('네트워크가 불안정합니다'))

    expect(wrapper.text()).toContain('네트워크가 불안정합니다')
    expect(upgradeLink(wrapper)).toBeUndefined()
  })

  /*
   * 재시도해서 다른 이유로 실패했는데 이전 링크가 남아 있으면, 결제로 풀리지 않는 문제에
   * 결제를 권하는 상태가 된다.
   */
  it('다시 업로드해 다른 오류가 나면 이전 링크가 남지 않는다', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useAssetsStore()
    const upload = vi.spyOn(store, 'uploadAsset')
    upload.mockRejectedValueOnce(serverError('저장 공간 한도를 초과했습니다', 'STORAGE_QUOTA_EXCEEDED'))

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/subscription', component: { template: '<div />' } },
      ],
    })
    await router.push('/')
    await router.isReady()
    const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
    const wrapper = mount(AssetUploadModal, {
      props: { modelValue: true },
      global: { plugins: [pinia, router, i18n], stubs: { Teleport: true, teleport: true } },
    })
    const vm = wrapper.vm as unknown as { selectedFiles: File[]; handleUpload: () => Promise<void> }
    vm.selectedFiles = [new File(['x'], 'a.png', { type: 'image/png' })]

    await vm.handleUpload()
    await flushPromises()
    expect(upgradeLink(wrapper)).toBeDefined()

    upload.mockRejectedValueOnce(serverError('파일 형식이 올바르지 않습니다', 'BAD_REQUEST'))
    vm.selectedFiles = [new File(['x'], 'b.png', { type: 'image/png' })]
    await vm.handleUpload()
    await flushPromises()

    expect(upgradeLink(wrapper)).toBeUndefined()
  })
})
