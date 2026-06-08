import request from '@/utils/request'

export function getContractProgress(params?: Record<string, unknown>) {
  return request.get('/report/contract-progress', { params })
}

export function getContractList(params?: Record<string, unknown>) {
  return request.get('/report/contracts', { params })
}
