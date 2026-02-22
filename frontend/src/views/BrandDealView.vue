<template>
  <div class="max-w-6xl mx-auto space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-900">브랜드 딜 관리</h1>
    </div>

    <!-- 탭 -->
    <div class="border-b border-gray-200">
      <nav class="flex gap-6">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          :class="[
            'pb-3 text-sm font-medium border-b-2 transition-colors',
            activeTab === tab.key
              ? 'border-indigo-600 text-indigo-600'
              : 'border-transparent text-gray-500 hover:text-gray-700',
          ]"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- 딜 트래커 탭 -->
    <div v-if="activeTab === 'tracker'">
      <div class="mb-4 flex items-center justify-between">
        <select v-model="statusFilter" class="px-3 py-2 border rounded-lg text-sm" @change="filterDeals">
          <option value="">전체</option>
          <option value="INQUIRY">문의</option>
          <option value="NEGOTIATION">협상 중</option>
          <option value="CONTRACTED">계약 완료</option>
          <option value="IN_PROGRESS">진행 중</option>
          <option value="COMPLETED">완료</option>
          <option value="CANCELLED">취소</option>
        </select>
        <button
          class="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm hover:bg-indigo-700"
          @click="showCreateModal = true"
        >
          새 딜 추가
        </button>
      </div>

      <!-- 로딩 -->
      <div v-if="store.loading" class="text-center py-12 text-gray-400">
        로딩 중...
      </div>

      <!-- 빈 상태 -->
      <div v-else-if="store.deals.length === 0" class="text-center py-12 text-gray-400">
        등록된 브랜드 딜이 없습니다.
      </div>

      <!-- 딜 카드 목록 -->
      <div v-else class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div
          v-for="deal in store.deals"
          :key="deal.id"
          class="bg-white rounded-lg border p-5 space-y-3 hover:shadow-md transition-shadow"
        >
          <div class="flex items-start justify-between">
            <h3 class="text-base font-semibold text-gray-900 truncate">{{ deal.brandName }}</h3>
            <span
              :class="[
                'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
                statusBadgeClass(deal.status),
              ]"
            >
              {{ statusLabel(deal.status) }}
            </span>
          </div>

          <div v-if="deal.dealValue != null" class="text-lg font-bold text-indigo-600">
            {{ formatKRW(deal.dealValue) }}
          </div>

          <div class="space-y-1 text-sm text-gray-500">
            <div v-if="deal.contactName" class="flex items-center gap-1">
              <span class="font-medium text-gray-600">담당자:</span> {{ deal.contactName }}
            </div>
            <div v-if="deal.deadline" class="flex items-center gap-1">
              <span class="font-medium text-gray-600">마감일:</span> {{ deal.deadline }}
            </div>
          </div>

          <div class="flex justify-end pt-2 border-t border-gray-100">
            <button
              class="text-xs text-red-500 hover:text-red-700"
              @click="handleDeleteDeal(deal.id)"
            >
              삭제
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 미디어키트 탭 -->
    <div v-if="activeTab === 'mediakit'">
      <div class="bg-white rounded-lg border p-6 space-y-5">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">표시 이름</label>
          <input
            v-model="mkForm.displayName"
            type="text"
            class="w-full px-3 py-2 border rounded-lg text-sm"
            placeholder="크리에이터 이름"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">소개 (Bio)</label>
          <textarea
            v-model="mkForm.bio"
            rows="3"
            class="w-full px-3 py-2 border rounded-lg text-sm"
            placeholder="간단한 자기소개를 입력하세요"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">카테고리 (쉼표 구분)</label>
          <input
            v-model="mkCategoriesInput"
            type="text"
            class="w-full px-3 py-2 border rounded-lg text-sm"
            placeholder="뷰티, 패션, 라이프스타일"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">소셜 링크</label>
          <div class="space-y-2">
            <div v-for="platform in socialPlatforms" :key="platform" class="flex items-center gap-2">
              <span class="text-sm font-medium text-gray-600 w-24">{{ platform }}</span>
              <input
                v-model="mkSocialLinks[platform]"
                type="url"
                class="flex-1 px-3 py-2 border rounded-lg text-sm"
                :placeholder="`https://${platform.toLowerCase()}.com/...`"
              />
            </div>
          </div>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">공개 슬러그</label>
          <input
            v-model="mkForm.slug"
            type="text"
            class="w-full px-3 py-2 border rounded-lg text-sm"
            placeholder="my-media-kit"
          />
        </div>
        <div class="flex items-center gap-2">
          <input
            id="isPublic"
            v-model="mkForm.isPublic"
            type="checkbox"
            class="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
          />
          <label for="isPublic" class="text-sm text-gray-700">미디어키트 공개</label>
        </div>
        <div class="flex justify-end">
          <button
            class="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm hover:bg-indigo-700 disabled:opacity-50"
            :disabled="store.loading"
            @click="handleSaveMediaKit"
          >
            저장
          </button>
        </div>
      </div>
    </div>

    <!-- 새 딜 추가 모달 -->
    <div v-if="showCreateModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="showCreateModal = false">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-lg p-6 space-y-4">
        <h2 class="text-lg font-bold text-gray-900">새 브랜드 딜 추가</h2>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">브랜드명 *</label>
          <input
            v-model="newDeal.brandName"
            type="text"
            class="w-full px-3 py-2 border rounded-lg text-sm"
            placeholder="브랜드 이름"
          />
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">담당자</label>
            <input
              v-model="newDeal.contactName"
              type="text"
              class="w-full px-3 py-2 border rounded-lg text-sm"
              placeholder="담당자 이름"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">이메일</label>
            <input
              v-model="newDeal.contactEmail"
              type="email"
              class="w-full px-3 py-2 border rounded-lg text-sm"
              placeholder="email@brand.com"
            />
          </div>
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">딜 금액 (원)</label>
            <input
              v-model.number="newDeal.dealValue"
              type="number"
              class="w-full px-3 py-2 border rounded-lg text-sm"
              placeholder="0"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">마감일</label>
            <input
              v-model="newDeal.deadline"
              type="date"
              class="w-full px-3 py-2 border rounded-lg text-sm"
            />
          </div>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">메모</label>
          <textarea
            v-model="newDeal.notes"
            rows="3"
            class="w-full px-3 py-2 border rounded-lg text-sm"
            placeholder="추가 메모"
          />
        </div>
        <div class="flex justify-end gap-3 pt-2">
          <button
            class="px-4 py-2 border rounded-lg text-sm text-gray-700 hover:bg-gray-50"
            @click="showCreateModal = false"
          >
            취소
          </button>
          <button
            class="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm hover:bg-indigo-700 disabled:opacity-50"
            :disabled="!newDeal.brandName.trim()"
            @click="handleCreateDeal"
          >
            추가
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useBrandDealStore } from '@/stores/branddeal'

