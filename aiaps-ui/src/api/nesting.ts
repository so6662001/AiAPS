import request from '@/utils/request'

export function getNestingList(params?: Record<string, unknown>) {
  return request.get('/nesting/list', { params })
}

export function getNestingDetail(id: string) {
  return request.get(`/nesting/${id}`)
}

export function createNesting(data: Record<string, unknown>) {
  return request.post('/nesting', data)
}

export function deleteNesting(id: string) {
  return request.delete(`/nesting/${id}`)
}
