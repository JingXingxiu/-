<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import {
  clearUserDatasetSession,
  deleteUserDataset,
  loadUserDatasetSession,
  saveUserDatasetSession,
  uploadUserDataset,
  userDatasetDownloadUrl,
} from '@/services/api'
import type {
  DataMode,
  DataSelection,
  UserDatasetSession,
  UserDatasetUploadResponse,
} from '@/types/api'

declare const wx: {
  chooseMessageFile(options: {
    count: number
    type: 'file'
    extension: string[]
    success(result: { tempFiles: Array<{ path: string; name: string }> }): void
    fail(error: { errMsg: string }): void
  }): void
  shareFileMessage(options: {
    filePath: string
    fileName: string
    fail(error: { errMsg: string }): void
  }): void
}

const props = defineProps<{
  disabled?: boolean
}>()

const emit = defineEmits<{
  (event: 'selection-change', selection: DataSelection): void
  (event: 'uploaded', response: UserDatasetUploadResponse, selection: DataSelection): void
}>()

const MODES: Array<{ value: DataMode; title: string; description: string }> = [
  {
    value: 'SYSTEM_ONLY',
    title: '系统演示数据',
    description: '只使用系统演示数据，不读取我的 CSV。',
  },
  {
    value: 'USER_OVERLAY',
    title: '我的数据优先',
    description: 'CSV 中同平台同 ISBN 的记录优先，缺失项回退系统数据。',
  },
  {
    value: 'USER_ONLY',
    title: '仅用我的数据',
    description: '只采用 CSV 报价；没有填写的报价按未知处理。',
  },
]

const session = ref<UserDatasetSession>(loadUserDatasetSession())
const reuseConsent = ref(false)
const uploadBusy = ref(false)
const deleteBusy = ref(false)
const uploadError = ref('')
const chosenFileName = ref('')
const formatOpen = ref(false)

const hasUpload = computed(() => Boolean(session.value.uploadId && session.value.accessToken))
const expiryText = computed(() => {
  if (!session.value.expiresAt) {
    return ''
  }
  const date = new Date(session.value.expiresAt)
  if (Number.isNaN(date.getTime())) {
    return session.value.expiresAt
  }
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
})

function currentSelection(): DataSelection {
  if (session.value.dataMode === 'SYSTEM_ONLY') {
    return { dataMode: 'SYSTEM_ONLY' }
  }
  return {
    dataMode: session.value.dataMode,
    ...(session.value.uploadId ? { uploadId: session.value.uploadId } : {}),
    ...(session.value.accessToken ? { accessToken: session.value.accessToken } : {}),
  }
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'CSV 上传失败，请稍后重试'
}

function selectMode(mode: DataMode): void {
  if (props.disabled || uploadBusy.value || deleteBusy.value) {
    return
  }
  if (mode !== 'SYSTEM_ONLY' && !hasUpload.value) {
    uni.showToast({ title: '请先上传 CSV', icon: 'none' })
    return
  }
  if (session.value.dataMode === mode) {
    return
  }
  session.value = { ...session.value, dataMode: mode }
  saveUserDatasetSession(session.value)
  emit('selection-change', currentSelection())
}

function handleConsentChange(event: { detail: { value: string[] } }): void {
  reuseConsent.value = event.detail.value.includes('candidate')
}

function chooseCsvFile(): Promise<{ path: string; name: string }> {
  // #ifdef H5
  return new Promise((resolve, reject) => {
    uni.chooseFile({
      count: 1,
      type: 'all',
      extension: ['.csv'],
      success: (result) => {
        const paths = Array.isArray(result.tempFilePaths) ? result.tempFilePaths : [result.tempFilePaths]
        const files = Array.isArray(result.tempFiles) ? result.tempFiles : [result.tempFiles]
        const candidate = files[0] as (File & { path?: string }) | undefined
        resolve({
          path: candidate?.path || paths[0],
          name: candidate?.name || 'user-offers.csv',
        })
      },
      fail: (error) => reject(new Error(error.errMsg || '未能选择 CSV 文件')),
    })
  })
  // #endif

  // #ifdef MP-WEIXIN
  return new Promise((resolve, reject) => {
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      extension: ['csv'],
      success: (result) => {
        const candidate = result.tempFiles[0]
        resolve({ path: candidate.path, name: candidate.name })
      },
      fail: (error) => reject(new Error(error.errMsg || '未能选择 CSV 文件')),
    })
  })
  // #endif

}

