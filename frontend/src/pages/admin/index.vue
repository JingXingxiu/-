<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  listAdminCandidates,
  loadAdminCandidate,
  publishAdminCandidate,
  rejectAdminCandidate,
} from '@/services/adminApi'
import { ApiError } from '@/services/api'
import type {
  AdminCredentials,
  AdminDatasetCandidateDetails,
  AdminDatasetCandidateSummary,
  RepeatPolicy,
} from '@/types/admin'
import type { OfferStatus } from '@/types/api'

const VERSION_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$/

const loginUsername = ref('')
const loginPassword = ref('')
const credentials = ref<AdminCredentials | null>(null)
const candidates = ref<AdminDatasetCandidateSummary[]>([])
const selectedCandidate = ref<AdminDatasetCandidateDetails | null>(null)
const selectedUploadId = ref('')
const publishVersion = ref('')
const rejectReason = ref('')
const loginBusy = ref(false)
const listBusy = ref(false)
const detailBusy = ref(false)
const actionBusy = ref(false)
const loginError = ref('')
const pageError = ref('')
const successMessage = ref('')

const loggedIn = computed(() => credentials.value !== null)
const pendingCount = computed(() => candidates.value.length)
const selectedSummary = computed(() => (
  candidates.value.find((candidate) => candidate.uploadId === selectedUploadId.value) ?? null
))

function errorMessage(error: unknown): string {
  if (error instanceof ApiError || error instanceof Error) {
    return error.message
  }
  return '操作失败，请稍后重试'
}

function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && (error.statusCode === 401 || error.statusCode === 403)
}

function clearSelection(): void {
  selectedUploadId.value = ''
  selectedCandidate.value = null
  publishVersion.value = ''
  rejectReason.value = ''
}

function logout(showToast = true): void {
  credentials.value = null
  loginPassword.value = ''
  candidates.value = []
  clearSelection()
  pageError.value = ''
  successMessage.value = ''
  if (showToast) {
    uni.showToast({ title: '已退出管理端', icon: 'none' })
  }
}

async function login(): Promise<void> {
  const username = loginUsername.value.trim()
  const password = loginPassword.value
  if (!username || !password) {
    loginError.value = '请输入管理员账号和密码'
    return
  }

  loginBusy.value = true
  loginError.value = ''
  try {
    const attemptedCredentials = { username, password }
    const result = await listAdminCandidates(attemptedCredentials)
    credentials.value = attemptedCredentials
    candidates.value = result
    loginPassword.value = ''
    successMessage.value = '已连接管理端'
  } catch (error) {
    loginError.value = errorMessage(error)
  } finally {
    loginBusy.value = false
  }
}

async function refreshCandidates(): Promise<void> {
  if (!credentials.value || listBusy.value) {
    return
  }
  listBusy.value = true
  pageError.value = ''
  try {
    const result = await listAdminCandidates(credentials.value)
    candidates.value = result
    if (selectedUploadId.value && !result.some((item) => item.uploadId === selectedUploadId.value)) {
      clearSelection()
    }
  } catch (error) {
    if (isUnauthorized(error)) {
      logout(false)
      loginError.value = '登录已失效，请重新输入管理员凭据'
    } else {
      pageError.value = errorMessage(error)
    }
  } finally {
    listBusy.value = false
  }
}

function suggestedVersion(uploadId: string): string {
  const now = new Date()
  const date = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
  return `review-${date}-${uploadId.slice(0, 8)}`
}

async function selectCandidate(candidate: AdminDatasetCandidateSummary): Promise<void> {
  if (!credentials.value || detailBusy.value || actionBusy.value) {
    return
  }
  selectedUploadId.value = candidate.uploadId
  selectedCandidate.value = null
  detailBusy.value = true
  pageError.value = ''
  successMessage.value = ''
  try {
    selectedCandidate.value = await loadAdminCandidate(credentials.value, candidate.uploadId)
    publishVersion.value = suggestedVersion(candidate.uploadId)
    rejectReason.value = ''
  } catch (error) {
    if (isUnauthorized(error)) {
      logout(false)
      loginError.value = '登录已失效，请重新输入管理员凭据'
    } else {
      pageError.value = errorMessage(error)
    }
  } finally {
    detailBusy.value = false
  }
}

