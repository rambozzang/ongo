import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  Brand,
  BrandScheduleItem,
  MultiBrandSummary,
  CreateBrandRequest,
  CreateScheduleRequest,
} from '@/types/multiBrandCalendar'

export const multiBrandCalendarApi = {
  getSummary: () =>
    apiClient.get<ResData<MultiBrandSummary>>('/multi-brand/summary').then(unwrapResponse),

  getBrands: () =>
    apiClient.get<ResData<Brand[]>>('/multi-brand/brands').then(unwrapResponse),

  createBrand: (request: CreateBrandRequest) =>
    apiClient.post<ResData<Brand>>('/multi-brand/brands', request).then(unwrapResponse),

  updateBrand: (id: number, data: Partial<Brand>) =>
    apiClient.put<ResData<Brand>>(`/multi-brand/brands/${id}`, data).then(unwrapResponse),

  deleteBrand: (id: number) =>
    apiClient.delete<ResData<void>>(`/multi-brand/brands/${id}`).then(unwrapResponse),

  getSchedule: (startDate: string, endDate: string, brandId?: number) =>
    apiClient.get<ResData<BrandScheduleItem[]>>('/multi-brand/schedule', {
      params: { startDate, endDate, brandId },
    }).then(unwrapResponse),

  createSchedule: (request: CreateScheduleRequest) =>
    apiClient.post<ResData<BrandScheduleItem>>('/multi-brand/schedule', request).then(unwrapResponse),

  updateSchedule: (id: number, data: Partial<BrandScheduleItem>) =>
    apiClient.put<ResData<BrandScheduleItem>>(`/multi-brand/schedule/${id}`, data).then(unwrapResponse),

  deleteSchedule: (id: number) =>
    apiClient.delete<ResData<void>>(`/multi-brand/schedule/${id}`).then(unwrapResponse),
}
