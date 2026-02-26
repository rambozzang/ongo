export interface MusicTrack {
  id: number
  title: string
  artist: string
  genre: string
  mood: string
  bpm: number
  duration: number
  previewUrl: string | null
  licenseType: 'FREE' | 'STANDARD' | 'PREMIUM'
  matchScore: number
  createdAt: string
}

export interface MusicRecommendation {
  id: number
  videoId: number
  videoTitle: string
  tracks: MusicTrack[]
  selectedTrackId: number | null
  status: 'PENDING' | 'RECOMMENDED' | 'APPLIED'
  createdAt: string
}

export interface MusicRecommenderSummary {
  totalRecommendations: number
  appliedTracks: number
  topGenre: string
  topMood: string
  avgMatchScore: number
}
