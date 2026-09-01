<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { computed, nextTick, onMounted, ref } from 'vue'
import DecisionPlanCard from '@/components/DecisionPlanCard.vue'
import InventoryBookCard from '@/components/InventoryBookCard.vue'
import {
  ApiError,
  loadUserDatasetSession,
  loadDemoCatalog,
  lookupOffers,
  requestDecisionOptions,
} from '@/services/api'
import { scanBookIsbn } from '@/services/scanner'
import type {
  CatalogPlatform,
  DataSelection,
  DecisionOptionsResponse,
  DemoCatalogResponse,
  Disclaimer,
  InventoryEntry,
  UserDatasetBook,
  UserDatasetSession,
} from '@/types/api'
import { parseIsbnInput } from '@/utils/isbn'
import { sourceKindText } from '@/utils/presentation'

const DEMO_ISBNS = [
  '9787020002207',
  '9787111544937',
  '9787508647357',
  '9787040396638',
  '9787040599008',
  '9787521766912',
]
const MAX_INVENTORY_QUANTITY = 100
const restoredDatasetSession = loadUserDatasetSession()

function selectionFromSession(session: UserDatasetSession): DataSelection {
  if (session.dataMode === 'SYSTEM_ONLY') {
    return { dataMode: 'SYSTEM_ONLY' }
  }
  return {
    dataMode: session.dataMode,
    ...(session.uploadId ? { uploadId: session.uploadId } : {}),
    ...(session.accessToken ? { accessToken: session.accessToken } : {}),
  }
}

const isbnInput = ref('')
const inventory = ref<InventoryEntry[]>([])
const lookupDisclaimers = ref<Disclaimer[]>([])
const sourceKind = ref<'OBSERVED' | 'SYNTHETIC' | 'MIXED' | null>(null)
const lookupBusy = ref(false)
const decisionBusy = ref(false)
const lookupError = ref('')
const decisionError = ref('')
const decisionOptions = ref<DecisionOptionsResponse | null>(null)
const catalog = ref<DemoCatalogResponse | null>(null)
const catalogBusy = ref(false)
const catalogError = ref('')
const ruleDialogOpen = ref(false)
const rulePlatformCode = ref('')
const inventoryRevision = ref(0)
const offersReady = ref(true)
const datasetSession = ref<UserDatasetSession>(restoredDatasetSession)
const dataSelection = ref<DataSelection>(selectionFromSession(restoredDatasetSession))

const knownInventory = computed(() => inventory.value.filter((item) => item.catalogStatus === 'FOUND'))
const totalCopies = computed(() => knownInventory.value.reduce((sum, item) => sum + item.quantity, 0))
const unknownCount = computed(() => inventory.value.length - knownInventory.value.length)
const interactionBusy = computed(() => lookupBusy.value || decisionBusy.value)
const canDecide = computed(() => totalCopies.value > 0 && offersReady.value && !interactionBusy.value)
const activeRule = computed<CatalogPlatform | null>(() => (
  catalog.value?.platforms.find((platform) => platform.platformCode === rulePlatformCode.value) ?? null
))
const datasetModeTitle = computed(() => {
  if (datasetSession.value.dataMode === 'USER_ONLY') {
    return '仅用我的报价'
  }
  if (datasetSession.value.dataMode === 'USER_OVERLAY') {
    return '我的报价优先'
  }
  return '系统演示数据'
})
const datasetModeDescription = computed(() => {
  if (datasetSession.value.dataMode === 'SYSTEM_ONLY') {
    return '可上传 CSV 报价，或切换数据混合方式'
  }
  const bookCount = datasetSession.value.books?.length ?? 0
  return `${bookCount} 种自定义书籍 · 私有保存 30 天`
})

function errorMessage(error: unknown): string {
  if (error instanceof ApiError || error instanceof Error) {
    return error.message
  }
  return '操作失败，请稍后重试'
}

function resetDecision(): void {
  inventoryRevision.value += 1
  decisionOptions.value = null
  decisionError.value = ''
}

function fillDemoIsbns(): void {
  if (interactionBusy.value) {
    return
  }
  isbnInput.value = DEMO_ISBNS.join('\n')
  lookupError.value = ''
}

