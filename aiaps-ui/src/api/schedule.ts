import request from '@/utils/request'

export function getScheduleList(params?: Record<string, unknown>) {
  return request.get('/schedule/list', { params })
}

export function getScheduleGantt(params?: Record<string, unknown>) {
  return request.get('/schedule/gantt', { params })
}

export function getScheduleDetail(id: string) {
  return request.get(`/schedule/${id}`)
}

export function autoSchedule(data: Record<string, unknown>) {
  return request.post('/schedule/auto', data)
}

export function insertOrder(data: Record<string, unknown>) {
  return request.post('/schedule/insert', data)
}

export function lockSchedule(ids: string[]) {
  return request.post('/schedule/lock', { ids })
}

export function simulateSchedule(data: Record<string, unknown>) {
  return request.post('/schedule/simulate', data)
}