function confirmAction(title: string, content: string, confirmText: string, confirmColor: string): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      content,
      confirmText,
      confirmColor,
      success: ({ confirm }) => resolve(confirm),
      fail: () => resolve(false),
    })
  })
}

async function publishCandidate(): Promise<void> {
  if (!credentials.value || !selectedCandidate.value || actionBusy.value) {
    return
  }
  const version = publishVersion.value.trim()
  if (!VERSION_PATTERN.test(version)) {
    pageError.value = '版本名需为 3～64 位，只能使用字母、数字、点、下划线和连字符，且必须以字母或数字开头'
    return
  }
  const confirmed = await confirmAction(
    '发布新数据版本？',
    `将候选数据叠加到 ${selectedCandidate.value.baseDatasetVersion}，生成不可变版本 ${version}。此操作不能在线修改或覆盖。`,
    '确认发布',
    '#236b4e',
  )
  if (!confirmed) {
    return
  }

  actionBusy.value = true
  pageError.value = ''
  successMessage.value = ''
  try {
    const result = await publishAdminCandidate(
      credentials.value,
      selectedCandidate.value.uploadId,
      version,
    )
    successMessage.value = `已发布新数据版本：${result.datasetVersion}`
    clearSelection()
    await refreshCandidates()
  } catch (error) {
    pageError.value = errorMessage(error)
  } finally {
    actionBusy.value = false
  }
}

async function rejectCandidate(): Promise<void> {
  if (!credentials.value || !selectedCandidate.value || actionBusy.value) {
    return
  }
  const reason = rejectReason.value.trim()
  if (!reason) {
    pageError.value = '请填写拒绝原因'
    return
  }
  if (reason.length > 500) {
    pageError.value = '拒绝原因不能超过 500 个字符'
    return
  }
  const confirmed = await confirmAction(
    '拒绝这个候选？',
    '拒绝后不会生成公共数据版本，该候选也不能再次处理。',
    '确认拒绝',
    '#a34b43',
  )
  if (!confirmed) {
    return
  }

  actionBusy.value = true
  pageError.value = ''
  successMessage.value = ''
  try {
    await rejectAdminCandidate(credentials.value, selectedCandidate.value.uploadId, reason)
    successMessage.value = '候选已拒绝，未创建任何公共数据版本'
    clearSelection()
    await refreshCandidates()
  } catch (error) {
    pageError.value = errorMessage(error)
  } finally {
    actionBusy.value = false
  }
}

function goToUserApp(): void {
  uni.reLaunch({ url: '/pages/index/index' })
}

function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function formatBytes(value: number): string {
  if (value < 1024) {
    return `${value} B`
  }
  return `${(value / 1024).toFixed(1)} KiB`
}

function formatMoney(cents: number, status: OfferStatus): string {
  return status === 'ACCEPTED' ? `¥${(cents / 100).toFixed(2)}` : '—'
}

function platformText(platformId: string): string {
  const labels: Record<string, string> = {
    'platform-a': '平台 A',
    'platform-b': '平台 B',
    'platform-c': '平台 C',
    'platform-d': '平台 D',
    'platform-e': '平台 E',
  }
  return labels[platformId] ?? platformId
}

function statusText(status: OfferStatus): string {
  return {
    ACCEPTED: '回收',
    REJECTED: '不回收',
    UNKNOWN: '未知',
  }[status]
}

function repeatPolicyText(policy: RepeatPolicy): string {
  return {
    UP_TO_INVENTORY: '按数量回收',
    ONE_PER_ORDER: '每单限一本',
    INHERIT_PLATFORM: '平台默认',
  }[policy]
}

