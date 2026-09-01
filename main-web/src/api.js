export const API_BASE = import.meta.env.VITE_API_BASE || '/api'

export class ApiError extends Error {
  constructor(message, status = 0, payload = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.payload = payload
  }
}

export async function request(path, options = {}) {
  const token = localStorage.getItem('polaris-token')
  try {
    const response = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers: {
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        Accept: 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.headers || {})
      }
    })
    let result
    try { result = await response.json() } catch { result = { success: false, message: `请求失败（HTTP ${response.status}）` } }
    if (response.status === 401) {
      localStorage.removeItem('polaris-token'); localStorage.removeItem('polaris-user'); localStorage.removeItem('polaris-tenant')
      if (window.location.hash !== '#/login') window.location.hash = '#/login'
    }
    if (!response.ok || result.success === false) throw new ApiError(result.message || `请求失败（HTTP ${response.status}）`, response.status, result)
    return result.data
  } catch (error) {
    if (!options.silent) window.dispatchEvent(new CustomEvent('polaris:api-error', { detail: error }))
    throw error
  }
}
