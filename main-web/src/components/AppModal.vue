<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div v-if="modelValue" class="app-modal-backdrop" @click.self="close">
        <section class="app-modal" :class="[`app-modal--${size}`]" role="dialog" aria-modal="true" :aria-label="title">
          <header class="app-modal__header">
            <div>
              <p v-if="eyebrow" class="app-modal__eyebrow">{{ eyebrow }}</p>
              <h2>{{ title }}</h2>
              <p v-if="subtitle" class="app-modal__subtitle">{{ subtitle }}</p>
            </div>
            <button v-if="closable" class="app-modal__close" type="button" aria-label="关闭" @click="close">×</button>
          </header>
          <div class="app-modal__body"><slot /></div>
          <footer v-if="!hideFooter" class="app-modal__footer">
            <slot name="footer">
              <button class="button button-ghost" type="button" @click="close">{{ cancelText }}</button>
              <button class="button button-primary" type="button" :disabled="loading" @click="$emit('confirm')">{{ loading ? loadingText : confirmText }}</button>
            </slot>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { onMounted, onUnmounted, watch } from 'vue'

const props = defineProps({
  modelValue: Boolean,
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  eyebrow: { type: String, default: '' },
  size: { type: String, default: 'medium' },
  confirmText: { type: String, default: '保存' },
  cancelText: { type: String, default: '取消' },
  loadingText: { type: String, default: '保存中…' },
  loading: Boolean,
  hideFooter: Boolean,
  closable: { type: Boolean, default: true }
})
const emit = defineEmits(['update:modelValue', 'confirm', 'cancel'])

function close() {
  if (props.loading) return
  emit('update:modelValue', false)
  emit('cancel')
}
function onKeydown(event) {
  if (event.key === 'Escape' && props.modelValue) close()
}
function syncBodyLock(value) {
  if (typeof document === 'undefined') return
  document.body.classList.toggle('modal-open', value)
}

watch(() => props.modelValue, syncBodyLock, { immediate: true })
onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  syncBodyLock(false)
})
</script>
