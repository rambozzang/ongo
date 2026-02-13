<template>
  <div
    ref="containerRef"
    class="group relative aspect-video w-full overflow-hidden rounded-lg bg-black"
    @mouseenter="showControls = true"
    @mousemove="handleMouseMove"
    @mouseleave="handleMouseLeave"
    @click="togglePlayPause"
  >
    <!-- Video Element -->
    <video
      ref="videoRef"
      :src="src"
      :poster="poster"
      :autoplay="autoplay"
      class="h-full w-full"
      @loadedmetadata="handleMetadataLoaded"
      @timeupdate="handleTimeUpdate"
      @progress="handleProgress"
      @ended="handleEnded"
      @error="handleError"
      @waiting="isBuffering = true"
      @canplay="isBuffering = false"
    />

    <!-- Loading Spinner -->
    <div
      v-if="isBuffering"
      class="pointer-events-none absolute inset-0 flex items-center justify-center"
    >
      <div class="h-12 w-12 animate-spin rounded-full border-4 border-white/20 border-t-white" />
    </div>

    <!-- Error State -->
    <div
      v-if="hasError"
      class="absolute inset-0 flex flex-col items-center justify-center bg-black/90 p-6 text-center"
    >
      <ExclamationCircleIcon class="mb-4 h-16 w-16 text-red-500" />
      <p class="mb-2 text-lg font-semibold text-white">영상을 재생할 수 없습니다</p>
      <p class="mb-4 text-sm text-gray-300">영상 파일이 손상되었거나 지원되지 않는 형식입니다</p>
      <button
        class="rounded-lg bg-white/10 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-white/20"
        @click.stop="retryLoad"
      >
        다시 시도
      </button>
    </div>

    <!-- Custom Controls -->
    <div
      v-if="!hasError"
      class="pointer-events-none absolute inset-0 flex flex-col justify-end transition-opacity duration-300"
      :class="showControls || !isPlaying ? 'opacity-100' : 'opacity-0'"
    >
      <!-- Play/Pause Center Button -->
      <div class="absolute inset-0 flex items-center justify-center">
        <button
          v-if="!isPlaying"
          class="pointer-events-auto rounded-full bg-black/60 p-5 backdrop-blur-sm transition-all hover:scale-110 hover:bg-black/80"
          @click.stop="togglePlayPause"
        >
          <PlayIcon class="h-10 w-10 text-white" />
        </button>
      </div>

      <!-- Controls Bar -->
      <div class="pointer-events-auto bg-gradient-to-t from-black/80 to-transparent px-4 pb-4 pt-8">
        <!-- Progress Bar -->
        <div class="mb-3 flex items-center gap-2">
          <div
            ref="progressBarRef"
            class="group/progress relative h-1.5 flex-1 cursor-pointer rounded-full bg-white/20"
            @click="seek"
            @mousedown="startDragging"
          >
            <!-- Buffered -->
            <div
              class="pointer-events-none absolute h-full rounded-full bg-white/30 transition-all"
              :style="{ width: `${bufferedPercent}%` }"
            />
            <!-- Played -->
            <div
              class="pointer-events-none absolute h-full rounded-full bg-primary-500 transition-all"
              :style="{ width: `${playedPercent}%` }"
            />
            <!-- Handle -->
            <div
              class="pointer-events-none absolute top-1/2 h-3 w-3 -translate-y-1/2 rounded-full bg-white opacity-0 shadow-lg transition-all group-hover/progress:opacity-100"
              :style="{ left: `${playedPercent}%`, transform: 'translate(-50%, -50%)' }"
            />
          </div>
          <span class="text-xs font-medium text-white">
            {{ formatTime(currentTime) }} / {{ formatTime(duration) }}
          </span>
        </div>

        <!-- Control Buttons -->
        <div class="flex items-center justify-between gap-3">
          <!-- Left Controls -->
          <div class="flex items-center gap-2">
            <!-- Play/Pause -->
            <button
              class="rounded p-1.5 text-white transition-colors hover:bg-white/20"
              @click.stop="togglePlayPause"
            >
              <PauseIcon v-if="isPlaying" class="h-5 w-5" />
              <PlayIcon v-else class="h-5 w-5" />
            </button>

            <!-- Volume -->
            <div class="group/volume relative flex items-center">
              <button
                class="rounded p-1.5 text-white transition-colors hover:bg-white/20"
                @click.stop="toggleMute"
              >
                <SpeakerXMarkIcon v-if="isMuted || volume === 0" class="h-5 w-5" />
                <SpeakerWaveIcon v-else class="h-5 w-5" />
              </button>
              <!-- Volume Slider -->
              <div
                class="ml-2 hidden w-20 items-center opacity-0 transition-opacity group-hover/volume:opacity-100 tablet:flex"
              >
                <input
                  type="range"
                  min="0"
                  max="100"
                  :value="volume * 100"
                  class="h-1 w-full cursor-pointer appearance-none rounded-full bg-white/20"
                  @input="handleVolumeChange"
                  @click.stop
                />
              </div>
            </div>
          </div>

          <!-- Right Controls -->
          <div class="flex items-center gap-2">
            <!-- Playback Speed -->
            <div class="relative" ref="speedMenuRef">
              <button
                class="rounded px-2 py-1.5 text-xs font-semibold text-white transition-colors hover:bg-white/20"
                @click.stop="showSpeedMenu = !showSpeedMenu"
              >
                {{ playbackRate }}x
              </button>
              <!-- Speed Menu -->
              <div
                v-if="showSpeedMenu"
                class="absolute bottom-full right-0 mb-2 w-20 overflow-hidden rounded-lg bg-black/90 shadow-xl backdrop-blur-sm"
              >
                <button
                  v-for="rate in [0.5, 0.75, 1, 1.25, 1.5, 2]"
                  :key="rate"
                  class="w-full px-3 py-2 text-left text-sm text-white transition-colors hover:bg-white/20"
                  :class="playbackRate === rate ? 'bg-primary-500/50 font-semibold' : ''"
                  @click.stop="setPlaybackRate(rate)"
                >
                  {{ rate }}x
                </button>
              </div>
            </div>

            <!-- Fullscreen -->
            <button
              class="rounded p-1.5 text-white transition-colors hover:bg-white/20"
              @click.stop="toggleFullscreen"
            >
              <ArrowsPointingOutIcon v-if="!isFullscreen" class="h-5 w-5" />
              <ArrowsPointingInIcon v-else class="h-5 w-5" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import {
  PlayIcon,
  PauseIcon,
  SpeakerWaveIcon,
  SpeakerXMarkIcon,
  ArrowsPointingOutIcon,
  ArrowsPointingInIcon,
  ExclamationCircleIcon,
} from '@heroicons/vue/24/solid'

