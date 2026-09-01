<template>
  <div class="relative min-h-full space-y-5 py-5 text-content">
    <PageHeader :title="$t('subscription.title')" :description="$t('subscription.description')" />

    <PageGuide :title="$t('subscription.pageGuideTitle')" :items="($tm('subscription.pageGuide') as string[])" />

    <div v-if="subscriptionStore.error || creditStore.balanceError || creditStore.transactionsError || usageError || usageAlertsError" class="flex flex-wrap items-center gap-2 rounded-lg border border-error-subtle bg-error-subtle px-3 py-2.5 text-body text-error-strong" role="alert">
      <span class="min-w-0 flex-1">{{ subscriptionStore.error || creditStore.balanceError || creditStore.transactionsError || usageError || usageAlertsError }}</span>
      <button v-if="creditStore.balanceError" type="button" class="rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold" :disabled="creditStore.isLoadingBalance" @click="creditStore.fetchBalance()">잔액 다시 시도</button>
      <button v-if="creditStore.transactionsError" type="button" class="rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold" :disabled="creditStore.isLoadingTransactions" @click="creditStore.fetchTransactions(0, 20)">내역 다시 시도</button>
      <button v-if="usageError" type="button" class="rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold" :disabled="usageLoading" @click="fetchUsage()">사용량 다시 시도</button>
      <button v-if="usageAlertsError" type="button" class="rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold" @click="fetchUsageAlerts()">알림 다시 시도</button>
    </div>

    <LoadingSpinner v-if="subscriptionStore.loading && !subscription" full-page />

    <template v-else>
      <!-- Section 1: Current Plan Card -->
      <div class="rounded-[11px] border border-line bg-surface-card p-4 mb-6">
        <div class="flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
          <div>
            <div class="mb-1 flex items-center gap-2">
              <h2 class="text-title font-semibold text-gray-900 dark:text-gray-100">{{ $t('subscription.currentPlan') }}</h2>
              <span
                v-if="subscription"
                class="inline-flex items-center rounded-full px-2.5 py-0.5 text-body-xs font-medium"
                :class="subscriptionStatusClass"
              >
                {{ subscriptionStatusLabel }}
              </span>
            </div>
            <div v-if="subscription" class="space-y-1">
              <p class="text-h1 font-bold text-primary-600">
                {{ currentPlanInfo?.name ?? subscription.planType }}
                <span class="text-body-lg font-normal text-gray-500 dark:text-gray-400">
                  {{ currentPlanInfo ? formatPrice(subscription.billingCycle === 'YEARLY' ? currentPlanInfo.yearlyPrice : currentPlanInfo.price) : '' }}
                  <template v-if="currentPlanInfo && currentPlanInfo.price > 0">
                    {{ subscription.billingCycle === 'YEARLY' ? '/년' : $t('subscription.perMonth') }}
                  </template>
                </span>
              </p>
              <p v-if="subscription.nextBillingDate" class="text-body text-gray-500 dark:text-gray-400">
                <CalendarIcon class="mr-1 inline h-4 w-4" />
                {{ $t('subscription.nextBillingDate') }}: {{ formatDate(subscription.nextBillingDate) }}
              </p>
              <p v-if="pendingPlanInfo && subscription.pendingPlanType" class="text-body text-warning-strong" role="status">
                다음 결제일에 {{ pendingPlanInfo.name }} 플랜
                <span v-if="subscription.pendingBillingCycle">
                  ({{ subscription.pendingBillingCycle === 'YEARLY' ? '연간' : '월간' }})
                </span>
                으로 변경됩니다.
              </p>
              <p v-if="subscription.status === 'TRIALING' && subscription.trialEnd" class="text-body text-info-strong">
                트라이얼 종료: {{ formatDate(subscription.trialEnd) }}
              </p>
              <p v-if="subscription.status === 'PAUSED' && subscription.resumeAt" class="text-body text-warning-strong">
                재개 예정: {{ formatDate(subscription.resumeAt) }}
              </p>
              <p v-if="currentPlanInfo" class="text-body text-gray-500 dark:text-gray-400">
                <ArrowUpTrayIcon class="mr-1 inline h-4 w-4" />
                {{ $t('subscription.uploads') }}:
                <template v-if="currentPlanInfo.maxUploadsPerMonth === -1">{{ $t('subscription.unlimited') }}</template>
                <template v-else>{{ $t('subscription.uploadsPerMonth', { count: currentPlanInfo.maxUploadsPerMonth }) }}</template>
              </p>
            </div>
            <p v-else class="text-body text-gray-500 dark:text-gray-400">{{ $t('subscription.noSubscriptionInfo') }}</p>
          </div>
          <div v-if="subscription" class="flex flex-wrap gap-2">
            <button class="btn-primary" @click="showPlanComparison">
              <ArrowPathIcon class="mr-1.5 h-4 w-4" />
              {{ $t('subscription.changePlan') }}
            </button>
            <button
              v-if="subscription.status === 'FREE' && subscription.planType === 'FREE'"
              class="btn-secondary"
              @click="handleStartTrial"
            >
              무료 체험 시작
            </button>
            <button
              v-if="subscription.status === 'ACTIVE' && subscription.planType !== 'FREE'"
              class="btn-secondary"
              @click="handlePause"
            >
              일시정지
            </button>
            <button
              v-if="subscription.status === 'PAUSED'"
              class="btn-primary"
              @click="handleResume"
            >
              구독 재개
            </button>
            <button
              v-if="subscription.status === 'ACTIVE' && subscription.planType !== 'FREE'"
              class="btn-danger"
              @click="showCancelModal = true"
            >
              {{ $t('subscription.cancelSubscription') }}
            </button>
          </div>
        </div>
      </div>

      <!-- Section 2: Usage Statistics -->
      <div v-if="currentPlanInfo" class="rounded-[11px] border border-line bg-surface-card p-4 mb-6">
        <h2 class="mb-4 text-title font-semibold text-gray-900 dark:text-gray-100">{{ $t('subscription.usageStatus') }}</h2>

        <div class="space-y-4">
          <!--
            서버가 잰 값이 도착했을 때만 막대를 그린다. 재는 중이거나 재지 못했는데
            0으로 그리면, 아무것도 안 쓴 사용자와 구분되지 않는 거짓 측정이 된다.
          -->
          <template v-if="usageData">
            <!-- Monthly Uploads Usage -->
            <UsageProgressBar
              :label="$t('subscription.monthlyUploads')"
              :current="usageData.uploadsThisMonth"
              :max="currentPlanInfo.maxUploadsPerMonth"
              :unit="$t('subscription.unitTimes')"
            />

            <!-- Storage Usage — 한도는 서버가 준 실효값(관리자 오버라이드 포함) -->
            <UsageProgressBar
              v-if="storageUsage"
              :label="$t('subscription.storage')"
              :current="storageUsage.current"
              :max="storageUsage.max"
              :unit="storageUsage.unit"
            />
            <p v-else class="text-body-xs text-content-tertiary" role="status">
              저장공간 한도를 확인할 수 없습니다
            </p>
          </template>
          <p v-else-if="usageLoading" class="text-body-xs text-content-tertiary" role="status">
            사용량을 불러오는 중…
          </p>
          <p v-else class="text-body-xs text-warning-strong" role="status">
            사용량을 확인할 수 없습니다
          </p>

          <!-- Connected Platforms — 채널 목록에서 직접 세므로 usage 응답과 무관하다 -->
          <UsageProgressBar
            :label="$t('subscription.connectedChannels')"
            :current="connectedPlatformCount"
            :max="currentPlanInfo.maxPlatforms"
            :unit="$t('subscription.unitCount')"
          />
        </div>
      </div>

      <!-- Section 3: AI Credit Section -->
      <div class="rounded-[11px] border border-line bg-surface-card p-4 mb-6">
        <div class="mb-4 flex items-center justify-between">
          <h2 class="text-title font-semibold text-gray-900 dark:text-gray-100">
            <SparklesIcon class="mr-1.5 inline h-5 w-5 text-primary-600" />
            {{ $t('subscription.aiCredits') }}
          </h2>
          <button
            class="btn-primary"
            :disabled="!paymentEnabled"
            :title="paymentEnabled ? undefined : paymentUnavailableCopy"
            @click="showCreditModal = true"
          >
            <PlusIcon class="mr-1.5 h-4 w-4" />
            {{ $t('subscription.chargeCredits') }}
          </button>
        </div>

        <!--
          결제를 열 수 없는 이유를 미리 알린다. 눌러서 실패를 보는 것보다,
          누르기 전에 아는 편이 낫다. 운영자가 방금 설정을 켰을 수도 있으므로
          다시 확인할 수단을 같이 준다 — 캐시 때문에 이 탭만 옛 답을 들고 있을 수 있다.
        -->
        <PaymentUnavailableNotice
          v-if="!paymentEnabled"
          class="mb-4"
          :reason="paymentDisabledReason"
          :checking="paymentChecking"
          :check-failed="paymentCheckFailed"
          @recheck="recheckPaymentAvailability"
        />

        <div v-if="creditBalance">
          <div class="mb-2 flex items-end justify-between">
            <div>
              <span class="text-display font-bold" :class="creditStore.isLow ? 'text-error-strong' : 'text-gray-900 dark:text-gray-100'">
                {{ creditStore.totalBalance.toLocaleString() }}
              </span>
              <span class="ml-1 text-body text-gray-500 dark:text-gray-400">
                / {{ creditBalance.freeMonthly.toLocaleString() }} ({{ $t('subscription.monthlyFree') }})
              </span>
            </div>
            <span class="text-body-xs text-gray-400 dark:text-gray-500">
              {{ $t('subscription.freeResetDate') }}: {{ formatDate(creditBalance.freeResetDate) }}
            </span>
          </div>

          <!-- Progress Bar -->
          <div class="h-3 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
            <div
              class="h-full rounded-full transition-all duration-500"
              :class="creditStore.isLow ? 'bg-error' : 'bg-primary-600'"
              :style="{ width: creditPercentage + '%' }"
            />
          </div>
          <div class="mt-2 flex justify-between text-body-xs text-gray-500 dark:text-gray-400">
            <span>{{ $t('subscription.freeRemaining') }}: {{ creditBalance.freeRemaining.toLocaleString() }}</span>
            <span>{{ $t('subscription.purchasedRemaining') }}: {{ creditBalance.purchasedBalance.toLocaleString() }}</span>
          </div>

          <!-- Low credit warning -->
          <div
            v-if="creditStore.isLow"
            class="mt-3 flex items-center gap-2 rounded-lg bg-error-subtle px-3 py-2 text-body text-error-strong"
          >
            <ExclamationTriangleIcon class="h-4 w-4 flex-shrink-0" />
            {{ $t('subscription.lowCreditWarning') }}
          </div>
        </div>
        <div v-else-if="creditStore.balanceError" class="py-4 text-center text-body text-error-strong">
          크레딧 잔액을 확인하지 못했습니다. 위의 다시 시도를 눌러 주세요.
        </div>
        <div v-else class="py-4 text-center text-body text-gray-500 dark:text-gray-400">
          {{ $t('subscription.noCreditInfo') }}
        </div>
      </div>

      <!-- Section 4: Credit Usage History Table -->
      <div class="rounded-[11px] border border-line bg-surface-card p-4 mb-6">
        <h2 class="mb-4 text-title font-semibold text-gray-900 dark:text-gray-100">{{ $t('subscription.creditHistory') }}</h2>

        <div class="overflow-x-auto">
          <table v-if="creditTransactions && creditTransactions.content.length > 0" class="w-full text-body">
            <thead>
              <tr class="border-b border-gray-200 dark:border-gray-700 text-left text-body-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                <th class="px-4 py-3">{{ $t('subscription.table.date') }}</th>
                <th class="px-4 py-3">{{ $t('subscription.table.type') }}</th>
                <th class="px-4 py-3">{{ $t('subscription.table.feature') }}</th>
                <th class="px-4 py-3 text-right">{{ $t('subscription.table.credits') }}</th>
                <th class="px-4 py-3 text-right">{{ $t('subscription.table.remaining') }}</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
              <tr
                v-for="tx in creditTransactions.content"
                :key="tx.id"
                class="hover:bg-gray-50 dark:hover:bg-gray-700"
              >
                <td class="whitespace-nowrap px-4 py-3 text-gray-600 dark:text-gray-300">
                  {{ formatDateTime(tx.createdAt) }}
                </td>
                <td class="px-4 py-3">
                  <span
                    class="inline-flex items-center rounded-full px-2 py-0.5 text-body-xs font-medium"
                    :class="creditTransactionTypeClass(tx.type)"
                  >
                    {{ creditTransactionTypeLabel(tx.type) }}
                  </span>
                </td>
                <td class="px-4 py-3 text-gray-700 dark:text-gray-300">
                  {{ tx.feature ?? '-' }}
                </td>
                <td class="whitespace-nowrap px-4 py-3 text-right font-medium" :class="tx.type === 'DEDUCT' ? 'text-error-strong' : 'text-success-strong'">
                  {{ tx.type === 'DEDUCT' ? '-' : '+' }}{{ Math.abs(tx.amount).toLocaleString() }}
                </td>
                <td class="whitespace-nowrap px-4 py-3 text-right text-gray-600 dark:text-gray-300">
                  {{ tx.balanceAfter.toLocaleString() }}
                </td>
              </tr>
            </tbody>
          </table>
          <div v-else-if="creditStore.transactionsError" class="py-8 text-center text-body text-error-strong">
            크레딧 사용 내역을 확인하지 못했습니다. 위의 다시 시도를 눌러 주세요.
          </div>
          <div v-else class="py-8 text-center text-body text-gray-500 dark:text-gray-400">
            {{ $t('subscription.noCreditHistory') }}
          </div>
        </div>

        <!-- Pagination -->
        <div
          v-if="creditTransactions && creditTransactions.totalPages > 1"
          class="mt-4 flex items-center justify-between border-t border-gray-100 dark:border-gray-700 pt-4"
        >
          <p class="text-body-xs text-gray-500 dark:text-gray-400">
            {{ $t('subscription.totalCount', { count: creditTransactions.totalElements.toLocaleString() }) }}
          </p>
          <div class="flex gap-1">
            <button
              class="rounded-lg px-3 py-1.5 text-body-xs font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-40"
              :disabled="!creditTransactions.hasPrevious"
              @click="loadCreditTransactions(creditTransactions!.page - 1)"
            >
              {{ $t('action.previous') }}
            </button>
            <span class="px-3 py-1.5 text-body-xs text-gray-500 dark:text-gray-400">
              {{ creditTransactions.page + 1 }} / {{ creditTransactions.totalPages }}
            </span>
            <button
              class="rounded-lg px-3 py-1.5 text-body-xs font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-40"
              :disabled="!creditTransactions.hasNext"
              @click="loadCreditTransactions(creditTransactions!.page + 1)"
            >
              {{ $t('action.next') }}
            </button>
          </div>
        </div>
      </div>

      <!--
        쿠폰 입력 섹션은 제거했다.
        쿠폰 할인을 반영하는 결제 경로가 없어서, "쿠폰 유효: 20% 할인"을 보여주고
        적용까지 누르게 한 뒤 정가로 청구되고 있었다. 결제에 연결되기 전까지는
        입력란 자체가 지킬 수 없는 약속이다.
      -->

      <!-- Section: Usage Alert Settings -->
      <div class="rounded-[11px] border border-line bg-surface-card p-4 mb-6">
        <h2 class="mb-4 text-title font-semibold text-gray-900 dark:text-gray-100">사용량 알림 설정</h2>
        <div class="space-y-4">
          <div v-for="alert in usageAlerts" :key="alert.type" class="flex items-center justify-between rounded-lg border border-gray-200 dark:border-gray-700 px-4 py-3">
            <div class="flex items-center gap-3">
              <button
                class="relative inline-flex h-5 w-9 items-center rounded-full transition-colors"
                :class="alert.enabled ? 'bg-primary-600' : 'bg-gray-300 dark:bg-gray-600'"
                @click="toggleAlert(alert)"
              >
                <span
                  class="inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform"
                  :class="alert.enabled ? 'translate-x-4.5' : 'translate-x-0.5'"
                />
              </button>
              <div>
                <p class="text-body font-medium text-gray-900 dark:text-gray-100">{{ alert.label }}</p>
                <p class="text-body-xs text-gray-500 dark:text-gray-400">{{ alert.description }}</p>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-body-xs text-gray-500 dark:text-gray-400">{{ alert.thresholdPercent }}%</span>
              <input
                type="range"
                :value="alert.thresholdPercent"
                min="50"
                max="95"
                step="5"
                class="h-1.5 w-24 cursor-pointer appearance-none rounded-full bg-gray-200 dark:bg-gray-700 accent-primary-600"
                @change="updateAlertThreshold(alert, ($event.target as HTMLInputElement).valueAsNumber)"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- Section 6: Plan Comparison Table -->
      <div id="plan-comparison" class="card mb-6">
        <h2 class="mb-4 text-title font-semibold text-gray-900 dark:text-gray-100">{{ $t('subscription.planComparison') }}</h2>

        <!-- Billing Cycle Toggle -->
        <div class="mb-4 flex items-center justify-center gap-3">
          <span class="text-body font-medium" :class="billingCycle === 'MONTHLY' ? 'text-gray-900 dark:text-gray-100' : 'text-gray-400 dark:text-gray-500'">
            {{ $t('subscription.monthly') }}
          </span>
          <button
            class="relative inline-flex h-6 w-11 items-center rounded-full transition-colors"
            :class="billingCycle === 'YEARLY' ? 'bg-primary-600' : 'bg-gray-300 dark:bg-gray-600'"
            @click="billingCycle = billingCycle === 'MONTHLY' ? 'YEARLY' : 'MONTHLY'"
          >
            <span
              class="inline-block h-4 w-4 transform rounded-full bg-white transition-transform"
              :class="billingCycle === 'YEARLY' ? 'translate-x-6' : 'translate-x-1'"
            />
          </button>
          <span class="text-body font-medium" :class="billingCycle === 'YEARLY' ? 'text-gray-900 dark:text-gray-100' : 'text-gray-400 dark:text-gray-500'">
            {{ $t('subscription.yearly') }}
            <span class="ml-1 rounded-full bg-success-subtle px-2 py-0.5 text-body-xs font-medium text-success-strong">
              ~17% {{ $t('subscription.discount') }}
            </span>
          </span>
        </div>

        <p v-if="subscriptionError && storePlans.length === 0" class="mb-4 rounded-lg border border-error-subtle bg-error-subtle px-4 py-3 text-body text-error-strong">
          {{ subscriptionError }}
        </p>
        <!--
          유료 전환만 막는다. 무료 플랜 전환·다운그레이드는 결제를 거치지 않으므로
          표는 그대로 두고, 왜 지금 결제할 수 없는지만 미리 알린다.
        -->
        <PaymentUnavailableNotice
          v-if="!paymentEnabled"
          class="mb-4"
          :reason="paymentDisabledReason"
          :checking="paymentChecking"
          :check-failed="paymentCheckFailed"
          @recheck="recheckPaymentAvailability"
        />
        <PlanComparisonTable
          :plans="storePlans"
          :current-plan="subscription?.planType"
          :billing-cycle="billingCycle"
          :payment-enabled="paymentEnabled"
          :payment-disabled-reason="paymentUnavailableCopy"
          @select-plan="selectPlan"
        />
      </div>

      <!-- Section 6: Payment History -->
      <div class="card">
        <div class="mb-4 flex items-center justify-between gap-2">
          <h2 class="text-title font-semibold text-gray-900 dark:text-gray-100">{{ $t('subscription.paymentHistory') }}</h2>
          <button
            type="button"
            class="btn-secondary"
            :disabled="subscriptionStore.loading"
            @click="refreshPayments"
          >
            {{ $t('subscription.paymentRefresh') }}
          </button>
        </div>

        <!--
          미확정 결제가 있을 때만 나온다. 결제가 됐는지 안 됐는지 단정하지 않고,
          확인이 아직 끝나지 않았다는 사실만 말한다.
        -->
        <p
          v-if="hasPendingPayment"
          class="mb-4 rounded-lg bg-gray-100 px-3 py-2 text-body-xs text-gray-700 dark:bg-gray-800 dark:text-gray-300"
          role="status"
        >
          {{ $t('subscription.paymentPendingNotice') }}
        </p>

        <div class="overflow-x-auto">
          <table v-if="paymentList && paymentList.content.length > 0" class="w-full text-body">
            <thead>
              <tr class="border-b border-gray-200 dark:border-gray-700 text-left text-body-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                <th class="px-4 py-3">{{ $t('subscription.table.date') }}</th>
                <th class="px-4 py-3">{{ $t('subscription.table.item') }}</th>
                <th class="px-4 py-3">{{ $t('subscription.table.description') }}</th>
                <th class="px-4 py-3 text-right">{{ $t('subscription.table.amount') }}</th>
                <th class="px-4 py-3 text-center">{{ $t('subscription.table.status') }}</th>
                <th class="px-4 py-3 text-center">{{ $t('subscription.table.receipt') }}</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
              <tr
                v-for="payment in paymentList.content"
                :key="payment.id"
                class="hover:bg-gray-50 dark:hover:bg-gray-700"
              >
                <td class="whitespace-nowrap px-4 py-3 text-gray-600 dark:text-gray-300">
                  {{ formatDateTime(payment.createdAt) }}
                </td>
                <td class="px-4 py-3">
                  <span
                    class="inline-flex items-center rounded-full px-2 py-0.5 text-body-xs font-medium"
                    :class="payment.type === 'SUBSCRIPTION' ? 'bg-info-subtle text-info-strong' : 'bg-info-subtle text-info-strong'"
                  >
                    {{ payment.type === 'SUBSCRIPTION' ? $t('subscription.typeSubscription') : $t('subscription.typeCredit') }}
                  </span>
                </td>
                <td class="px-4 py-3 text-gray-700 dark:text-gray-300">
                  {{ payment.description }}
                </td>
                <td class="whitespace-nowrap px-4 py-3 text-right font-medium text-gray-900 dark:text-gray-100">
                  {{ formatPrice(payment.amount) }}
                </td>
                <td class="px-4 py-3 text-center">
                  <span
                    class="inline-flex items-center rounded-full px-2 py-0.5 text-body-xs font-medium"
                    :class="paymentStatusClass(payment.status)"
                  >
                    {{ paymentStatusLabel(payment.status) }}
                  </span>
                </td>
                <td class="px-4 py-3 text-center">
                  <a
                    v-if="payment.receiptUrl"
                    :href="payment.receiptUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="text-primary-600 hover:text-primary-700 hover:underline"
                  >
                    <DocumentTextIcon class="mx-auto h-4 w-4" />
                  </a>
                  <span v-else class="text-gray-300 dark:text-gray-600">-</span>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-else class="py-8 text-center text-body text-gray-500 dark:text-gray-400">
            {{ $t('subscription.noPaymentHistory') }}
          </div>
        </div>

        <!-- Payment Pagination -->
        <div
          v-if="paymentList && paymentList.totalPages > 1"
          class="mt-4 flex items-center justify-between border-t border-gray-100 dark:border-gray-700 pt-4"
        >
          <p class="text-body-xs text-gray-500 dark:text-gray-400">
            {{ $t('subscription.totalCount', { count: paymentList.totalElements.toLocaleString() }) }}
          </p>
          <div class="flex gap-1">
            <button
              class="rounded-lg px-3 py-1.5 text-body-xs font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-40"
              :disabled="!paymentList.hasPrevious"
              @click="loadPayments(paymentList!.page - 1)"
            >
              {{ $t('action.previous') }}
            </button>
            <span class="px-3 py-1.5 text-body-xs text-gray-500 dark:text-gray-400">
              {{ paymentList.page + 1 }} / {{ paymentList.totalPages }}
            </span>
            <button
              class="rounded-lg px-3 py-1.5 text-body-xs font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-40"
              :disabled="!paymentList.hasNext"
              @click="loadPayments(paymentList!.page + 1)"
            >
              {{ $t('action.next') }}
            </button>
          </div>
        </div>
      </div>
    </template>

    <!-- Plan Change Confirmation Modal -->
    <ConfirmModal
      v-model="showChangePlanModal"
      :title="$t('subscription.changePlanTitle')"
      :message="changePlanMessage"
      :confirm-text="$t('subscription.changePlanConfirm')"
      @confirm="confirmChangePlan"
    />

    <!-- Cancel Subscription Confirmation Modal -->
    <ConfirmModal
      v-model="showCancelModal"
      :title="$t('subscription.cancelTitle')"
      :message="$t('subscription.cancelMessage')"
      :confirm-text="$t('subscription.cancelConfirmText')"
      danger
      @confirm="confirmCancel"
    />

    <!-- Credit Purchase Modal -->
    <CreditPurchaseModal
      v-model="showCreditModal"
      @purchase="handleCreditPurchase"
    />

    <!-- Payment Modal -->
    <PaymentModal
      v-model="showPaymentModal"
      :target-plan="targetPlan ?? 'FREE'"
      :price="targetCheckoutPrice"
      :billing-cycle="billingCycle"
      :plan="targetPlanInfo"
      @confirm="handlePaymentConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { usePaymentAvailability } from '@/composables/usePaymentAvailability'
