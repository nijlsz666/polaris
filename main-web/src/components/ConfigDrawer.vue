<template>
  <Teleport to="body">
    <Transition name="drawer-fade">
      <div v-if="modelValue" class="config-drawer-backdrop" @click.self="close">
        <aside class="config-drawer" aria-label="配置面板">
          <header class="config-drawer__header">
            <div><p class="app-modal__eyebrow">PERSONALIZE VIEW</p><h2>{{ title }}</h2><p>{{ subtitle }}</p></div>
            <button class="app-modal__close" type="button" @click="close">×</button>
          </header>
          <div class="config-drawer__body">
            <slot>
              <div class="config-drawer__section">
                <div class="config-drawer__section-title"><b>显示列</b><span>{{ visibleCount }} / {{ items.length }}</span></div>
                <label v-for="item in items" :key="item.key" class="config-check">
                  <input type="checkbox" :checked="item.visible !== false" :disabled="item.locked" @change="toggle(item)" />
                  <span>{{ item.label }}</span><small v-if="item.locked">固定</small>
                </label>
              </div>
            </slot>
          </div>
          <footer class="config-drawer__footer">
            <button class="button button-ghost" type="button" @click="reset">恢复默认</button>
            <button class="button button-primary" type="button" @click="close">完成</button>
          </footer>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: Boolean,
  title: { type: String, default: '视图配置' },
  subtitle: { type: String, default: '调整当前页面的显示方式' },
  items: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'update:items', 'reset'])
const visibleCount = computed(() => props.items.filter(item => item.visible !== false).length)
function close() { emit('update:modelValue', false) }
function toggle(item) {
  emit('update:items', props.items.map(entry => entry.key === item.key ? { ...entry, visible: !entry.visible } : entry))
}
function reset() { emit('reset') }
</script>
