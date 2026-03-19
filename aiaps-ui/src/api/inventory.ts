import request from '@/utils/request'

export interface StockQuery {
  materialCode?: string
  materialName?: string
  warehouse?: string
  page?: number
  pageSize?: number
}

export interface AvailableStockQuery {
  materialCode: string
  warehouse?: string
}

export interface MaterialIssueForm {
  scheduleId: number
  items: MaterialIssueItem[]
  remark?: string
}

export interface MaterialIssueItem {
  materialCode: string
  quantity: number
  warehouse?: string
  batchNo?: string
}

export interface MaterialIssueQuery {
  issueNo?: string
  status?: string
  startDate?: string
  endDate?: string
  page?: number
  pageSize?: number
}

export function getStockList(params: StockQuery) {
  return request.get('/inventory/stock', { params })
}

export function getAvailableStock(params: AvailableStockQuery) {
  return request.get('/inventory/stock/available', { params })
}

export function createMaterialIssue(data: MaterialIssueForm) {
  return request.post('/inventory/issue', data)
}

export function getMaterialIssueList(params: MaterialIssueQuery) {
  return request.get('/inventory/issue', { params })
}

export function approveIssue(id: number | string) {
  return request.put(`/inventory/issue/${id}/approve`)
}

export function executeIssue(id: number | string) {
  return request.put(`/inventory/issue/${id}/execute`)
}
