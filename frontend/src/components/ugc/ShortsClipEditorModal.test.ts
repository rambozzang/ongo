import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import ShortsClipEditorModal from './ShortsClipEditorModal.vue'
import { ugcShortsPipelineApi, type ClipEditorResponse } from '@/api/ugcShortsPipeline'
import { videoApi } from '@/api/video'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/ugcShortsPipeline', () => ({
  ugcShortsPipelineApi: { getClipEditor: vi.fn(), editClip: vi.fn() },
}))
vi.mock('@/api/video', () => ({ videoApi: { get: vi.fn() } }))

const editor = (overrides: Partial<ClipEditorResponse> = {}): ClipEditorResponse => ({
  clipId: 3,
  runId: 1,
  seq: 2,
  startMs: 60_000,
  endMs: 90_000,
  title: '제목',
  caption: null,
  status: 'RENDERED',
  subtitles: [{ startMs: 0, endMs: 2_000, text: '첫 줄', hasWordTiming: true }],
  cropJson: null,
  sourceDurationMs: 600_000,
  editable: true,
  notEditableReason: null,
  hasRenderedVideo: true,
  platformMeta: [{ platform: 'YOUTUBE', title: '유튜브', description: '', hashtags: ['재테크'] }],
  ...overrides,
})

function mountEditor() {
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  return mount(ShortsClipEditorModal, {
    props: { modelValue: true, workspaceId: 2, runId: 1, clipId: 3, sourceVideoId: 5 },
    global: {
      plugins: [i18n],
      stubs: {
        BaseModal: { template: '<div role="dialog"><slot /><slot name="footer" /></div>', props: ['modelValue'] },
        LoadingSpinner: true,
        ShortsSpeakerTrackButton: {
          name: 'ShortsSpeakerTrackButton',
          props: ['workspaceId', 'runId', 'clipId', 'disabled'],
          emits: ['applied'],
          template: '<button data-testid="track-speaker" :disabled="disabled" />',
        },
      },
    },
  })
}

const saveButton = (wrapper: ReturnType<typeof mountEditor>) => wrapper.get('[data-testid="clip-editor-save"]')