const props = withDefaults(
  defineProps<{
    src: string
    poster?: string
    autoplay?: boolean
  }>(),
  {
    poster: '',
    autoplay: false,
  }
)

// ---- Refs ----
const containerRef = ref<HTMLDivElement | null>(null)
const videoRef = ref<HTMLVideoElement | null>(null)
const progressBarRef = ref<HTMLDivElement | null>(null)
const speedMenuRef = ref<HTMLDivElement | null>(null)

// ---- State ----
const isPlaying = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const volume = ref(1)
const isMuted = ref(false)
const playbackRate = ref(1)
const bufferedPercent = ref(0)
const showControls = ref(true)
const isFullscreen = ref(false)
const isBuffering = ref(false)
const hasError = ref(false)
const showSpeedMenu = ref(false)
const isDragging = ref(false)

let hideControlsTimeout: ReturnType<typeof setTimeout> | null = null

// ---- Computed ----
const playedPercent = ref(0)

// ---- Handlers ----
function handleMetadataLoaded() {
  if (!videoRef.value) return
  duration.value = videoRef.value.duration
}

function handleTimeUpdate() {
  if (!videoRef.value || isDragging.value) return
  currentTime.value = videoRef.value.currentTime
  playedPercent.value = (currentTime.value / duration.value) * 100
}

function handleProgress() {
  if (!videoRef.value) return
  const buffered = videoRef.value.buffered
  if (buffered.length > 0) {
    const bufferedEnd = buffered.end(buffered.length - 1)
    bufferedPercent.value = (bufferedEnd / duration.value) * 100
  }
}

function handleEnded() {
  isPlaying.value = false
  showControls.value = true
}

function handleError() {
  hasError.value = true
  isPlaying.value = false
}

function retryLoad() {
  if (!videoRef.value) return
  hasError.value = false
  videoRef.value.load()
}

function togglePlayPause() {
  if (!videoRef.value || hasError.value) return
  if (isPlaying.value) {
    videoRef.value.pause()
    isPlaying.value = false
  } else {
    videoRef.value.play()
    isPlaying.value = true
  }
}

function toggleMute() {
  if (!videoRef.value) return
  isMuted.value = !isMuted.value
  videoRef.value.muted = isMuted.value
}

