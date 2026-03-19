import request from '@/utils/request'

export function getDemandList(params?: Record<string, unknown>) {
  return request.get('/demand/list', { params })
}

export function getDemandLines(demandId: string) {
  return request.get(`/demand/${demandId}/lines`)
}

export function createDemand(data: Record<string, unknown>) {
  return request.post('/demand', data)
}

export function updateDemand(id: string, data: Record<string, unknown>) {
  return request.put(`/demand/${id}`, data)
}

export function deleteDemand(id: string) {
  return request.delete(`/demand/${id}`)
}
