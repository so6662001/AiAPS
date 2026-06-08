import request from '@/utils/request'

export function checkNeedSign(enterpriseId: number, agreementType: string) {
  return request.get('/v1/agreement/sign/check', { params: { enterpriseId, agreementType } })
}

export function signAgreement(data: Record<string, unknown>) {
  return request.post('/v1/agreement/sign', data)
}

export function checkNeedAcknowledge(userId: string, enterpriseId: number) {
  return request.get('/v1/agreement/acknowledge/check', { params: { userId, enterpriseId } })
}

export function acknowledge(data: Record<string, unknown>) {
  return request.post('/v1/agreement/acknowledge', data)
}

export function getAuthScope(enterpriseId: number) {
  return request.get(`/v1/agreement/enterprise/${enterpriseId}/scope`)
}
