<script setup>
import AppIcon from './AppIcon.vue'

defineProps({ tasks: { type: Array, default: () => [] } })
defineEmits(['open'])
</script>

<template>
  <div class="task-list task-list--component">
    <button v-for="task in tasks" :key="task.id" class="task-row" type="button" @click="$emit('open', task.type)">
      <span class="task-icon" :class="task.tone"><AppIcon name="clipboard" :size="16" /></span>
      <span class="task-row-copy"><b>{{ task.title }}</b><small>{{ task.subtitle }}</small></span>
      <span class="task-count">{{ task.count }}<small>{{ task.unit }}</small></span><AppIcon name="chevron" :size="16" class="task-arrow" />
    </button>
    <div v-if="!tasks.length" class="empty-operation"><AppIcon name="check" :size="16" />暂无待处理工单</div>
  </div>
</template>

<style scoped>
.task-list--component{border:0;padding:0;background:transparent}.task-list--component .task-row-copy{min-width:0}.task-list--component .task-arrow{color:#a3b2bd;margin-left:5px;flex:none}.task-list--component .empty-operation{min-height:90px;display:flex;align-items:center;justify-content:center;gap:7px;color:#9cacb8;font-size:10px}.task-list--component .task-row{width:100%;display:flex;align-items:center;text-align:left;gap:11px;padding:13px 0;border-bottom:1px solid #edf2f6}.task-list--component .task-row:last-child{border:0}.task-list--component .task-icon{width:31px;height:31px;border-radius:9px;display:grid;place-items:center;flex:none}.task-list--component .task-icon.blue{background:#eaf2ff;color:#3979ce}.task-list--component .task-icon.orange{background:#fff3e5;color:#db9450}.task-list--component .task-icon.green{background:#eaf8f1;color:#35aa7e}.task-list--component .task-row b,.task-list--component .task-row small{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.task-list--component .task-row b{font-size:10px;color:#3d5970}.task-list--component .task-row small{font-size:8px;color:#99a9b5;margin-top:4px}.task-list--component .task-count{margin-left:auto;text-align:right;color:#2c4e6a;font-size:13px;font-weight:700;white-space:nowrap}.task-list--component .task-count small{font-size:8px;color:#9caab7;font-weight:400;margin-top:2px}
</style>
