import request from '@/utils/request'

export function getDashboardStats() {
  return request.get('/dashboard/stats')
}

export function getProductionLineStatus() {
  return request.get('/dashboard/production-lines')
}

export function getUrgentItems() {
  return request.get('/dashboard/urgent-items')
}
