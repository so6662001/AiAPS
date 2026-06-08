import request from '@/utils/request'

export function traceQuery(params: { mode: string; keyword: string }) {
  return request.get('/trace/query', { params })
}

export function getTraceTimeline(id: string) {
  return request.get(`/trace/${id}/timeline`)
}
