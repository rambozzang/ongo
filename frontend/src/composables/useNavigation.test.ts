import { beforeEach, describe, expect, it, vi } from 'vitest'
import { capabilitiesApi } from '@/api/capabilities'
import { ROUTE_CAPABILITIES } from '@/router/capability'
import { useNavigation } from './useNavigation'

const mocks = vi.hoisted(() => ({
  route: { path: '/today' },
  auth: { isAuthenticated: true, user: { value: { role: 'USER' } } },
}))

vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mocks.auth,
}))

vi.mock('@/composables/useLocale', () => ({
  useLocale: () => ({ t: (key: string) => key }),
}))

vi.mock('@/api/capabilities', () => ({
  capabilitiesApi: { list: vi.fn() },
}))

const paths = (navigation: ReturnType<typeof useNavigation>) =>
  navigation.allNavItems.value.map((item) => item.to)

describe('useNavigation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('does not flash static menu items while capability sync is pending', async () => {
    type Capability = { key: string; enabled: boolean }
    let release: (items: Capability[]) => void = () => undefined
    const pending = new Promise<Capability[]>((resolve) => {
      release = resolve
    })
    vi.mocked(capabilitiesApi.list).mockReturnValueOnce(pending)

    const navigation = useNavigation()
    expect(paths(navigation)).toEqual([])

    release([{ key: 'today', enabled: true }])
    await vi.waitFor(() => expect(paths(navigation)).toEqual(['/today']))
  })

  it('keeps the working creator menu and never exposes an ideas surface', async () => {
    vi.mocked(capabilitiesApi.list).mockResolvedValue([
      { key: 'today', enabled: true },
      { key: 'compose', enabled: true },
      { key: 'manual', enabled: true },
      { key: 'settings-v2', enabled: true },
      { key: 'ideas', enabled: true },
    ])

    const navigation = useNavigation()
    await vi.waitFor(() => expect(capabilitiesApi.list).toHaveBeenCalledTimes(1))

    expect(paths(navigation)).toEqual(['/today', '/compose', '/manual', '/settings-v2'])
    expect(paths(navigation)).not.toContain('/ideas')
    expect(navigation.capabilityError.value).toBeNull()
  })

  it('fails closed and exposes a retry when capability sync fails', async () => {
    vi.mocked(capabilitiesApi.list).mockRejectedValueOnce(new Error('capability offline'))
      .mockResolvedValueOnce([{ key: 'today', enabled: true }])

    const navigation = useNavigation()
    await vi.waitFor(() => expect(navigation.capabilityError.value).toBe('capability offline'))
    expect(paths(navigation)).toEqual([])
    expect(navigation.navGroups.value).toEqual([])

    await navigation.retryCapabilities()

    expect(paths(navigation)).toEqual(['/today'])
    expect(navigation.capabilityError.value).toBeNull()
  })

  /*
   * A menu entry added twice renders twice — the sidebar shows the same
   * destination on two rows and the user has to guess whether they differ.
   * Nothing downstream dedupes: the capability filter maps over the list and
   * keeps both. `/competitors` shipped duplicated inside the analytics group,
   * so hold every destination to a single row.
   *
   * Enabling every capability the route table knows about keeps this honest
   * when a new menu item lands — a narrower fixture would just hide it.
   */
  it('never lists the same destination twice', async () => {
    const everyCapability = [...new Set(ROUTE_CAPABILITIES.map(([, key]) => key))]
    vi.mocked(capabilitiesApi.list).mockResolvedValue(
      everyCapability.map((key) => ({ key, enabled: true })),
    )

    const navigation = useNavigation()
    await vi.waitFor(() => expect(paths(navigation).length).toBeGreaterThan(0))

    const destinations = paths(navigation)
    const duplicated = [...new Set(destinations.filter((to, i) => destinations.indexOf(to) !== i))]

    expect(duplicated).toEqual([])
  })
})
