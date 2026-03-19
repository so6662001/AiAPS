import request from '@/utils/request'

export interface ReportForm {
  scheduleId: number
  workCenterId: number
  operatorName: string
  quantity: number
  qualifiedQty: number
  defectQty?: number
  startTime: string
  endTime: string
  remark?: string
}

export interface ReportQuery {
  scheduleId?: number
  workCenterId?: number
  operatorName?: string
  startDate?: string
  endDate?: string
  page?: number
  pageSize?: number
}

export function submitReport(data: ReportForm) {
  return request.post('/production/reports', data)
}

export function getReportList(params: ReportQuery) {
  return request.get('/production/reports', { params })
}

export function getReportsBySchedule(scheduleId: number | string) {
  return request.get(`/production/reports/by-schedule/${scheduleId}`)
}
