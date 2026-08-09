import { describe, expect, it } from 'vitest'
import {
  filterReleasedManualSections,
  RELEASED_MANUAL_SECTION_IDS,
  type ManualSection,
} from './manualSections'

function sections(...ids: string[]): ManualSection[] {
  return ids.map((id) => ({
    id,
    title: id,
    icon: {} as ManualSection['icon'],
    content: [],
  }))
}

describe('released manual sections', () => {
  it('hides obsolete idea and WIP sections', () => {
    const result = filterReleasedManualSections(
      sections('getting-started', 'fan-funding', 'schedule-optimizer', 'faq'),
      new Set(),
    )

    expect(result.map((section) => section.id)).toEqual(['getting-started', 'faq'])
    expect(RELEASED_MANUAL_SECTION_IDS.has('fan-funding')).toBe(false)
    expect(RELEASED_MANUAL_SECTION_IDS.has('schedule-optimizer')).toBe(false)
  })

  it('requires the server capability for feature documentation', () => {
    const result = filterReleasedManualSections(
      sections('upload', 'channels', 'ugc-campaigns', 'faq'),
      new Set(['compose', 'faq']),
    )

    expect(result.map((section) => section.id)).toEqual(['upload', 'faq'])
  })
})
