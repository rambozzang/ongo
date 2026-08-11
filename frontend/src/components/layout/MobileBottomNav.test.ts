import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import MobileBottomNav from './MobileBottomNav.vue'
import { capabilitiesApi } from '@/api/capabilities'

const mocks = vi.hoisted(() => ({
  auth: { isAuthenticated: true, user: { value: { role: 'USER' } } },
}))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => mocks.auth }))
vi.mock('@/composables/useLocale', () => ({
  useLocale: () => ({ t: (key: string) => key }),
}))
vi.mock('@/api/capabilities', () => ({ capabilitiesApi: { list: vi.fn() } }))
// 시트는 이 테스트의 대상이 아니다. 하단 바가 무엇을 노출하는지만 본다.
vi.mock('@/components/layout/MobileMenuSheet.vue', () => ({
  default: { name: 'MobileMenuSheet', template: '<div />', props: ['modelValue'] },
}))

type Capability = { key: string; enabled: boolean }

const stub = { template: '<div />' }

async function renderNav() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/today', component: stub },
      { path: '/inbox-v2', component: stub },
      { path: '/compose', component: stub },
      { path: '/channels-v2', component: stub },
    ],
  })
  await router.push('/today')
  await router.isReady()

  const wrapper = mount(MobileBottomNav, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

const linkPaths = (wrapper: Awaited<ReturnType<typeof renderNav>>) =>
  wrapper.findAll('a').map((a) => a.attributes('href'))

/**
 * 하단 바는 모바일에서 사실상 유일한 상시 내비게이션이다.
 *
 * useNavigation 은 서버가 활성 기능 목록을 주지 못하면 fail-closed 하는데, 이 바가
 * 정적 목록을 그대로 렌더하면 레일과 전체 메뉴가 닫혀 있는 동안에도 모바일에서는
 * 비활성 기능으로 들어갈 수 있다. 그 차이를 고정한다.
 */
describe('MobileBottomNav capability 게이팅', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('서버가 활성화한 항목만 노출한다', async () => {
    vi.mocked(capabilitiesApi.list).mockResolvedValue([
      { key: 'today', enabled: true },
      { key: 'compose', enabled: true },
      { key: 'inbox-v2', enabled: false },
      { key: 'channels-v2', enabled: false },
    ] as Capability[] as never)

    const wrapper = await renderNav()

    expect(linkPaths(wrapper)).toEqual(['/today', '/compose'])
    // 비활성 기능으로 가는 입구가 남아 있으면 안 된다.
    expect(linkPaths(wrapper)).not.toContain('/inbox-v2')
  })

  it('capability 동기화가 실패하면 링크를 노출하지 않는다 (fail-closed)', async () => {
    vi.mocked(capabilitiesApi.list).mockRejectedValue(new Error('capability offline'))

    const wrapper = await renderNav()

    expect(linkPaths(wrapper)).toEqual([])
    // 전체 메뉴 버튼은 남는다 — 목적지가 아니라 시트를 여는 버튼이고,
    // 시트 내부도 같은 규칙으로 필터링된다.
    expect(wrapper.find('button[aria-haspopup="dialog"]').exists()).toBe(true)
  })

  it('전부 활성화되면 기존 4개 순서를 그대로 유지한다', async () => {
    vi.mocked(capabilitiesApi.list).mockResolvedValue([
      { key: 'today', enabled: true },
      { key: 'inbox-v2', enabled: true },
      { key: 'compose', enabled: true },
      { key: 'channels-v2', enabled: true },
    ] as Capability[] as never)

    const wrapper = await renderNav()

    expect(linkPaths(wrapper)).toEqual(['/today', '/inbox-v2', '/compose', '/channels-v2'])
  })
})
