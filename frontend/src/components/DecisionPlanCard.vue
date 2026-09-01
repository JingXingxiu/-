<script setup lang="ts">
import { computed, ref } from 'vue'

import type { DecisionPlan } from '@/types/api'
import { collapseIdenticalOrders, formatOrderNumbers } from '@/utils/orders'
import { formatMoney, unallocatedReasonText } from '@/utils/presentation'

const props = defineProps<{
  plan: DecisionPlan
  index: number
}>()

const emit = defineEmits<{
  (event: 'view-rules', platformCode: string): void
}>()

const groupedOrders = computed(() => collapseIdenticalOrders(props.plan.decision.orders))
const hasRepeatedOrders = computed(() => groupedOrders.value.some((group) => group.count > 1))
const selectedOrderKey = ref('')

function toggleOrderSelection(key: string): void {
  selectedOrderKey.value = selectedOrderKey.value === key ? '' : key
}
</script>

<template>
  <view class="plan-card" :class="{ 'plan-card--recommended': plan.kind === 'RECOMMENDED' }">
    <view class="plan-card__head">
      <view>
        <view class="plan-card__kicker-row">
          <text class="plan-card__number">方案 {{ index + 1 }}</text>
          <text v-if="plan.kind === 'RECOMMENDED'" class="recommended-tag">推荐</text>
        </view>
        <text class="plan-card__title">{{ plan.title }}</text>
      </view>
      <text class="solve-status" :class="`solve-status--${plan.decision.solveStatus.toLowerCase()}`">
        {{ plan.decision.solveStatus === 'OPTIMAL' ? '已证最优' : plan.decision.solveStatusMessage }}
      </text>
    </view>

    <text class="plan-card__description">{{ plan.description }}</text>

    <view class="stats-grid">
      <view class="stat stat--primary">
        <text class="stat__value">{{ plan.decision.sold }} / {{ plan.decision.input }}</text>
        <text class="stat__label">预计售出（本）</text>
      </view>
      <view class="stat">
        <text class="stat__value">{{ formatMoney(plan.decision.estimatedAmountCents) }}</text>
        <text class="stat__label">预估回收款</text>
      </view>
      <view class="stat">
        <text class="stat__value">{{ plan.decision.platformCount }}</text>
        <text class="stat__label">使用平台</text>
      </view>
      <view class="stat">
        <text class="stat__value">{{ plan.decision.orderCount }}</text>
        <text class="stat__label">需要订单</text>
      </view>
    </view>

    <view class="orders">
      <view
        v-for="group in groupedOrders"
        :key="group.key"
        class="order-stack"
        :class="{
          'order-stack--repeated': group.count > 1,
          'order-stack--selected': selectedOrderKey === group.key,
        }"
        @click="toggleOrderSelection(group.key)"
      >
        <view v-if="group.count > 1" class="order-stack__layer order-stack__layer--far" />
        <view v-if="group.count > 1" class="order-stack__layer order-stack__layer--near" />

        <view class="order-card">
          <view class="order-card__head">
            <view class="order-card__identity">
              <view class="order-card__platform-row">
                <text class="order-card__platform">{{ group.order.platformDisplayName }}</text>
                <text v-if="group.count > 1" class="repeat-badge">× {{ group.count }} 相同订单</text>
              </view>
              <text class="order-card__number">{{ formatOrderNumbers(group.orderNumbers) }}</text>
            </view>
            <view class="order-card__summary">
              <text>{{ group.count > 1 ? '每单 ' : '' }}{{ group.order.bookCount }} 本</text>
              <text>{{ formatMoney(group.order.estimatedAmountCents) }}</text>
            </view>
          </view>
          <view class="order-card__rule-row">
            <text class="order-card__rule">门槛：{{ group.order.ruleSummary }}</text>
            <button
              class="rule-button"
              :aria-label="`查看${group.order.platformDisplayName}的平台规则`"
              @click.stop="emit('view-rules', group.order.platformCode)"
            >查看规则</button>
          </view>

          <view class="line-list">
            <view v-for="line in group.order.lines" :key="line.isbn" class="line-item">
              <view class="line-item__book">
                <view class="line-item__title-row">
                  <text class="line-item__title">{{ line.title }}</text>
                  <text v-if="line.dataOrigin" class="line-origin" :class="`line-origin--${line.dataOrigin.toLowerCase()}`">
                    {{ line.dataOrigin === 'USER' ? '我的 CSV' : '系统' }}
                  </text>
                </view>
                <text class="line-item__isbn">{{ line.isbn }}</text>
              </view>
              <view class="line-item__amount">
                <text>× {{ line.quantity }}</text>
                <text>{{ formatMoney(line.lineAmountCents) }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view v-if="hasRepeatedOrders" class="repeat-risk">
      <text class="repeat-risk__title">重复订单可执行性未验证</text>
      <text class="repeat-risk__text">
        模型将折叠项视为多个分别满足门槛的独立订单；平台是否允许连续下单或合并上门尚未验证，请下单前确认。折叠只改变展示，上方订单数和预估回收款仍按完整方案统计。
      </text>
    </view>

    <view v-if="plan.decision.unallocated.length" class="unallocated-box">
      <text class="unallocated-box__title">未分配 {{ plan.decision.unsold }} 本</text>
      <view v-for="item in plan.decision.unallocated" :key="item.isbn" class="unallocated-item">
        <text class="unallocated-item__book">{{ item.title || item.isbn }} × {{ item.quantity }}</text>
        <text class="unallocated-item__reason">{{ unallocatedReasonText(item.reason) }}</text>
      </view>
    </view>

    <view v-if="plan.decision.dataWarnings.length" class="warning-line">
      部分平台报价数据不完整，结果只使用已确认接收的报价。
    </view>

    <text class="plan-card__meta">求解耗时 {{ plan.decision.durationMs }} ms · 数据集 {{ plan.decision.datasetVersion }}</text>
  </view>
</template>

<style scoped>
.plan-card {
  padding: 34rpx 30rpx;
  border: 1px solid #dce3dc;
  border-radius: 28rpx;
  background: #fff;
  box-shadow: 0 14rpx 40rpx rgba(39, 60, 48, 0.07);
}

.plan-card--recommended {
  border-color: #7eaa91;
  box-shadow: 0 18rpx 48rpx rgba(35, 107, 78, 0.12);
}

.plan-card__head,
.plan-card__kicker-row,
.order-card__head,
.line-item {
  display: flex;
  align-items: center;
}

.plan-card__head,
.order-card__head,
.line-item {
  justify-content: space-between;
}

.plan-card__head {
  align-items: flex-start;
}

.plan-card__kicker-row {
  margin-bottom: 9rpx;
}

.plan-card__number {
  color: #7a877f;
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.recommended-tag {
  margin-left: 12rpx;
  padding: 5rpx 13rpx;
  border-radius: 999rpx;
  background: #236b4e;
  color: #fff;
  font-size: 20rpx;
}

.plan-card__title {
  color: #17231c;
  font-size: 35rpx;
  font-weight: 800;
  line-height: 1.35;
}

.solve-status {
  flex: none;
  max-width: 210rpx;
  margin-left: 20rpx;
  padding: 7rpx 13rpx;
  border-radius: 999rpx;
  background: #e8f2eb;
  color: #29664c;
  font-size: 20rpx;
  line-height: 1.3;
  text-align: center;
}

.solve-status--unknown,
.solve-status--infeasible {
  background: #f6e8e5;
  color: #95453e;
}

.plan-card__description {
  display: block;
  margin-top: 16rpx;
  color: #657168;
  font-size: 25rpx;
  line-height: 1.65;
}

.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14rpx;
  margin-top: 26rpx;
}

.stat {
  padding: 22rpx;
  border-radius: 18rpx;
  background: #f4f6f2;
}

.stat--primary {
  background: #eaf4ed;
}

.stat__value,
.stat__label {
  display: block;
}

.stat__value {
  color: #1a3f2e;
  font-size: 31rpx;
  font-weight: 800;
}

.stat__label {
  margin-top: 5rpx;
  color: #6b786f;
  font-size: 21rpx;
}

.orders {
  margin-top: 30rpx;
}

.order-stack {
  position: relative;
  margin-top: 18rpx;
}

.order-stack:first-child {
  margin-top: 0;
}

.order-stack--repeated {
  padding-right: 12rpx;
  padding-bottom: 12rpx;
}

.order-stack__layer {
  position: absolute;
  border: 1px solid #e6c6c0;
  border-radius: 20rpx;
  background: #fff8f6;
}

.order-stack__layer--far {
  inset: 12rpx 0 0 12rpx;
}

.order-stack__layer--near {
  inset: 6rpx 6rpx 6rpx 6rpx;
}

.order-card {
  position: relative;
  z-index: 1;
  padding: 24rpx;
  border: 1px solid #e2e7e1;
  border-radius: 20rpx;
  background: #fcfdfb;
  transform: translateY(0) scale(1);
  transform-origin: center;
  transition: transform 180ms ease;
}

.order-stack--selected .order-card {
  border-color: #8fb29d;
  box-shadow: 0 12rpx 26rpx rgba(35, 107, 78, 0.12);
  transform: translateY(-2rpx) scale(1.01);
}

.order-card__identity {
  min-width: 0;
  padding-right: 16rpx;
}

.order-card__platform-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10rpx;
}

