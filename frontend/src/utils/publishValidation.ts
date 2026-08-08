import type { Platform } from '@/types/channel'

export interface PublishDraft {
  title: string
  description: string
  hashtags: string
}

export interface PublishCapability {
  platform: Platform
  maxTitleLength: number
  maxDescriptionLength: number
  maxTagCount: number
}

export interface PublishTarget {
  platform: Platform
  channelName: string
}

export interface PublishValidationIssue {
  platform: Platform
  channelName: string
  field: 'title' | 'description' | 'tags'
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
  drafts: Partial<Record<Platform, PublishDraft>>,
): PublishValidationIssue[] {
  const issues: PublishValidationIssue[] = []

  for (const target of targets) {
    const capability = capabilities.find((item) => item.platform === target.platform)
    const draft = drafts[target.platform]
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
    if (capability.maxTagCount > 0 && tagCount > capability.maxTagCount) {
      issues.push({
        platform: target.platform,
        channelName: target.channelName,
        field: 'tags',
        current: tagCount,
        limit: capability.maxTagCount,
      })
    }
  }

  return issues
}
