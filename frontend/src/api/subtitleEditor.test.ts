import { describe, it } from 'vitest'
import assert from 'node:assert/strict'
import {
  parseCues,
  serializeCues,
  countWords,
  totalDurationOf,
  formatTimestamp,
  toSrt,
  toVtt,
  toAss,
  toTxt,
  exportCues,
  type SubtitleCue,
} from './subtitleEditor'

const sampleCues: SubtitleCue[] = [
  { start: 0, end: 2.5, text: '안녕하세요' },
  { start: 65.25, end: 70, text: '두 번째 자막입니다', speaker: 'A', confidence: 0.92 },
]

describe('parseCues', () => {
  it('정상 JSON 배열을 큐로 파싱한다', () => {
    const cues = parseCues(serializeCues(sampleCues))
    assert.equal(cues.length, 2)
    assert.deepEqual(cues[0], { start: 0, end: 2.5, text: '안녕하세요' })
    assert.equal(cues[1].speaker, 'A')
    assert.equal(cues[1].confidence, 0.92)
  })

  it('깨진 JSON·배열 아닌 값은 빈 배열로 되돌린다', () => {
    assert.deepEqual(parseCues('not json'), [])
    assert.deepEqual(parseCues('{"a":1}'), [])
    assert.deepEqual(parseCues(''), [])
  })

  it('start/end 없는 항목은 걸러낸다', () => {
    const cues = parseCues('[{"text":"x"},{"start":1,"end":2,"text":"y"}]')
    assert.equal(cues.length, 1)
    assert.equal(cues[0].text, 'y')
  })
})

describe('지표 계산', () => {
  it('countWords는 공백 기준으로 합산한다', () => {
    assert.equal(countWords(sampleCues), 1 + 3)
    assert.equal(countWords([]), 0)
  })

  it('totalDurationOf는 가장 늦은 end를 돌려준다', () => {
    assert.equal(totalDurationOf(sampleCues), 70)
    assert.equal(totalDurationOf([]), 0)
  })
})

describe('formatTimestamp', () => {
  it('SRT용 쉼표 구분', () => {
    assert.equal(formatTimestamp(65.25, ','), '00:01:05,250')
  })

  it('VTT용 점 구분과 시간 자리', () => {
    assert.equal(formatTimestamp(3661.5, '.'), '01:01:01,500'.replace(',', '.'))
  })

  it('음수는 0으로 클램프한다', () => {
    assert.equal(formatTimestamp(-3, ','), '00:00:00,000')
  })
})

describe('납출 포맷', () => {
  it('SRT는 인덱스와 화살표 행을 만든다', () => {
    const srt = toSrt(sampleCues)
    assert.ok(srt.startsWith('1\n00:00:00,000 --> 00:00:02,500\n안녕하세요'))
    assert.ok(srt.includes('2\n00:01:05,250 --> 00:01:10,000\n두 번째 자막입니다'))
  })

  it('VTT는 WEBVTT 헤더로 시작한다', () => {
    const vtt = toVtt(sampleCues)
    assert.ok(vtt.startsWith('WEBVTT\n\n00:00:00.000 --> 00:00:02.500\n안녕하세요'))
  })

  it('ASS는 헤더와 Dialogue 행을 만든다', () => {
    const ass = toAss(sampleCues)
    assert.ok(ass.includes('[Script Info]'))
    assert.ok(ass.includes('Dialogue: 0,0:00:00.00,0:00:02.50,Default,,0,0,0,,안녕하세요'))
    assert.ok(ass.includes('Dialogue: 0,0:01:05.25,0:01:10.00,Default,,0,0,0,,두 번째 자막입니다'))
  })

  it('ASS 본문의 쉼표는 필드 구분자와 충돌하지 않게 치환한다', () => {
    const ass = toAss([{ start: 0, end: 1, text: '쉼표, 포함' }])
    assert.ok(!ass.includes('쉼표, 포함'))
    assert.ok(ass.includes('쉼표'))
  })

  it('TXT는 텍스트만 줄 단위로 합친다', () => {
    assert.equal(toTxt(sampleCues), '안녕하세요\n두 번째 자막입니다')
  })

  it('exportCues는 포맷별로 분기한다', () => {
    assert.equal(exportCues(sampleCues, 'TXT'), toTxt(sampleCues))
    assert.equal(exportCues(sampleCues, 'SRT'), toSrt(sampleCues))
    assert.equal(exportCues(sampleCues, 'VTT'), toVtt(sampleCues))
    assert.equal(exportCues(sampleCues, 'ASS'), toAss(sampleCues))
  })
})
