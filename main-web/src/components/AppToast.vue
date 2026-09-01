<template>
  <Teleport to="body">
    <TransitionGroup name="toast" tag="div" class="toast-stack" aria-live="polite">
      <article v-for="item in items" :key="item.id" class="app-toast" :class="`app-toast--${item.type}`" role="status">
        <span class="app-toast__icon">{{ icon(item.type) }}</span>
        <div><b>{{ item.title || title(item.type) }}</b><p>{{ item.message }}</p></div>
        <button type="button" aria-label="关闭提示" @click="remove(item.id)">×</button>
      </article>
    </TransitionGroup>
  </Teleport>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'

const items = ref([])
let sequence = 0
let lastSignature = ''
let lastShownAt = 0
function title(type) { return ({ success: '操作成功', warning: '请注意', error: '操作失败', info: '提示' }[type] || '提示') }
function icon(type) { return ({ success: '✓', warning: '!', error: '×', info: 'i' }[type] || 'i') }
function remove(id) { items.value = items.value.filter(item => item.id !== id) }
function show(event) {
  const detail = event.detail || {}
  const type = detail.type || (event.type === 'polaris:api-error' ? 'error' : 'info')
  const message = detail.message || '系统消息'
  const signature = `${type}:${message}`
  if (signature === lastSignature && Date.now() - lastShownAt < 800) return
  lastSignature = signature; lastShownAt = Date.now()
  const id = ++sequence
  const item = { id, type, message, title: detail.title, timer: null }
  items.value.push(item)
  item.timer = window.setTimeout(() => remove(id), Number(detail.duration || 4200))
}
onMounted(() => { window.addEventListener('polaris:toast', show); window.addEventListener('polaris:api-error', show) })
onUnmounted(() => { window.removeEventListener('polaris:toast', show); window.removeEventListener('polaris:api-error', show) })
</script>