describe('ShortsClipEditorModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(videoApi.get).mockResolvedValue({ id: 5, fileUrl: 'https://cdn.example.com/source.mp4' } as never)
  })

  it('고친 것이 없으면 저장할 수 없다', async () => {
    vi.mocked(ugcShortsPipelineApi.getClipEditor).mockResolvedValue(editor())
    const wrapper = mountEditor()
    await flushPromises()

    expect(wrapper.find('[data-testid="clip-editor"]').exists()).toBe(true)
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
  })

  it('제목만 고치면 제목만 보내고 다시 렌더 경고를 띄우지 않는다', async () => {
    vi.mocked(ugcShortsPipelineApi.getClipEditor).mockResolvedValue(editor())
    vi.mocked(ugcShortsPipelineApi.editClip).mockResolvedValue({
      editor: editor({ title: '새 제목' }),
      needsRerender: false,
      uncoveredSubtitleSpan: false,
    })
    const wrapper = mountEditor()
    await flushPromises()

    await wrapper.get('[data-testid="clip-title"]').setValue('새 제목')
    expect(wrapper.find('[data-testid="rerender-warning"]').exists()).toBe(false)
    await saveButton(wrapper).trigger('click')
    await flushPromises()

    expect(ugcShortsPipelineApi.editClip).toHaveBeenCalledWith(2, 1, 3, { title: '새 제목' })
    expect(wrapper.emitted('saved')?.[0]?.[0]).toMatchObject({ needsRerender: false })
  })

  it('구간을 바꾸면 렌더된 영상이 버려진다고 미리 알린다', async () => {
    vi.mocked(ugcShortsPipelineApi.getClipEditor).mockResolvedValue(editor())
    const wrapper = mountEditor()
    await flushPromises()

    await wrapper.get('[data-testid="clip-end"]').setValue('85')

    expect(wrapper.get('[data-testid="rerender-warning"]').text()).toContain('다시 렌더')
  })

  it('단어 강조가 있던 줄을 고치면 강조가 꺼진다고 알린다', async () => {
    vi.mocked(ugcShortsPipelineApi.getClipEditor).mockResolvedValue(editor())
    const wrapper = mountEditor()
    await flushPromises()

    expect(wrapper.find('[data-testid="word-timing-lost"]').exists()).toBe(false)
    await wrapper.get('[aria-label="1번째 줄 문구"]').setValue('고친 첫 줄')

    expect(wrapper.find('[data-testid="word-timing-lost"]').exists()).toBe(true)
  })

  it('편집할 수 없는 클립은 서버가 준 이유를 보이고 입력과 저장을 막는다', async () => {
    vi.mocked(ugcShortsPipelineApi.getClipEditor).mockResolvedValue(
      editor({ editable: false, notEditableReason: '렌더 중인 클립은 편집할 수 없습니다' }),
    )
    const wrapper = mountEditor()
    await flushPromises()

    expect(wrapper.get('[data-testid="clip-editor-locked"]').text()).toBe('렌더 중인 클립은 편집할 수 없습니다')
    expect(wrapper.get('[data-testid="clip-title"]').attributes('disabled')).toBeDefined()
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
  })

  it('서버가 거절하면 그 이유를 그대로 보이고 입력을 유지한다', async () => {
    vi.mocked(ugcShortsPipelineApi.getClipEditor).mockResolvedValue(editor())
    vi.mocked(ugcShortsPipelineApi.editClip).mockRejectedValue(new Error('YOUTUBE 제목은 100자까지입니다'))
    const wrapper = mountEditor()
    await flushPromises()

    await wrapper.get('[data-testid="clip-title"]').setValue('가'.repeat(150))
    await saveButton(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="save-error"]').text()).toBe('YOUTUBE 제목은 100자까지입니다')
    expect((wrapper.get('[data-testid="clip-title"]').element as HTMLInputElement).value).toBe('가'.repeat(150))
    expect(wrapper.emitted('saved')).toBeUndefined()
  })

  it('보낼 수 없는 입력은 저장 버튼을 막고 이유를 보인다', async () => {
    vi.mocked(ugcShortsPipelineApi.getClipEditor).mockResolvedValue(editor())
    const wrapper = mountEditor()
    await flushPromises()

    await wrapper.get('[aria-label="1번째 줄 문구"]').setValue('  ')

    expect(wrapper.get('[data-testid="draft-error"]').text()).toContain('1번째 자막이 비어 있습니다')
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
  })

  it('화자 추적 결과는 저장 직후처럼 편집기를 갱신하고 saved 를 알린다', async () => {
    vi.mocked(ugcShortsPipelineApi.getClipEditor).mockResolvedValue(editor())
    const wrapper = mountEditor()
    await flushPromises()
    const tracked = editor({ cropJson: '{"x":100,"y":0,"width":608,"height":1080,"mode":"CROP"}' })

    const button = wrapper.findComponent({ name: 'ShortsSpeakerTrackButton' })
    expect(button.props()).toMatchObject({ workspaceId: 2, runId: 1, clipId: 3, disabled: false })
    button.vm.$emit('applied', { edit: { editor: tracked, needsRerender: true, uncoveredSubtitleSpan: false }, foundTiles: 4, totalTiles: 5, creditsCharged: 2 })
    await flushPromises()

    expect((wrapper.get('[data-testid="crop-x"]').element as HTMLInputElement).value).toBe('100')
    expect(wrapper.emitted('saved')?.[0]?.[0]).toMatchObject({ needsRerender: true })
  })

  it('저장 안 한 편집이 있으면 화자 추적을 잠근다 — 결과로 덮어쓰면 편집이 사라진다', async () => {
    vi.mocked(ugcShortsPipelineApi.getClipEditor).mockResolvedValue(editor())
    const wrapper = mountEditor()
    await flushPromises()

    await wrapper.get('[data-testid="clip-title"]').setValue('저장 안 한 제목')

    expect(wrapper.findComponent({ name: 'ShortsSpeakerTrackButton' }).props('disabled')).toBe(true)
    expect(wrapper.find('[data-testid="track-needs-save"]').exists()).toBe(true)
  })

  it('편집할 수 없는 클립은 화자 추적도 잠근다', async () => {
    vi.mocked(ugcShortsPipelineApi.getClipEditor).mockResolvedValue(editor({ editable: false, notEditableReason: '이미 예약됨' }))
    const wrapper = mountEditor()
    await flushPromises()

    expect(wrapper.findComponent({ name: 'ShortsSpeakerTrackButton' }).props('disabled')).toBe(true)
  })

  it('해시태그를 고치면 플랫폼 메타를 보낸다', async () => {
    vi.mocked(ugcShortsPipelineApi.getClipEditor).mockResolvedValue(editor())
    vi.mocked(ugcShortsPipelineApi.editClip).mockResolvedValue({ editor: editor(), needsRerender: false, uncoveredSubtitleSpan: false })
    const wrapper = mountEditor()
    await flushPromises()

    await wrapper.get('[data-testid="meta-hashtags-YOUTUBE"]').setValue('#재테크 월급')
    await saveButton(wrapper).trigger('click')
    await flushPromises()

    expect(ugcShortsPipelineApi.editClip).toHaveBeenCalledWith(2, 1, 3, {
      platformMeta: [{ platform: 'YOUTUBE', title: '유튜브', description: '', hashtags: ['재테크', '월급'] }],
    })
  })
})
