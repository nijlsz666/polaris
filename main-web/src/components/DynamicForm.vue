<template>
  <form class="dynamic-form" :class="`dynamic-form--columns-${columns}`" @submit.prevent="$emit('submit')">
    <FormField v-for="field in schema" :key="field.key" :field="field" :model-value="modelValue[field.key]" @update:model-value="updateField(field.key, $event)" />
    <slot />
  </form>
</template>

<script setup>
import FormField from './FormField.vue'

const props = defineProps({
  schema: { type: Array, default: () => [] },
  modelValue: { type: Object, default: () => ({}) },
  columns: { type: Number, default: 1 }
})
const emit = defineEmits(['update:modelValue', 'submit'])
function updateField(key, value) { emit('update:modelValue', { ...props.modelValue, [key]: value }) }
</script>
