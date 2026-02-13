/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#f5f3ff',
          100: '#ede9fe',
          200: '#ddd6fe',
          300: '#c4b5fd',
          400: '#a78bfa',
          500: '#8b5cf6',
          600: '#7c3aed',
          700: '#6d28d9',
          800: '#5b21b6',
          900: '#4c1d95',
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
      fontSize: {
        'display': ['2rem', { lineHeight: '2.5rem', letterSpacing: '-0.02em', fontWeight: '700' }],
        'h1': ['1.5rem', { lineHeight: '2rem', letterSpacing: '-0.01em', fontWeight: '700' }],
        'h2': ['1.25rem', { lineHeight: '1.75rem', letterSpacing: '-0.01em', fontWeight: '600' }],
        'h3': ['1rem', { lineHeight: '1.5rem', fontWeight: '600' }],
        'body': ['0.875rem', { lineHeight: '1.375rem', fontWeight: '400' }],
        'body-sm': ['0.8125rem', { lineHeight: '1.25rem', fontWeight: '400' }],
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
