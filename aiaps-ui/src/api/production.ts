import request from '@/utils/request'

export function getReportList(params?: Record<string, unknown>) {
  return request.get('/production/reports', { params })
}

export function createReport(data: Record<string, unknown>) {
  return request.post('/production/reports', data)
}

export function getReportDetail(id: string) {
  return request.get(`/production/reports/${id}`)
}