function shortHash(value: string): string {
  return value.length > 18 ? `${value.slice(0, 10)}…${value.slice(-6)}` : value
}
</script>

<template>
  <view class="admin-page">
    <view class="admin-hero">
      <view class="admin-hero__inner">
        <view>
          <text class="admin-hero__eyebrow">DATA REVIEW CONSOLE</text>
          <text class="admin-hero__title">数据审核管理台</text>
          <text class="admin-hero__copy">查看用户明确授权的 CSV 候选，核对规范化报价后拒绝，或发布为新的不可变数据版本。</text>
        </view>
        <button class="user-app-link" @click="goToUserApp">返回用户端</button>
      </view>
    </view>

    <view v-if="!loggedIn" class="login-layout">
      <view class="login-intro">
        <text class="section-eyebrow">LOCAL ADMIN</text>
        <text class="login-intro__title">先连接受保护的管理 API</text>
        <text class="login-intro__copy">
          本页面使用 HTTP Basic 调用后端。账号和密码只保存在当前页面内存，刷新或关闭页面后需要重新输入，不会写入浏览器本地缓存。
        </text>
        <view class="boundary-list">
          <view class="boundary-item"><text class="boundary-item__number">01</text><text>普通上传不会自动公开</text></view>
          <view class="boundary-item"><text class="boundary-item__number">02</text><text>只显示未过期、待审核候选</text></view>
          <view class="boundary-item"><text class="boundary-item__number">03</text><text>发布只创建新版本，不覆盖旧版本</text></view>
        </view>
      </view>

      <view class="login-card">
        <text class="login-card__title">管理员登录</text>
        <text class="field-label">账号</text>
        <input
          v-model="loginUsername"
          class="form-input"
          maxlength="128"
          placeholder="ADMIN_USERNAME"
          confirm-type="next"
        />
        <text class="field-label">密码</text>
        <input
          v-model="loginPassword"
          class="form-input"
          type="text"
          password
          placeholder="ADMIN_PASSWORD"
          confirm-type="done"
          @confirm="login"
        />
        <text v-if="loginError" class="form-error">{{ loginError }}</text>
        <button class="primary-button login-button" :disabled="loginBusy" :loading="loginBusy" @click="login">
          {{ loginBusy ? '正在连接' : '登录并加载候选' }}
        </button>
        <text class="login-card__hint">公网部署时必须通过 HTTPS 访问，不能在明文 HTTP 上传输 Basic 凭据。</text>
      </view>
    </view>

    <view v-else class="dashboard-shell">
      <view class="dashboard-toolbar">
        <view>
          <text class="dashboard-toolbar__label">当前管理员</text>
          <text class="dashboard-toolbar__value">{{ credentials?.username }}</text>
        </view>
        <view class="dashboard-toolbar__summary">
          <text class="pending-number">{{ pendingCount }}</text>
          <text class="pending-label">个待审候选</text>
        </view>
        <view class="toolbar-actions">
          <button class="secondary-button" :disabled="listBusy || actionBusy" @click="refreshCandidates">
            {{ listBusy ? '刷新中' : '刷新列表' }}
          </button>
          <button class="ghost-button" :disabled="actionBusy" @click="logout()">退出</button>
        </view>
      </view>

      <view v-if="successMessage" class="notice notice--success">{{ successMessage }}</view>
      <view v-if="pageError" class="notice notice--error">{{ pageError }}</view>

      <view class="dashboard-grid">
        <view class="candidate-panel">
          <view class="panel-heading">
            <view>
              <text class="panel-heading__title">待审核队列</text>
              <text class="panel-heading__copy">按上传时间倒序，仅展示仍可处理的数据。</text>
            </view>
          </view>

          <view v-if="!candidates.length" class="empty-state empty-state--compact">
            <text class="empty-state__mark">✓</text>
            <text class="empty-state__title">队列是空的</text>
            <text class="empty-state__copy">让用户上传 CSV 时勾选“允许作为候选数据”，这里才会出现记录。</text>
          </view>

          <scroll-view v-else scroll-y class="candidate-list">
            <view
              v-for="candidate in candidates"
              :key="candidate.uploadId"
              class="candidate-card"
              :class="{ 'candidate-card--active': candidate.uploadId === selectedUploadId }"
              @click="selectCandidate(candidate)"
            >
              <view class="candidate-card__topline">
                <text class="candidate-card__filename">{{ candidate.originalFilename }}</text>
                <text class="status-pill">待审核</text>
              </view>
              <text class="candidate-card__meta">{{ candidate.isbnCount }} 种书 · {{ candidate.rowCount }} 行 · {{ formatBytes(candidate.byteSize) }}</text>
              <text class="candidate-card__time">上传 {{ formatDate(candidate.createdAt) }}</text>
              <view class="candidate-card__footer">
                <text>{{ candidate.schemaVersion }}</text>
                <text>到期 {{ formatDate(candidate.expiresAt) }}</text>
              </view>
            </view>
          </scroll-view>
        </view>

        <view class="detail-panel">
          <view v-if="detailBusy" class="empty-state">
            <text class="loading-ring" />
            <text class="empty-state__title">正在读取规范化数据</text>
          </view>

          <view v-else-if="!selectedCandidate" class="empty-state">
            <text class="empty-state__mark">⌁</text>
            <text class="empty-state__title">选择左侧候选开始审核</text>
            <text class="empty-state__copy">详情展示的是后端已校验并落库的数据，不会直接执行或重新解析原始 CSV。</text>
          </view>

          <template v-else>
            <view class="detail-heading">
              <view>
                <text class="section-eyebrow">CANDIDATE DETAIL</text>
                <text class="detail-heading__title">{{ selectedCandidate.originalFilename }}</text>
                <text class="detail-heading__id">{{ selectedCandidate.uploadId }}</text>
              </view>
              <text class="status-pill status-pill--large">{{ selectedCandidate.reviewStatus }}</text>
            </view>

            <view class="metadata-grid">
              <view class="metadata-item"><text>基础版本</text><strong>{{ selectedCandidate.baseDatasetVersion }}</strong></view>
              <view class="metadata-item"><text>数据格式</text><strong>{{ selectedCandidate.schemaVersion }}</strong></view>
              <view class="metadata-item"><text>书目 / 报价行</text><strong>{{ selectedCandidate.isbnCount }} / {{ selectedCandidate.rowCount }}</strong></view>
              <view class="metadata-item"><text>文件大小</text><strong>{{ formatBytes(selectedCandidate.byteSize) }}</strong></view>
              <view class="metadata-item"><text>授权时间</text><strong>{{ formatDate(selectedCandidate.consentAt) }}</strong></view>
              <view class="metadata-item"><text>失效时间</text><strong>{{ formatDate(selectedCandidate.expiresAt) }}</strong></view>
            </view>

            <view v-if="selectedSummary" class="hash-row">
              <text>文件 SHA-256</text>
              <code>{{ shortHash(selectedSummary.fileSha256) }}</code>
              <text>· {{ selectedSummary.consentTextVersion }}</text>
            </view>

            <view class="version-note">
              发布会创建可通过 datasetVersion 读取的新快照，但当前用户端仍固定使用 mixed-demo-v1，不会自动切换默认版本。发布后可先在 Swagger 中用新版本验证。
            </view>

            <view class="book-section">
              <view class="book-section__heading">
                <text class="panel-heading__title">规范化书目与报价</text>
                <text class="panel-heading__copy">价格单位为元；不回收或未知不会显示价格。</text>
              </view>

              <view v-for="book in selectedCandidate.books" :key="book.isbn" class="book-card">
                <view class="book-card__heading">
                  <view>
                    <text class="book-card__title">{{ book.title }}</text>
                    <text class="book-card__isbn">ISBN {{ book.isbn }}</text>
                  </view>
                  <text class="quantity-chip">数量 {{ book.quantity }}</text>
                </view>
                <scroll-view scroll-x class="offer-table-scroll">
                  <view class="offer-table">
                    <view class="offer-table__row offer-table__row--header">
                      <text>平台</text><text>状态</text><text>预估单价</text><text>重复书限制</text>
                    </view>
                    <view v-for="offer in book.offers" :key="offer.platformId" class="offer-table__row">
                      <text>{{ platformText(offer.platformId) }}</text>
                      <text class="offer-status" :class="`offer-status--${offer.status.toLowerCase()}`">{{ statusText(offer.status) }}</text>
                      <text>{{ formatMoney(offer.unitPriceCents, offer.status) }}</text>
                      <text>{{ repeatPolicyText(offer.repeatPolicy) }}</text>
                    </view>
                  </view>
                </scroll-view>
              </view>
            </view>

            <view class="review-actions">
              <view class="action-card action-card--publish">
                <text class="action-card__eyebrow">APPROVE</text>
                <text class="action-card__title">发布为新版本</text>
                <text class="action-card__copy">基于原版本复制快照，再以候选报价覆盖同键数据。版本名一经发布不可重复。</text>
                <text class="field-label">新 datasetVersion</text>
                <input v-model="publishVersion" class="form-input" maxlength="64" placeholder="review-20260825-abcd1234" />
                <button class="primary-button" :disabled="actionBusy" :loading="actionBusy" @click="publishCandidate">确认发布</button>
              </view>

              <view class="action-card action-card--reject">
                <text class="action-card__eyebrow">REJECT</text>
                <text class="action-card__title">拒绝候选</text>
                <text class="action-card__copy">拒绝原因会进入审核记录，但不会创建公共数据版本。</text>
                <text class="field-label">拒绝原因</text>
                <textarea v-model="rejectReason" class="reason-input" maxlength="500" placeholder="例如：书目信息与 ISBN 不一致" />
                <button class="danger-button" :disabled="actionBusy" :loading="actionBusy" @click="rejectCandidate">确认拒绝</button>
              </view>
            </view>
          </template>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.admin-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at 8% 8%, rgba(46, 112, 78, 0.08), transparent 28%),
    #f2f3ee;
  color: #1d2922;
}

