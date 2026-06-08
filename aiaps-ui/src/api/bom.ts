import request from '@/utils/request'

export function getBomList(params?: Record<string, unknown>) {
  return request.get('/base/boms', { params })
}

export function getBomTree(bomId: string) {
  return request.get(`/base/boms/${bomId}/tree`)
}

export function createBom(data: Record<string, unknown>) {
  return request.post('/base/boms', data)
}

export function updateBom(id: string, data: Record<string, unknown>) {
  return request.put(`/base/boms/${id}`, data)
}

export function deleteBom(id: string) {
  return request.delete(`/base/boms/${id}`)
}
