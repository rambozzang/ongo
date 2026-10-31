import { computed, ref, defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'
import { useDraftProtection } from './useDraftProtection'

function setup() {
  const title = ref('')
  let guard!: ReturnType<typeof useDraftProtection>
  const component = defineComponent({
    setup() {
      guard = useDraftProtection(
        computed(() => title.value),
        computed(() => title.value.length > 0),
      )
      return () => null
    },
  })
  const wrapper = mount(component)
  return { title, guard, wrapper }
}
describe('draft protection', () => {
  it('keeps edits made while saving dirty until their own save is acknowledged', () => {
    const { title, guard, wrapper } = setup()
    title.value = 'First revision'
    const submitted = title.value
    title.value = 'Second revision'
    guard.markSaved(submitted)
    expect(guard.dirty.value).toBe(true)
    guard.markSaved()
    expect(guard.dirty.value).toBe(false)
    title.value = ''
    expect(guard.dirty.value).toBe(true)
    wrapper.unmount()
  })
  it('warns before refresh and removes the listener on unmount', () => {
    const { title, wrapper } = setup()
    title.value = 'Unsaved copy'
    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
    wrapper.unmount()
    const after = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(after)
    expect(after.defaultPrevented).toBe(false)
  })
  it('resolves superseded and unmounted navigation requests instead of leaving them hanging', async () => {
    const { title, guard, wrapper } = setup()
    title.value = 'Unsaved copy'
    const old = guard.requestLeave()
    const current = guard.requestLeave()
    await expect(old).resolves.toBe(false)
    guard.settleLeave(true)
    await expect(current).resolves.toBe(true)
    const abandoned = guard.requestLeave()
    wrapper.unmount()
    await expect(abandoned).resolves.toBe(false)
  })
})
