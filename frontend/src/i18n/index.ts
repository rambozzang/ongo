import { createI18n } from 'vue-i18n'
import koCommon from '@/locales/ko/common.json'
import enCommon from '@/locales/en/common.json'

const i18n = createI18n({
  legacy: false,
  locale: localStorage.getItem('locale') || 'ko',
  fallbackLocale: 'ko',
  messages: {
    ko: koCommon,
    en: enCommon,
  },
})

export default i18n

export function setLocale(locale: 'ko' | 'en') {
  i18n.global.locale.value = locale
  localStorage.setItem('locale', locale)
  document.querySelector('html')?.setAttribute('lang', locale)
}

export function getCurrentLocale(): string {
  return i18n.global.locale.value
}
