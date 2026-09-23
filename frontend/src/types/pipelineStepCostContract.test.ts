import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { PIPELINE_STEPS } from './ai'

/*
 * 파이프라인 빌더가 보여 주는 단계 가격은 서버의 AiPipelineStep 과 같아야 한다. 서버가 실제로 차감하는 값이
 * 화면과 다르면 사용자는 표시보다 많이 내거나, 적게 보고 시작했다가 크레딧 부족을 만난다.
 * STT 는 원가 보장을 위해 52(10분 이하)로 올랐다 — 예전 화면은 10 을 보였다.
 */
const here = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(
  resolve(here, '../../../backend/onGo-domain/src/main/kotlin/com/ongo/domain/ai/AiPipelineStep.kt'),
  'utf8',
)

function serverCosts(): Record<string, number> {
  const costs: Record<string, number> = {}
  for (const match of source.matchAll(/^\s*([A-Z_]+)\("[^"]*",\s*(\d+)\)/gm)) {
    costs[match[1]] = Number(match[2])
  }
  return costs
}

describe('파이프라인 단계 가격과 서버 AiPipelineStep', () => {
  it('서버 선언을 모두 읽는다', () => {
    expect(Object.keys(serverCosts()).sort()).toEqual(PIPELINE_STEPS.map((s) => s.key).sort())
  })

  it('화면의 단계별 크레딧이 서버와 같다', () => {
    const server = serverCosts()
    for (const step of PIPELINE_STEPS) {
      expect(step.creditCost, step.key).toBe(server[step.key])
    }
  })
})