async function chooseAndUpload(): Promise<void> {
  if (uploadBusy.value || deleteBusy.value) {
    return
  }
  uploadError.value = ''
  try {
    const chosen = await chooseCsvFile()
    if (!chosen.name.toLowerCase().endsWith('.csv')) {
      throw new Error('请选择 .csv 文件')
    }
    chosenFileName.value = chosen.name
    uploadBusy.value = true
    const response = await uploadUserDataset(chosen.path, reuseConsent.value)
    session.value = {
      dataMode: 'USER_OVERLAY',
      uploadId: response.uploadId,
      accessToken: response.accessToken,
      baseDatasetVersion: response.baseDatasetVersion,
      status: response.status,
      expiresAt: response.expiresAt,
      books: response.books,
    }
    saveUserDatasetSession(session.value)
    const selection = currentSelection()
    emit('uploaded', response, selection)
    uni.showToast({ title: 'CSV 已导入', icon: 'success' })
  } catch (error) {
    const message = errorMessage(error)
    if (!message.includes('cancel')) {
      uploadError.value = message
    }
  } finally {
    uploadBusy.value = false
  }
}

function deleteUpload(): void {
  const uploadId = session.value.uploadId
  const accessToken = session.value.accessToken
  if (!uploadId || !accessToken || deleteBusy.value) {
    return
  }
  uni.showModal({
    title: '立即删除上传？',
    content: '服务端原始文件和已解析数据都会删除，当前使用该 CSV 的方案也将失效。',
    confirmColor: '#9b554e',
    success: async ({ confirm }) => {
      if (!confirm) {
        return
      }
      deleteBusy.value = true
      uploadError.value = ''
      try {
        await deleteUserDataset(uploadId, accessToken)
        clearUserDatasetSession()
        session.value = { dataMode: 'SYSTEM_ONLY' }
        chosenFileName.value = ''
        emit('selection-change', currentSelection())
        uni.showToast({ title: '上传已删除', icon: 'success' })
      } catch (error) {
        uploadError.value = errorMessage(error)
      } finally {
        deleteBusy.value = false
      }
    },
  })
}

function downloadCsv(kind: 'template' | 'example'): void {
  const url = userDatasetDownloadUrl(kind)
  const fileName = kind === 'template' ? '二手书报价空白模板.csv' : '二手书报价填写示例.csv'

  // #ifdef H5
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  anchor.rel = 'noopener'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  return
  // #endif

  // #ifdef MP-WEIXIN
  uni.showLoading({ title: '下载中' })
  uni.downloadFile({
    url,
    success: (response) => {
      if (response.statusCode < 200 || response.statusCode >= 300) {
        uni.showToast({ title: '模板下载失败', icon: 'none' })
        return
      }
      wx.shareFileMessage({
        filePath: response.tempFilePath,
        fileName,
        fail: (error) => {
          if (!error.errMsg.includes('cancel')) {
            uni.showToast({ title: '未能保存模板', icon: 'none' })
          }
        },
      })
    },
    fail: () => uni.showToast({ title: '模板下载失败', icon: 'none' }),
    complete: () => uni.hideLoading(),
  })
  // #endif
}

onMounted(() => {
  emit('selection-change', currentSelection())
})
</script>

