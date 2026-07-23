<template>
  <div class="relative">
    <!-- Header -->
    <PageHeader :title="$t('ideas.title')" :description="$t('ideas.description')">
      <template #actions>
        <button
          class="btn-secondary inline-flex items-center gap-2"
          @click="showAiModal = true"
        >
          <SparklesIcon class="h-4 w-4" />
          {{ $t('ideas.aiGenerate') }}
        </button>
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="openCreateModal"
        >
          <PlusIcon class="h-5 w-5" />
          {{ $t('ideas.newIdea') }}
        </button>
      </template>
    </PageHeader>

    <PageGuide :title="$t('ideas.pageGuideTitle')" :items="($tm('ideas.pageGuide') as string[])" />

    <!-- Filters -->
    <div class="mb-6 flex flex-col gap-4 mobile:flex-row">
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
            class="input-field pl-10"
          />
        </div>
      </div>

      <!-- Priority Filter -->
      <div class="flex gap-2">
        <button
          v-for="priority in priorityFilters"
          :key="priority.value"
          :class="[
            'px-3 py-2 rounded-lg text-sm font-medium transition-colors',
            selectedPriority === priority.value
              ? 'bg-primary-600 dark:bg-primary-500 text-white'
              : 'bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 border border-gray-300 dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-700'
          ]"
          @click="setPriorityFilter(priority.value)"
        >
          {{ priority.label }}
        </button>
      </div>
    </div>

    <!-- Kanban Board -->
    <div class="page-grid page-grid--dense h-[calc(100vh-16rem)]">
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
            @delete="handleDelete(idea.id)"
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
            @delete="handleDelete(idea.id)"
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
            @delete="handleDelete(idea.id)"
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
            @delete="handleDelete(idea.id)"
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

    <!-- AI 아이디어 생성 모달 -->
    <BaseModal
      v-model="showAiModal"
      :title="$t('ideas.aiGenerate')"
      max-width="sm"
    >
      <div class="space-y-4">
        <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('ideas.aiGenerateDesc') }}</p>
        <div>
          <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ $t('ideas.selectCategory') }}
          </label>
          <input
            v-model="aiCategory"
            type="text"
            :placeholder="$t('ideas.selectCategory')"
            class="input-field"
          />
        </div>
        <p class="text-xs text-gray-500 dark:text-gray-400">
          {{ $t('ideas.credits') }}
        </p>
        <button
          class="btn-primary inline-flex w-full items-center justify-center gap-2"
          :disabled="aiGenerating"
          @click="handleAiGenerate"
        >
          <SparklesIcon class="h-4 w-4" />
          {{ aiGenerating ? $t('ideas.generating') : $t('ideas.aiGenerate') }}
        </button>
      </div>
    </BaseModal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useIdeasStore } from '@/stores/ideas'
import { useNotificationStore } from '@/stores/notification'
import { ideaApi } from '@/api/idea'
import type { ContentIdea, IdeaStatus, IdeaPriority } from '@/types/idea'
import IdeaCard from '@/components/ideas/IdeaCard.vue'
import IdeaColumn from '@/components/ideas/IdeaColumn.vue'
import IdeaFormModal from '@/components/ideas/IdeaFormModal.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { PlusIcon, MagnifyingGlassIcon, SparklesIcon } from '@heroicons/vue/24/outline'

const { t } = useI18n({ useScope: 'global' })
const ideasStore = useIdeasStore()
const notificationStore = useNotificationStore()

const isModalOpen = ref(false)
const selectedIdea = ref<ContentIdea | null>(null)
const searchQuery = ref('')
const selectedPriority = ref<IdeaPriority | 'all'>('all')

// AI 아이디어 생성
const showAiModal = ref(false)
const aiCategory = ref('')
const aiGenerating = ref(false)

async function handleAiGenerate() {
  aiGenerating.value = true
  try {
    const category = aiCategory.value.trim() || undefined
    await ideaApi.generateAiIdeas(category)
    showAiModal.value = false
    aiCategory.value = ''
    await ideasStore.fetchIdeas()
    notificationStore.success('AI 아이디어가 생성되었습니다')
  } catch {
    notificationStore.error('AI 아이디어 생성에 실패했습니다')
  } finally {
    aiGenerating.value = false
  }
}

const priorityFilters = computed(() => [
  { label: t('ideas.priority.all'), value: 'all' as const },
  { label: t('ideas.priority.high'), value: 'high' as const },
  { label: t('ideas.priority.medium'), value: 'medium' as const },
  { label: t('ideas.priority.low'), value: 'low' as const },
])

// Load from localStorage on mount
onMounted(() => {
  ideasStore.fetchIdeas()
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
  if (confirm('이 콘텐츠 아이디어를 삭제하시겠습니까?')) {
    ideasStore.deleteIdea(id)
  }
}

const handleDrop = (ideaId: number, newStatus: IdeaStatus) => {
  ideasStore.moveIdea(ideaId, newStatus)
}
</script>
