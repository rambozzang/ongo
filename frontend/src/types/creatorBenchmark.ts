export interface BenchmarkResult {
  id: number
  platform: string
  category: string
  myValue: number
  avgValue: number
  topValue: number
  percentile: number
  metric: string
  trend: string
  updatedAt: string
}

export interface BenchmarkPeer {
  id: number
  name: string
  platform: string
  subscribers: number
  avgViews: number
  engagementRate: number
  category: string
}

export interface CreatorBenchmarkSummary {
  totalMetrics: number
  aboveAvgCount: number
  topPercentile: number
  strongestMetric: string
  weakestMetric: string
}