<template>
  <view class="dataset-card">
    <view class="dataset-card__head">
      <view>
        <text class="dataset-card__eyebrow">报价管理</text>
        <text class="dataset-card__title">上传我的 CSV 数据</text>
      </view>
      <text class="private-tag">私有上传</text>
    </view>

    <text class="dataset-card__copy">
      按固定模板填写 ISBN 与平台报价。订单门槛等平台规则仍由系统提供，原文件和解析数据默认保留 30 天。
    </text>

    <view class="template-actions">
      <button class="template-button" :disabled="disabled || uploadBusy || deleteBusy" @click="downloadCsv('template')">下载空白模板</button>
      <button class="template-button" :disabled="disabled || uploadBusy || deleteBusy" @click="downloadCsv('example')">下载填写示例</button>
    </view>

    <view class="format-note">
      <view class="format-note__toggle" @click="formatOpen = !formatOpen">
        <text>CSV 字段怎么填？</text>
        <text>{{ formatOpen ? '收起' : '展开' }}</text>
      </view>
      <view v-if="formatOpen" class="format-note__body">
        <text>列名固定为：数据格式版本、ISBN、书名、数量、平台、回收状态、单本价格（元）、重复书限制。</text>
        <text>数据格式版本统一填写“用户报价-v2”；平台填写“平台A”至“平台E”。</text>
        <text>回收状态填写“回收 / 不回收 / 未知”；不回收或未知时价格留空。</text>
        <text>重复书限制只选数字：1 = 可按数量回收，0 = 每个订单最多一本，留空 = 沿用平台默认。</text>
      </view>
    </view>

    <checkbox-group class="consent-row" @change="handleConsentChange">
      <label class="consent-label">
        <checkbox value="candidate" :checked="reuseConsent" color="#236b4e" />
        <text>允许作为候选数据供管理员复核（默认不选，不会自动公开）</text>
      </label>
    </checkbox-group>

    <button class="upload-button" :disabled="disabled || uploadBusy || deleteBusy" :loading="uploadBusy" @click="chooseAndUpload">
      {{ uploadBusy ? '正在校验并上传' : hasUpload ? '重新上传 CSV' : '选择并上传 CSV' }}
    </button>

    <view v-if="uploadError" class="upload-error">{{ uploadError }}</view>

    <view v-if="hasUpload" class="upload-status">
      <view>
        <text class="upload-status__title">CSV 已就绪</text>
        <text class="upload-status__meta">
          {{ chosenFileName || '已保存的上传' }} · {{ session.books?.length || 0 }} 种书 · {{ expiryText }} 到期
        </text>
      </view>
      <button class="forget-button" :disabled="disabled || uploadBusy || deleteBusy" :loading="deleteBusy" @click="deleteUpload">
        {{ deleteBusy ? '删除中' : '立即删除上传' }}
      </button>
    </view>

    <view class="mode-section">
      <text class="mode-section__label">本次计算使用</text>
      <view class="mode-list">
        <view
          v-for="mode in MODES"
          :key="mode.value"
          class="mode-option"
          :class="{
            'mode-option--active': session.dataMode === mode.value,
            'mode-option--disabled': mode.value !== 'SYSTEM_ONLY' && !hasUpload,
          }"
          @click="selectMode(mode.value)"
        >
          <view class="mode-option__radio"><view class="mode-option__dot" /></view>
          <view class="mode-option__text">
            <text class="mode-option__title">{{ mode.title }}</text>
            <text class="mode-option__description">{{ mode.description }}</text>
          </view>
        </view>
      </view>
    </view>

    <text class="privacy-note">访问凭据只保存在当前设备；公开复用必须另经管理员审核。</text>
  </view>
</template>

<style scoped>
.dataset-card {
  margin-top: 22rpx;
  padding: 30rpx;
  border: 1px solid #dfe5de;
  border-radius: 26rpx;
  background: #fff;
  box-shadow: 0 12rpx 34rpx rgba(30, 52, 39, 0.06);
}

.dataset-card__head,
.template-actions,
.upload-status,
.mode-option,
.consent-label {
  display: flex;
  align-items: center;
}

.dataset-card__head,
.upload-status {
  justify-content: space-between;
}

.dataset-card__eyebrow,
.dataset-card__title,
.dataset-card__copy,
.upload-status__title,
.upload-status__meta,
.mode-section__label,
.mode-option__title,
.mode-option__description,
.privacy-note {
  display: block;
}

.dataset-card__eyebrow {
  color: #337355;
  font-size: 20rpx;
  font-weight: 750;
  letter-spacing: 0.08em;
}

.dataset-card__title {
  margin-top: 6rpx;
  color: #1c2921;
  font-size: 31rpx;
  font-weight: 800;
}

.private-tag {
  flex: none;
  margin-left: 18rpx;
  padding: 7rpx 13rpx;
  border-radius: 999rpx;
  background: #edf4ef;
  color: #376b52;
  font-size: 19rpx;
}