function handleVolumeChange(event: Event) {
  if (!videoRef.value) return
  const target = event.target as HTMLInputElement
  volume.value = Number(target.value) / 100
  videoRef.value.volume = volume.value
  isMuted.value = volume.value === 0
}

function setPlaybackRate(rate: number) {
  if (!videoRef.value) return
  playbackRate.value = rate
  videoRef.value.playbackRate = rate
  showSpeedMenu.value = false
}

function seek(event: MouseEvent) {
  if (!videoRef.value || !progressBarRef.value) return
  const rect = progressBarRef.value.getBoundingClientRect()
  const percent = (event.clientX - rect.left) / rect.width
  const seekTime = percent * duration.value
  videoRef.value.currentTime = seekTime
  currentTime.value = seekTime
  playedPercent.value = percent * 100
}

function startDragging(event: MouseEvent) {
  isDragging.value = true
  seek(event)
  document.addEventListener('mousemove', handleDrag)
  document.addEventListener('mouseup', stopDragging)
}

function handleDrag(event: MouseEvent) {
  if (!isDragging.value) return
  seek(event)
}

function stopDragging() {
  isDragging.value = false
  document.removeEventListener('mousemove', handleDrag)
  document.removeEventListener('mouseup', stopDragging)
}

function toggleFullscreen() {
  if (!containerRef.value) return

  if (!document.fullscreenElement) {
    containerRef.value.requestFullscreen()
    isFullscreen.value = true
  } else {
    document.exitFullscreen()
    isFullscreen.value = false
  }
}

function handleMouseMove() {
  showControls.value = true
  if (hideControlsTimeout) {
    clearTimeout(hideControlsTimeout)
  }
  if (isPlaying.value) {
    hideControlsTimeout = setTimeout(() => {
      showControls.value = false
    }, 3000)
  }
}

function handleMouseLeave() {
  if (isPlaying.value) {
    showControls.value = false
  }
}

function handleKeyboard(event: KeyboardEvent) {
  if (!videoRef.value) return

  switch (event.key) {
    case ' ':
      event.preventDefault()
      togglePlayPause()
      break
    case 'ArrowLeft':
      event.preventDefault()
      videoRef.value.currentTime = Math.max(0, currentTime.value - 5)
      break
    case 'ArrowRight':
      event.preventDefault()
      videoRef.value.currentTime = Math.min(duration.value, currentTime.value + 5)
      break
    case 'ArrowUp':
      event.preventDefault()
      volume.value = Math.min(1, volume.value + 0.1)
      videoRef.value.volume = volume.value
      isMuted.value = false
      break
    case 'ArrowDown':
      event.preventDefault()
      volume.value = Math.max(0, volume.value - 0.1)
      videoRef.value.volume = volume.value
      isMuted.value = volume.value === 0
      break
    case 'f':
    case 'F':
      event.preventDefault()
      toggleFullscreen()
      break
    case 'm':
    case 'M':
      event.preventDefault()
      toggleMute()
      break
  }
}

function handleClickOutside(event: MouseEvent) {
  if (speedMenuRef.value && !speedMenuRef.value.contains(event.target as Node)) {
    showSpeedMenu.value = false
  }
}

// ---- Utilities ----
function formatTime(seconds: number): string {
  if (!isFinite(seconds)) return '0:00'
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins}:${String(secs).padStart(2, '0')}`
}

// ---- Lifecycle ----
onMounted(() => {
  document.addEventListener('keydown', handleKeyboard)
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('fullscreenchange', () => {
    isFullscreen.value = !!document.fullscreenElement
  })

  // Initialize volume
  if (videoRef.value) {
    videoRef.value.volume = volume.value
  }
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeyboard)
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('mousemove', handleDrag)
  document.removeEventListener('mouseup', stopDragging)
  if (hideControlsTimeout) {
    clearTimeout(hideControlsTimeout)
  }
})

// Watch for src changes
watch(() => props.src, () => {
  hasError.value = false
  isPlaying.value = false
  currentTime.value = 0
  playedPercent.value = 0
  bufferedPercent.value = 0
})
</script>

<style scoped>
input[type='range']::-webkit-slider-thumb {
  appearance: none;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: white;
  cursor: pointer;
  transition: transform 0.15s;
}

input[type='range']::-webkit-slider-thumb:hover {
  transform: scale(1.2);
}

input[type='range']::-moz-range-thumb {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: white;
  cursor: pointer;
  border: none;
  transition: transform 0.15s;
}

input[type='range']::-moz-range-thumb:hover {
  transform: scale(1.2);
}
</style>
