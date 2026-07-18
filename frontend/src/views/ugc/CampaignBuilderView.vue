<template>
  <div class="max-w-3xl">
    <PageHeader :title="isEdit ? $t('ugc.editTitle') : $t('ugc.createTitle')" :description="$t('ugc.builderDescription')" />

    <!-- Step indicator -->
    <ol class="mb-6 flex items-center gap-2 text-sm">
      <li
        v-for="(label, idx) in stepLabels"
        :key="idx"
        :class="[
          'flex items-center gap-2 rounded-lg px-3 py-1.5',
          step === idx + 1
            ? 'bg-primary-600 text-white dark:bg-primary-500'
            : step > idx + 1
              ? 'bg-primary-50 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
              : 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
        ]"
      >
        <span class="font-semibold">{{ idx + 1 }}</span>
        <span class="hidden mobile:inline">{{ label }}</span>
      </li>
    </ol>

    <div class="card space-y-5">
      <!-- Step 1: 기본 정보 -->
      <template v-if="step === 1">
        <div>
          <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('ugc.fieldName') }} *</label>
          <input v-model="form.name" type="text" class="input-field" :placeholder="$t('ugc.namePlaceholder')" />
        </div>
        <div>
          <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('ugc.fieldDescription') }}</label>
          <textarea v-model="form.description" rows="3" class="input-field" />
        </div>
        <div>
          <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('ugc.fieldObjective') }}</label>
          <select v-model="form.objective" class="input-field">
            <option value="AWARENESS">{{ $t('ugc.objective.AWARENESS') }}</option>
            <option value="CONVERSION">{{ $t('ugc.objective.CONVERSION') }}</option>
            <option value="ENGAGEMENT">{{ $t('ugc.objective.ENGAGEMENT') }}</option>
          </select>
        </div>
      </template>

      <!-- Step 2: 플레이북 -->
      <template v-else-if="step === 2">
        <div>
          <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('ugc.playbookTitle') }}</label>
          <input v-model="form.playbookTitle" type="text" class="input-field" :placeholder="$t('ugc.playbookTitlePlaceholder')" />
          <p class="mt-1 text-xs text-gray-400">{{ $t('ugc.playbookHint') }}</p>
        </div>
        <div>
          <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('ugc.playbookSummary') }}</label>
          <textarea v-model="form.playbookSummary" rows="2" class="input-field" />
        </div>
        <div>
          <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('ugc.contentType') }}</label>
          <select v-model="form.contentType" class="input-field">
            <option value="UGC_VIDEO">UGC_VIDEO</option>
            <option value="SLIDESHOW">SLIDESHOW</option>
            <option value="TESTIMONIAL">TESTIMONIAL</option>
            <option value="HOOK_DEMO">HOOK_DEMO</option>
          </select>
        </div>
        <div>
          <div class="mb-2 flex items-center justify-between">
            <label class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('ugc.steps') }}</label>
            <button class="btn-secondary inline-flex items-center gap-1 text-xs" @click="addStep">
              <PlusIcon class="h-4 w-4" />{{ $t('ugc.addStep') }}
            </button>
          </div>
          <div v-if="form.steps.length === 0" class="rounded-lg border border-dashed border-gray-300 py-4 text-center text-xs text-gray-400 dark:border-gray-600">
            {{ $t('ugc.noSteps') }}
          </div>
          <div v-for="(s, i) in form.steps" :key="i" class="mb-2 rounded-lg border border-gray-200 p-3 dark:border-gray-700">
            <div class="flex items-center gap-2">
              <input v-model="s.title" type="text" class="input-field flex-1" :placeholder="$t('ugc.stepTitlePlaceholder')" />
              <button class="rounded p-1.5 text-gray-400 hover:bg-gray-100 hover:text-red-500 dark:hover:bg-gray-700" @click="removeStep(i)">
                <TrashIcon class="h-4 w-4" />
              </button>
            </div>
            <textarea v-model="s.instruction" rows="2" class="input-field mt-2" :placeholder="$t('ugc.stepInstructionPlaceholder')" />
          </div>
        </div>
      </template>

      <!-- Step 3: 보상·일정 -->
      <template v-else-if="step === 3">
        <div class="grid grid-cols-1 gap-4 mobile:grid-cols-2">
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('ugc.totalBudget') }}</label>
            <input v-model.number="form.totalBudget" type="number" min="0" class="input-field" />
          </div>
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('ugc.fixedReward') }}</label>
            <input v-model.number="form.fixedRewardPerCreator" type="number" min="0" class="input-field" />
          </div>
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('ugc.startAt') }}</label>
            <input v-model="form.startAt" type="datetime-local" class="input-field" />
          </div>
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('ugc.endAt') }}</label>
            <input v-model="form.endAt" type="datetime-local" class="input-field" />
          </div>
        </div>
        <p class="text-xs text-gray-400">{{ $t('ugc.publishHint') }}</p>
      </template>

      <!-- Step 4: 검토 -->
      <template v-else>
        <dl class="space-y-2 text-sm">
          <div class="flex justify-between gap-4"><dt class="text-gray-500">{{ $t('ugc.fieldName') }}</dt><dd class="font-medium text-gray-900 dark:text-gray-100">{{ form.name || '-' }}</dd></div>
          <div class="flex justify-between gap-4"><dt class="text-gray-500">{{ $t('ugc.fieldObjective') }}</dt><dd class="text-gray-900 dark:text-gray-100">{{ form.objective }}</dd></div>
          <div class="flex justify-between gap-4"><dt class="text-gray-500">{{ $t('ugc.playbookTitle') }}</dt><dd class="text-gray-900 dark:text-gray-100">{{ form.playbookTitle || '-' }} ({{ form.steps.length }} {{ $t('ugc.stepsUnit') }})</dd></div>
          <div class="flex justify-between gap-4"><dt class="text-gray-500">{{ $t('ugc.totalBudget') }}</dt><dd class="text-gray-900 dark:text-gray-100">{{ form.totalBudget.toLocaleString() }}</dd></div>
          <div class="flex justify-between gap-4"><dt class="text-gray-500">{{ $t('ugc.fixedReward') }}</dt><dd class="text-gray-900 dark:text-gray-100">{{ form.fixedRewardPerCreator.toLocaleString() }}</dd></div>
          <div class="flex justify-between gap-4"><dt class="text-gray-500">{{ $t('ugc.period') }}</dt><dd class="text-gray-900 dark:text-gray-100">{{ form.startAt || '?' }} ~ {{ form.endAt || '?' }}</dd></div>
        </dl>
      </template>

      <!-- Nav buttons -->
      <div class="flex items-center justify-between border-t border-gray-100 pt-4 dark:border-gray-700">
        <button class="btn-secondary" @click="step > 1 ? step-- : cancel()">
          {{ step > 1 ? $t('ugc.prevStep') : $t('ugc.cancel') }}
        </button>
        <button v-if="step < 4" class="btn-primary" :disabled="step === 1 && !form.name.trim()" @click="step++">
          {{ $t('ugc.nextStep') }}
        </button>
        <button v-else class="btn-primary inline-flex items-center gap-2" :disabled="saving" @click="save">
          {{ saving ? $t('ugc.saving') : $t('ugc.saveDraft') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useUgcCampaignStore } from '@/stores/ugcCampaign'
import { useNotificationStore } from '@/stores/notification'
import PageHeader from '@/components/common/PageHeader.vue'
import { PlusIcon, TrashIcon } from '@heroicons/vue/24/outline'

interface StepForm {
  stepType: string
  title: string
  instruction: string
  exampleUrl: string
  required: boolean
}

const { t } = useI18n({ useScope: 'global' })
const route = useRoute()
const router = useRouter()
const store = useUgcCampaignStore()
const notify = useNotificationStore()

const editId = computed(() => (route.params.id ? Number(route.params.id) : null))
const isEdit = computed(() => editId.value !== null)

const step = ref(1)
const saving = ref(false)

const stepLabels = computed(() => [
  t('ugc.stepBasic'),
  t('ugc.stepPlaybook'),
  t('ugc.stepReward'),
  t('ugc.stepReview'),
])

const form = reactive({
  name: '',
  description: '',
  objective: 'AWARENESS',
  totalBudget: 0,
  fixedRewardPerCreator: 0,
  startAt: '',
  endAt: '',
  playbookTitle: '',
  playbookSummary: '',
  contentType: 'UGC_VIDEO',
  steps: [] as StepForm[],
})

function addStep() {
  form.steps.push({ stepType: 'INSTRUCTION', title: '', instruction: '', exampleUrl: '', required: true })
}

function removeStep(index: number) {
  form.steps.splice(index, 1)
}

function cancel() {
  router.push('/ugc/campaigns')
}

function toLocalInput(value: string | null): string {
  return value ? value.slice(0, 16) : ''
}

async function save() {
  if (!form.name.trim()) {
    notify.error(t('ugc.nameRequired'))
    step.value = 1
    return
  }
  if (form.startAt && form.endAt && form.endAt <= form.startAt) {
    notify.error(t('ugc.periodInvalid'))
    step.value = 3
    return
  }

  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      description: form.description.trim() || null,
      objective: form.objective,
      totalBudget: form.totalBudget,
      fixedRewardPerCreator: form.fixedRewardPerCreator,
      startAt: form.startAt || null,
      endAt: form.endAt || null,
    }

    const campaignId = isEdit.value
      ? (await store.updateCampaign(editId.value!, payload)).campaign.id
      : (await store.createCampaign(payload)).campaign.id

    if (form.playbookTitle.trim()) {
      await store.upsertPlaybook(campaignId, {
        title: form.playbookTitle.trim(),
        summary: form.playbookSummary.trim() || null,
        contentType: form.contentType,
        steps: form.steps
          .filter((s) => s.title.trim())
          .map((s) => ({
            stepType: s.stepType,
            title: s.title.trim(),
            instruction: s.instruction.trim() || null,
            exampleUrl: s.exampleUrl.trim() || null,
            required: s.required,
          })),
      })
    }

    notify.success(isEdit.value ? t('ugc.updated') : t('ugc.created'))
    router.push(`/ugc/campaigns/${campaignId}`)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.saveFailed'))
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  if (!isEdit.value) return
  try {
    const detail = await store.fetchCampaign(editId.value!)
    const c = detail.campaign
    form.name = c.name
    form.description = c.description ?? ''
    form.objective = c.objective
    form.totalBudget = c.totalBudget
    form.fixedRewardPerCreator = c.fixedRewardPerCreator
    form.startAt = toLocalInput(c.startAt)
    form.endAt = toLocalInput(c.endAt)
    if (detail.playbook) {
      form.playbookTitle = detail.playbook.title
      form.playbookSummary = detail.playbook.summary ?? ''
      form.contentType = detail.playbook.contentType
      form.steps = detail.playbook.steps.map((s) => ({
        stepType: s.stepType,
        title: s.title,
        instruction: s.instruction ?? '',
        exampleUrl: s.exampleUrl ?? '',
        required: s.required,
      }))
    }
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.loadFailed'))
  }
})
</script>