.dataset-card__copy {
  margin-top: 19rpx;
  color: #69776e;
  font-size: 23rpx;
  line-height: 1.65;
}

.template-actions {
  gap: 14rpx;
  margin-top: 22rpx;
}

.template-button {
  flex: 1;
  height: 70rpx;
  margin: 0;
  border: 1px solid #d5ded7;
  border-radius: 15rpx;
  background: #f5f8f4;
  color: #32664d;
  font-size: 22rpx;
  line-height: 70rpx;
}

.format-note {
  margin-top: 15rpx;
  border-radius: 14rpx;
  background: #f7f8f5;
}

.format-note__toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 18rpx;
  color: #567062;
  font-size: 21rpx;
}

.format-note__body {
  padding: 0 18rpx 16rpx;
  color: #748078;
  font-size: 19rpx;
  line-height: 1.6;
}

.format-note__body text {
  display: block;
  margin-top: 5rpx;
}

.consent-row {
  margin-top: 20rpx;
}

.consent-label {
  align-items: flex-start;
  color: #66736b;
  font-size: 21rpx;
  line-height: 1.55;
}

.consent-label checkbox {
  flex: none;
  margin-top: 2rpx;
  margin-right: 10rpx;
  transform: scale(0.82);
  transform-origin: left top;
}

.upload-button {
  height: 80rpx;
  margin-top: 20rpx;
  border-radius: 17rpx;
  background: #236b4e;
  color: #fff;
  font-size: 25rpx;
  font-weight: 700;
  line-height: 80rpx;
}

.upload-button[disabled],
.template-button[disabled] {
  opacity: 0.55;
}

.upload-error {
  margin-top: 16rpx;
  padding: 16rpx 18rpx;
  border-radius: 14rpx;
  background: #f9eae7;
  color: #933f38;
  font-size: 22rpx;
  line-height: 1.55;
}

.upload-status {
  margin-top: 18rpx;
  padding: 18rpx;
  border-radius: 15rpx;
  background: #edf5ef;
}

.upload-status__title {
  color: #245c43;
  font-size: 24rpx;
  font-weight: 750;
}

.upload-status__meta {
  margin-top: 5rpx;
  color: #687c6f;
  font-size: 20rpx;
  line-height: 1.45;
}

.forget-button {
  flex: none;
  min-width: 154rpx;
  margin-left: 16rpx;
  padding: 10rpx 3rpx;
  background: transparent;
  color: #976057;
  font-size: 21rpx;
  line-height: 1;
}

.mode-section {
  margin-top: 26rpx;
  padding-top: 23rpx;
  border-top: 1px solid #ebeeea;
}

.mode-section__label {
  color: #4e5c53;
  font-size: 23rpx;
  font-weight: 700;
}

.mode-list {
  margin-top: 12rpx;
}

.mode-option {
  align-items: flex-start;
  margin-top: 10rpx;
  padding: 17rpx;
  border: 1px solid #e1e6e0;
  border-radius: 15rpx;
  background: #fafbf9;
  transition: border-color 0.16s ease, background 0.16s ease, transform 0.16s ease;
}

.mode-option--active {
  border-color: #73a187;
  background: #eef5f0;
}

.mode-option--disabled {
  opacity: 0.48;
}

.mode-option__radio {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 30rpx;
  height: 30rpx;
  margin-top: 2rpx;
  margin-right: 14rpx;
  border: 2px solid #a6b3aa;
  border-radius: 50%;
}

.mode-option--active .mode-option__radio {
  border-color: #236b4e;
}

.mode-option__dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: transparent;
}

.mode-option--active .mode-option__dot {
  background: #236b4e;
}

.mode-option__text {
  min-width: 0;
}

.mode-option__title {
  color: #27372d;
  font-size: 24rpx;
  font-weight: 700;
}

.mode-option__description {
  margin-top: 5rpx;
  color: #758178;
  font-size: 20rpx;
  line-height: 1.5;
}

.privacy-note {
  margin-top: 17rpx;
  color: #8a948d;
  font-size: 19rpx;
  line-height: 1.55;
}

@media (hover: hover) {
  .mode-option:not(.mode-option--disabled):hover {
    border-color: #8eb09c;
    transform: translateY(-2rpx);
  }
}
</style>