import { storeToRefs } from 'pinia'
import {
  CalendarIcon,
  ArrowUpTrayIcon,
  ArrowPathIcon,
  SparklesIcon,
  PlusIcon,
  ExclamationTriangleIcon,
  DocumentTextIcon,
} from '@heroicons/vue/24/outline'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import UsageProgressBar from '@/components/subscription/UsageProgressBar.vue'
import PlanComparisonTable from '@/components/subscription/PlanComparisonTable.vue'
import PaymentUnavailableNotice from '@/components/subscription/PaymentUnavailableNotice.vue'
import { paymentUnavailableCopy as paymentUnavailableCopyFor } from '@/components/subscription/paymentAvailabilityCopy'
import CreditPurchaseModal from '@/components/subscription/CreditPurchaseModal.vue'
import PaymentModal from '@/components/subscription/PaymentModal.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { useSubscriptionStore } from '@/stores/subscription'
import { useCreditStore } from '@/stores/credit'
import { useChannelStore } from '@/stores/channel'
import { useNotificationStore } from '@/stores/notification'
import { type PlanType } from '@/types/subscription'
import type { CreditPackage } from '@/types/credit'
import { subscriptionApi } from '@/api/subscription'
import { useLocale } from '@/composables/useLocale'
import { usePortOne } from '@/composables/usePortOne'
import { useAuthStore } from '@/stores/auth'

