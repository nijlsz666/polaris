const API_BASE = import.meta.env.VITE_API_BASE || '/api'
export async function request(path, options = {}) {
  const token = localStorage.getItem('polaris-token')
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(options.headers || {}) } })
  const result = await response.json()
  if (!response.ok || result.success === false) throw new Error(result.message || '请求失败')
  return result.data
}
