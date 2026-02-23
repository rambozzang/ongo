<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('ideas.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('ideas.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="openCreateModal"
        >
          <PlusIcon class="h-5 w-5" />
          {{ $t('ideas.newIdea') }}
        </button>
      </div>
    </div>

    <PageGuide :title="$t('ideas.pageGuideTitle')" :items="($tm('ideas.pageGuide') as string[])" />

    <!-- Filters -->
    <div class="mb-6 flex flex-col gap-4 sm:flex-row">
      <!-- Search -->
      <div class="flex-1">
        <div class="relative">
          <MagnifyingGlassIcon
            class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400"
          />
          <input
            v-model="searchQuery"
            type="text"
            :placeholder="$t('ideas.searchPlaceholder')"
            class="w-full pl-10 pr-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-indigo-500 dark:focus:ring-indigo-400 focus:border-transparent"
          />
        </div>
      </div>

      <!-- Priority Filter -->
      <div class="flex gap-2">
        <button
          v-for="priority in priorityFilters"
          :key="priority.value"
          @click="setPriorityFilter(priority.value)"
          :class="[
            'px-3 py-2 rounded-lg text-sm font-medium transition-colors',
            selectedPriority === priority.value
              ? 'bg-indigo-600 dark:bg-indigo-500 text-white'
              : 'bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 border border-gray-300 dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-700'
          ]"
        >
          {{ priority.label }}
        </button>
      </div>
    </div>

    <!-- Kanban Board -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 h-[calc(100vh-16rem)]">
      <IdeaColumn
        :title="$t('ideas.columns.idea')"
        status="idea"
          :ideas="filteredIdeasByStatus.idea"
          color="blue"
          @drop="handleDrop"
        >
          <IdeaCard
            v-for="idea in filteredIdeasByStatus.idea"
            :key="idea.id"
            :idea="idea"
            @click="openEditModal(idea)"
          />
        </IdeaColumn>

        <IdeaColumn
          :title="$t('ideas.columns.planning')"
          status="planning"
          :ideas="filteredIdeasByStatus.planning"
          color="yellow"
          @drop="handleDrop"
        >
          <IdeaCard
            v-for="idea in filteredIdeasByStatus.planning"
            :key="idea.id"
            :idea="idea"
            @click="openEditModal(idea)"
          />
        </IdeaColumn>

        <IdeaColumn
          :title="$t('ideas.columns.producing')"
          status="producing"
          :ideas="filteredIdeasByStatus.producing"
          color="green"
          @drop="handleDrop"
        >
          <IdeaCard
            v-for="idea in filteredIdeasByStatus.producing"
            :key="idea.id"
            :idea="idea"
            @click="openEditModal(idea)"
          />
        </IdeaColumn>

        <IdeaColumn
          :title="$t('ideas.columns.completed')"
          status="completed"
          :ideas="filteredIdeasByStatus.completed"
          color="purple"
          @drop="handleDrop"
        >
          <IdeaCard
            v-for="idea in filteredIdeasByStatus.completed"
            :key="idea.id"
            :idea="idea"
            @click="openEditModal(idea)"
          />
        </IdeaColumn>
    </div>

    <!-- Modal -->
    <IdeaFormModal
      :is-open="isModalOpen"
      :idea="selectedIdea"
      @close="closeModal"
      @submit="handleCreate"
      @update="handleUpdate"
      @delete="handleDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useIdeasStore } from '@/stores/ideas'
import type { ContentIdea, IdeaStatus, IdeaPriority } from '@/types/idea'
import IdeaCard from '@/components/ideas/IdeaCard.vue'
import IdeaColumn from '@/components/ideas/IdeaColumn.vue'
import IdeaFormModal from '@/components/ideas/IdeaFormModal.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import { PlusIcon, MagnifyingGlassIcon } from '@heroicons/vue/24/outline'

const { t } = useI18n({ useScope: 'global' })
const ideasStore = useIdeasStore()

const isModalOpen = ref(false)
const selectedIdea = ref<ContentIdea | null>(null)
const searchQuery = ref('')
const selectedPriority = ref<IdeaPriority | 'all'>('all')

const priorityFilters = computed(() => [
  { label: t('ideas.priority.all'), value: 'all' as const },
  { label: t('ideas.priority.high'), value: 'high' as const },
  { label: t('ideas.priority.medium'), value: 'medium' as const },
  { label: t('ideas.priority.low'), value: 'low' as const },
])

// Load from localStorage on mount
onMounted(() => {
  ideasStore.loadFromLocalStorage()
})

// Computed filtered ideas by status
const filteredIdeasByStatus = computed(() => {
  let ideas = ideasStore.ideas

  // Apply search filter
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    ideas = ideas.filter(
      idea =>
        idea.title.toLowerCase().includes(query) ||
        idea.description.toLowerCase().includes(query) ||
        idea.tags.some(tag => tag.toLowerCase().includes(query))
    )
  }

  // Apply priority filter
  if (selectedPriority.value !== 'all') {
    ideas = ideas.filter(idea => idea.priority === selectedPriority.value)
  }

  // Group by status
  return {
    idea: ideas.filter(idea => idea.status === 'idea'),
    planning: ideas.filter(idea => idea.status === 'planning'),
    producing: ideas.filter(idea => idea.status === 'producing'),
    completed: ideas.filter(idea => idea.status === 'completed')
  }
})

const setPriorityFilter = (priority: IdeaPriority | 'all') => {
  selectedPriority.value = priority
}

// Watch search query and update store filter
watch(searchQuery, (newValue) => {
  ideasStore.setFilter('search', newValue)
})

const openCreateModal = () => {
  selectedIdea.value = null
  isModalOpen.value = true
}

const openEditModal = (idea: ContentIdea) => {
  selectedIdea.value = idea
  isModalOpen.value = true
}

const closeModal = () => {
  isModalOpen.value = false
  selectedIdea.value = null
}

const handleCreate = (data: Omit<ContentIdea, 'id' | 'createdAt' | 'updatedAt'>) => {
  ideasStore.addIdea(data)
}

const handleUpdate = (id: number, data: Partial<ContentIdea>) => {
  ideasStore.updateIdea(id, data)
}

const handleDelete = (id: number) => {
  ideasStore.deleteIdea(id)
}

const handleDrop = (ideaId: number, newStatus: IdeaStatus) => {
  ideasStore.moveIdea(ideaId, newStatus)
}
</script>
