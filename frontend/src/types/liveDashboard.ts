export type LiveMetricType = 'VIEWS' | 'SUBSCRIBERS' | 'LIKES' | 'COMMENTS' | 'WATCH_TIME' | 'REVENUE'

export interface LiveMetricPoint {
  timestamp: string
  value: number
}

export interface LiveMetric {
  type: LiveMetricType
  currentValue: number
  previousValue: number
  changePercent: number
  trend: 'UP' | 'DOWN' | 'STABLE'
  history: LiveMetricPoint[]
}

export interface LiveAlert {
  id: number
  type: 'SPIKE' | 'DROP' | 'MILESTONE' | 'VIRAL'
  title: string
  description: string
  metric: LiveMetricType
  value: number
  threshold: number
  createdAt: string
  read: boolean
}

export interface LiveDashboardState {
  metrics: LiveMetric[]
  alerts: LiveAlert[]
  activePlatforms: string[]
  lastUpdated: string
  isConnected: boolean
}

export interface LiveAlertConfig {
  metric: LiveMetricType
  threshold: number
  direction: 'ABOVE' | 'BELOW'
  enabled: boolean
}