async function addBooks(): Promise<void> {
  if (interactionBusy.value) {
    return
  }

  if (inventory.value.length && !offersReady.value) {
    await reloadInventoryOffers(dataSelection.value)
    if (!offersReady.value) {
      return
    }
  }

  lookupError.value = ''
  const parsed = parseIsbnInput(isbnInput.value)

  if (!parsed.counts.size && !parsed.invalidTokens.length) {
    lookupError.value = '请先输入至少一个 ISBN-13'
    return
  }
  if (parsed.invalidTokens.length) {
    lookupError.value = `以下内容不是有效的 ISBN-13：${parsed.invalidTokens.slice(0, 3).join('、')}`
    return
  }
  if ([...parsed.counts.values()].some((count) => count > MAX_INVENTORY_QUANTITY)) {
    lookupError.value = `同一个 ISBN 最多录入 ${MAX_INVENTORY_QUANTITY} 本`
    return
  }

  const requestedCopies = [...parsed.counts.values()].reduce((sum, count) => sum + count, 0)
  if (totalCopies.value + requestedCopies > MAX_INVENTORY_QUANTITY) {
    lookupError.value = `一次最多计算 ${MAX_INVENTORY_QUANTITY} 本；当前已有 ${totalCopies.value} 本，本次最多还能加入 ${MAX_INVENTORY_QUANTITY - totalCopies.value} 本`
    return
  }

  const newIsbns = [...parsed.counts.keys()].filter(
    (isbn) => !inventory.value.some((entry) => entry.isbn === isbn),
  )
  lookupBusy.value = true

  try {
    let lookedUpBooks = new Map<string, InventoryEntry>()
    if (newIsbns.length) {
      const response = await lookupOffers(newIsbns, dataSelection.value)
      lookupDisclaimers.value = response.disclaimers
      sourceKind.value = response.sourceKind
      lookedUpBooks = new Map(
        response.books.map((book) => [
          book.isbn,
          {
            ...book,
            quantity: Math.min(MAX_INVENTORY_QUANTITY, parsed.counts.get(book.isbn) ?? 1),
          },
        ]),
      )
    }

    parsed.counts.forEach((count, isbn) => {
      const existing = inventory.value.find((entry) => entry.isbn === isbn)
      if (existing) {
        if (existing.catalogStatus === 'FOUND') {
          existing.quantity = Math.min(MAX_INVENTORY_QUANTITY, existing.quantity + count)
        }
        return
      }
      const lookedUp = lookedUpBooks.get(isbn)
      if (lookedUp) {
        inventory.value.push(lookedUp)
      }
    })

    isbnInput.value = ''
    offersReady.value = true
    resetDecision()
  } catch (error) {
    lookupError.value = errorMessage(error)
  } finally {
    lookupBusy.value = false
  }
}

async function scanBook(): Promise<void> {
  if (interactionBusy.value) {
    return
  }
  lookupError.value = ''
  try {
    isbnInput.value = await scanBookIsbn()
    await addBooks()
  } catch (error) {
    lookupError.value = errorMessage(error)
  }
}

function changeQuantity(isbn: string, delta: number): void {
  if (interactionBusy.value) {
    return
  }
  const entry = inventory.value.find((item) => item.isbn === isbn)
  if (!entry || entry.catalogStatus !== 'FOUND') {
    return
  }
  if (delta > 0 && totalCopies.value >= MAX_INVENTORY_QUANTITY) {
    uni.showToast({ title: `一次最多计算 ${MAX_INVENTORY_QUANTITY} 本`, icon: 'none' })
    return
  }
  entry.quantity = Math.max(1, Math.min(MAX_INVENTORY_QUANTITY, entry.quantity + delta))
  resetDecision()
}

function removeBook(isbn: string): void {
  if (interactionBusy.value) {
    return
  }
  inventory.value = inventory.value.filter((item) => item.isbn !== isbn)
  if (!inventory.value.length) {
    offersReady.value = true
  }
  resetDecision()
}

function clearInventory(): void {
  if (interactionBusy.value) {
    return
  }
  uni.showModal({
    title: '清空书单？',
    content: '已录入的书籍和当前方案都会被清除。',
    confirmText: '确认清空',
    confirmColor: '#955b55',
    success: ({ confirm }) => {
      if (confirm) {
        inventory.value = []
        lookupDisclaimers.value = []
        sourceKind.value = null
        offersReady.value = true
        resetDecision()
      }
    },
  })
}

async function showPlatformRules(platformCode: string): Promise<void> {
  rulePlatformCode.value = platformCode
  ruleDialogOpen.value = true
  catalogError.value = ''
  if (catalog.value) {
    if (!activeRule.value) {
      catalogError.value = '当前数据版本没有找到这个平台的规则快照'
    }
    return
  }
  if (catalogBusy.value) {
    return
  }

  catalogBusy.value = true
  try {
    catalog.value = await loadDemoCatalog()
    if (!activeRule.value) {
      catalogError.value = '当前数据版本没有找到这个平台的规则快照'
    }
  } catch (error) {
    catalogError.value = errorMessage(error)
  } finally {
    catalogBusy.value = false
  }
}

function closeRuleDialog(): void {
  ruleDialogOpen.value = false
}

function retryPlatformRules(): void {
  catalog.value = null
  void showPlatformRules(rulePlatformCode.value)
}

function collectedAtText(value: string | null): string {
  if (!value) {
    return '现有快照未记录'
  }
  return value
}

