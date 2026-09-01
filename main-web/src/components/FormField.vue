<template>
  <label class="form-field" :class="[`form-field--${field.type || 'text'}`, { 'form-field--full': field.span === 2 }]">
    <span class="form-field__label">{{ field.label }}<i v-if="field.required">*</i></span>
    <span v-if="field.help" class="form-field__help">{{ field.help }}</span>
    <select v-if="field.type === 'select'" :value="modelValue" :disabled="field.disabled" @change="$emit('update:modelValue', selectValue($event))">
      <option v-if="field.placeholder" value="">{{ field.placeholder }}</option>
      <option v-for="option in field.options || []" :key="option.value ?? option" :value="option.value ?? option">{{ option.label ?? option }}</option>
    </select>
    <textarea v-else-if="field.type === 'textarea'" :value="modelValue" :disabled="field.disabled" :placeholder="field.placeholder" :rows="field.rows || 4" @input="$emit('update:modelValue', $event.target.value)" />
    <span v-else-if="field.type === 'switch'" class="form-switch"><input type="checkbox" :checked="Boolean(modelValue)" :disabled="field.disabled" @change="$emit('update:modelValue', $event.target.checked)" /><span></span><em>{{ modelValue ? (field.onText || '已启用') : (field.offText || '已停用') }}</em></span>
    <span v-else-if="field.type === 'radio'" class="form-radio-group"><label v-for="option in field.options || []" :key="option.value ?? option"><input type="radio" :name="field.key" :value="option.value ?? option" :checked="modelValue === (option.value ?? option)" :disabled="field.disabled" @change="$emit('update:modelValue', option.value ?? option)" />{{ option.label ?? option }}</label></span>
    <input v-else :type="field.type || 'text'" :value="modelValue" :disabled="field.disabled" :required="field.required" :min="field.min" :max="field.max" :step="field.step" :placeholder="field.placeholder" @input="$emit('update:modelValue', field.type === 'number' ? ($event.target.value === '' ? '' : Number($event.target.value)) : $event.target.value)" />
  </label>
</template>

<script setup>
const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Number, Boolean], default: '' }
})
defineEmits(['update:modelValue'])
function selectValue(event) {
  const raw = event.target.value
  const option = (props.field.options || []).find(item => String(item.value ?? item) === raw)
  return option ? (option.value ?? option) : raw
}
</script>
