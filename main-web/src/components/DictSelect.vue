<template>
  <label class="dict-select" :class="{ 'dict-select--loading': loading }">
    <span v-if="label" class="dict-select__label">{{ label }}<i v-if="required">*</i></span>
    <select :value="modelValue" :disabled="disabled || loading" :required="required" @change="change">
      <option v-if="placeholder" value="">{{ placeholder }}</option>
      <option v-for="option in options" :key="option.code" :value="option.value">{{ option.label }}</option>
    </select>
    <small v-if="error">{{ error }}</small>
  </label>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { request } from '../api'
import { useToast } from '../composables/useToast'

const props = defineProps({ modelValue: { type: [String, Number], default: '' }, type: { type: String, required: true }, label: String, placeholder: { type: String, default: '请选择' }, required: Boolean, disabled: Boolean, options: { type: Array, default: null } })
const emit = defineEmits(['update:modelValue', 'loaded'])
const options = ref(props.options || []); const loading = ref(false); const error = ref(''); const { error: notifyError } = useToast()
watch(() => props.options, value => { if (value) options.value = value }, { deep: true })
async function load() {
  if (props.options) return
  loading.value = true; error.value = ''
  try { options.value = await request(`/dictionaries/${encodeURIComponent(props.type)}`) || []; emit('loaded', options.value) }
  catch (requestError) { error.value = requestError.message; notifyError(requestError.message) }
  finally { loading.value = false }
}
function change(event) { const option = options.value.find(item => String(item.value) === event.target.value); emit('update:modelValue', option?.value ?? event.target.value) }
onMounted(load)
</script>
