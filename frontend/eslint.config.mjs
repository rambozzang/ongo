import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'
import prettier from 'eslint-config-prettier'
import vueParser from 'vue-eslint-parser'

/*
 * ---------------------------------------------------------------------------
 * Design-system drift guards.  See frontend/docs/design-tokens-migration.md
 * ---------------------------------------------------------------------------
 * Detects a Tailwind palette utility built on a raw hue, with any stack of
 * variant prefixes and an optional opacity modifier:
 *     bg-red-100 · dark:text-green-400 · dark:hover:bg-blue-900/30
 * `gray` and `primary` are deliberately allowed — those are the project's own
 * scales. Platform brand hues (youtube/tiktok/instagram/naver) are named colors
 * and never match either.
 *
 * Written as an esquery attribute-regex body (unanchored, matches anywhere in a
 * class string) so one pattern can serve every selector below.
 */
const HUE_RE_BODY = String.raw`(?:^|[\s'"\`])(?:[\w-]+:)*(?:bg|text|border|ring|divide|from|via|to|fill|stroke|outline|decoration|accent|caret|placeholder|shadow)-(?:indigo|blue|green|red|yellow|purple|pink|orange|emerald|teal|violet)-\d{2,3}(?:\/\d{1,3})?(?:$|[\s'"\`])`

const TOKEN_MESSAGE =
  'Raw Tailwind hue utility. Use a semantic token instead (bg-success-subtle / text-error-strong / .badge-*). See frontend/docs/design-tokens-migration.md'

/**
 * Template-side selectors. `vue/no-restricted-class` alone only understands
 * literals, arrays, objects and template literals — it silently misses the
 * ternaries that dominate `:class` bindings here, so we match Literal nodes
 * anywhere inside a class binding instead.
 */
const VUE_TEMPLATE_HUE_SELECTORS = [
  // <div class="... bg-red-100 ...">
  {
    selector: `VAttribute[directive=false][key.name='class'] > VLiteral[value=/${HUE_RE_BODY}/]`,
    message: TOKEN_MESSAGE,
  },
  // :class="cond ? 'bg-red-100' : 'bg-green-100'" · :class="['bg-red-100']" · :class="{ 'bg-red-100': x }"
  {
    selector: `VAttribute[directive=true][key.argument.name='class'] Literal[value=/${HUE_RE_BODY}/]`,
    message: TOKEN_MESSAGE,
  },
  // :class="`bg-red-100 ${x}`"
  {
    selector: `VAttribute[directive=true][key.argument.name='class'] TemplateElement[value.raw=/${HUE_RE_BODY}/]`,
    message: TOKEN_MESSAGE,
  },
]

/**
 * Script-side selector — status->class maps in <script setup> and .ts helpers
 * account for roughly a third of all hue usage and no template rule can see them.
 * Base `no-restricted-syntax` never visits the template AST, so this does not
 * double-report the selectors above.
 */
const SCRIPT_HUE_SELECTORS = [
  { selector: `Literal[value=/${HUE_RE_BODY}/]`, message: TOKEN_MESSAGE },
  { selector: `TemplateElement[value.raw=/${HUE_RE_BODY}/]`, message: TOKEN_MESSAGE },
]

export default [
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  prettier,
  {
    files: ['**/*.{js,mjs,cjs,jsx,ts,tsx,vue}'],
    languageOptions: {
      parser: vueParser,
      parserOptions: {
        parser: tseslint.parser,
        ecmaVersion: 'latest',
        sourceType: 'module',
      },
    },
    rules: {
      'vue/multi-word-component-names': 'off',
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
      '@typescript-eslint/no-explicit-any': 'warn',
      'no-useless-escape': 'warn',

      // --- design-system drift guards (warn only: there is a large existing backlog) ---

      // Raw Tailwind hue utilities -> semantic tokens.
      'vue/no-restricted-syntax': ['warn', ...VUE_TEMPLATE_HUE_SELECTORS],
      'no-restricted-syntax': ['warn', ...SCRIPT_HUE_SELECTORS],

      // Native browser dialogs -> use ConfirmModal.vue.
      // Resolves against the global scope only, so local helpers named `confirm`
      // (ConfirmModal.vue, CampaignRewardsView.vue) are correctly ignored.
      'no-restricted-globals': [
        'warn',
        {
          globals: [
            {
              name: 'confirm',
              message: 'Use the ConfirmModal.vue component instead of the native confirm() dialog.',
            },
            {
              name: 'alert',
              message: 'Use ConfirmModal.vue or a toast notification instead of the native alert() dialog.',
            },
          ],
          checkGlobalObject: true,
        },
      ],
    },
  },
  {
    // tokens.css / main.css are the one place semantic values may be defined,
    // and config files legitimately reference raw palette values.
    files: ['tailwind.config.js', 'vite.config.ts', 'eslint.config.mjs'],
    rules: {
      'vue/no-restricted-syntax': 'off',
      'no-restricted-syntax': 'off',
      'no-restricted-globals': 'off',
    },
  },
  {
    // Coverage is generated during CI before lint runs. It is not application
    // source and must not inflate the warning count with generated directives.
    ignores: ['dist/', 'coverage/', 'node_modules/', '*.d.ts'],
  },
]
