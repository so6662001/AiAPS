import request from '@/utils/request'

export function runMrp(data: Record<string, unknown>) {
  return request.post('/mrp/run', data)
}

export function getMrpRunStatus(runId: string) {
  return request.get(`/mrp/run/${runId}/status`)
}

export function getPlanOrders(params?: Record<string, unknown>) {
  return request.get('/mrp/plan-orders', { params })
}

export function getPurchaseSuggestions(params?: Record<string, unknown>) {
  return request.get('/mrp/purchase-suggestions', { params })
}

export function getMrpExceptions(params?: Record<string, unknown>) {
  return request.get('/mrp/exceptions', { params })
}

export function confirmPlanOrders(ids: string[]) {
  return request.post('/mrp/plan-orders/confirm', { ids })
}

export function cancelPlanOrders(ids: string[]) {
  return request.post('/mrp/plan-orders/cancel', { ids })
}

export function transferToSchedule(ids: string[]) {
  return request.post('/mrp/plan-orders/transfer-schedule', { ids })
}