.admin-hero {
  background: #153e2e;
  color: #fff;
}

.admin-hero__inner {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  width: calc(100% - 48px);
  max-width: 1440px;
  margin: 0 auto;
  padding: 42px 0 58px;
  gap: 32px;
}

.admin-hero__eyebrow,
.admin-hero__title,
.admin-hero__copy,
.section-eyebrow,
.login-intro__title,
.login-intro__copy,
.login-card__title,
.login-card__hint,
.field-label,
.form-error,
.dashboard-toolbar__label,
.dashboard-toolbar__value,
.pending-number,
.pending-label,
.panel-heading__title,
.panel-heading__copy,
.empty-state__mark,
.empty-state__title,
.empty-state__copy,
.candidate-card__filename,
.candidate-card__meta,
.candidate-card__time,
.detail-heading__title,
.detail-heading__id,
.book-card__title,
.book-card__isbn,
.action-card__eyebrow,
.action-card__title,
.action-card__copy {
  display: block;
}

.admin-hero__eyebrow,
.section-eyebrow,
.action-card__eyebrow {
  color: #a8cbb6;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.admin-hero__title {
  margin-top: 10px;
  font-size: clamp(34px, 4vw, 56px);
  font-weight: 850;
  letter-spacing: -0.03em;
  line-height: 1.12;
}