const subscriptionStore = useSubscriptionStore()
const creditStore = useCreditStore()
const channelStore = useChannelStore()
const notification = useNotificationStore()
const { t } = useLocale()
const { ensureInitialized: initPortOne } = usePortOne()
const authStore = useAuthStore()

const { subscription } = storeToRefs(subscriptionStore)
const { error: subscriptionError } = storeToRefs(subscriptionStore)
const { balance: creditBalance, transactions: creditTransactions } = storeToRefs(creditStore)
const { payments: paymentList } = storeToRefs(subscriptionStore)
const { channels } = storeToRefs(channelStore)

// Billing cycle
const billingCycle = ref<'MONTHLY' | 'YEARLY'>('MONTHLY')

// Modal states
const showChangePlanModal = ref(false)
const showCancelModal = ref(false)
const showCreditModal = ref(false)
const showPaymentModal = ref(false)
/** 결제 가능 여부는 서버가 정한다. 클라이언트 상수로 판단하지 않는다. */
const {
  paymentEnabled,
  paymentDisabledReason,
  paymentChecking,
  paymentCheckFailed,
  recheckPaymentAvailability,
} = usePaymentAvailability()
/** 잠긴 버튼의 툴팁. 옆의 안내와 **같은 문구를 공유한다.** */
const paymentUnavailableCopy = computed(
  () => paymentUnavailableCopyFor(paymentDisabledReason.value, paymentCheckFailed.value),
)
const targetPlan = ref<PlanType | null>(null)

