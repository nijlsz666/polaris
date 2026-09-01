<template><article class="stat-card" :class="`tone-${tone}`"><div class="stat-card__top"><span class="stat-card__label">{{ label }}</span><span class="stat-card__icon">{{ icon }}</span></div><div class="stat-card__value">{{ value }}</div><div class="stat-card__hint">{{ hint }}</div></article></template>
<script setup>
defineProps({ label: String, value: [String, Number], hint: String, icon: String, tone: { type: String, default: 'blue' } })
</script>
