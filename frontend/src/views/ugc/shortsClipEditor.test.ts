import { describe, expect, it } from 'vitest'
import type { ClipEditorResponse } from '@/api/ugcShortsPipeline'
import { buildEditRequest, draftFromEditor, msToSec, parseHashtags, sameSec, secToMs } from './shortsClipEditor'

const editor = (overrides: Partial<ClipEditorResponse> = {}): ClipEditorResponse => ({
  clipId: 3,
  runId: 1,
  seq: 1,
  startMs: 60_040,
  endMs: 90_000,
  title: '제목',
  caption: null,
  status: 'RENDERED',
  subtitles: [
    { startMs: 0, endMs: 2_049, text: '첫 줄', hasWordTiming: true },
    { startMs: 5_000, endMs: 8_000, text: '둘째 줄', hasWordTiming: false },
  ],
  cropJson: '{"x":420,"y":0,"width":608,"height":1080,"mode":"CROP"}',
  sourceDurationMs: 600_000,
  editable: true,
  notEditableReason: null,
  hasRenderedVideo: true,
  platformMeta: [{ platform: 'YOUTUBE', title: '유튜브', description: '', hashtags: ['재테크'] }],
  ...overrides,
})

describe('시간 변환', () => {
  it('초 문자열 ↔ ms', () => {
    expect(msToSec(60_040)).toBe('60.0')
    expect(secToMs('12.3')).toBe(12_300)
    expect(secToMs('')).toBeNaN()
    expect(secToMs('abc')).toBeNaN()
  })

  it('표시 단위(0.1초) 안에서 같은 값인지', () => {
    expect(sameSec('2', 2_000)).toBe(true)
    expect(sameSec(2, 2_049)).toBe(true)
    expect(sameSec('2.0', 2_000)).toBe(true)
    expect(sameSec('2.1', 2_000)).toBe(false)
    expect(sameSec('', 0)).toBe(false)
  })
})

describe('parseHashtags', () => {
  it('# 와 쉼표·띄어쓰기를 모두 구분자로 받고 중복을 뺀다', () => {
    expect(parseHashtags('#재테크 월급, 통장  #재테크 ##돈')).toEqual(['재테크', '월급', '통장', '돈'])
  })
})

describe('buildEditRequest', () => {
  it('아무것도 고치지 않으면 빈 요청 — 0.1초 반올림 때문에 "바뀜"으로 보지 않는다', () => {
    const e = editor()
    const result = buildEditRequest(e, draftFromEditor(e))

    expect(result).toEqual({ ok: true, request: {}, changed: false, touchesRender: false })
  })

  it('제목만 고치면 제목만 보내고 렌더에는 영향이 없다', () => {
    const e = editor()
    const draft = { ...draftFromEditor(e), title: '새 제목' }

    expect(buildEditRequest(e, draft)).toEqual({ ok: true, request: { title: '새 제목' }, changed: true, touchesRender: false })
  })

  it('구간을 고치면 바뀐 끝만 ms 로 보낸다', () => {
    const e = editor()
    const draft = { ...draftFromEditor(e), endSec: '85.5' }

    expect(buildEditRequest(e, draft)).toMatchObject({ ok: true, request: { endMs: 85_500 }, touchesRender: true })
  })

  it('자막 한 줄을 고치면 전체 줄을 원래 ms 그대로 보낸다', () => {
    const e = editor()
    const draft = draftFromEditor(e)
    draft.subtitles[1]!.text = '고친 둘째 줄'

    const result = buildEditRequest(e, draft)
    expect(result).toMatchObject({ ok: true, touchesRender: true })
    if (!result.ok) throw new Error('unreachable')
    expect(result.request.subtitles).toEqual([
      { startMs: 0, endMs: 2_049, text: '첫 줄' }, // 손대지 않은 줄은 반올림하지 않는다 — 단어 시각이 유지된다
      { startMs: 5_000, endMs: 8_000, text: '고친 둘째 줄' },
    ])
  })

  it('줄을 지우는 것도 자막 변경이다', () => {
    const e = editor()
    const draft = draftFromEditor(e)
    draft.subtitles.pop()

    const result = buildEditRequest(e, draft)
    expect(result.ok && result.request.subtitles).toEqual([{ startMs: 0, endMs: 2_049, text: '첫 줄' }])
  })

  it('크롭을 바꾸면 영상 요소의 실제 해상도를 함께 보낸다', () => {
    const e = editor()
    const draft = draftFromEditor(e)
    draft.crop!.x = '500'

    const result = buildEditRequest(e, draft, { width: 1920, height: 1080 })
    expect(result).toMatchObject({
      ok: true,
      touchesRender: true,
      request: { crop: { x: 500, y: 0, width: 608, height: 1080 }, sourceWidth: 1920, sourceHeight: 1080 },
    })
  })

  it('해시태그 글을 고치면 플랫폼 메타를 보낸다', () => {
    const e = editor()
    const draft = draftFromEditor(e)
    draft.platformMeta[0]!.hashtagsText = '#재테크 #월급'

    expect(buildEditRequest(e, draft)).toMatchObject({
      ok: true,
      touchesRender: false,
      request: { platformMeta: [{ platform: 'YOUTUBE', title: '유튜브', description: '', hashtags: ['재테크', '월급'] }] },
    })
  })

  it('숫자 입력 칸이 넘긴 number 도 받는다 — 손대지 않은 값은 원래 ms 를 지킨다', () => {
    const e = editor()
    const untouched = { ...draftFromEditor(e), startSec: 60.0, endSec: 90 }
    expect(buildEditRequest(e, untouched)).toMatchObject({ ok: true, changed: false })

    const moved = { ...draftFromEditor(e), endSec: 85.5 }
    expect(buildEditRequest(e, moved)).toMatchObject({ ok: true, request: { endMs: 85_500 } })
  })

  it('보낼 수 없는 입력은 막고 몇 번째 줄인지 알린다', () => {
    const e = editor()
    expect(buildEditRequest(e, { ...draftFromEditor(e), endSec: '50' })).toEqual({ ok: false, error: 'invalidRange' })

    const badTime = draftFromEditor(e)
    badTime.subtitles[1]!.endSec = '4'
    expect(buildEditRequest(e, badTime)).toEqual({ ok: false, error: 'invalidSubtitleTime', line: 2 })

    const empty = draftFromEditor(e)
    empty.subtitles[0]!.text = '  '
    expect(buildEditRequest(e, empty)).toEqual({ ok: false, error: 'emptySubtitle', line: 1 })

    const badCrop = draftFromEditor(e)
    badCrop.crop!.width = '0'
    expect(buildEditRequest(e, badCrop)).toEqual({ ok: false, error: 'invalidCrop' })
  })
})
