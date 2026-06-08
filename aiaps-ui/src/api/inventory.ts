import request from '@/utils/request'

export function getStockList(params?: Record<string, unknown>) {
  return request.get('/inventory/stock', { params })
}

export function getStockBinds(stockId: string) {
  return request.get(`/inventory/stock/${stockId}/binds`)
}

export function getMaterialIssueList(params?: Record<string, unknown>) {
  return request.get('/inventory/issues', { params })
}

export function createMaterialIssue(data: Record<string, unknown>) {
  return request.post('/inventory/issues', data)
}

export function approveMaterialIssue(id: string) {
  return request.post(`/inventory/issues/${id}/approve`)
}

export function issueMaterial(id: string) {
  return request.post(`/inventory/issues/${id}/issue`)
}

export function deliverMaterial(id: string) {
  return request.post(`/inventory/issues/${id}/deliver`)
}