/**
 * 서버가 잰 사용량. **측정 전과 실패는 `null`이다.**
 *
 * 예전에는 `{ uploadsThisMonth: 0, storageUsedMb: 0 }`으로 시작했다. 그러면 응답이
 * 오기 전과 요청이 실패한 뒤에 막대가 "0 / 50 GB, 0%"로 그려진다 — 아무것도 안 쓴
 * 사용자와 **완전히 같은 모양**이라 결제 페이지에서 업그레이드가 불필요하다고
 * 판단하게 만든다. 재지 못한 것은 0이 아니다.
 *
 * 타입은 API 응답에서 직접 끌어온다. 여기서 따로 선언하면 서버가 필드를 바꿔도
 * 화면만 옛 모양을 유지한 채 조용히 어긋난다.
 */
type UsageMeasurement = Awaited<ReturnType<typeof subscriptionApi.getUsage>>
const usageData = ref<UsageMeasurement | null>(null)
const usageLoading = ref(false)
const usageError = ref<string | null>(null)
const usageAlertsError = ref<string | null>(null)

// Usage Alerts
const usageAlerts = ref([
  { type: 'UPLOAD', label: '업로드 알림', description: '월간 업로드 횟수가 한도에 도달할 때 알림', enabled: false, thresholdPercent: 80 },
  { type: 'STORAGE', label: '저장공간 알림', description: '저장공간 사용량이 한도에 도달할 때 알림', enabled: false, thresholdPercent: 80 },
  { type: 'CREDIT', label: '크레딧 알림', description: 'AI 크레딧 잔여량이 부족할 때 알림', enabled: false, thresholdPercent: 80 },
])

