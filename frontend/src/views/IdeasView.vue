<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 p-6">
    <div class="max-w-7xl mx-auto">
      <!-- Header -->
      <div class="mb-6">
        <div class="flex items-center justify-between mb-4">
          <h1 class="text-3xl font-bold text-gray-900 dark:text-gray-100">
            콘텐츠 아이디어
          </h1>
          <button
            @click="openCreateModal"
            class="flex items-center gap-2 px-4 py-2 bg-indigo-600 dark:bg-indigo-500 text-white rounded-lg hover:bg-indigo-700 dark:hover:bg-indigo-600 transition-colors"
          >
            <PlusIcon class="w-5 h-5" />
            새 아이디어
          </button>
        </div>

        <!-- Filters -->
        <div class="flex flex-col sm:flex-row gap-4">
          <!-- Search -->
          <div class="flex-1">
            <div class="relative">
              <MagnifyingGlassIcon
                class="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400"
              />
              <input
                v-model="searchQuery"
                type="text"
                placeholder="아이디어 검색..."
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
      </div>

      <!-- Kanban Board -->
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 h-[calc(100vh-16rem)]">
        <IdeaColumn
          title="아이디어"
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
          title="기획 중"
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
          title="제작 중"
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
          title="완료"
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
import { useIdeasStore } from '@/stores/ideas'
import type { ContentIdea, IdeaStatus, IdeaPriority } from '@/types/idea'
import IdeaCard from '@/components/ideas/IdeaCard.vue'
import IdeaColumn from '@/components/ideas/IdeaColumn.vue'
import IdeaFormModal from '@/components/ideas/IdeaFormModal.vue'
import { PlusIcon, MagnifyingGlassIcon } from '@heroicons/vue/24/outline'

const ideasStore = useIdeasStore()

const isModalOpen = ref(false)
const selectedIdea = ref<ContentIdea | null>(null)
const searchQuery = ref('')
const selectedPriority = ref<IdeaPriority | 'all'>('all')

const priorityFilters = [
  { label: '전체', value: 'all' as const },
  { label: '높음', value: 'high' as const },
  { label: '보통', value: 'medium' as const },
  { label: '낮음', value: 'low' as const }
]

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
