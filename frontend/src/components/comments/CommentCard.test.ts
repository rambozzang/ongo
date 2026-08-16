import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import CommentCard from './CommentCard.vue'
import type { Comment } from '@/types/comment'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

const comment: Comment = {
  id: 1,
  videoId: 10,
  videoTitle: '테스트 영상',
  platform: 'YOUTUBE',
  author: 'Creator',
  content: '좋은 영상입니다.',
  likeCount: 2,
  replyCount: 0,
  sentiment: 'positive',
  isReplied: false,
  isHidden: false,
  isPinned: false,
  createdAt: new Date().toISOString(),
}

describe('CommentCard actions', () => {
  it('keeps local actions visible on touch layouts and does not fail open provider actions', () => {
    const wrapper = mount(CommentCard, {
      props: {
        comment,
        capabilities: {
          YOUTUBE: { canListComments: true, canReply: false, canLike: false, canDelete: false, canHide: true },
        },
      },
      global: {
        mocks: { $t: (key: string) => key },
        stubs: {
          PlatformBadge: { template: '<span />' },
          ConfirmModal: { template: '<div />' },
          CommentReplyForm: { template: '<div />' },
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })

    const actions = wrapper.find('[class*="group-focus-within"]')
    expect(wrapper.classes()).toContain('group')
    expect(actions.classes()).toContain('opacity-100')
    expect(actions.text()).toContain('comments.card.hide')
    expect(actions.text()).toContain('comments.card.pin')
    expect(actions.text()).not.toContain('comments.card.reply')
    expect(actions.text()).not.toContain('comments.card.delete')
  })
})
