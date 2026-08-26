import type { OfferStatus, SourceKind, UnallocatedReason } from '@/types/api'

export function formatMoney(cents: number | null | undefined): string {
  if (cents === null || cents === undefined) {
    return '—'
  }
  return `¥${(cents / 100).toFixed(2)}`
}

export function offerStatusText(status: OfferStatus): string {
  return {
    ACCEPTED: '可回收',
    REJECTED: '不回收',
    UNKNOWN: '暂无数据',
  }[status]
}

export function sourceKindText(sourceKind: SourceKind): string {
  return {
    OBSERVED: '人工观察数据',
    SYNTHETIC: '合成演示数据',
    MIXED: '观察与合成混合数据',
  }[sourceKind]
}

export function unallocatedReasonText(reason: UnallocatedReason): string {
  return {
    ISBN_NOT_IN_DATASET: 'ISBN 不在当前数据集中',
    NO_CONFIRMED_ACCEPTING_OFFER: '没有平台确认接收',
    UNALLOCATED_BY_ORDER_CONSTRAINTS: '受订单门槛或组合约束影响，未能分配',
  }[reason]
}
