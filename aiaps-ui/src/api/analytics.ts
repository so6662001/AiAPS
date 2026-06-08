import request from '@/utils/request'

export function getPageHeat(params: { dateFrom?: string; dateTo?: string }) {
  return request.get('/v1/analytics/page-heat', { params })
}

export function getFeatureValue(params: { dateFrom?: string; dateTo?: string }) {
  return request.get('/v1/analytics/feature-value', { params })
}

export function getUserActivity(params: { dateFrom?: string; dateTo?: string }) {
  return request.get('/v1/analytics/user-activity', { params })
}

export function getEfficiency(params: { dateFrom?: string; dateTo?: string }) {
  return request.get('/v1/analytics/efficiency', { params })
}

export function getPerformanceOverview() {
  return request.get('/v1/analytics/performance-overview')
}

export function getRecentAlerts(params: { limit?: number }) {
  return request.get('/v1/analytics/recent-alerts', { params })
}

export function getAlertRules() {
  return request.get('/v1/analytics/alert-rules')
}

export function createAlertRule(data: Record<string, any>) {
  return request.post('/v1/analytics/alert-rules', data)
}

export function updateAlertRule(id: number, data: Record<string, any>) {
  return request.put(`/v1/analytics/alert-rules/${id}`, data)
}
