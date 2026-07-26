<script setup lang="ts">
import { ref, computed } from 'vue'
import { storeToRefs } from 'pinia'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import { useAssetsStore } from '@/stores/assets'
import {
  FolderIcon,
  FolderOpenIcon,
  FolderPlusIcon,
  PencilIcon,
  TrashIcon,
  ArchiveBoxIcon,
  XMarkIcon,
  CheckIcon,
} from '@heroicons/vue/24/outline'

const assetsStore = useAssetsStore()
const { folders, selectedFolderId, assets } = storeToRefs(assetsStore)

const showNewFolderInput = ref(false)
const newFolderName = ref('')
const editingFolderId = ref<number | null>(null)
const editingFolderName = ref('')
const showDeleteModal = ref(false)
const deleteTargetId = ref<number | null>(null)

const totalAssetCount = computed(() => assets.value.length)

const unfiledCount = computed(() => {
  return assets.value.filter((a) => a.folderId === null).length
})

function selectFolder(id: number | null) {
  assetsStore.selectedFolderId = id
}

function startCreateFolder() {
  showNewFolderInput.value = true
  newFolderName.value = ''
}

function confirmCreateFolder() {
  const name = newFolderName.value.trim()
  if (name) {
    assetsStore.createFolder(name)
  }
  showNewFolderInput.value = false
  newFolderName.value = ''
}

function cancelCreateFolder() {
  showNewFolderInput.value = false
  newFolderName.value = ''
}

function startRename(id: number, currentName: string) {
  editingFolderId.value = id
  editingFolderName.value = currentName
}

function confirmRename() {
  if (editingFolderId.value !== null) {
    const name = editingFolderName.value.trim()
    if (name) {
      assetsStore.renameFolder(editingFolderId.value, name)
    }
  }
  editingFolderId.value = null
  editingFolderName.value = ''
}

function cancelRename() {
  editingFolderId.value = null
  editingFolderName.value = ''
}

function handleDeleteFolder(id: number) {
  deleteTargetId.value = id
  showDeleteModal.value = true
}

function confirmDeleteFolder() {
  if (deleteTargetId.value === null) return
  assetsStore.deleteFolder(deleteTargetId.value)
  deleteTargetId.value = null
}

function onNewFolderKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    confirmCreateFolder()
  } else if (e.key === 'Escape') {
    cancelCreateFolder()
  }
}

function onRenameKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    confirmRename()
  } else if (e.key === 'Escape') {
    cancelRename()
  }
}
</script>