async function calculateOptions(): Promise<void> {
  if (interactionBusy.value) {
    return
  }
  if (!knownInventory.value.length) {
    decisionError.value = '至少录入一本当前数据集已收录的书'
    return
  }

  decisionBusy.value = true
  decisionError.value = ''
  decisionOptions.value = null
  const requestRevision = inventoryRevision.value

  try {
    const response = await requestDecisionOptions(
      knownInventory.value.map(({ isbn, quantity }) => ({ isbn, quantity })),
      dataSelection.value,
    )
    if (requestRevision !== inventoryRevision.value) {
      return
    }
    decisionOptions.value = response
    await nextTick()
    uni.pageScrollTo({ selector: '#decision-results', duration: 350 })
  } catch (error) {
    decisionError.value = errorMessage(error)
  } finally {
    decisionBusy.value = false
  }
}

function sameSelection(left: DataSelection, right: DataSelection): boolean {
  return left.dataMode === right.dataMode
    && left.uploadId === right.uploadId
    && left.accessToken === right.accessToken
}

async function reloadInventoryOffers(selection: DataSelection): Promise<void> {
  if (!inventory.value.length || lookupBusy.value || decisionBusy.value) {
    return
  }
  lookupBusy.value = true
  lookupError.value = ''
  offersReady.value = false
  inventory.value = inventory.value.map((entry) => ({ ...entry, offers: [] }))
  lookupDisclaimers.value = []
  sourceKind.value = null
  resetDecision()
  try {
    const quantities = new Map(inventory.value.map((entry) => [entry.isbn, entry.quantity]))
    const fallbackTitles = new Map(inventory.value.map((entry) => [entry.isbn, entry.title]))
    const response = await lookupOffers(inventory.value.map((entry) => entry.isbn), selection)
    inventory.value = response.books.map((book) => ({
      ...book,
      title: book.title || fallbackTitles.get(book.isbn) || null,
      quantity: quantities.get(book.isbn) ?? 1,
    }))
    lookupDisclaimers.value = response.disclaimers
    sourceKind.value = response.sourceKind
    offersReady.value = true
    resetDecision()
  } catch (error) {
    lookupError.value = errorMessage(error)
  } finally {
    lookupBusy.value = false
  }
}

function openUserDataset(): void {
  if (interactionBusy.value) {
    return
  }
  uni.navigateTo({ url: '/pages/user-dataset/index' })
}

function boundedUploadedBooks(books: UserDatasetBook[]): UserDatasetBook[] {
  const bounded: UserDatasetBook[] = []
  let remaining = MAX_INVENTORY_QUANTITY
  for (const book of books) {
    if (remaining <= 0) {
      break
    }
    const quantity = Math.max(1, Math.min(remaining, book.quantity))
    bounded.push({ ...book, quantity })
    remaining -= quantity
  }
  return bounded
}

async function loadUploadedBooks(books: UserDatasetBook[], selection: DataSelection): Promise<void> {
  const boundedBooks = boundedUploadedBooks(books)
  if (!boundedBooks.length) {
    lookupError.value = 'CSV 中没有可参与计算的书籍'
    return
  }

  dataSelection.value = selection
  resetDecision()
  lookupBusy.value = true
  lookupError.value = ''
  offersReady.value = false
  try {
    const response = await lookupOffers(boundedBooks.map((book) => book.isbn), selection)
    const uploadedByIsbn = new Map(boundedBooks.map((book) => [book.isbn, book]))
    inventory.value = response.books.map((book) => ({
      ...book,
      title: book.title || uploadedByIsbn.get(book.isbn)?.title || null,
      quantity: uploadedByIsbn.get(book.isbn)?.quantity ?? 1,
    }))
    lookupDisclaimers.value = response.disclaimers
    sourceKind.value = response.sourceKind
    offersReady.value = true
    resetDecision()
  } catch (error) {
    lookupError.value = errorMessage(error)
  } finally {
    lookupBusy.value = false
  }
}

onMounted(() => {
  if (
    restoredDatasetSession.dataMode !== 'SYSTEM_ONLY'
    && restoredDatasetSession.books?.length
  ) {
    void loadUploadedBooks(restoredDatasetSession.books, dataSelection.value)
  }
})

onShow(() => {
  const latestSession = loadUserDatasetSession()
  datasetSession.value = latestSession
  const latestSelection = selectionFromSession(latestSession)
  if (sameSelection(dataSelection.value, latestSelection)) {
    return
  }

  dataSelection.value = latestSelection
  resetDecision()
  if (inventory.value.length) {
    void reloadInventoryOffers(latestSelection)
    return
  }
  if (latestSession.dataMode !== 'SYSTEM_ONLY' && latestSession.books?.length) {
    void loadUploadedBooks(latestSession.books, latestSelection)
  }
})
</script>

