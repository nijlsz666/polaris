<script setup>
defineProps({
  eyebrow: String,
  title: String,
  actionLabel: String,
  actionHint: String
})

defineEmits(['action'])
</script>

<template>
  <div class="section-heading">
    <div><span v-if="eyebrow" class="section-eyebrow">{{ eyebrow }}</span><h2>{{ title }}</h2></div>
    <button v-if="actionLabel" type="button" @click="$emit('action')">{{ actionLabel }} <span v-if="actionHint">{{ actionHint }}</span></button>
  </div>
</template>

<style scoped>
.section-heading{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:18px}.section-eyebrow{display:block;color:#9aaab7;font-size:8px}.section-heading h2{font-size:16px;letter-spacing:-.2px;color:#294760;margin:7px 0 0}.section-heading>button{color:#4f87bb;font-size:10px;display:flex;align-items:center;gap:4px;padding:3px 0}.section-heading>button span{color:#8aaabf;font-size:14px}
</style>
