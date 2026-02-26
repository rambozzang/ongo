export type PollType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'RANKING'

export type PollStatus = 'DRAFT' | 'ACTIVE' | 'CLOSED' | 'SCHEDULED'

export interface PollOption {
  id: number
  text: string
  voteCount: number
  percentage: number
}

export interface FanPoll {
  id: number
  title: string
  description: string
  type: PollType
  status: PollStatus
  options: PollOption[]
  totalVotes: number
  deadline: string
  createdAt: string
  updatedAt: string
  allowMultiple: boolean
  maxSelections?: number
}

export interface PollSummary {
  totalPolls: number
  activePolls: number
  closedPolls: number
  totalVotes: number
  avgVotesPerPoll: number
}

export interface PollCreateRequest {
  title: string
  description: string
  type: PollType
  options: string[]
  deadline: string
  allowMultiple: boolean
  maxSelections?: number
}