.order-card__platform {
  color: #21352a;
  font-size: 29rpx;
  font-weight: 750;
}

.order-card__number {
  display: block;
  margin-top: 7rpx;
  color: #819087;
  font-size: 22rpx;
}

.repeat-badge {
  padding: 5rpx 11rpx;
  border-radius: 999rpx;
  background: #fbe9e6;
  color: #a43f35;
  font-size: 20rpx;
  font-weight: 700;
  line-height: 1.3;
}

.order-card__summary {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  color: #245f47;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 1.45;
}

.order-card__rule-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 15rpx;
  padding: 13rpx 16rpx;
  border-radius: 12rpx;
  background: #f1f4ef;
}

.order-card__rule {
  display: block;
  min-width: 0;
  padding-right: 16rpx;
  color: #637068;
  font-size: 22rpx;
  line-height: 1.5;
}

.rule-button {
  flex: none;
  min-width: 118rpx;
  height: 54rpx;
  margin: 0;
  padding: 0 15rpx;
  border: 1px solid #b9c9be;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.72);
  color: #356b50;
  font-size: 20rpx;
  font-weight: 650;
  line-height: 52rpx;
}

@media (hover: hover) and (pointer: fine) {
  .order-stack--repeated:hover .order-card {
    box-shadow: 0 15rpx 34rpx rgba(38, 70, 51, 0.13);
    transform: translateY(-4rpx) scale(1.02);
  }
}

