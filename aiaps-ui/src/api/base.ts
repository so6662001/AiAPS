import request from '@/utils/request'

export interface MaterialQuery {
  materialCode?: string
  materialName?: string
  category?: string
  page?: number
  pageSize?: number
}

export interface MaterialForm {
  materialCode: string
  materialName: string
  specification?: string
  unit: string
  category: string
  steelGrade?: string
  thickness?: number
  width?: number
  length?: number
  density?: number
}

export interface BomQuery {
  parentMaterialCode?: string
  productName?: string
  page?: number
  pageSize?: number
}

export interface WorkCenterQuery {
  centerCode?: string
  centerName?: string
  page?: number
  pageSize?: number
}

export function getMaterialList(params: MaterialQuery) {
  return request.get('/base/materials', { params })
}

export function getMaterialById(id: number | string) {
  return request.get(`/base/materials/${id}`)
}

export function createMaterial(data: MaterialForm) {
  return request.post('/base/materials', data)
}

export function updateMaterial(id: number | string, data: MaterialForm) {
  return request.put(`/base/materials/${id}`, data)
}

export function deleteMaterial(id: number | string) {
  return request.delete(`/base/materials/${id}`)
}

export function getBomList(params: BomQuery) {
  return request.get('/base/boms', { params })
}

export function getBomById(id: number | string) {
  return request.get(`/base/boms/${id}`)
}

export function getWorkCenterList(params: WorkCenterQuery) {
  return request.get('/base/work-centers', { params })
}
