import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { brandKitApi } from '@/api/brandkit'
import type { BrandKit, BrandColor, BrandFont, BrandAsset } from '@/types/brandkit'

const STORAGE_KEY = 'ongo_brandkit'

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

  function saveToStorage() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(brandKit.value))
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
          colors: [
            { id: 1, name: '메인 컬러', hex: kit.primaryColor, usage: '주요 색상' },
            { id: 2, name: '서브 컬러', hex: kit.secondaryColor, usage: '보조 색상' },
            { id: 3, name: '액센트', hex: kit.accentColor, usage: '강조 색상' },
          ],
          fonts: [
            { id: 1, name: '메인 서체', family: kit.fontFamily, weight: '400', usage: '본문', sampleText: '샘플 텍스트' },
          ],
          assets: [],
          guidelines: kit.guidelines || '',
          createdAt: kit.createdAt,
          updatedAt: kit.updatedAt,
        }
      }
    } catch (e) {
      console.error('Failed to fetch brand kits:', e)
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

      if (kitId && !isNaN(kitId)) {
        await brandKitApi.update(kitId, {
          name: brandKit.value.name,
          primaryColor: primary?.hex,
          secondaryColor: secondary?.hex,
          accentColor: accent?.hex,
          fontFamily: mainFont?.family,
          guidelines: brandKit.value.guidelines,
        })
      } else {
        await brandKitApi.create({
          name: brandKit.value.name,
          primaryColor: primary?.hex,
          secondaryColor: secondary?.hex,
          accentColor: accent?.hex,
          fontFamily: mainFont?.family,
          guidelines: brandKit.value.guidelines,
        })
      }
    } catch (e) {
      console.error('Failed to save brand kit:', e)
    }
    saveToStorage()
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