.admin-hero__copy {
  max-width: 760px;
  margin-top: 16px;
  color: #d4e3d8;
  font-size: 16px;
  line-height: 1.7;
}

.user-app-link,
.secondary-button,
.ghost-button {
  min-height: 42px;
  padding: 0 18px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 750;
  line-height: 42px;
}

.user-app-link {
  flex: 0 0 auto;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: rgba(255, 255, 255, 0.08);
  color: #f3f7f4;
}

.login-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(340px, 0.8fr);
  width: calc(100% - 48px);
  max-width: 1120px;
  margin: -28px auto 0;
  padding-bottom: 64px;
  gap: 24px;
}

.login-intro,
.login-card,
.dashboard-toolbar,
.candidate-panel,
.detail-panel {
  border: 1px solid #dedfd8;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18px 60px rgba(25, 51, 38, 0.08);
}

.login-intro,
.login-card {
  border-radius: 24px;
  padding: 36px;
}

.login-intro__title {
  margin-top: 12px;
  font-size: 30px;
  font-weight: 820;
  line-height: 1.25;
}

.login-intro__copy {
  max-width: 650px;
  margin-top: 16px;
  color: #68726b;
  font-size: 15px;
  line-height: 1.75;
}

.boundary-list {
  display: grid;
  margin-top: 32px;
  gap: 12px;
}