// Connected platforms count derived from the channel store
const connectedPlatformCount = computed(() => channels.value.length)

// Computed
const storePlans = computed(() => subscriptionStore.plans)

const currentPlanInfo = computed(() => {
  if (!subscription.value) return null
  return storePlans.value.find((p) => p.type === subscription.value!.planType) ?? null
})

const pendingPlanInfo = computed(() => {
  if (!subscription.value?.pendingPlanType) return null
  return storePlans.value.find((p) => p.type === subscription.value!.pendingPlanType) ?? null
})

// 기존 구독의 실제 주기와 비교표·체크아웃 기본값을 맞춘다. 연간 구독자가 페이지를
// 열자마자 월간 가격을 보고 잘못된 상품을 선택하는 것을 막는다.
watch(() => subscription.value?.billingCycle, (cycle) => {
  if (cycle) billingCycle.value = cycle
}, { immediate: true })

const targetPlanInfo = computed(() => {
  if (!targetPlan.value) return null
  return storePlans.value.find((p) => p.type === targetPlan.value) ?? null
})

/** 결제 모달과 서버 intent가 같은 주기의 금액을 보여주게 한다. */
const targetCheckoutPrice = computed(() => {
  if (!targetPlanInfo.value) return 0
  return billingCycle.value === 'YEARLY' ? targetPlanInfo.value.yearlyPrice : targetPlanInfo.value.price
})

