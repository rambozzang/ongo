export interface Portfolio {
  id: number
  title: string
  description: string
  template: 'MINIMAL' | 'CREATIVE' | 'PROFESSIONAL' | 'BOLD'
  theme: string
  isPublished: boolean
  publicUrl: string | null
  viewCount: number
  sections: PortfolioSection[]
  createdAt: string
  updatedAt: string
}

export interface PortfolioSection {
  id: number
  portfolioId: number
  sectionType: 'INTRO' | 'STATS' | 'VIDEOS' | 'BRANDS' | 'TESTIMONIALS' | 'CONTACT'
  title: string
  content: string
  order: number
  isVisible: boolean
}

export interface PortfolioBuilderSummary {
  totalPortfolios: number
  publishedCount: number
  totalViews: number
  avgSections: number
  topTemplate: string
}