.boundary-item {
  display: flex;
  align-items: center;
  min-height: 48px;
  padding: 0 16px;
  border-radius: 14px;
  background: #f4f6f1;
  color: #3d4b43;
  font-size: 14px;
  font-weight: 650;
}

.boundary-item__number {
  width: 34px;
  color: #377758;
  font-size: 12px;
  font-weight: 850;
  letter-spacing: 0.08em;
}

.login-card__title {
  margin-bottom: 26px;
  font-size: 24px;
  font-weight: 820;
}

.field-label {
  margin: 18px 0 8px;
  color: #4f5b53;
  font-size: 13px;
  font-weight: 750;
}

.form-input,
.reason-input {
  width: 100%;
  border: 1px solid #d9ddd6;
  border-radius: 12px;
  background: #fbfcf9;
  color: #17231c;
  font-size: 15px;
}

.form-input {
  height: 48px;
  padding: 0 14px;
}

.reason-input {
  min-height: 100px;
  padding: 12px 14px;
  line-height: 1.55;
}

.form-input:focus,
.reason-input:focus {
  border-color: #4c8b69;
  box-shadow: 0 0 0 3px rgba(44, 112, 76, 0.1);
}

.form-error {
  margin-top: 14px;
  color: #a3443e;
  font-size: 13px;
  line-height: 1.5;
}

.primary-button,
.danger-button {
  min-height: 48px;
  border-radius: 12px;
  color: #fff;
  font-size: 15px;
  font-weight: 780;
  line-height: 48px;
}

.primary-button {
  background: #236b4e;
}

.danger-button {
  background: #a34b43;
}

.primary-button[disabled],
.danger-button[disabled],
.secondary-button[disabled],
.ghost-button[disabled] {
  opacity: 0.55;
}

.login-button {
  margin-top: 24px;
}

.login-card__hint {
  margin-top: 16px;
  color: #8a655f;
  font-size: 12px;
  line-height: 1.55;
}

.dashboard-shell {
  width: calc(100% - 40px);
  max-width: 1440px;
  margin: -26px auto 0;
  padding-bottom: 70px;
}

.dashboard-toolbar {
  display: flex;
  align-items: center;
  min-height: 92px;
  padding: 18px 22px;
  border-radius: 20px;
  gap: 28px;
}

.dashboard-toolbar__label {
  color: #7a847d;
  font-size: 12px;
}

.dashboard-toolbar__value {
  margin-top: 3px;
  font-size: 16px;
  font-weight: 760;
}

.dashboard-toolbar__summary {
  display: flex;
  align-items: baseline;
  margin-left: auto;
  gap: 7px;
}

.pending-number {
  color: #236b4e;
  font-size: 30px;
  font-weight: 850;
}

.pending-label {
  color: #69736c;
  font-size: 13px;
}

.toolbar-actions {
  display: flex;
  gap: 10px;
}

.secondary-button {
  border: 1px solid #cbd6ce;
  background: #f5f8f5;
  color: #285a43;
}

