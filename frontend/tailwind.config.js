/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        gray: {
          50: '#f8f8fb',
          100: '#f0f1f5',
          200: '#e2e4eb',
          300: '#cdd0da',
          400: '#979cab',
          500: '#707585',
          600: '#565b6b',
          700: '#3f4453',
          800: '#2b2f3e',
          900: '#202330',
          950: '#151722',
        },
        primary: {
          50: '#f1f2ff',
          100: '#e3e5ff',
          200: '#cdd0ff',
          300: '#adb1ff',
          400: '#888cf5',
          500: '#696de8',
          600: '#5659d6',
          700: '#4548bd',
          800: '#363894',
          900: '#282a6e',
        },
        // Semantic status colors — backed by CSS vars in src/assets/tokens.css,
        // so `bg-success-subtle` / `text-success-strong` flip automatically in dark mode.
        // NOTE: these do NOT support Tailwind's opacity modifier (`text-error/50`)
        // because the values are raw var() references, not channel triplets.
        success: {
          DEFAULT: 'var(--color-success)',
          subtle: 'var(--color-success-subtle)',
          strong: 'var(--color-success-strong)',
        },
        warning: {
          DEFAULT: 'var(--color-warning)',
          subtle: 'var(--color-warning-subtle)',
          strong: 'var(--color-warning-strong)',
        },
        error: {
          DEFAULT: 'var(--color-error)',
          subtle: 'var(--color-error-subtle)',
          strong: 'var(--color-error-strong)',
        },
        info: {
          DEFAULT: 'var(--color-info)',
          subtle: 'var(--color-info-subtle)',
          strong: 'var(--color-info-strong)',
        },
        muted: {
          DEFAULT: 'var(--color-muted)',
          subtle: 'var(--color-muted-subtle)',
          strong: 'var(--color-muted-strong)',
        },
        // 리디자인(2026-08) 토큰. tokens.css 의 CSS 변수를 그대로 노출해
        // 라이트/다크가 자동으로 뒤집힌다. 의미 이름으로만 쓰고 hex 를 직접 박지 말 것.
        surface: {
          DEFAULT: 'var(--surface-primary)',
          base: 'var(--surface-secondary)', // 앱 바깥 배경
          raised: 'var(--surface-tertiary)', // hover · 선택 행
          card: 'var(--surface-elevated)',
          input: 'var(--surface-input)',
          rail: 'var(--surface-sidebar)',
          'rail-raised': 'var(--surface-sidebar-raised)',
        },
        line: {
          DEFAULT: 'var(--border-default)',
          row: 'var(--border-row)',
          control: 'var(--border-control)',
          soft: 'var(--border-control-soft)',
          hover: 'var(--border-hover)',
        },
        content: {
          DEFAULT: 'var(--text-primary)',
          secondary: 'var(--text-secondary)',
          tertiary: 'var(--text-tertiary)',
          quaternary: 'var(--text-quaternary)',
          rail: 'var(--text-rail-primary)',
          'rail-secondary': 'var(--text-rail-secondary)',
          'rail-tertiary': 'var(--text-rail-tertiary)',
          'rail-quaternary': 'var(--text-rail-quaternary)',
        },
        accent: {
          DEFAULT: 'var(--accent-primary)',
          hover: 'var(--accent-primary-hover)',
          dim: 'var(--accent-primary-subtle)',
          on: 'var(--accent-on-accent)',
        },
        warn: 'var(--color-warn-accent)',
        bad: 'var(--color-bad-accent)',
        platform: {
          'yt-bg': 'var(--platform-yt-bg)',
          'yt-fg': 'var(--platform-yt-fg)',
          'ig-bg': 'var(--platform-ig-bg)',
          'ig-fg': 'var(--platform-ig-fg)',
          'tt-bg': 'var(--platform-tt-bg)',
          'tt-fg': 'var(--platform-tt-fg)',
          'fb-bg': 'var(--platform-fb-bg)',
          'fb-fg': 'var(--platform-fb-fg)',
          'nv-bg': 'var(--platform-nv-bg)',
          'nv-fg': 'var(--platform-nv-fg)',
          'th-bg': 'var(--platform-th-bg)',
          'th-fg': 'var(--platform-th-fg)',
          'x-bg': 'var(--platform-x-bg)',
          'x-fg': 'var(--platform-x-fg)',
        },
        youtube: '#FF0000',
        tiktok: '#000000',
        instagram: '#E1306C',
        naver: '#03C75A',
      },
      fontFamily: {
        sans: ['Pretendard Variable', 'Pretendard', 'Inter', '-apple-system', 'BlinkMacSystemFont', 'system-ui', 'Segoe UI', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace'],
      },
      // Typography scale. Each step bakes in line-height + weight, but the weight is
      // only a DEFAULT: Tailwind emits `fontSize` utilities before `fontWeight`, so
      // `text-body font-semibold` still resolves to 600. Never use raw `text-sm` /
      // `text-xs` / `text-lg` in new code — pick the semantic step instead.
      fontSize: {
        'display': ['2rem', { lineHeight: '2.5rem', letterSpacing: '-0.035em', fontWeight: '700' }],
        'display-sm': ['1.75rem', { lineHeight: '2.25rem', letterSpacing: '-0.035em', fontWeight: '700' }],
        'h1': ['1.5rem', { lineHeight: '2rem', letterSpacing: '-0.01em', fontWeight: '700' }],
        'h2': ['1.25rem', { lineHeight: '1.75rem', letterSpacing: '-0.01em', fontWeight: '600' }],
        // 1.125rem heading step — the gap that forced 155 raw `text-lg` usages.
        'title': ['1.125rem', { lineHeight: '1.625rem', letterSpacing: '-0.01em', fontWeight: '600' }],
        'h3': ['1rem', { lineHeight: '1.5rem', fontWeight: '600' }],
        // Regular-weight 1rem — `h3` is the same size but semibold.
        'body-lg': ['1rem', { lineHeight: '1.5rem', fontWeight: '400' }],
        'body': ['0.875rem', { lineHeight: '1.375rem', fontWeight: '400' }],
        'body-sm': ['0.8125rem', { lineHeight: '1.25rem', fontWeight: '400' }],
        // Regular-weight 0.75rem — `caption` is the same size but medium (500).
        'body-xs': ['0.75rem', { lineHeight: '1rem', fontWeight: '400' }],
        'caption': ['0.75rem', { lineHeight: '1rem', letterSpacing: '0.01em', fontWeight: '500' }],
        'overline': ['0.6875rem', { lineHeight: '1rem', letterSpacing: '0.05em', fontWeight: '600' }],
      },
      screens: {
        mobile: '480px',
        tablet: '768px',
        desktop: '1280px',
      },
    },
  },
  plugins: [],
}
