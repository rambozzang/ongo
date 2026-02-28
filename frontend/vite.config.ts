import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  define: {
    // Vue 3.5 프로덕션 최적화 플래그
    __VUE_PROD_DEVTOOLS__: false,               // 프로덕션 DevTools 제거
    __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: false,  // SSR 미사용
    // vue-i18n 최적화 플래그
    __VUE_I18N_FULL_INSTALL__: true,            // $t() 글로벌 등록 필요
    __VUE_I18N_LEGACY_API__: false,             // Legacy API 제거 (Composition API 모드)
    __INTLIFY_DROP_MESSAGE_COMPILER__: false,   // JSON 메시지 런타임 컴파일 필요
    __INTLIFY_PROD_DEVTOOLS__: false,
  },
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['vite.svg'],
      manifest: {
        name: 'onGo - 크리에이터 멀티 플랫폼 관리',
        short_name: 'onGo',
        description: '영상 크리에이터를 위한 멀티 플랫폼 관리 도구',
        theme_color: '#7c3aed',
        background_color: '#ffffff',
        display: 'standalone',
        orientation: 'portrait-primary',
        scope: '/',
        start_url: '/',
        icons: [
          {
            src: '/icons/icon.svg',
            sizes: 'any',
            type: 'image/svg+xml',
            purpose: 'any',
          },
          {
            src: '/icons/icon.svg',
            sizes: 'any',
            type: 'image/svg+xml',
            purpose: 'maskable',
          },
        ],
        categories: ['productivity', 'social'],
        lang: 'ko',
      },
      workbox: {
        globPatterns: ['**/*.{css,html,ico,png,svg,woff2}'],
        navigateFallback: 'index.html',
        navigateFallbackDenylist: [/^\/api\//, /\.(?:js|css|json|woff2?|png|svg|ico)$/],
        runtimeCaching: [
          {
            urlPattern: /\.js$/i,
            handler: 'StaleWhileRevalidate',
            options: {
              cacheName: 'js-chunks',
              expiration: { maxEntries: 200, maxAgeSeconds: 60 * 60 * 24 * 30 },
            },
          },
          {
            urlPattern: /^https:\/\/cdn\.jsdelivr\.net\/.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'cdn-fonts',
              expiration: { maxEntries: 10, maxAgeSeconds: 60 * 60 * 24 * 365 },
            },
          },
        ],
      },
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    // 모던 브라우저 타겟 → 더 작은 번들 출력 (polyfill 제거)
    target: 'es2022',
    // 500KB 이상 청크 경고
    chunkSizeWarningLimit: 500,
    rollupOptions: {
      output: {
        // 세분화된 벤더 청크 분리 → 캐시 효율 극대화
        manualChunks: {
          'vendor-vue': ['vue', 'vue-router', 'pinia'],
          'vendor-i18n': ['vue-i18n'],
          'vendor-charts': ['chart.js', 'vue-chartjs'],
          'vendor-ui': ['@headlessui/vue', '@vueuse/core'],
          'vendor-network': ['axios'],
        },
        // 에셋 파일을 타입별 디렉토리로 정리
        assetFileNames: (assetInfo) => {
          const name = assetInfo.name ?? ''
          if (/\.(woff2?|ttf|eot)$/.test(name)) return 'assets/fonts/[name]-[hash][extname]'
          if (/\.(png|jpe?g|gif|svg|webp|avif|ico)$/.test(name)) return 'assets/images/[name]-[hash][extname]'
          return 'assets/[name]-[hash][extname]'
        },
      },
    },
    // 소스맵은 hidden으로 (에러 추적용, 브라우저에 노출 X)
    sourcemap: 'hidden',
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8070',
        changeOrigin: true,
      },
    },
  },
})