.ghost-button {
  border: 1px solid #e0e1dc;
  background: #fff;
  color: #6b746e;
}

.notice {
  margin-top: 14px;
  padding: 13px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
}

.notice--success {
  border: 1px solid #c9dfd1;
  background: #edf7f0;
  color: #276143;
}

.notice--error {
  border: 1px solid #ebcbc7;
  background: #fff1ef;
  color: #963e38;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  align-items: start;
  margin-top: 18px;
  gap: 18px;
}

.candidate-panel,
.detail-panel {
  border-radius: 20px;
}

.candidate-panel {
  position: sticky;
  top: 16px;
  overflow: hidden;
}

.panel-heading {
  padding: 22px;
  border-bottom: 1px solid #ebede8;
}

.panel-heading__title {
  font-size: 18px;
  font-weight: 810;
}

.panel-heading__copy {
  margin-top: 6px;
  color: #7a837d;
  font-size: 12px;
  line-height: 1.5;
}

.candidate-list {
  height: min(670px, calc(100vh - 220px));
  min-height: 420px;
  padding: 10px;
  box-sizing: border-box;
}

.candidate-card {
  margin-bottom: 9px;
  padding: 16px;
  border: 1px solid transparent;
  border-radius: 14px;
  background: #f6f7f3;
  cursor: pointer;
  transition: transform 160ms ease, border-color 160ms ease, box-shadow 160ms ease;
}

.candidate-card:hover {
  transform: translateY(-2px);
  border-color: #bdd3c4;
  box-shadow: 0 10px 22px rgba(33, 72, 51, 0.08);
}

.candidate-card--active {
  border-color: #4d8d69;
  background: #edf5ef;
}

.candidate-card__topline,
.candidate-card__footer,
.detail-heading,
.book-card__heading,
.book-section__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.candidate-card__filename {
  overflow: hidden;
  max-width: 210px;
  font-size: 14px;
  font-weight: 780;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-pill,
.quantity-chip {
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: 999px;
  background: #dcece1;
  color: #2b6648;
  font-size: 10px;
  font-weight: 800;
}

.status-pill--large {
  padding: 6px 10px;
  font-size: 11px;
}

.candidate-card__meta,
.candidate-card__time {
  margin-top: 9px;
  color: #69736c;
  font-size: 12px;
}

.candidate-card__time {
  margin-top: 4px;
}

.candidate-card__footer {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #e3e6e0;
  color: #879089;
  font-size: 10px;
}

.detail-panel {
  min-height: 670px;
  padding: 26px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 570px;
  padding: 30px;
  text-align: center;
}

.empty-state--compact {
  min-height: 320px;
}

.empty-state__mark {
  color: #8eac99;
  font-size: 48px;
  font-weight: 300;
}

.empty-state__title {
  margin-top: 14px;
  font-size: 19px;
  font-weight: 780;
}

.empty-state__copy {
  max-width: 480px;
  margin-top: 9px;
  color: #7a837d;
  font-size: 13px;
  line-height: 1.65;
}

.loading-ring {
  width: 32px;
  height: 32px;
  border: 3px solid #d9e5dd;
  border-top-color: #347654;
  border-radius: 50%;
  animation: rotate 700ms linear infinite;
}

@keyframes rotate {
  to { transform: rotate(360deg); }
}

.detail-heading {
  padding-bottom: 22px;
  border-bottom: 1px solid #e7e9e4;
}

.detail-heading__title {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 840;
  line-height: 1.25;
}

.detail-heading__id {
  margin-top: 7px;
  color: #7b857e;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
}

.metadata-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 20px;
  gap: 10px;
}

.metadata-item {
  padding: 13px 14px;
  border-radius: 12px;
  background: #f5f6f2;
}

.metadata-item text,
.metadata-item strong {
  display: block;
}

.metadata-item text {
  color: #7a837d;
  font-size: 10px;
}

