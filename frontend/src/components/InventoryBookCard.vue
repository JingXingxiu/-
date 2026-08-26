<script setup lang="ts">
import type { InventoryEntry } from '@/types/api'
import { formatMoney, offerStatusText } from '@/utils/presentation'

defineProps<{
  entry: InventoryEntry
  disabled?: boolean
}>()

defineEmits<{
  (event: 'increase'): void
  (event: 'decrease'): void
  (event: 'remove'): void
}>()
</script>

<template>
  <view class="book-card" :class="{ 'book-card--unknown': entry.catalogStatus !== 'FOUND' }">
    <view class="book-card__head">
      <view class="book-card__identity">
        <view class="book-card__title-row">
          <text class="book-card__title">{{ entry.title || '当前数据集未收录' }}</text>
          <text v-if="entry.catalogStatus !== 'FOUND'" class="catalog-tag">未收录</text>
        </view>
        <text class="book-card__isbn">ISBN {{ entry.isbn }}</text>
      </view>
      <button class="remove-button" :disabled="disabled" aria-label="移除这本书" @click="$emit('remove')">移除</button>
    </view>

    <view v-if="entry.catalogStatus === 'FOUND'" class="quantity-row">
      <text class="quantity-row__label">我有几本</text>
      <view class="quantity-stepper">
        <button
          class="quantity-stepper__button"
          :disabled="disabled || entry.quantity <= 1"
          aria-label="减少数量"
          @click="$emit('decrease')"
        >−</button>
        <text class="quantity-stepper__value">{{ entry.quantity }}</text>
        <button
          class="quantity-stepper__button"
          :disabled="disabled || entry.quantity >= 100"
          aria-label="增加数量"
          @click="$emit('increase')"
        >＋</button>
      </view>
    </view>

    <view v-if="entry.catalogStatus === 'FOUND'" class="offer-list">
      <view v-for="offer in entry.offers" :key="offer.platformCode" class="offer-row">
        <text class="offer-row__platform">{{ offer.platformDisplayName }}</text>
        <view class="offer-row__result">
          <text v-if="offer.dataOrigin" class="origin-tag" :class="`origin-tag--${offer.dataOrigin.toLowerCase()}`">
            {{ offer.dataOrigin === 'USER' ? '我的 CSV' : '系统' }}
          </text>
          <text class="status-pill" :class="`status-pill--${offer.status.toLowerCase()}`">
            {{ offerStatusText(offer.status) }}
          </text>
          <text v-if="offer.status === 'ACCEPTED'" class="offer-row__price">
            {{ formatMoney(offer.unitPriceCents) }} / 本
          </text>
        </view>
      </view>
    </view>

    <view v-else class="unknown-note">
      这不代表平台拒收，只表示演示数据集中没有这本书，暂不能参与方案计算。
    </view>
  </view>
</template>

<style scoped>
.book-card {
  padding: 30rpx;
  border: 1px solid #dfe4dc;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: 0 10rpx 32rpx rgba(39, 60, 48, 0.06);
}

.book-card--unknown {
  background: #faf9f4;
}

.book-card__head,
.book-card__title-row,
.quantity-row,
.offer-row,
.offer-row__result {
  display: flex;
  align-items: center;
}

.book-card__head,
.quantity-row,
.offer-row {
  justify-content: space-between;
}

.book-card__identity {
  min-width: 0;
  padding-right: 20rpx;
}

.book-card__title-row {
  flex-wrap: wrap;
}

.book-card__title {
  color: #18241d;
  font-size: 31rpx;
  font-weight: 700;
  line-height: 1.45;
}

.book-card__isbn {
  display: block;
  margin-top: 7rpx;
  color: #738078;
  font-size: 23rpx;
  letter-spacing: 0.02em;
}

.catalog-tag {
  margin-left: 12rpx;
  padding: 5rpx 12rpx;
  border-radius: 999rpx;
  background: #eee9dc;
  color: #786b50;
  font-size: 21rpx;
}

.remove-button {
  flex: none;
  padding: 8rpx 4rpx;
  background: transparent;
  color: #9b554e;
  font-size: 24rpx;
  line-height: 1;
}

.remove-button[disabled] {
  color: #b9bfbb;
  opacity: 1;
}

.quantity-row {
  margin-top: 26rpx;
  padding: 18rpx 20rpx;
  border-radius: 16rpx;
  background: #f3f6f2;
}

.quantity-row__label {
  color: #536159;
  font-size: 25rpx;
}

.quantity-stepper {
  display: flex;
  align-items: center;
  overflow: hidden;
  border: 1px solid #d5ddd6;
  border-radius: 14rpx;
  background: #fff;
}

.quantity-stepper__button {
  width: 66rpx;
  height: 54rpx;
  padding: 0;
  border-radius: 0;
  background: #fff;
  color: #236b4e;
  font-size: 32rpx;
  line-height: 54rpx;
}

.quantity-stepper__button[disabled] {
  color: #bdc5bf;
  opacity: 1;
}

.quantity-stepper__value {
  min-width: 56rpx;
  color: #1d2922;
  font-size: 27rpx;
  font-weight: 700;
  text-align: center;
}

.offer-list {
  margin-top: 24rpx;
  border-top: 1px solid #edf0eb;
}

.offer-row {
  min-height: 72rpx;
  border-bottom: 1px solid #edf0eb;
}

.offer-row:last-child {
  border-bottom: none;
}

.offer-row__platform {
  padding-right: 16rpx;
  color: #38463e;
  font-size: 25rpx;
}

.offer-row__result {
  flex: none;
}

.offer-row__price {
  margin-left: 14rpx;
  color: #1e5d45;
  font-size: 25rpx;
  font-weight: 700;
}

.origin-tag {
  margin-right: 10rpx;
  padding: 4rpx 9rpx;
  border-radius: 999rpx;
  font-size: 18rpx;
  white-space: nowrap;
}

.origin-tag--user {
  background: #e8effb;
  color: #315f91;
}

.origin-tag--system {
  background: #edf0ed;
  color: #667169;
}

.status-pill {
  padding: 5rpx 12rpx;
  border-radius: 999rpx;
  font-size: 21rpx;
}

.status-pill--accepted {
  background: #e6f3eb;
  color: #1f6a49;
}

.status-pill--rejected {
  background: #f7eae8;
  color: #9b3e37;
}

.status-pill--unknown {
  background: #eeeef0;
  color: #696b71;
}

.unknown-note {
  margin-top: 24rpx;
  padding: 20rpx;
  border-radius: 16rpx;
  background: #f1eee5;
  color: #72684f;
  font-size: 24rpx;
  line-height: 1.65;
}
</style>
