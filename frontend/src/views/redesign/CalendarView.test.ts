import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import CalendarView from './CalendarView.vue'
import { scheduleApi } from '@/api/schedule'
import { recurringApi } from '@/api/recurring'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/schedule', () => ({
  scheduleApi: { list: vi.fn(), update: vi.fn(), cancel: vi.fn() },
}))

vi.mock('@/api/recurring', () => ({
  recurringApi: { list: vi.fn(), toggle: vi.fn(), remove: vi.fn() },
}))

function localDateTime(date: Date, hour: number) {
  const value = new Date(date)
  value.setHours(hour, 0, 0, 0)
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}:00`
}

function monday() {
  const value = new Date()
  value.setDate(value.getDate() - ((value.getDay() + 6) % 7))
  value.setHours(0, 0, 0, 0)
  return value
}

function schedule(id: number, hour: number) {
  return {
    id,
    videoId: id + 100,
    videoTitle: `예약 영상 ${id}`,
    thumbnailUrl: null,
    scheduledAt: localDateTime(monday(), hour),
    platforms: [{ platform: 'YOUTUBE', scheduledAt: localDateTime(monday(), hour), status: 'SCHEDULED' }],
    status: 'SCHEDULED',
    createdAt: '2026-08-01T00:00:00',
    updatedAt: '2026-08-01T00:00:00',
  }
}

function recurring() {
  return {
    id: 21,
    videoId: 101,
    name: '매일 업로드',
    frequency: 'DAILY',
    dayOfWeek: null,
    dayOfMonth: null,
    timeOfDay: '09:00:00',
    timezone: 'Asia/Seoul',
    platforms: ['YOUTUBE'],
    titleTemplate: null,
    descriptionTemplate: null,
    tags: [],
    isActive: true,
    nextRunAt: '2099-03-01T09:00:00',
    lastRunAt: null,
    createdAt: null,
    updatedAt: null,
  }
}

async function renderCalendar() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/videos/:id', component: { template: '<div />' } },
      { path: '/compose', component: { template: '<div />' } },
    ],
  })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  await router.push('/compose')
  await router.isReady()
  const wrapper = mount(CalendarView, {
    global: {
      plugins: [pinia, router, i18n],
      stubs: {
        PlatformChip: { template: '<span><slot /></span>' },
        StatusPill: { template: '<span><slot /></span>' },
        ChevronLeftIcon: true,
        ChevronRightIcon: true,
        ConfirmModal: {
          name: 'ConfirmModal',
          props: ['modelValue', 'title', 'message'],
          emits: ['confirm', 'update:modelValue'],
          template: '<div v-if="modelValue" role="dialog">{{ title }} {{ message }}</div>',
        },
      },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('CalendarView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(scheduleApi.list).mockResolvedValue([schedule(1, 9), schedule(2, 11)] as never)
    vi.mocked(recurringApi.list).mockResolvedValue([recurring()] as never)
  })

  it('renders scheduled content and opens the video detail from a calendar block', async () => {
    const { wrapper, router } = await renderCalendar()
    expect(wrapper.text()).toContain('예약 영상 1')
    expect(wrapper.text()).toContain('매일 업로드')
    expect(scheduleApi.list).toHaveBeenCalledOnce()

    await wrapper.find('.schedule-open').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/videos/101')
  })

  it('requires confirmation before cancelling an unstarted scheduled post', async () => {
    const { wrapper } = await renderCalendar()
    const cancel = wrapper.find('button[aria-label*="예약 영상 1"][aria-label*="취소"]')
    expect(cancel.exists()).toBe(true)

    await cancel.trigger('click')
    expect(scheduleApi.cancel).not.toHaveBeenCalled()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)

    vi.mocked(scheduleApi.cancel).mockResolvedValue(undefined as never)
    wrapper.findComponent({ name: 'ConfirmModal' }).vm.$emit('confirm')
    await flushPromises()

    expect(scheduleApi.cancel).toHaveBeenCalledWith(1)
    expect(wrapper.text()).not.toContain('예약 영상 1')
  })

  it('renders the server-confirmed status and published link', async () => {
    const published = {
      ...schedule(3, 9),
      status: 'PUBLISHED',
      platforms: [{
        platform: 'YOUTUBE',
        scheduledAt: localDateTime(monday(), 9),
        status: 'PUBLISHED',
        platformUrl: 'https://youtube.test/watch/3',
      }],
    }
    vi.mocked(scheduleApi.list).mockResolvedValue([published] as never)

    const { wrapper } = await renderCalendar()

    expect(wrapper.text()).toContain('게시 완료')
    expect(wrapper.find('a[target="_blank"]').attributes('href')).toBe('https://youtube.test/watch/3')
  })

  it('requires confirmation before a drag-and-drop reschedule reaches the server', async () => {
    const { wrapper } = await renderCalendar()
    const blocks = wrapper.findAll('[draggable="true"]')
    const dataTransfer = {
      setData: vi.fn(),
      getData: vi.fn().mockReturnValue('1'),
      effectAllowed: '',
    }
    await blocks[0].trigger('dragstart', { dataTransfer })
    await blocks[1].trigger('drop', { dataTransfer })
    expect(scheduleApi.update).not.toHaveBeenCalled()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)

    vi.mocked(scheduleApi.update).mockResolvedValue({ ...schedule(1, 11), scheduledAt: localDateTime(monday(), 11) } as never)
    wrapper.findComponent({ name: 'ConfirmModal' }).vm.$emit('confirm')
    await flushPromises()
    expect(scheduleApi.update).toHaveBeenCalledWith(1, { scheduledAt: localDateTime(monday(), 11).slice(0, -3) })
  })

  it('keeps server failures visible and allows the calendar fetch to be retried', async () => {
    vi.mocked(scheduleApi.list).mockRejectedValueOnce(new Error('예약 서버 장애'))
    const { wrapper } = await renderCalendar()
    expect(wrapper.get('[role="alert"]').text()).toContain('예약을 불러오지 못했습니다.')

    vi.mocked(scheduleApi.list).mockResolvedValue([schedule(3, 13)] as never)
    const retry = wrapper.findAll('button').find((button) => button.text().includes('다시 시도'))
    expect(retry).toBeDefined()
    await retry!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('예약 영상 3')
  })
})
