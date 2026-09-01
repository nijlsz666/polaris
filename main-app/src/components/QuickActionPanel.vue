<script setup>
import AppIcon from './AppIcon.vue'

defineProps({ operationModes: { type: Array, default: () => [] } })
defineEmits(['open', 'select'])

const actionIcons = {
  receipt: 'package', issue: 'arrowUpRight', return: 'refresh', putaway: 'warehouse', transfer: 'zap', count: 'clipboard', exception: 'activity',
  finished: 'box', sales: 'arrowUpRight', scrap: 'close', adjust: 'activity', quality: 'check'
}
</script>

<template>
  <section class="quick-panel">
    <div class="quick-panel-glow quick-panel-glow--one"></div><div class="quick-panel-glow quick-panel-glow--two"></div>
    <div class="quick-panel-head">
      <div><span class="quick-panel-kicker">FIELD OPERATIONS / 01</span><h2>扫码作业</h2><p>扫描物料、工单或库位条码，快速完成现场事务。</p></div>
      <button class="quick-scan-button" type="button" @click="$emit('open')"><AppIcon name="scan" :size="24" /><span>启动相机</span></button>
    </div>
    <div class="quick-panel-divider"></div>
    <div class="quick-actions-grid">
      <button v-for="mode in operationModes" :key="mode.key" type="button" @click="$emit('select', mode.key)">
        <span class="quick-action-icon"><AppIcon :name="actionIcons[mode.key] || 'activity'" :size="16" /></span><span>{{ mode.label }}</span><AppIcon name="arrowUpRight" :size="13" class="quick-action-arrow" />
      </button>
    </div>
  </section>
</template>

<style scoped>
.quick-panel{min-height:322px;border-radius:16px;padding:25px 27px 21px;background:linear-gradient(135deg,#153c5d 0%,#1b5876 55%,#258a8b 100%);color:#fff;position:relative;overflow:hidden;box-shadow:0 14px 32px #123c5d22}.quick-panel-glow{position:absolute;border:1px solid #b9f2e522;border-radius:50%;pointer-events:none}.quick-panel-glow--one{width:330px;height:330px;right:-155px;top:-210px}.quick-panel-glow--two{width:430px;height:430px;right:-210px;top:-260px;border-color:#b9f2e511}.quick-panel-head{display:flex;align-items:flex-start;justify-content:space-between;position:relative;z-index:1}.quick-panel-kicker{color:#8ddfd1;font-size:9px;letter-spacing:1.5px;font-weight:700}.quick-panel h2{font-size:22px;letter-spacing:-.5px;margin:11px 0 6px}.quick-panel p{font-size:11px;color:#b8d8df;margin:0;line-height:1.6}.quick-scan-button{width:78px;height:78px;border-radius:15px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:7px;background:#effcfb;color:#267b81;box-shadow:0 12px 21px #09293b3b;font-size:9px;font-weight:700;transition:.2s}.quick-scan-button:hover{transform:translateY(-2px);background:#fff}.quick-panel-divider{height:1px;background:#ffffff1f;margin:23px 0 15px;position:relative;z-index:1}.quick-actions-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:7px;position:relative;z-index:1}.quick-actions-grid button{min-width:0;display:flex;align-items:center;gap:7px;color:#e2f3f3;border:1px solid #ffffff20;border-radius:8px;padding:9px 8px;text-align:left;background:#ffffff0d;transition:.18s}.quick-actions-grid button:hover{background:#ffffff1b;border-color:#ffffff42;transform:translateY(-1px)}.quick-action-icon{width:24px;height:24px;display:grid;place-items:center;flex:none;border-radius:6px;background:#ffffff14;color:#8be1d0}.quick-actions-grid button>span:nth-child(2){font-size:9px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.quick-action-arrow{margin-left:auto;color:#85bdc3;opacity:.6}@media(max-width:1040px){.quick-actions-grid{grid-template-columns:repeat(3,1fr)}}@media(max-width:720px){.quick-panel{min-height:0;border-radius:14px;padding:19px 17px 16px}.quick-panel h2{font-size:19px}.quick-panel p{font-size:10px}.quick-scan-button{width:62px;height:62px;border-radius:12px;font-size:8px}.quick-actions-grid{grid-template-columns:repeat(3,1fr);gap:6px}.quick-actions-grid button{padding:8px 6px;gap:5px}.quick-actions-grid button>span:nth-child(2){font-size:8px}.quick-action-icon{width:22px;height:22px}.quick-action-arrow{display:none}}
</style>
