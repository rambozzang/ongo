import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import ShortsPilotView from './ShortsPilotView.vue'
import { adminShortsPilotApi } from '@/api/adminShortsPilot'
import koMessages from '@/locales/ko/common.json'
import enMessages from '@/locales/en/common.json'

/**
 * 파일럿 운영자 화면.
 *
 * 백엔드는 "미입력"과 "0"을 구분하려고 전 금액 필드를 nullable 로 내려보낸다. 화면이
 * `?? 0` 한 줄로 그 구분을 뭉개면 원가 미입력 실행이 **이익률 100%** 로 보인다. 파일럿
 * 판단에서 가장 위험한 오독이 그것이라, 이 파일은 그 구분이 화면까지 살아 오는지를 고정한다.
 *
 * 원장이 append-only 라 잘못 넣은 값은 지울 수 없다. 그래서 입력 경계와 확인 단계도 함께 고정한다.
 */
vi.mock('@/api/adminShortsPilot', () => ({
  adminShortsPilotApi: {
    getReport: vi.fn(),
    enroll: vi.fn(),
    logRevenue: vi.fn(),
    logExternalCost: vi.fn(),
    logOperatorTime: vi.fn(),
  },
}))

const PILOT = koMessages.admin.shortsPilot

const runRow = (overrides: Record<string, unknown> = {}) => ({
  runId: 7,
  createdAt: '2026-08-20T01:00:00Z',
  startedAt: '2026-08-20T01:05:00Z',
  deliveredAt: '2026-08-20T03:05:00Z',
  leadTimeMs: 7_200_000,
  stageRerunCount: 2,
  renderAttemptFailureCount: 1,
  operatorMinutes: 90,
  operatorReportedRevenueKrw: 300_000,
  operatorReportedExternalCostKrw: 50_000,
  contributionExcludingExternalCostKrw: 250_000,
  contributionPerOperatorHourKrw: 166_666,
  ...overrides,
})

const summary = (overrides: Record<string, unknown> = {}) => ({
  enrolledRunCount: 1,
  startedRunCount: 1,
  deliveredRunCount: 1,
  totalStageReruns: 2,
  totalRenderAttemptFailures: 1,
  totalOperatorMinutes: 90,
  totalOperatorReportedRevenueKrw: 300_000,
  totalOperatorReportedExternalCostKrw: 50_000,
  totalContributionExcludingExternalCostKrw: 250_000,
  contributionObservedRunCount: 1,
  leadTime: { observedRunCount: 1, minMs: 7_200_000, maxMs: 7_200_000, averageMs: 7_200_000 },
  ...overrides,
})

const report = (overrides: Record<string, unknown> = {}) => ({
  state: 'OK',
  summary: summary(),
  runs: [runRow()],
  limitations: ['REVENUE_AND_COST_ARE_OPERATOR_REPORTED', 'LABOR_COST_NOT_INCLUDED_IN_CONTRIBUTION'],
  ...overrides,
})

async function renderView() {
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    messages: { ko: koMessages, en: enMessages },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }],
  })
  await router.push('/')
  await router.isReady()

  const wrapper = mount(ShortsPilotView, {
    global: { plugins: [i18n, router] },
    // BaseModal 은 Teleport 로 body 에 붙는다. stub 하면 컴포넌트가 아예 렌더되지 않아
    // "문구가 없다" 류 단언이 전부 무의미하게 통과한다.
    attachTo: document.body,
  })
  await flushPromises()
  return wrapper
}

/*
 * 모달은 Teleport 로 body 에 붙으므로 `wrapper.find` 로는 닿지 않는다. 여기서 조회에
 * 실패하면 던진다 — 못 찾은 것을 조용히 넘기면 "보내지 않았다" 류 단언이 모달이 열리지
 * 않았을 때도 통과해 버린다.
 */
function modalInput(): HTMLInputElement {
  const el = document.getElementById('pilot-log-value')
  if (!(el instanceof HTMLInputElement)) throw new Error('모달 입력란이 열려 있지 않다')
  return el
}

