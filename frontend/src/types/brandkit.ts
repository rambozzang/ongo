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
  /**
   * 표시에 쓰는 URL. **서버가 조회할 때마다 새로 채워 준다**(assetId 가 있을 때).
   *
   * 이 값을 그대로 다시 저장해도 무해하지만, 신선함의 근거는 `assetId` 다.
   */
  url: string
  format: string
  size: string
  uploadedAt: string
  /**
   * 원본 에셋(`assets.id`).
   *
   * `null` 이면 이 필드가 생기기 전에 저장된 항목이다. 그 항목의 `url` 은 업로드 당시의
   * 7 일짜리 서명 URL 이라 **이미 만료됐을 수 있다.** 서버는 문자열에서 키를 되짚지 않으므로
   * 되살리려면 파일을 다시 올려야 한다.
   */
  assetId?: number | null
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
