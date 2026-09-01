/**
 * Maps every authenticated feature route to the server capability that owns
 * it. Keeping this table centralized prevents a hidden menu from remaining
 * reachable through an old bookmark or a manually typed URL.
 *
 * Each prefix appears exactly once: lookup takes the first match, so a repeat
 * entry is either a dead line or a gate silently pinned to whichever row came
 * first. Exported so the test can hold that invariant.
 */
export const ROUTE_CAPABILITIES: ReadonlyArray<readonly [string, string]> = [
  ['/ugc/shorts/runs', 'ugc/shorts/runs'],
  ['/ugc/shorts/templates', 'ugc/shorts/templates'],
  ['/ugc/shorts/prompts', 'ugc/shorts/prompts'],
  ['/ugc/campaigns', 'ugc/campaigns'],
  ['/creator/campaigns', 'creator/campaigns'],
  ['/analytics/compare', 'analytics/compare'],
  ['/competitors', 'competitors'],
  ['/channel-audit', 'channel-audit'],
  ['/brand-deals', 'brand-deals'],
  ['/activity-log', 'activity-log'],
  ['/manual', 'manual'],
  ['/subtitle-editor', 'subtitle-editor'],
  ['/notifications', 'notifications'],
  ['/automation', 'automation'],
  ['/templates', 'templates'],
  ['/brandkit', 'brandkit'],
  ['/recycling', 'recycling'],
  ['/assets', 'assets'],
  ['/audience', 'audience'],
  ['/linkbio', 'linkbio'],
  ['/webhooks', 'webhooks'],
  ['/subscription', 'subscription'],
  ['/revenue', 'revenue'],
  ['/ab-tests', 'ab-tests'],
  ['/goals', 'goals'],
  ['/team', 'team'],
  ['/admin', 'admin'],
  ['/videos', 'videos'],
  ['/ai', 'ai'],
  ['/inbox', 'inbox-v2'],
  ['/comments', 'inbox-v2'],
  ['/inbox-v2', 'inbox-v2'],
  ['/calendar', 'calendar-v2'],
  ['/schedule', 'calendar-v2'],
  ['/calendar-v2', 'calendar-v2'],
  ['/channels', 'channels-v2'],
  ['/channels-v2', 'channels-v2'],
  ['/settings', 'settings-v2'],
  ['/settings-v2', 'settings-v2'],
  ['/analytics', 'performance'],
  ['/performance', 'performance'],
  ['/upload', 'compose'],
  ['/compose', 'compose'],
  ['/dashboard', 'today'],
  ['/today', 'today'],
]

export function requiredCapabilityForPath(path: string): string | null {
  const normalized = path.replace(/\/+$/, '') || '/'
  return ROUTE_CAPABILITIES.find(([prefix]) =>
    normalized === prefix || normalized.startsWith(`${prefix}/`),
  )?.[1] ?? null
}
