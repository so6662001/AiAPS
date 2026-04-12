import request from '@/utils/request'

export function reverseMatch(rawStockIds: number[]) {
  return request.post('/v1/reverse-match', rawStockIds)
}
