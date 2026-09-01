<template><span class="status-badge" :class="`status-badge--${tone}`"><i></i>{{ label || value || '-' }}</span></template>

<script setup>
import { computed } from 'vue'
import { useLocale } from '../i18n'

const props = defineProps({ value: { type: [String, Number, Boolean], default: '' }, label: { type: String, default: '' }, tone: { type: String, default: '' } })
const { t } = useLocale()
const tone = computed(() => props.tone || String(props.value || 'default').toLowerCase().replace(/\s+/g, '-'))
const label = computed(() => props.label || t(`status.${props.value}`, props.value))
</script>
