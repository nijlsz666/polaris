const QUEUE_PREFIX = 'polaris-pda-outbox'

function queueKey() {
  const tenant = localStorage.getItem('polaris-tenant') || 'default'
  return `${QUEUE_PREFIX}:${tenant}`
}

function readQueue() {
  try {
    const value = JSON.parse(localStorage.getItem(queueKey()) || '[]')
    return Array.isArray(value) ? value : []
  } catch {
    return []
  }
}

function writeQueue(queue) {
  localStorage.setItem(queueKey(), JSON.stringify(queue))
}

function createId() {
  return globalThis.crypto?.randomUUID?.() || `pda-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function listQueuedOperations() {
  return readQueue()
}

export function enqueueOperation(operation) {
  const queue = readQueue()
  const entry = {
    id: createId(),
    createdAt: new Date().toISOString(),
    ...operation
  }
  queue.push(entry)
  writeQueue(queue)
  return entry
}

export function removeQueuedOperation(id) {
  writeQueue(readQueue().filter(item => item.id !== id))
}

export function isNetworkError(error) {
  if (globalThis.navigator && navigator.onLine === false) return true
  if (!error) return false
  return error.name === 'TypeError' || /failed to fetch|network|网络|连接|超时/i.test(error.message || '')
}
