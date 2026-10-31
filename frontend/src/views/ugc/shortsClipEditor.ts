import type { ClipEditRequest, ClipEditorResponse, PlatformMeta } from '@/api/ugcShortsPipeline'

/**
 * 클립 편집기의 화면 상태 ↔ 서버 요청 변환.
 *
 * **바뀐 항목만 보낸다.** 서버는 구간·자막·크롭이 바뀌면 이전 렌더 결과를 버린다 — 제목만 고쳤는데
 * 자막까지 함께 보내면 영상이 그대로인데도 다시 렌더해야 하는 상태가 된다.
 *
 * 시각은 화면에서 초(소수 첫째 자리까지), 서버에는 ms 로 보낸다. 자막 시각은 클립 시작 기준이다.
 * 최종 검증은 서버가 한다. 여기서는 입력 자체가 숫자가 아닌 경우처럼 보낼 수 없는 것만 막는다.
 */

export interface CropBox {
  x: number
  y: number
  width: number
  height: number
}

/** 입력 칸 값. 숫자 입력은 Vue 가 number 로 바꿔 넣을 수 있다. */
type FieldValue = string | number

export interface DraftSubtitle {
  startSec: FieldValue
  endSec: FieldValue
  text: string
  /** 원본 줄에 단어 강조가 있었는지. 고치면 사라진다는 안내에 쓴다. */
  hadWordTiming: boolean
}

export interface DraftPlatformMeta {
  platform: string
  title: string
  description: string
  /** 사용자가 쓰는 그대로. "#재테크 월급, 통장" 모두 받는다. */
  hashtagsText: string
}

export interface ClipDraft {
  startSec: FieldValue
  endSec: FieldValue
  title: string
  caption: string
  subtitles: DraftSubtitle[]
  crop: { x: FieldValue; y: FieldValue; width: FieldValue; height: FieldValue } | null
  platformMeta: DraftPlatformMeta[]
}

export type DraftError = 'invalidRange' | 'invalidSubtitleTime' | 'emptySubtitle' | 'invalidCrop'

export type BuildResult =
  | { ok: true; request: ClipEditRequest; changed: boolean; touchesRender: boolean }
  | { ok: false; error: DraftError; line?: number }

export function msToSec(ms: number): string {
  return (Math.round(ms / 100) / 10).toFixed(1)
}

/**
 * "12.3" → 12300. 비었거나 숫자가 아니면 NaN.
 * `<input type="number" v-model>` 은 Vue 가 숫자로 바꿔 넣으므로 숫자도 받는다.
 */
export function secToMs(sec: string | number): number {
  if (typeof sec === 'number') return Number.isFinite(sec) ? Math.round(sec * 1000) : Number.NaN
  const trimmed = sec.trim()
  if (trimmed === '') return Number.NaN
  const n = Number(trimmed)
  return Number.isFinite(n) ? Math.round(n * 1000) : Number.NaN
}

