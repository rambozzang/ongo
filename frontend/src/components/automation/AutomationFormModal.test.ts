import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AutomationFormModal from './AutomationFormModal.vue'

const mountModal = (props: Record<string, unknown> = {}) => mount(AutomationFormModal, {
  props: { isOpen: true, ...props },
  global: {
    stubs: {
      XMarkIcon: true,
      ChevronLeftIcon: true,
      ChevronRightIcon: true,
    },
  },
})

const buttonByText = (wrapper: ReturnType<typeof mountModal>, text: string) => {
  const button = wrapper.findAll('button').find(candidate => candidate.text().includes(text))
  expect(button, `button containing "${text}"`).toBeDefined()
  return button!
}

describe('AutomationFormModal', () => {
  it('prefills a smart trigger and submits values accepted by the backend', async () => {
    const wrapper = mountModal({
      initialTrigger: {
        triggerType: 'VIEWS_MILESTONE',
        config: { milestones: [1000, 5000] },
        name: '조회수 마일스톤 알림',
        description: '조회수가 목표에 도달하면 알립니다',
      },
    })

    expect((wrapper.find('input[type="text"]').element as HTMLInputElement).value).toBe('조회수 마일스톤 알림')
    expect(wrapper.find('textarea').element.value).toBe('조회수가 목표에 도달하면 알립니다')

    await buttonByText(wrapper, '다음').trigger('click')
    await buttonByText(wrapper, '조회수 마일스톤').trigger('click')
    await buttonByText(wrapper, '다음').trigger('click')
    await buttonByText(wrapper, '알림 전송').trigger('click')
    await buttonByText(wrapper, '다음').trigger('click')
    await buttonByText(wrapper, '저장').trigger('click')

    const saved = wrapper.emitted('save')?.[0]?.[0] as Record<string, any>
    expect(saved.trigger).toMatchObject({
      type: 'VIEWS_MILESTONE',
      config: { milestones: [1000, 5000] },
    })
    expect(saved.actions).toEqual([{ type: 'SEND_NOTIFICATION', config: {} }])
  })

  it('resets stale draft values whenever a new create modal is opened', async () => {
    const wrapper = mountModal({ isOpen: false })

    await wrapper.setProps({
      isOpen: true,
      initialTrigger: {
        triggerType: 'VIRAL_DETECTED',
        config: { multiplier: 3 },
        name: '바이럴 감지',
        description: '바이럴을 감지합니다',
      },
    })
    expect((wrapper.find('input[type="text"]').element as HTMLInputElement).value).toBe('바이럴 감지')

    await buttonByText(wrapper, '취소').trigger('click')
    await wrapper.setProps({
      isOpen: true,
      initialTrigger: {
        triggerType: 'ENGAGEMENT_DROP',
        config: { dropPercent: 50 },
        name: '참여율 하락',
        description: '참여율 하락을 감지합니다',
      },
    })
    expect((wrapper.find('input[type="text"]').element as HTMLInputElement).value).toBe('참여율 하락')
    expect(wrapper.find('textarea').element.value).toBe('참여율 하락을 감지합니다')
  })
})