const creditPercentage = computed(() => {
  if (!creditBalance.value) return 0
  const total = creditBalance.value.freeMonthly + creditBalance.value.purchasedBalance
  if (total === 0) return 0
  return Math.min(100, Math.round((creditStore.totalBalance / total) * 100))
})

const subscriptionStatusClass = computed(() => {
  if (!subscription.value) return ''
  const classes: Record<string, string> = {
    ACTIVE: 'bg-success-subtle text-success-strong',
    FREE: 'bg-info-subtle text-info-strong',
    CANCELLED: 'bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300',
    PAST_DUE: 'bg-warning-subtle text-warning-strong',
    TRIALING: 'bg-info-subtle text-info-strong',
    PAUSED: 'bg-warning-subtle text-warning-strong',
  }
  return classes[subscription.value.status] ?? 'bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300'
})

const subscriptionStatusLabel = computed(() => {
  if (!subscription.value) return ''
  const labelKeys: Record<string, string> = {
    ACTIVE: 'subscription.statusActive',
    FREE: 'subscription.statusFree',
    CANCELLED: 'subscription.statusCancelled',
    PAST_DUE: 'subscription.statusPastDue',
    TRIALING: '트라이얼',
    PAUSED: '일시정지',
  }
  const key = labelKeys[subscription.value.status]
  return key ? t(key) : subscription.value.status
})

const changePlanMessage = computed(() => {
  if (!targetPlan.value) return ''
  const target = storePlans.value.find((p) => p.type === targetPlan.value)
  if (!target) return ''
  const current = currentPlanInfo.value
  if (!current) return t('subscription.changePlanSimple', { plan: target.name })

  const targetAmount = billingCycle.value === 'YEARLY' ? target.yearlyPrice : target.price
  const targetPeriod = billingCycle.value === 'YEARLY' ? '/년' : t('subscription.perMonth')
  if (target.price > current.price) {
    return t('subscription.upgradeMessage', {
      current: current.name,
      target: target.name,
      price: formatPrice(targetAmount),
      period: targetPeriod,
    })
  }
  const targetPrice = targetAmount === 0 ? t('subscription.free') : formatPrice(targetAmount) + targetPeriod
  return t('subscription.downgradeMessage', { current: current.name, target: target.name, price: targetPrice })
})

// Helpers
function formatPrice(amount: number): string {
  if (amount === 0) return t('subscription.free')
  return '\u20A9' + amount.toLocaleString()
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })
}

/**
 * 값이 없거나 날짜로 읽히지 않으면 "Invalid Date" 대신 `-` 를 보여준다.
 *
 * 서버는 `createdAt` 을 nullable 로 보낸다. 예전에는 없는 필드를 읽어 모든 행이
 * "Invalid Date" 였는데, 그 상태에서는 날짜가 비어 있는 것인지 값이 깨진 것인지
 * 구분할 수 없었다.
 */
function formatDateTime(dateStr: string | null | undefined): string {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }) + ' ' + date.toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatStorageUnit(mb: number): string {
  if (mb >= 1024) return 'GB'
  return 'MB'
}

function formatStorageValue(valueMb: number, maxMb: number): number {
  if (maxMb >= 1024) return Math.round((valueMb / 1024) * 10) / 10
  return valueMb
}

/**
 * 저장공간 막대에 쓸 값. **한도는 서버가 준 것만 쓴다.**
 *
 * 예전에는 최대값을 `currentPlanInfo.storageMb`(플랜 상수표)에서 가져왔다. 그런데 실제
 * 한도는 `StorageQuotaUseCase.getEffectiveLimit`이 정하고, 관리자가 올려 준 사용자는
 * `subscription.storage_quota_limit_bytes`가 플랜 기본값보다 크다. 상수표를 쓰면 그
 * 사용자는 **에셋 화면에서는 200GB, 이 화면에서는 50GB**를 본다 — 같은 사용자에게
 * 두 개의 한도를 보여주는 셈이고, 결제 판단이 일어나는 화면이 틀린 쪽이었다.
 *
 * 아래 비교표(`PlanComparisonTable`)는 "이 플랜을 사면 무엇을 받는지"를 말하므로 오버라이드가
 * 아니라 상품 사양이 맞다. 그 사양도 이제 상수가 아니라 서버가 준 `storePlans` 다.
 *
 * 한도가 0 이하면 `null`이다. 0으로 나누면 `UsageProgressBar`가 100%를 그려서 멀쩡한
 * 사용자에게 "가득 찼다"고 말한다.
 */
