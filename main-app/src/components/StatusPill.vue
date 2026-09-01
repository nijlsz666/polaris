<script setup>
import AppIcon from './AppIcon.vue'

defineProps({
  online: Boolean,
  syncing: Boolean,
  pending: { type: Number, default: 0 }
})

defineEmits(['click'])
</script>

<template>
  <button class="status-pill" type="button" @click="$emit('click')">
    <span class="status-indicator" :class="{ offline: !online, syncing }"></span>
    <span>{{ syncing ? '同步中' : online ? '系统在线' : '离线模式' }}</span>
    <span v-if="pending" class="status-count">{{ pending }}</span>
    <AppIcon name="chevron" :size="13" />
  </button>
</template>

<style scoped>
.status-pill{height:35px;display:flex;align-items:center;gap:7px;border:1px solid #e1e9ef;background:#fbfcfd;border-radius:18px;padding:0 10px 0 11px;color:#668091;font-size:9px}.status-indicator{width:6px;height:6px;border-radius:50%;background:#2bb994;box-shadow:0 0 0 4px #2bb99418}.status-indicator.offline{background:#d99456;box-shadow:0 0 0 4px #d9945618}.status-indicator.syncing{animation:pulse 1.2s infinite}.status-count{min-width:17px;height:17px;padding:0 4px;background:#f8e5d6;color:#b8733f;border-radius:9px;display:grid;place-items:center;font-size:8px}@keyframes pulse{50%{opacity:.42}}
</style>
