import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  define: {
    __VUE_I18N_FULL_INSTALL__: true,
    __VUE_I18N_LEGACY_API__: false,
    __INTLIFY_DROP_MESSAGE_COMPILER__: false,
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
        // 앱 셸(index.html, CSS, 핵심 JS)만 프리캐시. 뷰 청크는 런타임 캐시.
        globPatterns: ['**/*.{css,html,ico,png,svg,woff2}'],
        navigateFallback: 'index.html',
        navigateFallbackDenylist: [/^\/api\//, /\.(?:js|css|json|woff2?|png|svg|ico)$/],
        runtimeCaching: [
          {
            // lazy-loaded JS 청크: 방문 시에만 캐시
            urlPattern: /\.js$/i,
            handler: 'StaleWhileRevalidate',
            options: {
              cacheName: 'js-chunks',
              expiration: {
                maxEntries: 200,
                maxAgeSeconds: 60 * 60 * 24 * 30,
              },
            },
          },
          {
            urlPattern: /^https:\/\/cdn\.jsdelivr\.net\/.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'cdn-fonts',
              expiration: {
                maxEntries: 10,
                maxAgeSeconds: 60 * 60 * 24 * 365,
              },
            },
          },
          {
            urlPattern: /\/api\/v1\/.*/i,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'api-cache',
              expiration: {
                maxEntries: 50,
                maxAgeSeconds: 60 * 5,
              },
              networkTimeoutSeconds: 10,
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
    rollupOptions: {
      output: {
        manualChunks: {
          // 벤더 라이브러리를 큰 청크로 묶어 HTTP 요청 수 감소
          'vendor-vue': ['vue', 'vue-router', 'pinia', 'vue-i18n'],
          'vendor-charts': ['chart.js', 'vue-chartjs'],
          'vendor-ui': ['@heroicons/vue'],
        },
      },
    },
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
