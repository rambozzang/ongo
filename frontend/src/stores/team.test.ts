import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useTeamStore } from './team'
import { teamApi } from '@/api/team'

vi.mock('@/api/team', () => ({
  teamApi: {
    listMembers: vi.fn(),
    inviteMember: vi.fn(),
    removeMember: vi.fn(),
    resendInvite: vi.fn(),
    updateRole: vi.fn(),
  },
}))

const entry = (overrides: Record<string, unknown> = {}) => ({
  id: 1,
  memberEmail: 'editor@example.com',
  memberName: '에디터',
  role: 'EDITOR',
  status: 'JOINED',
  invitedAt: '2026-08-01T00:00:00Z',
  joinedAt: '2026-08-02T00:00:00Z',
  createdAt: '2026-08-01T00:00:00Z',
  expiresAt: null,
  ...overrides,
})

describe('team store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('분리된 서버 응답을 멤버와 초대 목록으로 나눈다', async () => {
    vi.mocked(teamApi.listMembers).mockResolvedValue([
      entry(),
      entry({ id: 2, memberEmail: 'pending@example.com', status: 'INVITED', joinedAt: null, expiresAt: '2026-08-08T00:00:00Z' }),
      entry({ id: 3, memberEmail: 'expired@example.com', status: 'EXPIRED', joinedAt: null }),
    ] as never)

    const store = useTeamStore()
    await store.fetchMembers()

    expect(store.members.map((member) => member.email)).toEqual(['editor@example.com'])
    expect(store.invites.map((invite) => invite.email)).toEqual(['pending@example.com', 'expired@example.com'])
    expect(store.invites[0].expiresAt).toBe('2026-08-08T00:00:00Z')
    expect(store.loading).toBe(false)
  })

  it('초대·취소·재발송을 모두 서버 성공 뒤 로컬 상태에 반영한다', async () => {
    vi.mocked(teamApi.inviteMember).mockResolvedValue(entry({ id: 4, memberEmail: 'new@example.com', status: 'INVITED', joinedAt: null }) as never)
    vi.mocked(teamApi.resendInvite).mockResolvedValue(entry({ id: 4, memberEmail: 'new@example.com', status: 'INVITED', joinedAt: null, invitedAt: '2026-08-09T00:00:00Z' }) as never)
    vi.mocked(teamApi.removeMember).mockResolvedValue(undefined as never)

    const store = useTeamStore()
    await store.inviteMember('new@example.com', 'editor')
    expect(store.invites).toHaveLength(1)
    expect(teamApi.inviteMember).toHaveBeenCalledWith({ email: 'new@example.com', role: 'EDITOR' })

    await store.resendInvite(4)
    expect(teamApi.resendInvite).toHaveBeenCalledWith(4)
    expect(store.invites[0].invitedAt).toBe('2026-08-09T00:00:00Z')

    await store.cancelInvite(4)
    expect(teamApi.removeMember).toHaveBeenCalledWith(4)
    expect(store.invites).toHaveLength(0)
  })
})
