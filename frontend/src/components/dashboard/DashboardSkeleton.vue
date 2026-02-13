<template>
  <div class="space-y-6">
    <!-- Header skeleton -->
    <div class="space-y-2">
      <OSkeleton type="text" width="60%" height="28px" />
      <OSkeleton type="text" width="40%" height="16px" />
    </div>

    <!-- KPI Cards skeleton -->
    <div class="grid grid-cols-2 gap-4 tablet:grid-cols-4">
      <div v-for="i in 4" :key="i" class="card space-y-3">
        <OSkeleton type="text" width="50%" height="14px" />
        <OSkeleton type="text" width="70%" height="28px" />
        <OSkeleton type="text" width="30%" height="12px" />
      </div>
    </div>

    <!-- Charts skeleton -->
    <div class="grid gap-6 tablet:grid-cols-3">
      <div class="card tablet:col-span-2">
        <OSkeleton type="text" width="30%" height="20px" />
        <OSkeleton type="card" height="200px" class="mt-4" />
      </div>
      <div class="card">
        <OSkeleton type="text" width="40%" height="20px" />
        <OSkeleton type="circle" width="180px" height="180px" class="mx-auto mt-4" />
      </div>
    </div>

    <!-- Lists skeleton -->
    <div class="grid gap-6 tablet:grid-cols-2">
      <div class="card space-y-4">
        <OSkeleton type="text" width="30%" height="20px" />
        <div v-for="i in 3" :key="i" class="flex items-center gap-3">
          <OSkeleton type="rect" width="80px" height="48px" />
          <div class="flex-1 space-y-2">
            <OSkeleton type="text" width="80%" />
            <OSkeleton type="text" width="40%" height="12px" />
          </div>
        </div>
      </div>
      <div class="card space-y-4">
        <OSkeleton type="text" width="30%" height="20px" />
        <div v-for="i in 3" :key="i" class="flex items-center gap-3">
          <OSkeleton type="circle" width="36px" height="36px" />
          <div class="flex-1 space-y-2">
            <OSkeleton type="text" width="60%" />
            <OSkeleton type="text" width="30%" height="12px" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import OSkeleton from '@/components/ui/OSkeleton.vue'
</script>