<template>
  <view class="page-shell">
    <view class="hero">
      <view class="hero__glow hero__glow--one" />
      <view class="hero__glow hero__glow--two" />
      <view class="hero__content">
        <view class="brand-line">
          <image class="brand-mark" src="/static/book-decision-logo.png" mode="aspectFit" />
          <text>二手书回收决策系统</text>
        </view>
        <text class="hero__title">先看谁收，{{ '\n' }}再决定怎么卖。</text>
        <text class="hero__copy">
          录入 ISBN 查看各平台演示报价；确认数量后，再计算满足订单门槛的组合方案。
        </text>
        <view class="hero__chips">
          <text>售出册数优先</text>
          <text>多平台约束</text>
          <text>可解释方案</text>
        </view>
      </view>
    </view>

    <view class="main-content">
      <view class="section-card entry-section">
        <view class="section-heading">
          <view>
            <text class="section-heading__step">01 · 录入书籍</text>
            <text class="section-heading__title">输入或粘贴 ISBN</text>
          </view>
          <text class="section-heading__hint">支持多行</text>
        </view>

        <textarea
          v-model="isbnInput"
          class="isbn-input"
          :disabled="interactionBusy"
          :maxlength="2000"
          auto-height
          placeholder="例如：9787111544937&#10;多个 ISBN 可换行或用逗号分隔"
          confirm-type="done"
        />

        <view class="input-actions">
          <button class="primary-button input-actions__primary" :disabled="interactionBusy" :loading="lookupBusy" @click="addBooks">
            {{ lookupBusy ? '查询报价中' : '查询并加入书单' }}
          </button>
          <!-- #ifdef MP-WEIXIN -->
          <button class="secondary-button scan-button" :disabled="interactionBusy" @click="scanBook">扫码</button>
          <!-- #endif -->
          <!-- #ifndef MP-WEIXIN -->
          <button class="secondary-button scan-button" disabled>小程序可扫码</button>
          <!-- #endif -->
        </view>

        <button class="demo-button" :disabled="interactionBusy" @click="fillDemoIsbns">填入 6 本演示书籍</button>

        <view v-if="lookupError" class="error-banner">
          <text class="error-banner__icon">!</text>
          <view class="error-banner__body">
            <text>{{ lookupError }}</text>
            <button
              v-if="inventory.length && !offersReady && !interactionBusy"
              class="retry-button"
              @click="reloadInventoryOffers(dataSelection)"
            >重新查询当前书单</button>
          </view>
        </view>
      </view>

      <view class="dataset-entry" :class="{ 'dataset-entry--disabled': interactionBusy }" @click="openUserDataset">
        <view class="dataset-entry__icon">表</view>
        <view class="dataset-entry__body">
          <text class="dataset-entry__eyebrow">自定义数据集</text>
          <text class="dataset-entry__title">{{ datasetModeTitle }}</text>
          <text class="dataset-entry__copy">{{ datasetModeDescription }}</text>
        </view>
        <text class="dataset-entry__arrow">›</text>
      </view>

      <view v-if="inventory.length" class="inventory-section">
        <view class="section-heading section-heading--outside">
          <view>
            <text class="section-heading__step">02 · 确认书单</text>
            <text class="section-heading__title">{{ inventory.length }} 种书，{{ totalCopies }} 本可计算</text>
          </view>
          <button class="clear-button" :disabled="interactionBusy" @click="clearInventory">清空</button>
        </view>

        <view v-if="unknownCount" class="info-banner">
          有 {{ unknownCount }} 个 ISBN 不在当前所选数据中，已保留展示但不会提交求解。
        </view>

        <view class="inventory-list">
          <InventoryBookCard
            v-for="entry in inventory"
            :key="entry.isbn"
            :entry="entry"
            :disabled="interactionBusy"
            @increase="changeQuantity(entry.isbn, 1)"
            @decrease="changeQuantity(entry.isbn, -1)"
            @remove="removeBook(entry.isbn)"
          />
        </view>

        <view v-if="lookupDisclaimers.length" class="data-notice">
          <view class="data-notice__head">
            <text>数据边界</text>
            <text v-if="sourceKind" class="data-notice__source">{{ sourceKindText(sourceKind) }}</text>
          </view>
          <view v-for="item in lookupDisclaimers" :key="item.code" class="data-notice__item">
            <text class="data-notice__dot">•</text>
            <text>{{ item.text }}</text>
          </view>
        </view>

        <view class="decision-action">
          <button class="primary-button decision-button" :disabled="!canDecide" :loading="decisionBusy" @click="calculateOptions">
            {{ decisionBusy ? '正在计算组合方案' : `生成售卖方案 · ${totalCopies} 本` }}
          </button>
          <text class="decision-action__hint">系统会自动处理复本限制和各平台订单门槛</text>
          <view v-if="decisionError" class="error-banner">
            <text class="error-banner__icon">!</text>
            <text>{{ decisionError }}</text>
          </view>
        </view>
      </view>

      <view v-else class="empty-state">
        <view class="empty-state__icon">
          <view class="empty-state__book empty-state__book--back" />
          <view class="empty-state__book empty-state__book--front" />
        </view>
        <text class="empty-state__title">书单还是空的</text>
        <text class="empty-state__copy">先录入一本书，平台报价会在这里逐项展示。</text>
      </view>

      <view v-if="decisionOptions" id="decision-results" class="results-section">
        <view class="results-heading">
          <text class="section-heading__step">03 · 比较方案</text>
          <text class="results-heading__title">这批书有 {{ decisionOptions.plans.length }} 种不同取舍</text>
          <text class="results-heading__copy">默认优先多卖书；其余方案用于比较金额、平台数量和操作成本。</text>
        </view>

        <view class="plan-list">
          <DecisionPlanCard
            v-for="(plan, index) in decisionOptions.plans"
            :key="plan.kind"
            :plan="plan"
            :index="index"
            @view-rules="showPlatformRules"
          />
        </view>

        <view v-if="decisionOptions.plans[0]?.decision.disclaimers.length" class="result-disclaimer">
          <text class="result-disclaimer__title">结果说明</text>
          <text
            v-for="item in decisionOptions.plans[0].decision.disclaimers"
            :key="item.code"
            class="result-disclaimer__text"
          >{{ item.text }}</text>
        </view>
      </view>

      <view class="footer-note">
        <text>作品演示 · 非实时交易建议</text>
        <text>本项目与相关平台无合作或授权关系；数据为人工采样的历史快照，不是实时官方报价，实际结果以平台为准。</text>
      </view>
    </view>

    <view v-if="ruleDialogOpen" class="rule-dialog-backdrop" @click="closeRuleDialog">
      <view class="rule-dialog" @click.stop>
        <view class="rule-dialog__head">
          <view>
            <text class="rule-dialog__eyebrow">PLATFORM RULE SNAPSHOT</text>
            <text class="rule-dialog__title">{{ activeRule?.platformDisplayName || '平台规则' }}</text>
          </view>
          <button class="rule-dialog__close" aria-label="关闭规则详情" @click="closeRuleDialog">×</button>
        </view>

        <view v-if="catalogBusy" class="rule-dialog__state">正在读取规则快照…</view>
        <view v-else-if="catalogError" class="rule-dialog__state rule-dialog__state--error">
          <text>{{ catalogError }}</text>
          <button class="rule-dialog__retry" @click="retryPlatformRules">重新加载</button>
        </view>
        <view v-else-if="activeRule" class="rule-detail-list">
          <view class="rule-detail">
            <text class="rule-detail__label">拒收条件</text>
            <text class="rule-detail__value">{{ activeRule.rejectionConditions || '现有快照未记录' }}</text>
          </view>
          <view class="rule-detail">
            <text class="rule-detail__label">重复书策略</text>
            <text class="rule-detail__value">{{ activeRule.repeatPolicyDescription || '现有快照未记录' }}</text>
          </view>
          <view class="rule-detail">
            <text class="rule-detail__label">起送 / 免上门费条件</text>
            <text class="rule-detail__value">{{ activeRule.ruleSummary }}</text>
          </view>
          <view class="rule-detail">
            <text class="rule-detail__label">采集时间</text>
            <text class="rule-detail__value">{{ collectedAtText(activeRule.collectedAt) }}</text>
          </view>
          <view class="rule-detail">
            <text class="rule-detail__label">来源</text>
            <text class="rule-detail__value">{{ activeRule.sourceDescription }}</text>
            <text v-if="activeRule.sourceReference" class="rule-detail__reference">{{ activeRule.sourceReference }}</text>
          </view>
          <view class="rule-detail">
            <text class="rule-detail__label">数据版本</text>
            <text class="rule-detail__value">{{ catalog?.datasetVersion }}</text>
          </view>
        </view>

        <text class="rule-dialog__notice">规则与报价均为历史快照，实际接收范围和下单条件请以平台当前页面为准。</text>
      </view>
    </view>
  </view>
