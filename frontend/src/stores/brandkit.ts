import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { brandKitApi, type BrandKitResponse } from '@/api/brandkit'
import { useNotificationStore } from '@/stores/notification'
import type { BrandKit, BrandColor, BrandFont, BrandAsset } from '@/types/brandkit'

const emptyBrandKit = (): BrandKit => ({
  id: '',
  name: '',
  description: '',
  colors: [],
  fonts: [],
  assets: [],
  guidelines: '',
  createdAt: '',
  updatedAt: '',
})

export const useBrandKitStore = defineStore('brandkit', () => {
  const brandKit = ref<BrandKit>(emptyBrandKit())
  const isDirty = ref(false)
  const loading = ref(false)

  function assetsFromResponse(kit: BrandKitResponse): BrandAsset[] {
    if (kit.assets.length > 0) {
      return kit.assets.map((asset) => ({
        ...asset,
        type: asset.type as BrandAsset['type'],
      }))
    }
    const entries: Array<[BrandAsset['type'], string | null]> = [
      ['logo', kit.logoUrl],
      ['watermark', kit.watermarkUrl],
      ['intro', kit.introTemplateUrl],
      ['outro', kit.outroTemplateUrl],
    ]
    return entries.flatMap(([type, url], index) => url ? [{
      id: kit.id * 10 + index,
      name: type,
      type,
      url,
      format: url.split('.').pop()?.split('?')[0]?.toUpperCase() || 'FILE',
      size: '',
      uploadedAt: kit.updatedAt,
    }] : [])
  }

  async function fetchBrandKits() {
    loading.value = true
    try {
      const data = await brandKitApi.list()
      if (data.length > 0) {
        const kit = data[0]
        brandKit.value = {
          id: String(kit.id),
          name: kit.name,
          description: '',
          colors: kit.colors.length > 0 ? kit.colors : [
            { id: 1, name: '메인 컬러', hex: kit.primaryColor, usage: '주요 색상' },
            { id: 2, name: '서브 컬러', hex: kit.secondaryColor, usage: '보조 색상' },
            { id: 3, name: '액센트', hex: kit.accentColor, usage: '강조 색상' },
          ],
          fonts: kit.fonts.length > 0 ? kit.fonts : [
            { id: 1, name: '메인 서체', family: kit.fontFamily, weight: '400', usage: '본문', sampleText: '샘플 텍스트' },
          ],
          assets: assetsFromResponse(kit),
          guidelines: kit.guidelines || '',
          createdAt: kit.createdAt,
          updatedAt: kit.updatedAt,
        }
      }
    } catch (e) {
      useNotificationStore().error('브랜드킷 저장 중 오류가 발생했습니다')
      throw e
    } finally {
      loading.value = false
    }
  }

  function updateColor(id: number, updates: Partial<BrandColor>) {
    const index = brandKit.value.colors.findIndex(c => c.id === id)
    if (index !== -1) {
      brandKit.value.colors[index] = { ...brandKit.value.colors[index], ...updates }
      isDirty.value = true
    }
  }

  function addColor(color: Omit<BrandColor, 'id'>) {
    const newId = Math.max(0, ...brandKit.value.colors.map(c => c.id)) + 1
    brandKit.value.colors.push({ id: newId, ...color })
    isDirty.value = true
  }

  function removeColor(id: number) {
    brandKit.value.colors = brandKit.value.colors.filter(c => c.id !== id)
    isDirty.value = true
  }

  function updateFont(id: number, updates: Partial<BrandFont>) {
    const index = brandKit.value.fonts.findIndex(f => f.id === id)
    if (index !== -1) {
      brandKit.value.fonts[index] = { ...brandKit.value.fonts[index], ...updates }
      isDirty.value = true
    }
  }

  function addFont(font: Omit<BrandFont, 'id'>) {
    const newId = Math.max(0, ...brandKit.value.fonts.map(f => f.id)) + 1
    brandKit.value.fonts.push({ id: newId, ...font })
    isDirty.value = true
  }

  function removeFont(id: number) {
    brandKit.value.fonts = brandKit.value.fonts.filter(f => f.id !== id)
    isDirty.value = true
  }

  function addAsset(asset: Omit<BrandAsset, 'id'>) {
    const newId = Math.max(0, ...brandKit.value.assets.map(a => a.id)) + 1
    brandKit.value.assets.push({ id: newId, ...asset })
    isDirty.value = true
  }

  function removeAsset(id: number) {
    brandKit.value.assets = brandKit.value.assets.filter(a => a.id !== id)
    isDirty.value = true
  }

  function updateGuidelines(text: string) {
    brandKit.value.guidelines = text
    isDirty.value = true
  }

  async function saveBrandKit() {
    brandKit.value.updatedAt = new Date().toISOString()
    try {
      const kitId = Number(brandKit.value.id)
      const primary = brandKit.value.colors.find(c => c.usage === '주요 색상')
      const secondary = brandKit.value.colors.find(c => c.usage === '보조 색상')
      const accent = brandKit.value.colors.find(c => c.usage === '강조 색상')
      const mainFont = brandKit.value.fonts[0]
      const assetUrl = (type: BrandAsset['type']) => brandKit.value.assets.find((asset) => asset.type === type)?.url
      const request = {
        name: brandKit.value.name,
        primaryColor: primary?.hex,
        secondaryColor: secondary?.hex,
        accentColor: accent?.hex,
        fontFamily: mainFont?.family,
        guidelines: brandKit.value.guidelines,
        logoUrl: assetUrl('logo') ?? '',
        watermarkUrl: assetUrl('watermark') ?? '',
        introTemplateUrl: assetUrl('intro') ?? '',
        outroTemplateUrl: assetUrl('outro') ?? '',
        colors: brandKit.value.colors,
        fonts: brandKit.value.fonts,
        assets: brandKit.value.assets,
      }

      const saved = kitId && !isNaN(kitId)
        ? await brandKitApi.update(kitId, request)
        : await brandKitApi.create(request)
      brandKit.value.id = String(saved.id)
      brandKit.value.createdAt = saved.createdAt
      brandKit.value.updatedAt = saved.updatedAt
      brandKit.value.assets = assetsFromResponse(saved)
    } catch (e) {
      useNotificationStore().error('브랜드킷 저장 중 오류가 발생했습니다')
      throw e
    }
    isDirty.value = false
  }

  const primaryColor = computed(() => brandKit.value.colors.find(c => c.usage === '주요 색상'))
  const logoAssets = computed(() => brandKit.value.assets.filter(a => a.type === 'logo'))
  const templateAssets = computed(() => brandKit.value.assets.filter(a => a.type === 'thumbnail_template'))

  return {
    brandKit,
    isDirty,
    loading,
    fetchBrandKits,
    updateColor,
    addColor,
    removeColor,
    updateFont,
    addFont,
    removeFont,
    addAsset,
    removeAsset,
    updateGuidelines,
    saveBrandKit,
    primaryColor,
    logoAssets,
    templateAssets,
  }
})