@media (prefers-reduced-motion: reduce) {
  .order-card {
    transition: none;
  }

  .order-stack--selected .order-card,
  .order-stack--repeated:hover .order-card {
    transform: none;
  }
}

.line-list {
  margin-top: 13rpx;
}

.line-item {
  min-height: 78rpx;
  border-bottom: 1px solid #ebeeea;
}

.line-item:last-child {
  border-bottom: none;
}

.line-item__book {
  min-width: 0;
  padding-right: 18rpx;
}

.line-item__title-row {
  display: flex;
  align-items: center;
  min-width: 0;
}

.line-item__title,
.line-item__isbn {
  display: block;
}

.line-item__title {
  min-width: 0;
  overflow: hidden;
  color: #334139;
  font-size: 24rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.line-origin {
  flex: none;
  margin-left: 8rpx;
  padding: 3rpx 8rpx;
  border-radius: 999rpx;
  font-size: 17rpx;
  white-space: nowrap;
}

.line-origin--user {
  background: #e8effb;
  color: #315f91;
}

.line-origin--system {
  background: #edf0ed;
  color: #667169;
}

.line-item__isbn {
  margin-top: 4rpx;
  color: #8a958e;
  font-size: 19rpx;
}

.line-item__amount {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: flex-end;
  color: #59675e;
  font-size: 22rpx;
  line-height: 1.45;
}

.unallocated-box {
  margin-top: 24rpx;
  padding: 22rpx;
  border-radius: 18rpx;
  background: #fbf1eb;
}

.unallocated-box__title {
  color: #8b4939;
  font-size: 25rpx;
  font-weight: 700;
}

.unallocated-item {
  margin-top: 16rpx;
}

.unallocated-item__book,
.unallocated-item__reason {
  display: block;
}

.unallocated-item__book {
  color: #5f4038;
  font-size: 23rpx;
}

.unallocated-item__reason {
  margin-top: 4rpx;
  color: #906b61;
  font-size: 21rpx;
  line-height: 1.5;
}

.warning-line {
  margin-top: 20rpx;
  padding: 17rpx;
  border-radius: 14rpx;
  background: #fbf2dd;
  color: #795d29;
  font-size: 22rpx;
  line-height: 1.55;
}

.repeat-risk {
  margin-top: 22rpx;
  padding: 20rpx;
  border: 1px solid #efc1ba;
  border-radius: 16rpx;
  background: #fff1ef;
}

.repeat-risk__title,
.repeat-risk__text {
  display: block;
}

.repeat-risk__title {
  color: #9e352e;
  font-size: 24rpx;
  font-weight: 800;
}

.repeat-risk__text {
  margin-top: 8rpx;
  color: #85463f;
  font-size: 22rpx;
  line-height: 1.65;
}

.plan-card__meta {
  display: block;
  margin-top: 22rpx;
  color: #939d96;
  font-size: 20rpx;
}
</style>
