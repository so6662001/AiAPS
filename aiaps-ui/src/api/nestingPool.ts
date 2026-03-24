import request from '@/utils/request'

export function collectFromMrp(runId: number) {
  return request.post('/v1/nesting-pool/collect', null, { params: { runId } })
}

export function getPoolGroups(status = 'PENDING') {
  return request.get('/v1/nesting-pool/groups', { params: { status } })
}

export function getPoolGroupItems(groupKey: string, status = 'PENDING') {
  return request.get(`/v1/nesting-pool/group/${encodeURIComponent(groupKey)}`, { params: { status } })
}

export function addToPool(data: Record<string, unknown>) {
  return request.post('/v1/nesting-pool', data)
}

export function multiOptimize(groupKey: string) {
  return request.post('/v1/nesting/multi-optimize', null, { params: { groupKey } })
}

export function multiOptimizePreview(groupKey: string) {
  return request.post('/v1/nesting/multi-optimize/preview', null, { params: { groupKey } })
}

export function confirmNestingSchedule(nestingId: number) {
  return request.post(`/v1/nesting/${nestingId}/confirm-schedule`)
}

export function getCostSplit(nestingId: number) {
  return request.get(`/v1/nesting/${nestingId}/cost-split`)
}