</template>

<style scoped>
.page-shell {
  min-height: 100vh;
  background: #f4f5f0;
}

.hero {
  position: relative;
  overflow: hidden;
  padding: calc(env(safe-area-inset-top) + 34rpx) 34rpx 78rpx;
  background: #153e2e;
  color: #fff;
}

.hero::after {
  position: absolute;
  right: -80rpx;
  bottom: -150rpx;
  width: 430rpx;
  height: 250rpx;
  border: 1px solid rgba(255, 255, 255, 0.13);
  border-radius: 50%;
  content: '';
  transform: rotate(-12deg);
}

.hero__glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(5rpx);
  opacity: 0.45;
}

.hero__glow--one {
  top: -100rpx;
  right: -40rpx;
  width: 360rpx;
  height: 360rpx;
  background: radial-gradient(circle, rgba(152, 205, 169, 0.5), rgba(21, 62, 46, 0));
}

.hero__glow--two {
  bottom: -200rpx;
  left: -160rpx;
  width: 430rpx;
  height: 430rpx;
  background: radial-gradient(circle, rgba(221, 175, 92, 0.26), rgba(21, 62, 46, 0));
}

.hero__content {
  position: relative;
  z-index: 1;
  max-width: 1000rpx;
  margin: 0 auto;
}

