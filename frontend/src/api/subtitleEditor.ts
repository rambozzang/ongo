import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

/**
 * 자막 에디터 API — 백엔드 SubtitleEditorController(`/api/v1/subtitle-editor`) 대응.
 * 주의: 백엔드가 `@Profile("wip")` 이라 wip 프로필에서만 활성화된다.
 *
 * cues 는 백엔드가 JSONB 문자열 그대로 저장·반환하므로 직렬화 형식은 여기가 기준이다.
 * (백엔드 SubtitleEditorDtos.kt 의 cues: String 주석 참고)
 */

/** 자막 큐 하나. start/end 는 초 단위(소수 가능). */
export interface SubtitleCue {
  start: number
  end: number
  text: string
  speaker?: string
  confidence?: number
}

export interface SubtitleTrackResponse {
  id: number
  videoId: number
  videoTitle: string | null
  language: string
  status: string
  /** SubtitleCue[] 를 직렬화한 JSON 문자열 */
  cues: string
  totalDuration: number
  wordCount: number
  createdAt: string | null
  updatedAt: string | null
}

export interface CreateSubtitleTrackRequest {
  videoId: number
  videoTitle?: string | null
  language: string
  cues?: string
  totalDuration?: number
  wordCount?: number
}

export interface UpdateSubtitleTrackRequest {
  language?: string
  status?: string
  cues?: string
  totalDuration?: number
  wordCount?: number
}

export type SubtitleExportFormat = 'SRT' | 'VTT' | 'ASS' | 'TXT'

const base = '/subtitle-editor'

export const subtitleEditorApi = {
  listTracks() {
    return apiClient
      .get<ResData<SubtitleTrackResponse[]>>(`${base}/tracks`)
      .then(unwrapResponse)
  },

  listTracksByVideo(videoId: number) {
    return apiClient
      .get<ResData<SubtitleTrackResponse[]>>(`${base}/tracks/by-video`, { params: { videoId } })
      .then(unwrapResponse)
  },

  createTrack(request: CreateSubtitleTrackRequest) {
    return apiClient
      .post<ResData<SubtitleTrackResponse>>(`${base}/tracks`, request)
      .then(unwrapResponse)
  },

  updateTrack(id: number, request: UpdateSubtitleTrackRequest) {
    return apiClient
      .put<ResData<SubtitleTrackResponse>>(`${base}/tracks/${id}`, request)
      .then(unwrapResponse)
  },

  deleteTrack(id: number) {
    return apiClient.delete<ResData<null>>(`${base}/tracks/${id}`).then(unwrapResponse)
  },
}

// ---- 큐 직렬화·납출 헬퍼 ----

/** cues JSON 문자열을 파싱한다. 깨진 값이면 빈 배열로 되돌린다(편집기가 죽지 않게). */
export function parseCues(json: string): SubtitleCue[] {
  try {
    const parsed: unknown = JSON.parse(json)
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter(
        (c): c is Record<string, unknown> =>
          typeof c === 'object' && c !== null && 'start' in c && 'end' in c,
      )
      .map((c) => ({
        start: Number(c.start) || 0,
        end: Number(c.end) || 0,
        text: typeof c.text === 'string' ? c.text : '',
        ...(typeof c.speaker === 'string' ? { speaker: c.speaker } : {}),
        ...(typeof c.confidence === 'number' ? { confidence: c.confidence } : {}),
      }))
  } catch {
    return []
  }
}

export function serializeCues(cues: SubtitleCue[]): string {
  return JSON.stringify(cues)
}

/** 단어 수 — 공백 기준. 한국어처럼 띄어쓰기가 적어도 저장 지표로는 충분하다 */
export function countWords(cues: SubtitleCue[]): number {
  return cues.reduce((sum, c) => sum + c.text.split(/\s+/).filter(Boolean).length, 0)
}

/** 총 길이(초) — 가장 늦게 끝나는 큐 기준 */
export function totalDurationOf(cues: SubtitleCue[]): number {
  return cues.reduce((max, c) => Math.max(max, c.end), 0)
}

/** 초 → 'HH:MM:SS,mmm' (SRT) 또는 'HH:MM:SS.mmm' (VTT) */
export function formatTimestamp(seconds: number, separator: ',' | '.'): string {
  const safe = Math.max(0, seconds)
  const h = Math.floor(safe / 3600)
  const m = Math.floor((safe % 3600) / 60)
  const s = Math.floor(safe % 60)
  const ms = Math.round((safe - Math.floor(safe)) * 1000)
  const pad = (n: number, len = 2) => String(n).padStart(len, '0')
  return `${pad(h)}:${pad(m)}:${pad(s)}${separator}${pad(ms, 3)}`
}

/** ASS 타임스탬프는 H:MM:SS.cc (센티초) 형식이다 */
function formatAssTimestamp(seconds: number): string {
  const safe = Math.max(0, seconds)
  const h = Math.floor(safe / 3600)
  const m = Math.floor((safe % 3600) / 60)
  const s = Math.floor(safe % 60)
  const cs = Math.round((safe - Math.floor(safe)) * 100)
  return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}.${String(cs).padStart(2, '0')}`
}

/** ASS 의 쉼표는 필드 구분자라 본문에서는 제거한다 */
function assEscape(text: string): string {
  return text.replace(/,/g, '،').replace(/\n/g, '\\N')
}

export function toSrt(cues: SubtitleCue[]): string {
  return cues
    .map(
      (c, i) =>
        `${i + 1}\n${formatTimestamp(c.start, ',')} --> ${formatTimestamp(c.end, ',')}\n${c.text}`,
    )
    .join('\n\n')
}

export function toVtt(cues: SubtitleCue[]): string {
  const body = cues
    .map((c) => `${formatTimestamp(c.start, '.')} --> ${formatTimestamp(c.end, '.')}\n${c.text}`)
    .join('\n\n')
  return `WEBVTT\n\n${body}`
}

export function toAss(cues: SubtitleCue[]): string {
  const header = [
    '[Script Info]',
    'ScriptType: v4.00+',
    '',
    '[V4+ Styles]',
    'Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding',
    'Style: Default,Pretendard,20,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,0,2,10,10,10,1',
    '',
    '[Events]',
    'Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text',
  ]
  const lines = cues.map(
    (c) =>
      `Dialogue: 0,${formatAssTimestamp(c.start)},${formatAssTimestamp(c.end)},Default,,0,0,0,,${assEscape(c.text)}`,
  )
  return [...header, ...lines].join('\n')
}

export function toTxt(cues: SubtitleCue[]): string {
  return cues.map((c) => c.text).join('\n')
}

export function exportCues(cues: SubtitleCue[], format: SubtitleExportFormat): string {
  switch (format) {
    case 'SRT':
      return toSrt(cues)
    case 'VTT':
      return toVtt(cues)
    case 'ASS':
      return toAss(cues)
    case 'TXT':
      return toTxt(cues)
  }
}
