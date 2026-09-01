import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { AxiosError } from 'axios'
import InviteMemberModal from './InviteMemberModal.vue'
import { useTeamStore } from '@/stores/team'
import { PLAN_UPGRADE_PATH } from '@/composables/usePlanLimit'

/**
 * 초대가 거절됐을 때 **서버가 준 사유를 그대로 보여주는지** 고정한다.
 *
 * ## 무엇이 문제였나
 *
 * `catch` 가 오류를 통째로 버리고 "초대 전송에 실패했습니다. 입력한 주소와 권한을 확인해
 * 주세요" 라는 고정 문구만 띄웠다. 서버가 팀 좌석 한도(`PLAN_LIMIT_EXCEEDED`)로 막기
 * 시작하면서 그 문구는 **틀린 안내**가 됐다 — 주소도 권한도 멀쩡한데 주소를 고치라고 하니
 * 사용자는 될 때까지 이메일만 다시 친다. 업그레이드하면 풀린다는 사실이 어디에도 없다.
 *
 * 팀 메뉴가 Free 사용자에게 보이는 것 자체는 결함이 아니다. `/capabilities` 는 사용자를
 * 인자로 받지 않는 **배포 수준 계약**이라 플랜을 알 수 없고, 이 제품의 규약은
 * `AssetUploadModal` 처럼 **서버가 막고 화면이 업그레이드를 안내**하는 것이다.
 */
describe('팀 초대 거절 안내', () => {
  const mountModal = () => {
    setActivePinia(createPinia())
    return mount(InviteMemberModal, {
      props: { show: true },
      global: {
        stubs: { RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' } },
        mocks: { $t: (key: string) => key },
      },
    })
  }

  /** 서버 계약: 400 + ResData{message, error}. `client.ts` 가 message 를 error.message 로 올린다. */
  const rejection = (message: string, code: string) => {
    const error = new AxiosError(message, 'ERR_BAD_REQUEST')
    error.response = {
      data: { success: false, message, data: null, error: code },
      status: 400,
      statusText: 'Bad Request',
      headers: {},
      config: {},
    } as never
    error.message = message
    return error
  }

  const submit = async (wrapper: ReturnType<typeof mountModal>, email = 'new@example.com') => {
    await wrapper.find('#email').setValue(email)
    await wrapper.findAll('button').find(b => b.text().includes('초대'))?.trigger('click')
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()
  }

  beforeEach(() => {
    vi.restoreAllMocks()
  })

  /** **핵심 회귀.** 좌석 한도를 "주소를 확인하세요" 로 바꿔 말하면 안 된다. */
  it('좌석 한도 거절은 서버 사유를 그대로 보여준다', async () => {
    const wrapper = mountModal()
    const reason = '팀 멤버 한도를 초과했습니다. 현재 플랜 한도: 0'
    vi.spyOn(useTeamStore(), 'inviteMember').mockRejectedValue(
      rejection(reason, 'PLAN_LIMIT_EXCEEDED'),
    )

    await submit(wrapper)

    expect(wrapper.text()).toContain(reason)
    // 사용자를 엉뚱한 방향으로 보내던 문구가 남아 있으면 안 된다.
    expect(wrapper.text()).not.toContain('입력한 주소와 권한을 확인')
  })

  /** 업그레이드로 풀리는 거절에는 결제 화면으로 가는 길을 준다. */
  it('좌석 한도 거절에는 업그레이드 링크를 보여준다', async () => {
    const wrapper = mountModal()
    vi.spyOn(useTeamStore(), 'inviteMember').mockRejectedValue(
      rejection('팀 멤버 한도를 초과했습니다. 현재 플랜 한도: 0', 'PLAN_LIMIT_EXCEEDED'),
    )

    await submit(wrapper)

    const link = wrapper.find(`a[href="${PLAN_UPGRADE_PATH}"]`)
    expect(link.exists()).toBe(true)
  })

  /**
   * **돈을 내도 풀리지 않는 거절에는 결제를 권하지 않는다.**
   *
   * 한 번이라도 엉뚱하게 결제를 권하면, 정작 필요한 순간의 안내도 믿지 않게 된다.
   */
  it('중복 초대에는 업그레이드 링크를 보여주지 않는다', async () => {
    const wrapper = mountModal()
    const reason = '이미 존재하는 팀 멤버입니다: dup@example.com'
    vi.spyOn(useTeamStore(), 'inviteMember').mockRejectedValue(rejection(reason, 'DUPLICATE'))

    await submit(wrapper, 'dup@example.com')

    expect(wrapper.text()).toContain(reason)
    expect(wrapper.find(`a[href="${PLAN_UPGRADE_PATH}"]`).exists()).toBe(false)
  })

  /** 사유를 알 수 없는 실패(네트워크 등)에는 재시도를 안내한다 — 주소를 고치라고 하지 않는다. */
  it('사유 없는 실패에는 재시도를 안내한다', async () => {
    const wrapper = mountModal()
    vi.spyOn(useTeamStore(), 'inviteMember').mockRejectedValue(new Error(''))

    await submit(wrapper)

    expect(wrapper.text()).toContain('다시 시도')
    expect(wrapper.text()).not.toContain('입력한 주소와 권한을 확인')
  })

  /** 성공하면 오류 흔적을 남기지 않고 닫는다. */
  it('성공하면 모달을 닫고 오류를 남기지 않는다', async () => {
    const wrapper = mountModal()
    vi.spyOn(useTeamStore(), 'inviteMember').mockResolvedValue(undefined)

    await submit(wrapper)

    expect(wrapper.emitted('close')).toBeTruthy()
    expect(wrapper.find(`a[href="${PLAN_UPGRADE_PATH}"]`).exists()).toBe(false)
  })
})
