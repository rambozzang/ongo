import { describe, expect, it } from 'vitest'
import {
  filterReleasedManualSections,
  RELEASED_MANUAL_SECTION_IDS,
  sectionsEn,
  sectionsKo,
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

  /*
   * Both manuals answer the same question in different languages, so a section
   * added to one and forgotten in the other leaves that audience with no
   * documentation for a feature they can see in the menu. Nothing at runtime
   * notices: each locale renders its own array.
   */
  it('documents the same sections in Korean and English', () => {
    const ko = sectionsKo.map((section) => section.id)
    const en = sectionsEn.map((section) => section.id)

    expect(ko).toEqual(en)
  })

  /*
   * The release list and the section bodies are two separate edits. Registering
   * an id without writing its section leaves the feature silently undocumented,
   * and the filter cannot tell the difference — it only ever removes.
   */
  it('defines a section body for every id it releases', () => {
    const defined = new Set(sectionsKo.map((section) => section.id))
    const missing = [...RELEASED_MANUAL_SECTION_IDS].filter((id) => !defined.has(id))

    expect(missing).toEqual([])
  })
})
