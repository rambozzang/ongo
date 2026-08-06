<template>
  <span
    class="ongo-logo"
    :class="[`ongo-logo--${size}`, { 'ongo-logo--inverse': inverse, 'ongo-logo--mark-only': markOnly }]"
    role="img"
    aria-label="ongo"
  >
    <svg class="ongo-logo__mark" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="8.25" class="ongo-logo__orbit" />
      <path d="M17.9 6.25a8.25 8.25 0 0 1 2.18 5.57" class="ongo-logo__accent-orbit" />
      <circle cx="19.85" cy="6.1" r="1.85" class="ongo-logo__dot" />
    </svg>
    <span v-if="!markOnly" class="ongo-logo__word">ongo</span>
  </span>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    size?: 'sm' | 'md' | 'lg'
    inverse?: boolean
    markOnly?: boolean
  }>(),
  {
    size: 'md',
    inverse: false,
    markOnly: false,
  },
)
</script>

<style scoped>
.ongo-logo {
  --logo-word-color: var(--text-primary);
  --logo-mark-color: var(--text-primary);
  --logo-accent-color: var(--accent-primary);
  display: inline-flex;
  align-items: center;
  gap: 0.48rem;
  flex: none;
  color: var(--logo-word-color);
  line-height: 1;
  white-space: nowrap;
}

.ongo-logo--inverse {
  --logo-word-color: var(--text-inverse);
  --logo-mark-color: var(--text-inverse);
}

.ongo-logo--sm {
  gap: 0.36rem;
}

.ongo-logo--lg {
  gap: 0.58rem;
}

.ongo-logo--mark-only {
  gap: 0;
}

.ongo-logo__mark {
  display: block;
  width: 1.45rem;
  height: 1.45rem;
  overflow: visible;
}

.ongo-logo--sm .ongo-logo__mark {
  width: 1.25rem;
  height: 1.25rem;
}

.ongo-logo--lg .ongo-logo__mark {
  width: 1.7rem;
  height: 1.7rem;
}

.ongo-logo__orbit {
  stroke: var(--logo-mark-color);
  stroke-width: 2.15;
  stroke-linecap: round;
  stroke-dasharray: 43 9;
}

.ongo-logo__accent-orbit {
  stroke: var(--logo-accent-color);
  stroke-width: 2.15;
  stroke-linecap: round;
}

.ongo-logo__dot {
  fill: var(--logo-accent-color);
}

.ongo-logo__word {
  color: var(--logo-word-color);
  font-family: 'Pretendard Variable', Pretendard, sans-serif;
  font-size: 1.08rem;
  font-weight: 750;
  letter-spacing: -0.075em;
}

.ongo-logo--sm .ongo-logo__word {
  font-size: 0.98rem;
}

.ongo-logo--lg .ongo-logo__word {
  font-size: 1.32rem;
}
</style>
