import { describe, it, expect, vi, beforeEach } from 'vitest'

import { requiredCapabilityForPath } from '@/router/capability'
import router from '@/router'

vi.mock('@/composables/useLocale', () => ({
  useLocale: () => ({ t: (k: string) => k }),
}))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ user: null, isAuthenticated: true }),
}))
vi.mock('@/api/capabilities', () => ({
  capabilitiesApi: { list: vi.fn() },
}))

import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { nextTick } from 'vue'
import { useNavigation } from '@/composables/useNavigation'
import { capabilitiesApi } from '@/api/capabilities'

const TestComp = {
  setup() {
    const nav = useNavigation()
    return { navGroups: nav.navGroups }
  },
  template: '<div />',
}

describe('competitor route + capability + menu', () => {
  it('requiredCapabilityForPath("/competitors") 는 "competitors" 를 반환한다', () => {
    expect(requiredCapabilityForPath('/competitors')).toBe('competitors')
  })

  it('/competitors 라우트가 등록되어 있다', () => {
    const route = router.getRoutes().find((r) => r.path === '/competitors')
    expect(route).toBeDefined()
    expect(route?.name).toBe('competitors')
  })

  describe('사이드바 메뉴 노출', () => {
    let pinia: ReturnType<typeof createPinia>
    let memoryRouter: ReturnType<typeof createRouter>

    beforeEach(() => {
      pinia = createPinia()
      setActivePinia(pinia)
      memoryRouter = createRouter({
        history: createMemoryHistory(),
        routes: [{ path: '/', component: { template: '<div />' } }],
      })
      vi.clearAllMocks()
    })

    it('capability 가 활성화되면 분석 그룹에 /competitors 메뉴가 노출된다', async () => {
      ;(capabilitiesApi.list as any).mockResolvedValue([{ key: 'competitors', enabled: true }])

      const wrapper = mount(TestComp, {
        global: { plugins: [pinia, memoryRouter] },
      })
      await flushPromises()
      await nextTick()

      const groups = wrapper.vm.navGroups as any
      const flat = groups.flatMap((g: any) => [
        ...(g.items ?? []),
        ...(g.subGroups?.flatMap((s: any) => s.items) ?? []),
      ])
      const item = flat.find((i: any) => i.to === '/competitors')
      expect(item).toBeDefined()
      // capabilityKey 가 to 에서 파생되어 'competitors' 가 된다.
      expect(item.capabilityKey).toBe('competitors')
    })
  })
})
