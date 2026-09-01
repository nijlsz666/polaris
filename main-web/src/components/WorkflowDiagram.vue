<template>
  <div :class="['workflow-diagram', { compact }]">
    <div v-for="(step, index) in steps" :key="step.code || step.label || index" class="workflow-diagram-item">
      <div :class="['workflow-diagram-node', `workflow-diagram-node--${states[index] || 'upcoming'}`]">
        <span class="workflow-diagram-index">{{ states[index] === 'done' ? '✓' : index + 1 }}</span>
        <div>
          <b>{{ step.label }}</b>
          <small>{{ step.detail }}</small>
        </div>
      </div>
      <span v-if="index < steps.length - 1" class="workflow-diagram-arrow">→</span>
    </div>
  </div>
</template>

<script setup>
defineProps({
  steps: { type: Array, default: () => [] },
  states: { type: Array, default: () => [] },
  compact: Boolean
})
</script>
