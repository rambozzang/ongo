import { describe, expect, it, vi } from 'vitest'
import { capabilitiesApi } from '@/api/capabilities'
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

    await navigation.retryCapabilities()

    expect(paths(navigation)).toEqual(['/today'])
    expect(navigation.capabilityError.value).toBeNull()
  })
})