const storageUsage = computed(() => {
  const usage = usageData.value
  if (!usage || usage.storageLimitBytes <= 0) return null
  const limitMb = usage.storageLimitBytes / (1024 * 1024)
  return {
    current: formatStorageValue(usage.storageUsedMb, limitMb),
    max: formatStorageValue(limitMb, limitMb),
    unit: formatStorageUnit(limitMb),
  }
})

function creditTransactionTypeClass(type: string): string {
  const classes: Record<string, string> = {
    DEDUCT: 'bg-error-subtle text-error-strong',
    CHARGE: 'bg-success-subtle text-success-strong',
    FREE_RESET: 'bg-info-subtle text-info-strong',
  }
  return classes[type] ?? 'bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300'
}

function creditTransactionTypeLabel(type: string): string {
  const labelKeys: Record<string, string> = {
    DEDUCT: 'subscription.creditTypeDeduct',
    CHARGE: 'subscription.creditTypeCharge',
    FREE_RESET: 'subscription.creditTypeFreeReset',
  }
  const key = labelKeys[type]
  return key ? t(key) : type
}

function paymentStatusClass(status: string): string {
  const classes: Record<string, string> = {
    COMPLETED: 'bg-success-subtle text-success-strong',
    FAILED: 'bg-error-subtle text-error-strong',
    REFUNDED: 'bg-warning-subtle text-warning-strong',
    // 성공도 실패도 아니다. PG 확인 전에는 어느 쪽으로도 단정하지 않는다.
    PENDING: 'bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300',
  }
  return classes[status] ?? 'bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300'
}

function paymentStatusLabel(status: string): string {
  const labelKeys: Record<string, string> = {
    COMPLETED: 'subscription.paymentCompleted',
    FAILED: 'subscription.paymentFailed',
    REFUNDED: 'subscription.paymentRefunded',
    /*
     * 체크아웃은 결제창을 열기 전에 PENDING 행을 만든다. 그래서 이 상태는
     * "결제 진행 중"일 수도, "사용자가 취소함"일 수도, "웹훅 대기 중"일 수도 있다.
     * 서버에는 셋을 구분할 근거가 없으므로 문구도 어느 쪽으로도 단정하지 않는다.
     * 예전에는 매핑이 없어 영문 원문 "PENDING" 이 그대로 노출됐다.
     */
    PENDING: 'subscription.paymentPending',
  }
  const key = labelKeys[status]
  return key ? t(key) : status
}

/** 미확정 결제가 목록에 있는가. 안내 문구를 보일지 판단하는 데만 쓴다. */
const hasPendingPayment = computed(
  () => paymentList.value?.content.some((p) => p.status === 'PENDING') ?? false,
)

/**
 * 결제 내역을 현재 페이지 그대로 다시 읽는다.
 *
 * 조회만 한다. PENDING 을 취소로 바꾸거나 PG 에 묻지 않는다 — 웹훅이 도착했다면
 * 서버 상태가 이미 바뀌어 있고, 아직이라면 다시 읽어도 PENDING 그대로인 것이 정직하다.
 */
async function refreshPayments() {
  await subscriptionStore.fetchPayments(paymentList.value?.page ?? 0, 20)
}

// Actions
function showPlanComparison() {
  const el = document.getElementById('plan-comparison')
  if (el) {
    el.scrollIntoView({ behavior: 'smooth' })
  }
}

function selectPlan(plan: PlanType) {
  targetPlan.value = plan
  const currentIdx = storePlans.value.findIndex((p) => p.type === subscription.value?.planType)
  const targetIdx = storePlans.value.findIndex((p) => p.type === plan)
  const target = storePlans.value.find((p) => p.type === plan)
  const isPlanUpgrade = targetIdx > currentIdx
  const blocksAnnualToMonthlyUpgrade = Boolean(
    subscription.value &&
      isPlanUpgrade &&
      subscription.value.billingCycle === 'YEARLY' &&
      billingCycle.value === 'MONTHLY',
  )
  if (blocksAnnualToMonthlyUpgrade) {
    targetPlan.value = null
    notification.warning('연간 구독의 업그레이드는 연간 결제 주기로 진행해 주세요.')
    return
  }
  const changesPaidBillingCycle = Boolean(
    subscription.value &&
      target &&
      target.price > 0 &&
      targetIdx >= currentIdx &&
      subscription.value.billingCycle === 'MONTHLY' &&
      billingCycle.value === 'YEARLY',
  )

  // 플랜 상향뿐 아니라 유료 플랜의 결제 주기 변경도 결제 모달을 거친다.
  if (isPlanUpgrade || changesPaidBillingCycle) {
    /*
     * 결제가 불가한 상태면 결제창을 열지 않는다. 열고 나서 실패를 보여주면 사용자는
     * 원인을 알 수 없고, 서버에는 아무도 정리하지 않는 대기 결제가 남는다.
     * FREE 전환·다운그레이드는 결제를 거치지 않으므로 아래 분기로 그대로 간다.
     */
    if (!paymentEnabled.value) return
    showPaymentModal.value = true
  } else {
    // 무료 전환·같은 결제 주기의 다운그레이드는 다음 기간 적용을 확인한다.
    showChangePlanModal.value = true
  }
}

async function confirmChangePlan() {
  if (!targetPlan.value) return
  try {
    await subscriptionStore.changePlan(targetPlan.value, billingCycle.value)
    notification.success(t('subscription.changePlanSuccess'))
    targetPlan.value = null
    await creditStore.fetchBalance()
  } catch (e: unknown) {
    notification.error(e instanceof Error ? e.message : t('subscription.changePlanError'))
  }
}