.metadata-item strong {
  overflow: hidden;
  margin-top: 5px;
  font-size: 13px;
  font-weight: 760;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hash-row {
  display: flex;
  align-items: center;
  margin-top: 12px;
  padding: 10px 13px;
  border: 1px dashed #d8dcd5;
  border-radius: 10px;
  color: #788079;
  font-size: 11px;
  gap: 8px;
}

.hash-row code {
  color: #3d4c43;
  font-family: "SFMono-Regular", Consolas, monospace;
}

.version-note {
  margin-top: 12px;
  padding: 11px 13px;
  border-left: 3px solid #b6964d;
  border-radius: 8px;
  background: #faf6e9;
  color: #74643c;
  font-size: 12px;
  line-height: 1.65;
}

.book-section {
  margin-top: 28px;
}

.book-card {
  margin-top: 12px;
  padding: 17px;
  border: 1px solid #e1e4de;
  border-radius: 15px;
  background: #fff;
}

.book-card__title {
  font-size: 15px;
  font-weight: 780;
}

.book-card__isbn {
  margin-top: 4px;
  color: #7d8680;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
}

.quantity-chip {
  background: #f0eee2;
  color: #71663f;
}

.offer-table-scroll {
  margin-top: 14px;
}

.offer-table {
  min-width: 620px;
  overflow: hidden;
  border: 1px solid #e5e7e2;
  border-radius: 10px;
}

.offer-table__row {
  display: grid;
  grid-template-columns: 1fr 0.8fr 0.9fr 1.2fr;
  min-height: 38px;
  border-top: 1px solid #eceeea;
}

.offer-table__row:first-child {
  border-top: 0;
}

.offer-table__row text {
  display: flex;
  align-items: center;
  padding: 8px 11px;
  color: #445148;
  font-size: 12px;
}

.offer-table__row--header {
  background: #f3f5f1;
}

.offer-table__row--header text {
  color: #737d76;
  font-size: 10px;
  font-weight: 760;
}

.offer-status {
  font-weight: 780;
}

.offer-status--accepted { color: #2e724f !important; }
.offer-status--rejected { color: #9a4c45 !important; }
.offer-status--unknown { color: #8a7848 !important; }

.review-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 28px;
  gap: 14px;
}

.action-card {
  display: flex;
  flex-direction: column;
  padding: 20px;
  border: 1px solid #dfe3dc;
  border-radius: 16px;
  background: #f8faf7;
}

.action-card--reject {
  border-color: #ead9d6;
  background: #fcf8f7;
}

.action-card--reject .action-card__eyebrow {
  color: #a5635c;
}

.action-card__title {
  margin-top: 7px;
  font-size: 18px;
  font-weight: 810;
}

.action-card__copy {
  min-height: 62px;
  margin-top: 8px;
  color: #747e77;
  font-size: 12px;
  line-height: 1.6;
}

.action-card .primary-button,
.action-card .danger-button {
  margin-top: 16px;
}

@media (max-width: 980px) {
  .login-layout,
  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .candidate-panel {
    position: static;
  }

  .candidate-list {
    height: auto;
    min-height: 0;
    max-height: 430px;
  }
}

@media (max-width: 680px) {
  .admin-hero__inner {
    align-items: flex-start;
    width: calc(100% - 28px);
    padding: 28px 0 50px;
    flex-direction: column;
  }

  .login-layout,
  .dashboard-shell {
    width: calc(100% - 24px);
  }

  .login-layout {
    grid-template-columns: 1fr;
  }

  .login-intro,
  .login-card,
  .detail-panel {
    padding: 22px;
  }

  .dashboard-toolbar {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .dashboard-toolbar__summary {
    margin-left: 0;
  }

  .toolbar-actions {
    width: 100%;
  }

  .toolbar-actions button {
    flex: 1;
  }

  .metadata-grid,
  .review-actions {
    grid-template-columns: 1fr;
  }

  .hash-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .detail-heading {
    flex-direction: column;
  }
}
</style>
