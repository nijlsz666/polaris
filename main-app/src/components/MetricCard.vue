<script setup>
import AppIcon from './AppIcon.vue'

defineProps({
  label: String,
  value: [String, Number],
  caption: String,
  icon: { type: String, default: 'activity' },
  tone: { type: String, default: 'blue' },
  trend: String
})
</script>

<template>
  <article class="metric-card" :class="`metric-card--${tone}`">
    <div class="metric-card-top"><span class="metric-icon"><AppIcon :name="icon" :size="16" /></span><span v-if="trend" class="metric-trend">{{ trend }}</span></div>
    <strong>{{ value }}</strong>
    <span class="metric-label">{{ label }}</span>
    <small>{{ caption }}</small>
  </article>
</template>

<style scoped>
.metric-card{min-height:105px;border:1px solid #e8eef3;border-radius:11px;padding:12px 13px;background:#fcfdfe;position:relative;overflow:hidden}.metric-card:after{content:"";position:absolute;width:80px;height:80px;border-radius:50%;right:-39px;bottom:-47px;background:currentColor;opacity:.06}.metric-card-top{display:flex;justify-content:space-between;align-items:center}.metric-icon{width:26px;height:26px;border-radius:8px;display:grid;place-items:center}.metric-trend{font-size:8px;color:#3aa788}.metric-card strong{display:block;color:#294b67;font-size:22px;letter-spacing:-.7px;margin-top:10px}.metric-label{display:block;color:#536d81;font-size:10px;margin-top:2px}.metric-card small{display:block;color:#9aabb7;font-size:8px;margin-top:5px}.metric-card--blue{color:#2774d0}.metric-card--blue .metric-icon{background:#eaf2ff}.metric-card--teal{color:#1fa992}.metric-card--teal .metric-icon{background:#e7f8f4}.metric-card--amber{color:#d48b42}.metric-card--amber .metric-icon{background:#fff3e5}.metric-card--violet{color:#7d62c5}.metric-card--violet .metric-icon{background:#f0edff}
</style>
