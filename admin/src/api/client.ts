import { ElMessage } from 'element-plus'

const baseUrl = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ?? ''

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); this.name = 'ApiError' }
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  if (!path.startsWith('/') || path.startsWith('//')) throw new Error('API path must start with a single /')
  try {
    const response = await fetch(`${baseUrl}${path}`, {
      ...init,
      headers: { Accept: 'application/json', ...(init.body ? { 'Content-Type': 'application/json' } : {}), ...init.headers },
    })
    const data: unknown = response.status === 204 ? undefined : await response.json()
    if (!response.ok) {
      const message = typeof data === 'object' && data !== null && 'message' in data && typeof data.message === 'string'
        ? data.message : `请求失败 (${response.status})`
      throw new ApiError(response.status, message)
    }
    return data as T
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '网络请求失败')
    throw error
  }
}
