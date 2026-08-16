import { describe, expect, it } from 'vitest'
import { requiredCapabilityForPath } from './capability'

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
})