<template>
  <aside class="w-full shrink-0 lg:w-60">
    <div class="rounded-xl border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-800">
      <!-- Header -->
      <div class="mb-3 flex items-center justify-between">
        <h3 class="text-body font-semibold text-gray-900 dark:text-white">폴더</h3>
        <button
          class="rounded-lg p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
          title="새 폴더"
          @click="startCreateFolder"
        >
          <FolderPlusIcon class="h-4 w-4" />
        </button>
      </div>

      <!-- Folder List -->
      <nav class="space-y-0.5">
        <!-- All Assets -->
        <button
          class="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-body transition-colors"
          :class="
            selectedFolderId === null
              ? 'bg-primary-50 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
              : 'text-gray-700 hover:bg-gray-50 dark:text-gray-300 dark:hover:bg-gray-700/50'
          "
          @click="selectFolder(null)"
        >
          <ArchiveBoxIcon class="h-4 w-4 flex-shrink-0" />
          <span class="flex-1 truncate">전체 에셋</span>
          <span
            class="rounded-full px-1.5 py-0.5 text-body-xs"
            :class="
              selectedFolderId === null
                ? 'bg-primary-100 text-primary-700 dark:bg-primary-800/50 dark:text-primary-400'
                : 'bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-400'
            "
          >
            {{ totalAssetCount }}
          </span>
        </button>

        <!-- Folders -->
        <div
          v-for="folder in folders"
          :key="folder.id"
          class="group"
        >
          <!-- Editing Mode -->
          <div
            v-if="editingFolderId === folder.id"
            class="flex items-center gap-1 rounded-lg px-2.5 py-1.5"
          >
            <FolderOpenIcon class="h-4 w-4 flex-shrink-0 text-primary-500" />
            <input
              v-model="editingFolderName"
              type="text"
              class="min-w-0 flex-1 rounded border border-primary-300 bg-white px-1.5 py-0.5 text-body text-gray-900 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-primary-600 dark:bg-gray-900 dark:text-gray-100"
              autofocus
              @keydown="onRenameKeydown"
            />
            <button
              class="rounded p-0.5 text-success-strong hover:bg-success-subtle"
              @click="confirmRename"
            >
              <CheckIcon class="h-3.5 w-3.5" />
            </button>
            <button
              class="rounded p-0.5 text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700"
              @click="cancelRename"
            >
              <XMarkIcon class="h-3.5 w-3.5" />
            </button>
          </div>

          <!-- Normal Mode -->
          <button
            v-else
            class="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-body transition-colors"
            :class="
              selectedFolderId === folder.id
                ? 'bg-primary-50 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
                : 'text-gray-700 hover:bg-gray-50 dark:text-gray-300 dark:hover:bg-gray-700/50'
            "
            @click="selectFolder(folder.id)"
          >
            <component
              :is="selectedFolderId === folder.id ? FolderOpenIcon : FolderIcon"
              class="h-4 w-4 flex-shrink-0"
            />
            <span class="flex-1 truncate">{{ folder.name }}</span>

            <!-- Folder actions (visible on hover) -->
            <span
              class="hidden items-center gap-0.5 group-hover:flex"
              @click.stop
            >
              <button
                class="rounded p-0.5 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
                title="이름 변경"
                @click="startRename(folder.id, folder.name)"
              >
                <PencilIcon class="h-3 w-3" />
              </button>
              <button
                class="rounded p-0.5 text-gray-400 hover:text-error-strong"
                title="삭제"
                @click="handleDeleteFolder(folder.id)"
              >
                <TrashIcon class="h-3 w-3" />
              </button>
            </span>

            <!-- Count (hidden on hover) -->
            <span
              class="flex group-hover:hidden rounded-full px-1.5 py-0.5 text-body-xs"
              :class="
                selectedFolderId === folder.id
                  ? 'bg-primary-100 text-primary-700 dark:bg-primary-800/50 dark:text-primary-400'
                  : 'bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-400'
              "
            >
              {{ folder.assetCount }}
            </span>
          </button>
        </div>

        <!-- Unfiled -->
        <button
          v-if="unfiledCount > 0"
          class="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-body text-gray-500 transition-colors hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-700/50"
          disabled
        >
          <FolderIcon class="h-4 w-4 flex-shrink-0 opacity-50" />
          <span class="flex-1 truncate italic">미분류</span>
          <span class="rounded-full bg-gray-100 px-1.5 py-0.5 text-body-xs text-gray-400 dark:bg-gray-700 dark:text-gray-500">
            {{ unfiledCount }}
          </span>
        </button>

        <!-- New Folder Input -->
        <div
          v-if="showNewFolderInput"
          class="flex items-center gap-1 rounded-lg px-2.5 py-1.5"
        >
          <FolderPlusIcon class="h-4 w-4 flex-shrink-0 text-primary-500" />
          <input
            v-model="newFolderName"
            type="text"
            placeholder="폴더 이름"
            class="min-w-0 flex-1 rounded border border-primary-300 bg-white px-1.5 py-0.5 text-body text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-primary-600 dark:bg-gray-900 dark:text-gray-100 dark:placeholder-gray-500"
            autofocus
            @keydown="onNewFolderKeydown"
          />
          <button
            class="rounded p-0.5 text-success-strong hover:bg-success-subtle"
            @click="confirmCreateFolder"
          >
            <CheckIcon class="h-3.5 w-3.5" />
          </button>
          <button
            class="rounded p-0.5 text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700"
            @click="cancelCreateFolder"
          >
            <XMarkIcon class="h-3.5 w-3.5" />
          </button>
        </div>
      </nav>
    </div>

    <!-- 폴더 삭제 확인 -->
    <ConfirmModal
      v-model="showDeleteModal"
      :title="$t('assets.folder.deleteTitle')"
      :message="$t('assets.folder.deleteMessage')"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="confirmDeleteFolder"
      @cancel="deleteTargetId = null"
    />
  </aside>
</template>
