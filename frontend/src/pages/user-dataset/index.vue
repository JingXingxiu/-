<script setup lang="ts">
import UserDatasetCard from '@/components/UserDatasetCard.vue'

function goBack(): void {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.reLaunch({ url: '/pages/index/index' })
}
</script>

<template>
  <view class="page-shell">
    <view class="page-header">
      <button class="back-button" @click="goBack">
        <text class="back-button__arrow">‹</text>
        <text>返回书单</text>
      </button>
      <text class="page-header__eyebrow">可选功能</text>
      <text class="page-header__title">自定义数据集</text>
      <text class="page-header__copy">
        上传自己整理的报价，并决定它与系统演示数据如何组合。设置保存后，返回书单即可继续查询和求解。
      </text>
    </view>

    <view class="page-content">
      <UserDatasetCard />

      <view class="usage-note">
        <text class="usage-note__title">为什么使用 CSV？</text>
        <text class="usage-note__copy">
          CSV 是纯文本表格，不依赖特定办公软件，文件更小，也能在 H5、微信小程序和 Java 后端之间稳定解析。请按模板填写，不要自行增删列名。
        </text>
      </view>

      <button class="done-button" @click="goBack">保存设置并返回书单</button>
    </view>
  </view>
</template>

<style scoped>
.page-shell {
  min-height: 100vh;
  background: #f4f5f0;
}

.page-header {
  padding: calc(env(safe-area-inset-top) + 24rpx) 34rpx 72rpx;
  background: #153e2e;
  color: #fff;
}

.back-button {
  display: inline-flex;
  align-items: center;
  height: 60rpx;
  margin: 0 0 40rpx;
  padding: 0 18rpx 0 10rpx;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #e4eee7;
  font-size: 22rpx;
  line-height: 60rpx;
}

.back-button::after {
  border: 0;
}

.back-button__arrow {
  margin-right: 7rpx;
  font-size: 38rpx;
  font-weight: 300;
  line-height: 1;
}

.page-header__eyebrow,
.page-header__title,
.page-header__copy,
.usage-note__title,
.usage-note__copy {
  display: block;
}

.page-header__eyebrow {
  color: #b9d4c3;
  font-size: 21rpx;
  font-weight: 750;
  letter-spacing: 0.1em;
}

.page-header__title {
  margin-top: 10rpx;
  font-size: 48rpx;
  font-weight: 850;
  line-height: 1.25;
}

.page-header__copy {
  max-width: 860rpx;
  margin-top: 18rpx;
  color: #d4e3d8;
  font-size: 23rpx;
  line-height: 1.65;
}

.page-content {
  position: relative;
  z-index: 2;
  width: calc(100% - 40rpx);
  max-width: 1000rpx;
  margin: -38rpx auto 0;
  padding-bottom: calc(env(safe-area-inset-bottom) + 54rpx);
}

.page-content :deep(.dataset-card) {
  margin-top: 0;
}

.usage-note {
  margin-top: 20rpx;
  padding: 25rpx 27rpx;
  border: 1px solid #e4e1d7;
  border-radius: 20rpx;
  background: #faf8f1;
}

.usage-note__title {
  color: #514c3d;
  font-size: 23rpx;
  font-weight: 750;
}

.usage-note__copy {
  margin-top: 9rpx;
  color: #766f5e;
  font-size: 21rpx;
  line-height: 1.65;
}

.done-button {
  height: 86rpx;
  margin-top: 24rpx;
  border-radius: 19rpx;
  background: #236b4e;
  color: #fff;
  font-size: 26rpx;
  font-weight: 750;
  line-height: 86rpx;
}

@media (min-width: 768px) {
  .page-header {
    padding-top: 54rpx;
    padding-bottom: 90rpx;
  }
}
</style>