.brand-line,
.hero__chips,
.section-heading,
.input-actions,
.data-notice__head,
.error-banner {
  display: flex;
  align-items: center;
}

.brand-line {
  color: #dceadf;
  font-size: 24rpx;
  font-weight: 650;
  letter-spacing: 0.08em;
}

.brand-mark {
  display: block;
  width: 54rpx;
  height: 54rpx;
  margin-right: 14rpx;
}

.hero__title {
  display: block;
  margin-top: 58rpx;
  color: #fffdf7;
  font-family: "Songti SC", "STSong", serif;
  font-size: 66rpx;
  font-weight: 700;
  letter-spacing: -0.025em;
  line-height: 1.18;
  white-space: pre-line;
}

.hero__copy {
  display: block;
  max-width: 690rpx;
  margin-top: 30rpx;
  color: #c8d9ce;
  font-size: 27rpx;
  line-height: 1.75;
}

.hero__chips {
  flex-wrap: wrap;
  margin-top: 35rpx;
}

.hero__chips text {
  margin: 10rpx 12rpx 0 0;
  padding: 10rpx 17rpx;
  border: 1px solid rgba(220, 234, 223, 0.22);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.07);
  color: #d9e8dd;
  font-size: 21rpx;
}

.main-content {
  position: relative;
  z-index: 2;
  width: calc(100% - 40rpx);
  max-width: 1000rpx;
  margin: -36rpx auto 0;
  padding-bottom: calc(env(safe-area-inset-bottom) + 50rpx);
}

.section-card {
  padding: 34rpx 30rpx;
  border: 1px solid #e0e5df;
  border-radius: 28rpx;
  background: #fff;
  box-shadow: 0 16rpx 45rpx rgba(30, 52, 39, 0.09);
}

.dataset-entry {
  display: flex;
  align-items: center;
  margin-top: 22rpx;
  padding: 24rpx 25rpx;
  border: 1px solid #dfe5de;
  border-radius: 22rpx;
  background: #fff;
  box-shadow: 0 10rpx 28rpx rgba(30, 52, 39, 0.055);
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.dataset-entry--disabled {
  opacity: 0.58;
}

.dataset-entry__icon {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 70rpx;
  height: 70rpx;
  border-radius: 19rpx;
  background: #eaf3ed;
  color: #276748;
  font-size: 26rpx;
  font-weight: 800;
}

.dataset-entry__body {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
}

.dataset-entry__eyebrow,
.dataset-entry__title,
.dataset-entry__copy {
  display: block;
}

.dataset-entry__eyebrow {
  color: #397456;
  font-size: 19rpx;
  font-weight: 750;
  letter-spacing: 0.06em;
}

.dataset-entry__title {
  margin-top: 4rpx;
  color: #223129;
  font-size: 26rpx;
  font-weight: 750;
}

.dataset-entry__copy {
  overflow: hidden;
  margin-top: 5rpx;
  color: #78837c;
  font-size: 20rpx;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dataset-entry__arrow {
  flex: none;
  margin-left: auto;
  padding-left: 18rpx;
  color: #73907e;
  font-size: 46rpx;
  font-weight: 300;
  line-height: 1;
}

@media (hover: hover) {
  .dataset-entry:not(.dataset-entry--disabled):hover {
    border-color: #9bb8a6;
    box-shadow: 0 14rpx 32rpx rgba(30, 52, 39, 0.09);
    transform: translateY(-2rpx);
  }
}

.section-heading {
  justify-content: space-between;
}

.section-heading--outside {
  padding: 0 8rpx;
}

.section-heading__step,
.section-heading__title {
  display: block;
}

.section-heading__step {
  color: #337355;
  font-size: 21rpx;
  font-weight: 750;
  letter-spacing: 0.08em;
}

.section-heading__title {
  margin-top: 6rpx;
  color: #1c2921;
  font-size: 34rpx;
  font-weight: 800;
}

.section-heading__hint {
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  background: #eff3ed;
  color: #728077;
  font-size: 21rpx;
}

.isbn-input {
  width: 100%;
  min-height: 178rpx;
  margin-top: 28rpx;
  padding: 24rpx;
  border: 1px solid #d8dfd8;
  border-radius: 20rpx;
  background: #f8faf7;
  color: #1d2922;
  font-size: 28rpx;
  line-height: 1.6;
}

.input-actions {
  margin-top: 20rpx;
}

.input-actions__primary {
  flex: 1;
}

.primary-button,
.secondary-button {
  height: 88rpx;
  border-radius: 18rpx;
  font-size: 27rpx;
  font-weight: 700;
  line-height: 88rpx;
}

.primary-button {
  background: #236b4e;
  color: #fff;
  box-shadow: 0 10rpx 22rpx rgba(35, 107, 78, 0.19);
}

.primary-button[disabled] {
  background: #aab8af;
  color: #edf0ee;
  opacity: 1;
  box-shadow: none;
}

.secondary-button {
  border: 1px solid #d5ded7;
  background: #f4f7f3;
  color: #2e664e;
}

.secondary-button[disabled] {
  color: #94a198;
  opacity: 1;
}

.scan-button {
  flex: none;
  width: 210rpx;
  margin-left: 16rpx;
  font-size: 24rpx;
}

.demo-button {
  display: inline-block;
  height: 62rpx;
  margin: 16rpx 0 0;
  padding: 0 20rpx;
  border: 1px solid #b9c9be;
  border-radius: 999rpx;
  background: #fff;
  color: #426d57;
  font-size: 23rpx;
  line-height: 60rpx;
}

.demo-button[disabled] {
  border-color: #d8ded9;
  color: #a1aaa4;
  opacity: 1;
}

.error-banner,
.info-banner {
  margin-top: 20rpx;
  padding: 19rpx 20rpx;
  border-radius: 16rpx;
  font-size: 23rpx;
  line-height: 1.55;
}

.error-banner {
  align-items: flex-start;
  background: #f9eae7;
  color: #933f38;
}

.error-banner__icon {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 32rpx;
  height: 32rpx;
  margin: 2rpx 13rpx 0 0;
  border-radius: 50%;
  background: #b6574e;
  color: #fff;
  font-size: 20rpx;
  font-weight: 800;
}

.error-banner__body {
  flex: 1;
  min-width: 0;
}

.error-banner__body > text {
  display: block;
}

.retry-button {
  display: inline-block;
  height: 54rpx;
  margin: 12rpx 0 0;
  padding: 0 18rpx;
  border: 1px solid rgba(147, 63, 56, 0.28);
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.45);
  color: #8a4039;
  font-size: 20rpx;
  line-height: 52rpx;
}

.info-banner {
  background: #f6efdc;
  color: #776030;
}

.inventory-section {
  margin-top: 58rpx;
}

.clear-button {
  flex: none;
  height: 58rpx;
  margin: 0;
  padding: 0 19rpx;
  border: 1px solid rgba(145, 92, 85, 0.38);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.52);
  color: #8e5f59;
  font-size: 21rpx;
  line-height: 56rpx;
}

