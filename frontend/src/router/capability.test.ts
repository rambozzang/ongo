import { describe, expect, it } from 'vitest'
import { ROUTE_CAPABILITIES, requiredCapabilityForPath } from './capability'

describe('route capability contract', () => {
  it.each([
    ['/today', 'today'],
    ['/videos/42', 'videos'],
    ['/schedule', 'calendar-v2'],
    ['/ugc/campaigns/7/edit', 'ugc/campaigns'],
    ['/ugc/shorts/runs/9', 'ugc/shorts/runs'],
    ['/analytics/compare', 'analytics/compare'],
    ['/manual', 'manual'],
  ])('maps %s to %s', (path, capability) => {
    expect(requiredCapabilityForPath(path)).toBe(capability)
  })

  it.each(['/login', '/support', '/onboarding', '/feature-unavailable', '/unknown'])
    ('does not gate public or unmapped path %s', (path) => {
      expect(requiredCapabilityForPath(path)).toBeNull()
    })

  // A repeated prefix is unreachable: lookup stops at the first match. It reads
  // as a mapping but never applies, so the table stops describing the gate.
  // `/competitors` was added twice once; this holds the line.
  it('lists each route prefix only once', () => {
    const prefixes = ROUTE_CAPABILITIES.map(([prefix]) => prefix)
    const duplicated = [...new Set(prefixes.filter((p, i) => prefixes.indexOf(p) !== i))]

    expect(duplicated).toEqual([])
  })
})
