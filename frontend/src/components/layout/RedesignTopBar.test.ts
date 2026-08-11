import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import RedesignTopBar from './RedesignTopBar.vue'

const mocks = vi.hoisted(() => ({
  auth: {
    user: { nickname: 'Creator', name: 'Creator', email: 'creator@example.com' },
    logout: vi.fn(),
  },
}))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => mocks.auth }))
vi.mock('@/composables/useLocale', () => ({
  useLocale: () => ({ t: (key: string) => key }),
}))

describe('RedesignTopBar account menu', () => {
  beforeEach(() => {
    mocks.auth.logout.mockReset()
  })

  it('exposes settings and logout from the active redesign shell', async () => {
    const wrapper = mount(RedesignTopBar, {
      props: { title: 'Today' },
      global: {
        stubs: {
          RouterLink: {
            props: ['to'],
            template: '<a :href="to"><slot /></a>',
          },
        },
      },
    })

    const profileButton = wrapper.find('button[aria-haspopup="menu"]')
    expect(profileButton.exists()).toBe(true)

    await profileButton.trigger('click')

    expect(wrapper.find('[role="menu"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/settings-v2"]').exists()).toBe(true)

    await wrapper.find('button[role="menuitem"]').trigger('click')

    expect(mocks.auth.logout).toHaveBeenCalledOnce()
  })
})
