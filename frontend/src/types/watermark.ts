export type WatermarkType = 'image' | 'text'
export type WatermarkPosition = 'top-left' | 'top-center' | 'top-right' | 'center-left' | 'center' | 'center-right' | 'bottom-left' | 'bottom-center' | 'bottom-right'

export interface ImageWatermark {
  type: 'image'
  imageUrl: string
  fileName: string
  position: WatermarkPosition
  size: number // percentage 5-50
  opacity: number // 0-100
  margin: number // pixels 0-50
}

export interface TextWatermark {
  type: 'text'
  text: string
  position: WatermarkPosition
  fontSize: number // 12-72
  fontFamily: string
  color: string
  opacity: number // 0-100
  margin: number // pixels 0-50
  bold: boolean
  italic: boolean
}

export type Watermark = ImageWatermark | TextWatermark

export interface WatermarkPreset {
  id: string
  name: string
  watermark: Watermark
  isDefault: boolean
  platformOverrides: Partial<Record<string, Watermark>>
  createdAt: string
  updatedAt: string
}

// Backend DTO types
export interface WatermarkResponse {
  id: number
  name: string
  imageUrl: string
  position: string
  opacity: number
  size: number
  isDefault: boolean
  createdAt: string | null
}

export interface WatermarkListResponse {
  watermarks: WatermarkResponse[]
}

export interface CreateWatermarkRequest {
  name: string
  imageUrl: string
  position?: string
  opacity?: number
  size?: number
  isDefault?: boolean
}

export interface UpdateWatermarkRequest {
  name: string
  imageUrl: string
  position?: string
  opacity?: number
  size?: number
}
