import request from '@/utils/request'

export interface ScheduleParams {
  workCenterId?: number
  planOrderIds?: number[]
  startDate?: string
  endDate?: string
  strategy?: string
}

export interface ScheduleQuery {
  workCenterId?: number
  status?: string
  startDate?: string
  endDate?: string
  page?: number
  pageSize?: number
}

export interface ScheduleMoveData {
  scheduleId: number
  newStartTime: string
  newWorkCenterId?: number
}

export interface InsertOrderData {
  planOrderId: number
  workCenterId: number
  startTime: string
  priority?: number
}

export interface LockScheduleData {
  scheduleIds: number[]
  locked: boolean
}

export interface GanttQuery {
  workCenterIds?: number[]
  startDate: string
  endDate: string
}

export function autoSchedule(params: ScheduleParams) {
  return request.post('/schedule/auto', params)
}

export function getScheduleList(params: ScheduleQuery) {
  return request.get('/schedule/list', { params })
}

export function getScheduleById(id: number | string) {
  return request.get(`/schedule/${id}`)
}

export function moveSchedule(data: ScheduleMoveData) {
  return request.put('/schedule/move', data)
}

export function insertOrder(data: InsertOrderData) {
  return request.post('/schedule/insert', data)
}

export function lockSchedules(data: LockScheduleData) {
  return request.put('/schedule/lock', data)
}

export function getGanttData(params: GanttQuery) {
  return request.get('/schedule/gantt', { params })
}

export function getSchedulesByContract(contractNo: string) {
  return request.get('/schedule/by-contract', { params: { contractNo } })
}
