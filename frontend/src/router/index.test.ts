import { describe, expect, it } from 'vitest'
import router from './index'

describe('application navigation contract', () => {
  it('resolves every server-enabled menu path to a concrete route', () => {
    const menuPaths = [
      '/today', '/compose', '/videos', '/ai', '/templates', '/brandkit', '/assets',
      '/calendar-v2', '/automation', '/channels-v2',
      '/performance', '/revenue', '/ab-tests', '/inbox-v2', '/audience', '/channel-audit',
      '/brand-deals', '/linkbio', '/ugc/campaigns', '/creator/campaigns',
      '/ugc/shorts/prompts', '/ugc/shorts/templates', '/ugc/shorts/runs', '/team',
      '/manual', '/subscription', '/settings-v2', '/admin',
    ]

    for (const path of menuPaths) {
      expect(router.resolve(path).matched.length, path).toBeGreaterThan(0)
    }
    const ideasMatched = router.resolve('/ideas').matched
    expect(ideasMatched[ideasMatched.length - 1]?.redirect).toBe('/today')
    const keywordResearchMatched = router.resolve('/keyword-research').matched
    expect(keywordResearchMatched[keywordResearchMatched.length - 1]?.redirect).toBe('/today')
    const trendsMatched = router.resolve('/trends').matched
    expect(trendsMatched[trendsMatched.length - 1]?.redirect).toBe('/today')
  })

  it('keeps required public legal and support documents directly reachable', () => {
    for (const path of ['/terms', '/privacy', '/refund', '/data-deletion', '/support']) {
      const resolved = router.resolve(path)
      expect(resolved.matched.length, path).toBeGreaterThan(0)
      expect(resolved.meta.requiresAuth, path).toBe(false)
      expect(resolved.meta.allowAuthenticated, path).toBe(true)
    }
  })
})
