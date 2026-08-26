import type { DecisionOrder } from '@/types/api'

export interface CollapsedOrderGroup {
  key: string
  order: DecisionOrder
  count: number
  orderNumbers: number[]
}

/**
 * 只合并除 orderNo 外所有展示与金额字段都相同的订单。
 * 这只是展示层折叠，不改变接口返回的真实订单、金额或统计值。
 */
export function collapseIdenticalOrders(orders: readonly DecisionOrder[]): CollapsedOrderGroup[] {
  const groups = new Map<string, CollapsedOrderGroup>()

  orders.forEach((order) => {
    const signature = orderSignature(order)
    const existing = groups.get(signature)
    if (existing) {
      existing.count += 1
      existing.orderNumbers.push(order.orderNo)
      return
    }

    groups.set(signature, {
      key: `${order.platformCode}-${order.orderNo}`,
      order,
      count: 1,
      orderNumbers: [order.orderNo],
    })
  })

  return [...groups.values()]
}

export function formatOrderNumbers(orderNumbers: readonly number[]): string {
  const sorted = [...orderNumbers].sort((left, right) => left - right)
  if (sorted.length === 1) {
    return `第 ${sorted[0]} 单`
  }

  const contiguous = sorted.every((number, index) => index === 0 || number === sorted[index - 1] + 1)
  if (contiguous) {
    return `第 ${sorted[0]}–${sorted[sorted.length - 1]} 单`
  }

  if (sorted.length <= 4) {
    return `第 ${sorted.join('、')} 单`
  }

  return `第 ${sorted[0]}、${sorted[1]}…${sorted[sorted.length - 1]} 单`
}

function orderSignature(order: DecisionOrder): string {
  const lines = [...order.lines]
    .sort((left, right) => left.isbn.localeCompare(right.isbn))
    .map((line) => ({
      isbn: line.isbn,
      title: line.title,
      quantity: line.quantity,
      unitPriceCents: line.unitPriceCents,
      lineAmountCents: line.lineAmountCents,
      currency: line.currency,
    }))

  return JSON.stringify({
    platformCode: order.platformCode,
    platformDisplayName: order.platformDisplayName,
    ruleSummary: order.ruleSummary,
    bookCount: order.bookCount,
    estimatedAmountCents: order.estimatedAmountCents,
    currency: order.currency,
    lines,
  })
}