async function handlePaymentConfirm() {
  // 포트원 결제 완료를 서버에서 검증한 뒤 화면을 갱신한다.
  showPaymentModal.value = false
  targetPlan.value = null
  notification.success(t('subscription.upgradeSuccess'))
  // 결제 완료 직후 데이터 리페치
  setTimeout(async () => {
    await Promise.all([
      subscriptionStore.fetchSubscription(),
      creditStore.fetchBalance(),
      subscriptionStore.fetchPayments(0, 20),
      /*
       * **프로필도 함께 읽는다.**
       *
       * 결제가 끝나면 서버는 구독과 `users.plan_type` 을 함께 올리는데, 이 화면은 결제
       * 전에 로드된 세션을 들고 있어 `authStore.user.planType` 이 아직 이전 플랜이다.
       * 구독 스토어만 갱신하면 이 화면은 맞아 보이지만 **상단바는 세션 내내 옛 플랜**을
       * 보여준다 — 방금 결제한 사용자에게 "반영이 안 됐다" 는 신호가 된다.
       *
       * `fetchProfile` 은 내부에서 실패를 삼키므로 결제 결과에 영향을 주지 않는다.
       * 같은 이유로 `OnboardingView.handlePlanPaymentSuccess` 가 이미 이렇게 한다.
       */
      authStore.fetchProfile(),
    ])
  }, 1500)
}

async function confirmCancel() {
  try {
    await subscriptionStore.cancelSubscription()
    notification.success(t('subscription.cancelSuccess'))
  } catch (e: unknown) {
    notification.error(e instanceof Error ? e.message : t('subscription.cancelError'))
  }
}

async function handleCreditPurchase(_pkg: CreditPackage) {
  notification.success(t('subscription.creditChargeSuccess'))
  /*
   * 지연 없이 곧바로 다시 읽는다.
   *
   * 이 핸들러는 서버가 `complete` 로 결제를 검증하고 크레딧을 지급한 **뒤에** 불린다.
   * 기다릴 비동기 작업이 남아 있지 않으므로 예전의 1.5 초 타이머는 오래된 잔액을 그만큼
   * 더 오래 보여주기만 했다.
   *
   * await 해서 실패가 이 함수 밖으로 드러나게 둔다. 리페치가 실패하면 스토어의
   * balanceError/transactionsError 가 화면에 "다시 시도" 버튼을 띄운다.
   */
  await Promise.all([
    creditStore.fetchBalance(),
    creditStore.fetchTransactions(0, 20),
    subscriptionStore.fetchPayments(0, 20),
  ])
}

async function handleStartTrial() {
  try {
    await subscriptionStore.startTrial('STARTER')
    notification.success('트라이얼이 시작되었습니다')
  } catch (e: unknown) {
    notification.error(e instanceof Error ? e.message : '트라이얼 시작에 실패했습니다')
  }
}

async function handlePause() {
  try {
    await subscriptionStore.pauseSubscription()
    notification.success('구독이 일시정지되었습니다')
  } catch (e: unknown) {
    notification.error(e instanceof Error ? e.message : '구독 일시정지에 실패했습니다')
  }
}

async function handleResume() {
  try {
    await subscriptionStore.resumeSubscription()
    notification.success('구독이 재개되었습니다')
  } catch (e: unknown) {
    notification.error(e instanceof Error ? e.message : '구독 재개에 실패했습니다')
  }
}

async function fetchUsageAlerts() {
  usageAlertsError.value = null
  try {
    const alerts = await subscriptionApi.getUsageAlerts()
    for (const serverAlert of alerts) {
      const local = usageAlerts.value.find(a => a.type === serverAlert.alertType)
      if (local) {
        local.enabled = serverAlert.enabled
        local.thresholdPercent = serverAlert.thresholdPercent
      }
    }
  } catch (error) {
    usageAlertsError.value = error instanceof Error ? error.message : '사용량 알림 설정을 불러오지 못했습니다.'
  }
}

async function toggleAlert(alert: { type: string; enabled: boolean; thresholdPercent: number }) {
  const newEnabled = !alert.enabled
  try {
    await subscriptionApi.updateUsageAlert({
      alertType: alert.type,
      thresholdPercent: alert.thresholdPercent,
      enabled: newEnabled,
    })
    alert.enabled = newEnabled
  } catch {
    notification.error('알림 설정 변경에 실패했습니다')
  }
}

async function updateAlertThreshold(alert: { type: string; enabled: boolean; thresholdPercent: number }, value: number) {
  try {
    await subscriptionApi.updateUsageAlert({
      alertType: alert.type,
      thresholdPercent: value,
      enabled: alert.enabled,
    })
    alert.thresholdPercent = value
  } catch {
    notification.error('알림 설정 변경에 실패했습니다')
  }
}

function loadCreditTransactions(page: number) {
  creditStore.fetchTransactions(page, 20)
}

function loadPayments(page: number) {
  subscriptionStore.fetchPayments(page, 20)
}

// Load usage data from the API
async function fetchUsage() {
  usageLoading.value = true
  usageError.value = null
  try {
    // `?? 0`을 하지 않는다. 서버가 값을 주지 않았다면 그건 0이 아니라 모르는 것이다.
    usageData.value = await subscriptionApi.getUsage()
  } catch (error) {
    // 실패한 갱신이 직전 측정치를 "현재 값"으로 남겨 두게 하지 않는다.
    usageData.value = null
    usageError.value = error instanceof Error ? error.message : '사용량을 불러오지 못했습니다.'
  } finally {
    usageLoading.value = false
  }
}

// Init
onMounted(() => {
  Promise.all([
    subscriptionStore.fetchSubscription(),
    subscriptionStore.fetchPlans(),
    creditStore.fetchBalance(),
    creditStore.fetchTransactions(0, 20),
    subscriptionStore.fetchPayments(0, 20),
    channelStore.fetchChannels(),
    fetchUsage(),
    recheckPaymentAvailability(),
    fetchUsageAlerts(),
    initPortOne(),
  ])
})
</script>