const store = useBrandDealStore()

const activeTab = ref('tracker')
const statusFilter = ref('')
const showCreateModal = ref(false)

const newDeal = reactive({
  brandName: '',
  contactName: '',
  contactEmail: '',
  dealValue: undefined as number | undefined,
  deadline: '',
  notes: '',
})

// 미디어키트 폼
const mkForm = reactive({
  displayName: '',
  bio: '',
  slug: '',
  isPublic: false,
})
const mkCategoriesInput = ref('')
const mkSocialLinks = reactive<Record<string, string>>({})
const socialPlatforms = ['YouTube', 'Instagram', 'TikTok', 'Twitter']

const tabs = [
  { key: 'tracker', label: '딜 트래커' },
  { key: 'mediakit', label: '미디어키트' },
]

const statusLabels: Record<string, string> = {
  INQUIRY: '문의',
  NEGOTIATION: '협상 중',
  CONTRACTED: '계약 완료',
  IN_PROGRESS: '진행 중',
  COMPLETED: '완료',
  CANCELLED: '취소',
}

function statusLabel(status: string): string {
  return statusLabels[status] ?? status
}

function statusBadgeClass(status: string): string {
  const map: Record<string, string> = {
    INQUIRY: 'bg-gray-100 text-gray-700',
    NEGOTIATION: 'bg-yellow-100 text-yellow-700',
    CONTRACTED: 'bg-blue-100 text-blue-700',
    IN_PROGRESS: 'bg-indigo-100 text-indigo-700',
    COMPLETED: 'bg-green-100 text-green-700',
    CANCELLED: 'bg-red-100 text-red-700',
  }
  return map[status] ?? 'bg-gray-100 text-gray-700'
}

function formatKRW(value: number): string {
  return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(value)
}

async function filterDeals() {
  await store.loadDeals(statusFilter.value || undefined)
}

function resetNewDeal() {
  newDeal.brandName = ''
  newDeal.contactName = ''
  newDeal.contactEmail = ''
  newDeal.dealValue = undefined
  newDeal.deadline = ''
  newDeal.notes = ''
}

async function handleCreateDeal() {
  if (!newDeal.brandName.trim()) return
  try {
    await store.createDeal({
      brandName: newDeal.brandName,
      contactName: newDeal.contactName || undefined,
      contactEmail: newDeal.contactEmail || undefined,
      dealValue: newDeal.dealValue,
      deadline: newDeal.deadline || undefined,
      notes: newDeal.notes || undefined,
    })
    showCreateModal.value = false
    resetNewDeal()
  } catch (e) {
    console.error('딜 생성 실패:', e)
  }
}

async function handleDeleteDeal(id: number) {
  if (!confirm('이 딜을 삭제하시겠습니까?')) return
  try {
    await store.deleteDeal(id)
  } catch (e) {
    console.error('딜 삭제 실패:', e)
  }
}

async function handleSaveMediaKit() {
  const categories = mkCategoriesInput.value
    .split(',')
    .map(c => c.trim())
    .filter(Boolean)
  const socialLinks: Record<string, string> = {}
  for (const p of socialPlatforms) {
    if (mkSocialLinks[p]) {
      socialLinks[p] = mkSocialLinks[p]
    }
  }
  try {
    await store.saveMediaKit({
      displayName: mkForm.displayName || undefined,
      bio: mkForm.bio || undefined,
      categories: categories.length > 0 ? categories : undefined,
      socialLinks: Object.keys(socialLinks).length > 0 ? socialLinks : undefined,
      isPublic: mkForm.isPublic,
      slug: mkForm.slug || undefined,
    })
  } catch (e) {
    console.error('미디어키트 저장 실패:', e)
  }
}

function populateMediaKitForm() {
  if (store.mediaKit) {
    mkForm.displayName = store.mediaKit.displayName ?? ''
    mkForm.bio = store.mediaKit.bio ?? ''
    mkForm.slug = store.mediaKit.slug ?? ''
    mkForm.isPublic = store.mediaKit.isPublic ?? false
    mkCategoriesInput.value = (store.mediaKit.categories ?? []).join(', ')
    for (const p of socialPlatforms) {
      mkSocialLinks[p] = store.mediaKit.socialLinks?.[p] ?? ''
    }
  }
}

watch(() => activeTab.value, async (tab) => {
  if (tab === 'mediakit' && !store.mediaKit) {
    await store.loadMediaKit()
    populateMediaKitForm()
  }
})

onMounted(async () => {
  await store.loadDeals()
})
</script>
