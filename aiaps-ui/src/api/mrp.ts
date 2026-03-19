import request from '@/utils/request'

export interface MrpRunParams {
  demandIds?: number[]
  planStartDate?: string
  planEndDate?: string
  strategy?: string
}

export interface PlanOrderQuery {
  orderNo?: string
  materialCode?: string
  status?: string
  page?: number
  pageSize?: number
}

export function startMrpRun(params: MrpRunParams) {
  return request.post('/mrp/run', params)
}

export function getMrpProgress(runId: number | string) {
  return request.get(`/mrp/run/${runId}/progress`)
}

export function cancelMrpRun(runId: number | string) {
  return request.post(`/mrp/run/${runId}/cancel`)
}

export function getPlanOrderList(params: PlanOrderQuery) {
  return request.get('/mrp/plan-orders', { params })
}

export function confirmPlanOrders(ids: number[]) {
  return request.post('/mrp/plan-orders/confirm', { ids })
}

export function cancelPlanOrders(ids: number[]) {
  return request.post('/mrp/plan-orders/cancel', { ids })
}
