<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="showShortcutHelp"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm"
        @click="closeModal"
        @keydown.esc="closeModal"
      >
        <Transition
          enter-active-class="transition duration-200 ease-out"
          enter-from-class="opacity-0 scale-95"
          enter-to-class="opacity-100 scale-100"
          leave-active-class="transition duration-150 ease-in"
          leave-from-class="opacity-100 scale-100"
          leave-to-class="opacity-0 scale-95"
        >
          <div
            v-if="showShortcutHelp"
            class="glass-elevated w-full max-w-2xl overflow-hidden rounded-xl border border-gray-200/50 dark:border-gray-700/50"
            @click.stop
          >
            <!-- Header -->
            <div class="border-b border-gray-200/50 px-6 py-4 dark:border-gray-700/50">
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-3">
                  <CommandLineIcon class="h-6 w-6 text-primary-600 dark:text-primary-400" />
                  <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
                    키보드 단축키
                  </h2>
                </div>
                <button
                  type="button"
                  class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
                  @click="closeModal"
                >
                  <XMarkIcon class="h-5 w-5" />
                </button>
              </div>
            </div>

            <!-- Shortcuts List -->
            <div class="max-h-[60vh] overflow-y-auto px-6 py-4">
              <!-- Global Shortcuts -->
              <div class="mb-6">
                <h3
                  class="mb-3 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400"
                >
                  전역 (Global)
                </h3>
                <div class="space-y-2">
                  <div
                    v-for="shortcut in globalShortcuts"
                    :key="shortcut.description"
                    class="flex items-center justify-between rounded-lg px-3 py-2 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700/50"
                  >
                    <span class="text-sm text-gray-700 dark:text-gray-300">
                      {{ shortcut.description }}
                    </span>
                    <div class="flex items-center gap-1">
                      <kbd
                        v-for="(key, index) in shortcut.keys"
                        :key="index"
                        class="kbd-key"
                      >
                        {{ key }}
                      </kbd>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Video Management Shortcuts -->
              <div class="mb-6">
                <h3
                  class="mb-3 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400"
                >
                  영상 관리 (Videos)
                </h3>
                <div class="space-y-2">
                  <div
                    v-for="shortcut in videoShortcuts"
                    :key="shortcut.description"
                    class="flex items-center justify-between rounded-lg px-3 py-2 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700/50"
                  >
                    <span class="text-sm text-gray-700 dark:text-gray-300">
                      {{ shortcut.description }}
                    </span>
                    <div class="flex items-center gap-1">
                      <kbd
                        v-for="(key, index) in shortcut.keys"
                        :key="index"
                        class="kbd-key"
                      >
                        {{ key }}
                      </kbd>
                    </div>
                  </div>
                </div>
              </div>

              <!-- General Shortcuts -->
              <div>
                <h3
                  class="mb-3 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400"
                >
                  일반 (General)
                </h3>
                <div class="space-y-2">
                  <div
                    v-for="shortcut in generalShortcuts"
                    :key="shortcut.description"
                    class="flex items-center justify-between rounded-lg px-3 py-2 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700/50"
                  >
                    <span class="text-sm text-gray-700 dark:text-gray-300">
                      {{ shortcut.description }}
                    </span>
                    <div class="flex items-center gap-1">
                      <kbd
                        v-for="(key, index) in shortcut.keys"
                        :key="index"
                        class="kbd-key"
                      >
                        {{ key }}
                      </kbd>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Footer -->
            <div class="border-t border-gray-200/50 px-6 py-4 dark:border-gray-700/50">
              <p class="text-xs text-gray-500 dark:text-gray-400">
                <kbd class="kbd-key">Esc</kbd> 또는 배경을 클릭하여 닫기
              </p>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CommandLineIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { showShortcutHelp } from '@/composables/useKeyboardShortcuts'

interface Shortcut {
  keys: string[]
  description: string
}

// Detect platform for keyboard symbols
const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0
const cmdKey = isMac ? '⌘' : 'Ctrl'

// Global shortcuts
const globalShortcuts = computed<Shortcut[]>(() => [
  {
    keys: [cmdKey, 'K'],
    description: '명령 팔레트 열기',
  },
  {
    keys: ['?'],
    description: '단축키 도움말',
  },
  {
    keys: ['G', 'D'],
    description: '대시보드로 이동',
  },
  {
    keys: ['G', 'V'],
    description: '영상 관리로 이동',
  },
  {
    keys: ['G', 'U'],
    description: '업로드로 이동',
  },
  {
    keys: ['G', 'A'],
    description: '분석으로 이동',
  },
  {
    keys: ['G', 'S'],
    description: '설정으로 이동',
  },
])

// Video management shortcuts
const videoShortcuts = computed<Shortcut[]>(() => [
  {
    keys: ['J', '/', 'K'],
    description: '다음/이전 영상',
  },
  {
    keys: ['X'],
    description: '영상 선택 토글',
  },
  {
    keys: [cmdKey, 'A'],
    description: '전체 선택',
  },
  {
    keys: ['Delete'],
    description: '선택된 영상 삭제',
  },
])

// General shortcuts
const generalShortcuts = computed<Shortcut[]>(() => [
  {
    keys: ['Esc'],
    description: '모달/패널 닫기',
  },
  {
    keys: ['/'],
    description: '검색 포커스',
  },
])

function closeModal() {
  showShortcutHelp.value = false
}
</script>

<style scoped>
.kbd-key {
  @apply inline-flex min-w-[1.5rem] items-center justify-center rounded border border-gray-300 bg-gray-100 px-2 py-1 text-xs font-mono text-gray-700 shadow-sm dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300;
}
</style>
