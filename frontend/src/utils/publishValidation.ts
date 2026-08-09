import type { Platform } from '@/types/channel'

export interface PublishDraft {
  title: string
  description: string
  hashtags: string
}

export function composePublishCaption(
  platform: Platform,
  draft: PublishDraft,
): string | null {
  const body = [draft.title.trim(), draft.description.trim()]
    .filter(Boolean)
    .join('\n\n')
  const hashtags = parsePublishHashtags(draft.hashtags)
    .map((tag) => `#${tag}`)
    .join(' ')

  if (
    platform === 'TWITTER' ||
    platform === 'TIKTOK' ||
    platform === 'INSTAGRAM' ||
    platform === 'THREADS'
  ) {
    return [body, hashtags].filter(Boolean).join('\n\n')
  }
  if (platform === 'FACEBOOK' || platform === 'LINKEDIN') {
    return [draft.description.trim(), hashtags].filter(Boolean).join('\n\n')
  }
  return null
}

export interface PublishCapability {
  platform: Platform
  maxTitleLength: number
  maxDescriptionLength: number
  maxTagCount: number
  maxCaptionLength?: number | null
}

export interface PublishTarget {
  platform: Platform
  channelName: string
  channelId?: number
}

export interface PublishValidationIssue {
  platform: Platform
  channelName: string
  field: 'title' | 'description' | 'tags' | 'caption'
  current: number
  limit: number
}

/** Normalize the exact tag representation sent to the API. */
export function parsePublishHashtags(raw: string): string[] {
  return raw
    .split(/[\s,]+/)
    .map((token) => token.replace(/^#/, '').trim())
    .filter(Boolean)
}

/** Apply server-declared limits before a publish request is sent. */
export function validatePublishDrafts(
  targets: PublishTarget[],
  capabilities: PublishCapability[],
  drafts: Partial<Record<string, PublishDraft>>,
): PublishValidationIssue[] {
  const issues: PublishValidationIssue[] = []

  for (const target of targets) {
    const capability = capabilities.find((item) => item.platform === target.platform)
    const draft =
      (target.channelId != null ? drafts[String(target.channelId)] : undefined) ??
      drafts[target.platform]
    if (!capability || !draft) continue

    if (capability.maxTitleLength > 0 && draft.title.length > capability.maxTitleLength) {
      issues.push({
        platform: target.platform,
        channelName: target.channelName,
        field: 'title',
        current: draft.title.length,
        limit: capability.maxTitleLength,
      })
    }
    if (
      capability.maxDescriptionLength > 0 &&
      draft.description.length > capability.maxDescriptionLength
    ) {
      issues.push({
        platform: target.platform,
        channelName: target.channelName,
        field: 'description',
        current: draft.description.length,
        limit: capability.maxDescriptionLength,
      })
    }
    const tagCount = parsePublishHashtags(draft.hashtags).length
    const tagsUnsupported = capability.maxTagCount === 0 && tagCount > 0
    const tagsExceedLimit = capability.maxTagCount > 0 && tagCount > capability.maxTagCount
    if (tagsUnsupported || tagsExceedLimit) {
      issues.push({
        platform: target.platform,
        channelName: target.channelName,
        field: 'tags',
        current: tagCount,
        limit: capability.maxTagCount,
      })
    }
    const caption = composePublishCaption(target.platform, draft)
    if (capability.maxCaptionLength && caption && caption.length > capability.maxCaptionLength) {
      issues.push({
        platform: target.platform,
        channelName: target.channelName,
        field: 'caption',
        current: caption.length,
        limit: capability.maxCaptionLength,
      })
    }
  }

  return issues
}
