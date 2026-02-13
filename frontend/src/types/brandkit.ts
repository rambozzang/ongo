export interface BrandColor {
  id: number
  name: string
  hex: string
  usage: string  // e.g. '주요 색상', '보조 색상', '배경색'
}

export interface BrandFont {
  id: number
  name: string
  family: string
  weight: string
  usage: string  // e.g. '제목', '본문', '캡션'
  sampleText: string
}

export interface BrandAsset {
  id: number
  name: string
  type: 'logo' | 'watermark' | 'intro' | 'outro' | 'overlay' | 'thumbnail_template'
  url: string
  format: string
  size: string
  uploadedAt: string
}

export interface BrandKit {
  id: string
  name: string
  description: string
  colors: BrandColor[]
  fonts: BrandFont[]
  assets: BrandAsset[]
  guidelines: string
  createdAt: string
  updatedAt: string
}
