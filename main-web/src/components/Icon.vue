<template><span class="icon" :class="`icon-${name}`" aria-hidden="true">{{ glyph }}</span></template>
<script setup>
const props = defineProps({ name: { type: String, default: 'grid' } })
const icons = { grid: '▦', factory: '⌂', layers: '◈', calendar: '▣', clipboard: '▤', warehouse: '⌗', inbox: '⇩', file: '▱', box: '□', qr: '▦', chart: '◒', layout: '▥', monitor: '▣', settings: '⚙', users: '♙', shield: '◇', scan: '⌁', arrow: '›', workflow: '◉', timeline: '⌁', flow: '◇', form: '▤', rocket: '➤', money: '¥', activity: '∿', database: '▥' }
const glyph = icons[props.name] || '•'
</script>