async function typeInModal(value: string) {
  const input = modalInput()
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

function bodyButton(label: string): HTMLButtonElement {
  const found = Array.from(document.querySelectorAll('button')).find(
    (b) => b.textContent?.trim() === label,
  )
  if (!found) throw new Error(`버튼을 찾지 못했다: ${label}`)
  return found
}

async function clickInBody(label: string) {
  bodyButton(label).click()
  await flushPromises()
}

/** 실행 탭으로 이동한 뒤 해당 기록 모달을 연다. */
async function openLogModal(wrapper: Awaited<ReturnType<typeof renderView>>, label: string) {
  await wrapper.findAll('[role="tab"]')[1].trigger('click')
  await clickInBody(label)
}

describe('ShortsPilotView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(adminShortsPilotApi.getReport).mockResolvedValue(report() as never)
    vi.mocked(adminShortsPilotApi.enroll).mockResolvedValue({
      runId: 7,
      alreadyEnrolled: false,
    } as never)
    vi.mocked(adminShortsPilotApi.logRevenue).mockResolvedValue(undefined as never)
    vi.mocked(adminShortsPilotApi.logExternalCost).mockResolvedValue(undefined as never)
    vi.mocked(adminShortsPilotApi.logOperatorTime).mockResolvedValue(undefined as never)
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  /* ---- 미입력 ≠ 0 ---- */

  /**
   * 이 화면의 존재 이유. 원가가 null 인 실행을 0원으로 그리면 기여이익이 매출 전액으로
   * 보이고, 그 실행은 이익률 100% 짜리 성공 사례로 보고서에 남는다.
   */
  it('미기록 금액을 0원이 아니라 미입력으로 표시한다', async () => {
    vi.mocked(adminShortsPilotApi.getReport).mockResolvedValue(
      report({
        summary: summary({
          totalOperatorReportedExternalCostKrw: null,
          totalContributionExcludingExternalCostKrw: null,
          contributionObservedRunCount: 0,
        }),
        runs: [
          runRow({
            operatorReportedExternalCostKrw: null,
            contributionExcludingExternalCostKrw: null,
            contributionPerOperatorHourKrw: null,
          }),
        ],
      }) as never,
    )
    const wrapper = await renderView()
    await wrapper.findAll('[role="tab"]')[1].trigger('click')

    const text = wrapper.text()
    expect(text).toContain(PILOT.notRecorded)
    // "₩0" 이 어디에도 나오면 안 된다 — 미입력을 0으로 그렸다는 뜻이다.
    expect(text).not.toMatch(/₩\s*0(?!\d)/)
  })

  it('미기록 투입 시간도 0분으로 그리지 않는다', async () => {
    vi.mocked(adminShortsPilotApi.getReport).mockResolvedValue(
      report({
        summary: summary({ totalOperatorMinutes: null }),
        runs: [runRow({ operatorMinutes: null })],
      }) as never,
    )
    const wrapper = await renderView()

    expect(wrapper.text()).toContain(PILOT.notRecorded)
    expect(wrapper.text()).not.toMatch(/\b0분\b/)
  })

  it('리드타임 미관측이면 0시간이 아니라 미입력이다', async () => {
    vi.mocked(adminShortsPilotApi.getReport).mockResolvedValue(
      report({ summary: summary({ leadTime: null }) }) as never,
    )
    const wrapper = await renderView()

    expect(wrapper.text()).toContain(PILOT.notRecorded)
  })

  /** 미입력 문구 자체가 0이나 빈 문자열이면 위 단언이 전부 무의미해진다. */
  it('미입력 문구가 두 로케일 모두 0을 뜻하지 않는다', () => {
    for (const value of [koMessages.admin.shortsPilot.notRecorded, enMessages.admin.shortsPilot.notRecorded]) {
      expect(value.trim()).not.toBe('')
      expect(value).not.toBe('0')
      expect(value).not.toContain('0')
    }
  })

  /* ---- 집계 모수 ---- */

  /**
   * 기여이익 합계는 매출·원가가 둘 다 적힌 실행만으로 낸다. 몇 건 기준인지 적지 않으면
   * 전체 등록 수 기준으로 읽혀서 실제보다 낮은 건당 이익으로 오해한다.
   */
  it('기여이익 합계에 몇 건 기준인지 함께 보여준다', async () => {
    const wrapper = await renderView()

    expect(wrapper.text()).toContain(
      PILOT.summary.contributionBasis.replace('{count}', '1'),
    )
  })

  /* ---- 한계 노출 ---- */

  /** 이 목록이 없으면 운영자가 수기 입력값을 "측정된 원가"로 읽는다. */
  it('보고서 한계를 항상 화면에 보여준다', async () => {
    const wrapper = await renderView()

    expect(wrapper.text()).toContain(PILOT.limitations.REVENUE_AND_COST_ARE_OPERATOR_REPORTED)
    expect(wrapper.text()).toContain(PILOT.limitations.LABOR_COST_NOT_INCLUDED_IN_CONTRIBUTION)
  })

  it('등록이 없어도 한계는 그대로 보인다', async () => {
    vi.mocked(adminShortsPilotApi.getReport).mockResolvedValue(
      report({
        state: 'NO_DATA',
        summary: null,
        runs: [],
        limitations: ['PAYMENT_NOT_ATTRIBUTED'],
      }) as never,
    )
    const wrapper = await renderView()

    expect(wrapper.text()).toContain(PILOT.limitations.PAYMENT_NOT_ATTRIBUTED)
  })

  /**
   * 등록 0건에 0으로 채운 요약을 그리면 "실패율 0%, 재실행 0건"으로 읽혀 아직 시작도
   * 안 한 파일럿이 완벽해 보인다.
   */
  it('NO_DATA면 요약 숫자를 그리지 않는다', async () => {
    vi.mocked(adminShortsPilotApi.getReport).mockResolvedValue(
      report({ state: 'NO_DATA', summary: null, runs: [] }) as never,
    )
    const wrapper = await renderView()

    expect(wrapper.text()).toContain(PILOT.noData)
    expect(wrapper.text()).not.toContain(PILOT.summary.enrolledRunCount)
  })

  /* ---- append-only 입력 ---- */

  it('금액 입력 확인 단계에 되돌릴 수 없다는 경고가 있다', async () => {
    const wrapper = await renderView()
    await openLogModal(wrapper, PILOT.log.revenue)

    expect(document.body.textContent).toContain(PILOT.log.irreversibleWarning)
  })

  /** 확인 단계를 건너뛰고 바로 보내면 자릿수 오타가 영구히 남는다. */
  it('확인 단계를 거치기 전에는 서버로 보내지 않는다', async () => {
    const wrapper = await renderView()
    await openLogModal(wrapper, PILOT.log.revenue)
    await typeInModal('300000')

    expect(adminShortsPilotApi.logRevenue).not.toHaveBeenCalled()
  })

  it('확인까지 마치면 입력한 금액 그대로 보낸다', async () => {
    const wrapper = await renderView()
    await openLogModal(wrapper, PILOT.log.revenue)
    await typeInModal('300000')
    await clickInBody(koMessages.action.next)
    await clickInBody(PILOT.log.submit)

    expect(adminShortsPilotApi.logRevenue).toHaveBeenCalledWith(7, 300000)
  })

  /** 확인 화면에 서식 적용값이 다시 보여야 자릿수 오타를 눈으로 잡는다. */
  it('확인 화면에 서식이 적용된 금액을 다시 보여준다', async () => {
    const wrapper = await renderView()
    await openLogModal(wrapper, PILOT.log.revenue)
    await typeInModal('300000')
    await clickInBody(koMessages.action.next)

    expect(document.body.textContent).toContain('300,000')
  })

  /* ---- 입력 경계 (서버와 동일) ---- */

  it.each([
    ['0', '하한 미만'],
    ['100000001', '상한 초과'],
    ['1.5', '정수 아님'],
    ['', '빈 값'],
  ])('금액 %s(%s)은 확인 단계로 넘어가지 못한다', async (value) => {
    const wrapper = await renderView()
    await openLogModal(wrapper, PILOT.log.revenue)
    await typeInModal(value)

    expect(bodyButton(koMessages.action.next).disabled).toBe(true)
  })

  it('투입 시간은 1440분을 넘기지 못한다', async () => {
    const wrapper = await renderView()
    await openLogModal(wrapper, PILOT.log.operatorTime)
    await typeInModal('1441')

    expect(bodyButton(koMessages.action.next).disabled).toBe(true)
  })

  /** 경계 안의 값은 통과해야 한다 — 위 거절 단언이 "항상 잠김"으로 통과하면 안 된다. */
  it('경계 안의 값은 확인 단계로 넘어간다', async () => {
    const wrapper = await renderView()
    await openLogModal(wrapper, PILOT.log.operatorTime)
    await typeInModal('1440')

    expect(bodyButton(koMessages.action.next).disabled).toBe(false)
  })

  /* ---- 코호트 등록 ---- */

  it('실행 ID를 넣지 않으면 등록 버튼이 잠겨 있다', async () => {
    const wrapper = await renderView()

    const submit = wrapper.findAll('button').find((b) => b.text() === PILOT.enroll.submit)!
    expect(submit.attributes('disabled')).toBeDefined()
  })

  it('등록하면 보고서를 다시 읽어 방금 등록분을 반영한다', async () => {
    const wrapper = await renderView()
    await wrapper.find('#pilot-enroll-run-id').setValue('7')
    await wrapper.findAll('button').find((b) => b.text() === PILOT.enroll.submit)!.trigger('click')
    await flushPromises()

    expect(adminShortsPilotApi.enroll).toHaveBeenCalledWith(7)
    expect(adminShortsPilotApi.getReport).toHaveBeenCalledTimes(2)
  })

  /**
   * 이미 등록된 실행도 성공이지만 같은 문구로 알리면 운영자가 중복 등록했는지 모른다.
   * 두 문구가 실제로 달라야 그 구분이 성립한다.
   */
  it('이미 등록된 실행과 신규 등록을 다른 문구로 알린다', () => {
    expect(PILOT.enroll.alreadyEnrolled).not.toBe(PILOT.enroll.success)
    expect(enMessages.admin.shortsPilot.enroll.alreadyEnrolled).not.toBe(
      enMessages.admin.shortsPilot.enroll.success,
    )
  })
})