export function parseHashtags(text: string): string[] {
  const seen = new Set<string>()
  return text
    .split(/[\s,]+/)
    .map((tag) => tag.replace(/^#+/, '').trim())
    .filter((tag) => tag.length > 0 && !seen.has(tag.toLowerCase()) && seen.add(tag.toLowerCase()))
}

export function formatHashtags(tags: string[]): string {
  return tags.map((tag) => `#${tag}`).join(' ')
}

export function parseCropJson(json: string | null): CropBox | null {
  if (!json) return null
  try {
    const raw = JSON.parse(json) as Partial<CropBox>
    const { x, y, width, height } = raw
    if ([x, y, width, height].every((v) => typeof v === 'number')) return { x: x!, y: y!, width: width!, height: height! }
  } catch {
    // 깨진 저장값은 "크롭 없음"으로 본다 — 렌더도 같은 판단을 한다.
  }
  return null
}

export function draftFromEditor(editor: ClipEditorResponse): ClipDraft {
  const crop = parseCropJson(editor.cropJson)
  return {
    startSec: msToSec(editor.startMs),
    endSec: msToSec(editor.endMs),
    title: editor.title ?? '',
    caption: editor.caption ?? '',
    subtitles: editor.subtitles.map((line) => ({
      startSec: msToSec(line.startMs),
      endSec: msToSec(line.endMs),
      text: line.text,
      hadWordTiming: line.hasWordTiming,
    })),
    crop: crop && {
      x: String(crop.x),
      y: String(crop.y),
      width: String(crop.width),
      height: String(crop.height),
    },
    platformMeta: editor.platformMeta.map((meta) => ({
      platform: meta.platform,
      title: meta.title,
      description: meta.description,
      hashtagsText: formatHashtags(meta.hashtags),
    })),
  }
}

/** 화면에서 0.1초 단위로 보였던 값은, 손대지 않았으면 원래 ms 를 그대로 쓴다(반올림 오차로 "바뀜"이 되지 않게). */
/** 화면 값(0.1초 단위)이 원래 ms 와 같은 칸인가. "2", "2.0", 2 모두 2000ms 와 같다. */
export function sameSec(draftSec: string | number, originalMs: number): boolean {
  const ms = secToMs(draftSec)
  return Number.isFinite(ms) && Math.round(ms / 100) === Math.round(originalMs / 100)
}

function keepOrParse(draftSec: string | number, originalMs: number): number {
  return sameSec(draftSec, originalMs) ? originalMs : secToMs(draftSec)
}

function sameMeta(a: PlatformMeta[], b: PlatformMeta[]): boolean {
  return JSON.stringify(a) === JSON.stringify(b)
}

export function buildEditRequest(
  editor: ClipEditorResponse,
  draft: ClipDraft,
  source?: { width: number; height: number } | null,
): BuildResult {
  const request: ClipEditRequest = {}
  let touchesRender = false

  // 구간
  const startMs = keepOrParse(draft.startSec, editor.startMs)
  const endMs = keepOrParse(draft.endSec, editor.endMs)
  if (!Number.isFinite(startMs) || !Number.isFinite(endMs) || startMs < 0 || endMs <= startMs) {
    return { ok: false, error: 'invalidRange' }
  }
  if (startMs !== editor.startMs) request.startMs = startMs
  if (endMs !== editor.endMs) request.endMs = endMs
  if (request.startMs !== undefined || request.endMs !== undefined) touchesRender = true

  // 제목·설명
  if (draft.title !== (editor.title ?? '')) request.title = draft.title
  if (draft.caption !== (editor.caption ?? '')) request.caption = draft.caption

  // 자막 — 하나라도 다르면 전체를 보낸다(서버가 줄마다 단어 시각 유지 여부를 판단한다).
  const lines: { startMs: number; endMs: number; text: string }[] = []
  for (const [index, line] of draft.subtitles.entries()) {
    const original = editor.subtitles[index]
    const lineStart = original ? keepOrParse(line.startSec, original.startMs) : secToMs(line.startSec)
    const lineEnd = original ? keepOrParse(line.endSec, original.endMs) : secToMs(line.endSec)
    if (!Number.isFinite(lineStart) || !Number.isFinite(lineEnd) || lineEnd <= lineStart) {
      return { ok: false, error: 'invalidSubtitleTime', line: index + 1 }
    }
    if (line.text.trim() === '') return { ok: false, error: 'emptySubtitle', line: index + 1 }
    lines.push({ startMs: lineStart, endMs: lineEnd, text: line.text })
  }
  const subtitlesChanged =
    lines.length !== editor.subtitles.length ||
    lines.some((line, i) => {
      const o = editor.subtitles[i]!
      return line.startMs !== o.startMs || line.endMs !== o.endMs || line.text !== o.text
    })
  if (subtitlesChanged) {
    request.subtitles = lines
    touchesRender = true
  }

  // 크롭
  const originalCrop = parseCropJson(editor.cropJson)
  if (draft.crop) {
    const crop = {
      x: Number(draft.crop.x),
      y: Number(draft.crop.y),
      width: Number(draft.crop.width),
      height: Number(draft.crop.height),
    }
    if (!Object.values(crop).every((v) => Number.isInteger(v)) || crop.width <= 0 || crop.height <= 0 || crop.x < 0 || crop.y < 0) {
      return { ok: false, error: 'invalidCrop' }
    }
    const same = originalCrop && (Object.keys(crop) as (keyof CropBox)[]).every((k) => crop[k] === originalCrop[k])
    if (!same) {
      request.crop = crop
      if (source && source.width > 0 && source.height > 0) {
        request.sourceWidth = source.width
        request.sourceHeight = source.height
      }
      touchesRender = true
    }
  }

  // 플랫폼별 메타
  const meta: PlatformMeta[] = draft.platformMeta.map((m) => ({
    platform: m.platform,
    title: m.title,
    description: m.description,
    hashtags: parseHashtags(m.hashtagsText),
  }))
  if (!sameMeta(meta, editor.platformMeta)) request.platformMeta = meta

  return { ok: true, request, changed: Object.keys(request).length > 0, touchesRender }
}