.clear-button[disabled] {
  border-color: #d5d9d6;
  color: #aeb5b0;
  opacity: 1;
}

.inventory-list,
.plan-list {
  margin-top: 24rpx;
}

.inventory-list > view,
.plan-list > view {
  margin-top: 20rpx;
}

.inventory-list > view:first-child,
.plan-list > view:first-child {
  margin-top: 0;
}

.data-notice {
  margin-top: 22rpx;
  padding: 25rpx;
  border: 1px solid #e5dfcf;
  border-radius: 20rpx;
  background: #faf7ee;
}

.data-notice__head {
  justify-content: space-between;
  margin-bottom: 14rpx;
  color: #544c39;
  font-size: 25rpx;
  font-weight: 750;
}

.data-notice__source {
  color: #827558;
  font-size: 20rpx;
  font-weight: 500;
}

.data-notice__item {
  display: flex;
  margin-top: 8rpx;
  color: #766d57;
  font-size: 21rpx;
  line-height: 1.6;
}

.data-notice__dot {
  flex: none;
  margin-right: 10rpx;
}

.decision-action {
  margin-top: 26rpx;
  padding: 25rpx;
  border-radius: 24rpx;
  background: #eaf2ec;
}

.decision-button {
  width: 100%;
}

.decision-action__hint {
  display: block;
  margin-top: 15rpx;
  color: #64756a;
  font-size: 21rpx;
  line-height: 1.5;
  text-align: center;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 92rpx 30rpx 70rpx;
  text-align: center;
}

.empty-state__icon {
  position: relative;
  width: 116rpx;
  height: 92rpx;
}

.empty-state__book {
  position: absolute;
  width: 72rpx;
  height: 88rpx;
  border: 2px solid #90a298;
  border-radius: 8rpx 16rpx 16rpx 8rpx;
  background: #edf1eb;
}

.empty-state__book::after {
  position: absolute;
  top: 16rpx;
  left: 13rpx;
  width: 35rpx;
  height: 2px;
  background: #a8b7ae;
  box-shadow: 0 14rpx 0 #a8b7ae;
  content: '';
}

.empty-state__book--back {
  top: 2rpx;
  left: 8rpx;
  transform: rotate(-11deg);
}

.empty-state__book--front {
  top: 6rpx;
  right: 4rpx;
  background: #f7f8f5;
  transform: rotate(8deg);
}

.empty-state__title {
  margin-top: 27rpx;
  color: #3b4b41;
  font-size: 28rpx;
  font-weight: 700;
}

.empty-state__copy {
  margin-top: 10rpx;
  color: #869189;
  font-size: 23rpx;
  line-height: 1.6;
}

