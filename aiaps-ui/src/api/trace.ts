import request from '@/utils/request'

export function traceByBarcode(barcode: string) {
  return request.get('/trace/barcode', { params: { barcode } })
}

export function traceByCardNo(cardNo: string) {
  return request.get('/trace/card-no', { params: { cardNo } })
}

export function traceByContract(contractNo: string) {
  return request.get('/trace/contract', { params: { contractNo } })
}

export function traceForward(cardNo: string) {
  return request.get('/trace/forward', { params: { cardNo } })
}

export function traceBackward(cardNo: string) {
  return request.get('/trace/backward', { params: { cardNo } })
}
