<template>
  <div class="relative">
    <PageHeader :title="$t('inbox.hubTitle')" :description="$t('inbox.hubDescription')">
      <template #title-suffix>
        <span
          v-if="inboxStore.unreadCount > 0"
          class="px-2.5 py-0.5 text-xs font-semibold rounded-full bg-primary-600 text-white"
        >
          {{ inboxStore.unreadCount }}
        </span>
      </template>
    </PageHeader>

    <PageGuide :title="$t('inbox.pageGuideTitle')" :items="($tm('inbox.pageGuide') as string[])" />

    <OTabs
      :model-value="activeTab"
      :tabs="tabs"
      :aria-label="$t('inbox.hubTitle')"
      class="mb-6"
      @update:model-value="selectTab"
    >
      <template #default="{ panelId, tabId }">
        <div :id="panelId" role="tabpanel" :aria-labelledby="tabId" class="mt-6">
          <div v-show="activeTab === 'comments'">
            <CommentsPanel v-if="visited.comments" sync-query />
          </div>
          <div v-show="activeTab === 'messages'">
            <InboxMessagesPanel v-if="visited.messages" sync-query />
          </div>
        </div>
      </template>
    </OTabs>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ChatBubbleLeftEllipsisIcon, InboxIcon } from '@heroicons/vue/24/outline'
import { useInboxStore } from '@/stores/inbox'
import { useCommentsStore } from '@/stores/comments'
import CommentsPanel from '@/components/comments/CommentsPanel.vue'
import InboxMessagesPanel from '@/components/inbox/InboxMessagesPanel.vue'
import OTabs from '@/components/ui/OTabs.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'

type HubTab = 'comments' | 'messages'

const DEFAULT_TAB: HubTab = 'comments'

const { t } = useI18n({ useScope: 'global' })
const route = useRoute()
const router = useRouter()
const inboxStore = useInboxStore()
const commentsStore = useCommentsStore()

const readTab = (): HubTab => (route.query.tab === 'messages' ? 'messages' : DEFAULT_TAB)

const activeTab = ref<HubTab>(readTab())

// 탭을 한 번이라도 연 경우에만 패널을 마운트해 불필요한 API 호출을 막는다
const visited = reactive<Record<HubTab, boolean>>({
  comments: activeTab.value === 'comments',
  messages: activeTab.value === 'messages',
})

const tabs = computed(() => [
  {
    key: 'comments',
    label: t('inbox.tabs.comments'),
    icon: ChatBubbleLeftEllipsisIcon,
    count: visited.comments ? commentsStore.totalCount : undefined,
  },
  {
    key: 'messages',
    label: t('inbox.tabs.messages'),
    icon: InboxIcon,
    count: inboxStore.unreadCount > 0 ? inboxStore.unreadCount : undefined,
  },
])

const selectTab = (value: string) => {
  const tab: HubTab = value === 'messages' ? 'messages' : 'comments'
  activeTab.value = tab
}

// 탭 전환 → history push (뒤로가기로 이전 탭 복귀)
watch(activeTab, (tab) => {
  visited[tab] = true
  if (readTab() === tab) return
  router.push({ query: { ...route.query, tab } })
})

// 주소창/뒤로가기 → 탭 반영
watch(
  () => route.query.tab,
  () => {
    const tab = readTab()
    if (activeTab.value !== tab) activeTab.value = tab
  },
)
</script>
