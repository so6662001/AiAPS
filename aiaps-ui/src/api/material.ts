import request from '@/utils/request'

export function getMaterialList(params?: Record<string, unknown>) {
  return request.get('/base/materials', { params })
}

export function createMaterial(data: Record<string, unknown>) {
  return request.post('/base/materials', data)
}

export function updateMaterial(id: string, data: Record<string, unknown>) {
  return request.put(`/base/materials/${id}`, data)
}

export function deleteMaterial(id: string) {
  return request.delete(`/base/materials/${id}`)
}
