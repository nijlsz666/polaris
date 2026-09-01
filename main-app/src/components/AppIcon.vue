<script setup>
const props = defineProps({
  name: { type: String, default: 'dot' },
  size: { type: [String, Number], default: 18 },
  strokeWidth: { type: [String, Number], default: 1.8 }
})

const iconPaths = {
  activity: ['M3 12h3l2-7 4 14 2-7h7'],
  alert: ['M12 3 22 20H2L12 3z', 'M12 9v4', 'M12 17h.01'],
  arrowUpRight: ['M7 17 17 7', 'M8 7h9v9'],
  box: ['M4 7.5 12 3l8 4.5v9L12 21l-8-4.5z', 'M12 21V12', 'M4.4 7.7 12 12l7.6-4.3'],
  check: ['m5 12 4 4L19 6'],
  chevron: ['m9 18 6-6-6-6'],
  clipboard: ['M8 5h8', 'M9 3h6v4H9z', 'M6 5H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-1', 'm8 13 2 2 5-5'],
  close: ['M6 6l12 12', 'M18 6 6 18'],
  home: ['m3 10 9-7 9 7', 'M5 9v10h14V9', 'M9 19v-6h6v6'],
  inventory: ['M4 6.5 12 3l8 3.5v11L12 21l-8-3.5z', 'M12 21V10', 'M4.5 6.7 12 10l7.5-3.3'],
  logout: ['M10 17l5-5-5-5', 'M15 12H3', 'M21 19V5a2 2 0 0 0-2-2h-5'],
  package: ['M5 8.5 12 5l7 3.5v8L12 20l-7-3.5z', 'M12 20v-8', 'M5.2 8.6 12 12l6.8-3.4', 'M9 6.5 16 10'],
  refresh: ['M20 11a8 8 0 0 0-14.8-4L3 10', 'M3 5v5h5', 'M4 13a8 8 0 0 0 14.8 4L21 14', 'M21 19v-5h-5'],
  scan: ['M7 3H5a2 2 0 0 0-2 2v2', 'M17 3h2a2 2 0 0 1 2 2v2', 'M7 21H5a2 2 0 0 1-2-2v-2', 'M17 21h2a2 2 0 0 0 2-2v-2', 'M7 12h10'],
  search: ['m21 21-4.3-4.3', 'M10.8 18a7.2 7.2 0 1 1 0-14.4 7.2 7.2 0 0 1 0 14.4z'],
  shield: ['M12 3 20 6v5c0 5-3.4 8.6-8 10-4.6-1.4-8-5-8-10V6z', 'm8.5 12 2.2 2.2 4.8-4.8'],
  sun: ['M12 3v2', 'M12 19v2', 'm4.2 4.2 1.4 1.4', 'm18.4 18.4 1.4 1.4', 'M3 12h2', 'M19 12h2', 'm4.2 19.8 1.4-1.4', 'm18.4 5.6 1.4-1.4', 'M16.5 12a4.5 4.5 0 1 1-9 0 4.5 4.5 0 0 1 9 0z'],
  task: ['M5 4h14v16H5z', 'M8 8h8', 'M8 12h8', 'M8 16h5'],
  user: ['M19 21a7 7 0 0 0-14 0', 'M12 13a4 4 0 1 0 0-8 4 4 0 0 0 0 8z'],
  warehouse: ['M3 21h18', 'M5 21V8l7-5 7 5v13', 'M8 12h2', 'M14 12h2', 'M8 16h2', 'M14 16h2'],
  zap: ['m13 2-9 12h7l-1 8 9-12h-7z']
}

const paths = iconPaths[props.name] || iconPaths.activity
</script>

<template>
  <svg class="app-icon" :width="size" :height="size" viewBox="0 0 24 24" fill="none" stroke="currentColor" :stroke-width="strokeWidth" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path v-for="path in paths" :key="path" :d="path" />
  </svg>
</template>
