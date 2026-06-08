import request from '@/utils/request'

export function getWorkCenterList(params?: Record<string, unknown>) {
  return request.get('/base/work-centers', { params })
}

export function createWorkCenter(data: Record<string, unknown>) {
  return request.post('/base/work-centers', data)
}

export function updateWorkCenter(id: string, data: Record<string, unknown>) {
  return request.put(`/base/work-centers/${id}`, data)
}

export function deleteWorkCenter(id: string) {
  return request.delete(`/base/work-centers/${id}`)
}
