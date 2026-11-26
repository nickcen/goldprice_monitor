import { ElMessage } from 'element-plus'
import { ref } from 'vue'

const baseUrl = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ?? ''
const TOKEN_KEY = 'goldprice.admin.token'
export const adminToken = ref(sessionStorage.getItem(TOKEN_KEY) ?? '')
export function setAdminToken(value: string) {
  adminToken.value = value
  if (value) sessionStorage.setItem(TOKEN_KEY, value)
  else sessionStorage.removeItem(TOKEN_KEY)
}
export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); this.name = 'ApiError' }
}
export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  if (!path.startsWith('/') || path.startsWith('//')) throw new Error('API path must start with a single /')
  try {
    const response = await fetch(`${baseUrl}${path}`, {
      ...init,
      headers: { Accept: 'application/json', ...(init.body ? { 'Content-Type': 'application/json' } : {}), ...(adminToken.value ? { Authorization: `Bearer ${adminToken.value}` } : {}), ...init.headers },
    })
    const data: unknown = response.status === 204 ? undefined : await response.json()
    if (!response.ok) {
      const message = typeof data === 'object' && data !== null && 'message' in data && typeof data.message === 'string'
        ? data.message : `请求失败 (${response.status})`
      if (response.status === 401 && path !== '/api/v1/admin/auth/login') {
        setAdminToken('')
        if (location.pathname !== '/login') location.assign('/login')
      }
      throw new ApiError(response.status, message)
    }
    return data as T
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '网络请求失败')
    throw error
  }
}
export function query(params: Record<string, string | number | null | undefined>) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => { if (value !== null && value !== undefined && value !== '') search.set(key, String(value)) })
  return search.toString()
}
export function jsonBody(value: unknown): RequestInit { return { method: 'POST', body: JSON.stringify(value) } }
export interface Page<T> { items: T[]; page: number; pageSize: number; total: number; totalPages: number }
export interface User { id: number; phone: string; level: number | null; status: number; validFromDate: string | null; validToDate: string | null; createTime: string }
export interface Payment { id: number; userId: number; phone: string; skuId: number; skuName: string; paymentNumber: string; thirdpartyNumber: string | null; payTime: string | null; createTime: string }
export interface Sku { id: number; name: string; price: number; status: number; sortOrder: number; createTime: string }
