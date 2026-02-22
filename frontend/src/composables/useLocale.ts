import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { setLocale } from '@/i18n'

export function useLocale() {
  const { locale, t } = useI18n({ useScope: 'global' })

  const currentLocale = computed(() => locale.value)
  const isKorean = computed(() => locale.value === 'ko')

  function switchLocale(newLocale: 'ko' | 'en') {
    setLocale(newLocale)
  }

  function toggleLocale() {
    const next = locale.value === 'ko' ? 'en' : 'ko'
    setLocale(next)
  }

  return {
    currentLocale,
    isKorean,
    switchLocale,
    toggleLocale,
    t,
  }
}