.results-section {
  margin-top: 70rpx;
  scroll-margin-top: 20rpx;
}

.results-heading {
  padding: 0 8rpx;
}

.results-heading__title,
.results-heading__copy {
  display: block;
}

.results-heading__title {
  margin-top: 8rpx;
  color: #17251c;
  font-size: 39rpx;
  font-weight: 850;
  line-height: 1.35;
}

.results-heading__copy {
  margin-top: 13rpx;
  color: #6d796f;
  font-size: 24rpx;
  line-height: 1.65;
}

.result-disclaimer {
  margin-top: 24rpx;
  padding: 26rpx;
  border-radius: 20rpx;
  background: #ebece7;
}

.result-disclaimer__title,
.result-disclaimer__text {
  display: block;
}

.result-disclaimer__title {
  color: #4e5851;
  font-size: 24rpx;
  font-weight: 700;
}

.result-disclaimer__text {
  margin-top: 10rpx;
  color: #737b75;
  font-size: 20rpx;
  line-height: 1.6;
}

.footer-note {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 56rpx;
  color: #98a19a;
  font-size: 20rpx;
  line-height: 1.7;
  text-align: center;
}

.footer-note text:last-child {
  max-width: 820rpx;
  margin-top: 5rpx;
}

.rule-dialog-backdrop {
  position: fixed;
  z-index: 30;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-top: env(safe-area-inset-top);
  background: rgba(13, 29, 20, 0.46);
}

.rule-dialog {
  box-sizing: border-box;
  width: 100%;
  max-height: calc(88vh - env(safe-area-inset-top));
  overflow-y: auto;
  padding: 30rpx 28rpx calc(28rpx + env(safe-area-inset-bottom));
  border-radius: 30rpx 30rpx 0 0;
  background: #fbfcf9;
  box-shadow: 0 -20rpx 55rpx rgba(12, 31, 20, 0.2);
}

.rule-dialog__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.rule-dialog__eyebrow,
.rule-dialog__title {
  display: block;
}

.rule-dialog__eyebrow {
  color: #4f8065;
  font-size: 18rpx;
  font-weight: 750;
  letter-spacing: 0.08em;
}

.rule-dialog__title {
  margin-top: 6rpx;
  color: #19271f;
  font-size: 34rpx;
  font-weight: 800;
}

.rule-dialog__close {
  flex: none;
  width: 62rpx;
  height: 62rpx;
  margin: 0 0 0 20rpx;
  padding: 0;
  border: 1px solid #d7ddd7;
  border-radius: 50%;
  background: #fff;
  color: #67736b;
  font-size: 38rpx;
  font-weight: 300;
  line-height: 58rpx;
}

.rule-dialog__state {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-top: 28rpx;
  padding: 28rpx;
  border-radius: 18rpx;
  background: #eef2ed;
  color: #617067;
  font-size: 23rpx;
  line-height: 1.6;
}

.rule-dialog__state--error {
  background: #f8ece9;
  color: #8c4942;
}

.rule-dialog__retry {
  height: 54rpx;
  margin: 15rpx 0 0;
  padding: 0 18rpx;
  border: 1px solid rgba(140, 73, 66, 0.3);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.65);
  color: inherit;
  font-size: 20rpx;
  line-height: 52rpx;
}

.rule-detail-list {
  margin-top: 24rpx;
  border-top: 1px solid #e3e7e2;
}

.rule-detail {
  padding: 20rpx 2rpx;
  border-bottom: 1px solid #e8ebe7;
}

.rule-detail__label,
.rule-detail__value,
.rule-detail__reference {
  display: block;
}

.rule-detail__label {
  color: #738078;
  font-size: 20rpx;
  font-weight: 650;
}

.rule-detail__value {
  margin-top: 7rpx;
  color: #27372e;
  font-size: 24rpx;
  line-height: 1.65;
}

.rule-detail__reference {
  overflow-wrap: anywhere;
  margin-top: 6rpx;
  color: #71877a;
  font-size: 20rpx;
  line-height: 1.55;
}

.rule-dialog__notice {
  display: block;
  margin-top: 20rpx;
  padding: 18rpx;
  border-radius: 15rpx;
  background: #f6f1e3;
  color: #776b4d;
  font-size: 20rpx;
  line-height: 1.6;
}

@media (min-width: 768px) {
  .hero {
    padding-top: 72rpx;
    padding-bottom: 100rpx;
  }

  .hero__title {
    font-size: 72rpx;
  }

  .main-content {
    margin-top: -48rpx;
  }

  .rule-dialog-backdrop {
    align-items: center;
    padding: 48rpx;
  }

  .rule-dialog {
    width: min(680px, 100%);
    max-height: min(760px, 88vh);
    padding: 34px;
    border-radius: 24px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .dataset-entry {
    transition: none;
  }

  .dataset-entry:not(.dataset-entry--disabled):hover {
    transform: none;
  }
}
</style>
