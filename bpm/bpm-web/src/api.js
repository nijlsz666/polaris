const API_BASE = import.meta.env.VITE_BPM_API_BASE || '/api'

export async function request(path, options = {}) {
  const actor = localStorage.getItem('bpm-user') || (() => { try { return JSON.parse(localStorage.getItem('polaris-user') || '{}').username || 'admin' } catch { return 'admin' } })()
  let tenant = {}
  try { tenant = JSON.parse(localStorage.getItem('polaris-tenant') || '{}') } catch {}
  const token = localStorage.getItem('polaris-token')
  try {
    const response = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers: { 'Content-Type': 'application/json', 'X-User': actor, ...(tenant.code ? { 'X-Tenant-Code': tenant.code } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(options.headers || {}) }
    })
    const result = await response.json()
    if (!response.ok || result.success === false) throw new Error(result.message || '请求失败')
    return result.data
  } catch (error) {
    if (options.allowFallback === true) return null
    throw error
  }
}
