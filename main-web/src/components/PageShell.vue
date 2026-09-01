<template><section class="page-shell"><div class="page-heading"><div class="page-heading__main"><p class="eyebrow">POLARIS OPERATIONS</p><h1>{{ title }}</h1><p v-if="description" class="page-description">{{ description }}</p><div class="page-heading__status"><span class="live-signal"></span><b>LIVE</b><i></i><span>业务数据持续同步</span><i></i><span>当前租户工作区</span></div></div><div class="page-actions"><slot name="actions" /></div></div><slot /></section></template>
<script setup>
defineProps({ title: String, description: String })
</script>
