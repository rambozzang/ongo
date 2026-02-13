import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Watermark, WatermarkPreset } from '@/types/watermark'
import { watermarkApi } from '@/api/watermark'

const STORAGE_KEY = 'ongo_watermark_presets'

export const useWatermarkStore = defineStore('watermark', () => {
  const presets = ref<WatermarkPreset[]>(loadFromStorage())
  const activePresetId = ref<string | null>(presets.value.length > 0 ? presets.value[0].id : null)

  function loadFromStorage(): WatermarkPreset[] {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) {
      try {
        return JSON.parse(stored)
      } catch {
        return []
      }
    }
    return []
  }

  function saveToStorage() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(presets.value))
  }

  async function fetchWatermarks() {
    try {
      const response = await watermarkApi.list()
      if (response.watermarks.length > 0) {
        presets.value = response.watermarks.map((w) => ({
          id: `server-${w.id}`,
          name: w.name,
          watermark: {
            type: 'image' as const,
            imageUrl: w.imageUrl,
            fileName: w.name,
            position: w.position.toLowerCase().replace('_', '-') as WatermarkPreset['watermark'] extends { position: infer P } ? P : never,
            size: w.size,
            opacity: w.opacity * 100,
            margin: 16,
          },
          isDefault: w.isDefault,
          platformOverrides: {},
          createdAt: w.createdAt ?? new Date().toISOString(),
          updatedAt: w.createdAt ?? new Date().toISOString(),
        }))
        saveToStorage()
      }
    } catch {
      // Keep local presets
    }
  }

  // Try to load from server
  fetchWatermarks()

  const defaultPreset = computed(() =>
    presets.value.find((p) => p.isDefault) ?? null,
  )

  const activePreset = computed(() =>
    presets.value.find((p) => p.id === activePresetId.value) ?? null,
  )

  function generateId(): string {
    return `preset-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`
  }

  async function addPreset(name: string, watermark: Watermark) {
    const now = new Date().toISOString()
    const preset: WatermarkPreset = {
      id: generateId(),
      name,
      watermark,
      isDefault: presets.value.length === 0,
      platformOverrides: {},
      createdAt: now,
      updatedAt: now,
    }

    // Try to save to server
    if (watermark.type === 'image') {
      try {
        await watermarkApi.create({
          name,
          imageUrl: watermark.imageUrl,
          position: watermark.position.toUpperCase().replace('-', '_'),
          opacity: watermark.opacity / 100,
          size: watermark.size,
          isDefault: preset.isDefault,
        })
      } catch {
        // Continue with local save
      }
    }

    presets.value.push(preset)
    activePresetId.value = preset.id
    saveToStorage()
  }

  function updatePreset(id: string, watermark: Watermark) {
    const index = presets.value.findIndex((p) => p.id === id)
    if (index !== -1) {
      presets.value[index] = {
        ...presets.value[index],
        watermark,
        updatedAt: new Date().toISOString(),
      }
      saveToStorage()
    }
  }

  function updatePresetName(id: string, name: string) {
    const index = presets.value.findIndex((p) => p.id === id)
    if (index !== -1) {
      presets.value[index] = {
        ...presets.value[index],
        name,
        updatedAt: new Date().toISOString(),
      }
      saveToStorage()
    }
  }

  async function deletePreset(id: string) {
    const index = presets.value.findIndex((p) => p.id === id)
    if (index === -1) return

    // Try server delete
    if (id.startsWith('server-')) {
      const serverId = parseInt(id.replace('server-', ''))
      try {
        await watermarkApi.delete(serverId)
      } catch {
        // Continue with local delete
      }
    }

    const wasDefault = presets.value[index].isDefault
    presets.value.splice(index, 1)

    if (wasDefault && presets.value.length > 0) {
      presets.value[0].isDefault = true
    }

    if (activePresetId.value === id) {
      activePresetId.value = presets.value.length > 0 ? presets.value[0].id : null
    }

    saveToStorage()
  }

  async function setDefault(id: string) {
    // Try server update
    if (id.startsWith('server-')) {
      const serverId = parseInt(id.replace('server-', ''))
      try {
        await watermarkApi.setDefault(serverId)
      } catch {
        // Continue locally
      }
    }

    presets.value.forEach((p) => {
      p.isDefault = p.id === id
    })
    saveToStorage()
  }

  function setPlatformOverride(presetId: string, platform: string, watermark: Watermark) {
    const index = presets.value.findIndex((p) => p.id === presetId)
    if (index !== -1) {
      presets.value[index].platformOverrides[platform] = watermark
      presets.value[index].updatedAt = new Date().toISOString()
      saveToStorage()
    }
  }

  function removePlatformOverride(presetId: string, platform: string) {
    const index = presets.value.findIndex((p) => p.id === presetId)
    if (index !== -1) {
      delete presets.value[index].platformOverrides[platform]
      presets.value[index].updatedAt = new Date().toISOString()
      saveToStorage()
    }
  }

  function setActivePreset(id: string | null) {
    activePresetId.value = id
  }

  return {
    presets,
    activePresetId,
    defaultPreset,
    activePreset,
    fetchWatermarks,
    addPreset,
    updatePreset,
    updatePresetName,
    deletePreset,
    setDefault,
    setPlatformOverride,
    removePlatformOverride,
    setActivePreset,
  }
})
