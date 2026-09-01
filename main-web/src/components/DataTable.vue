<template>
  <section class="data-table-card">
    <div v-if="showToolbar" class="data-table-toolbar">
      <div class="data-table-toolbar__title"><slot name="toolbar"><b>{{ tableTitle }}</b><span v-if="tableHint">{{ tableHint }}</span></slot></div>
      <div class="data-table-toolbar__actions">
        <button v-if="enableImport" class="table-tool-button" type="button" @click="fileInput?.click()">⇧ 导入</button>
        <button v-if="enableExport" class="table-tool-button" type="button" @click="exportCsv">⇩ 下载</button>
        <button v-if="enableColumnSettings" class="table-tool-button" type="button" @click="showConfig = true">☷ 列配置</button>
        <input ref="fileInput" class="visually-hidden" type="file" accept=".csv,.xlsx,.xls" @change="handleImport" />
      </div>
    </div>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th v-if="selectable" class="table-select-cell"><input type="checkbox" :checked="allSelected" aria-label="全选" @change="toggleAll" /></th>
            <th v-for="column in visibleColumns" :key="column.key" :style="column.width ? { width: column.width } : undefined">
              <button v-if="sortable && column.sortable !== false" class="table-sort-button" type="button" @click="sortBy(column)">{{ column.label }}<span :class="['sort-indicator', { active: sortKey === column.key }]">{{ sortKey === column.key ? (sortOrder === 'asc' ? '↑' : '↓') : '↕' }}</span></button>
              <span v-else>{{ column.label }}</span>
            </th>
            <th v-if="$slots.actions" class="table-actions-head">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading"><td :colspan="colspan" class="table-state"><span class="table-spinner"></span>正在加载数据…</td></tr>
          <tr v-for="row in displayRows" v-else :key="getRowKey(row)" :class="{ 'is-selected': selectedKeys.includes(getRowKey(row)) }">
            <td v-if="selectable" class="table-select-cell"><input type="checkbox" :checked="selectedKeys.includes(getRowKey(row))" aria-label="选择此行" @change="toggleRow(row)" /></td>
            <td v-for="column in visibleColumns" :key="column.key">
              <slot :name="`cell-${column.key}`" :row="row" :value="row[column.key]">
                <span v-if="column.status" class="status-pill" :class="statusClass(row[column.key], column)">{{ formatCell(column, row[column.key], row) }}</span>
                <span v-else>{{ formatCell(column, row[column.key], row) }}</span>
              </slot>
            </td>
            <td v-if="$slots.actions" class="table-actions-cell"><slot name="actions" :row="row" /></td>
          </tr>
          <tr v-if="!loading && !displayRows.length"><td :colspan="colspan" class="table-state table-empty-state"><span class="table-empty-icon">◌</span><b>{{ emptyText }}</b><small>{{ emptyHint }}</small></td></tr>
        </tbody>
      </table>
    </div>
    <footer v-if="paginationEnabled" class="data-table-pagination">
      <span>共 {{ totalRows }} 条</span><label>每页 <select v-model.number="pageSize"><option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }}</option></select> 条</label>
      <div class="pagination-pages"><button type="button" :disabled="currentPage <= 1" @click="currentPage--">上一页</button><button v-for="page in pageItems" :key="page" type="button" :class="{ active: currentPage === page }" @click="currentPage = page">{{ page }}</button><button type="button" :disabled="currentPage >= totalPages" @click="currentPage++">下一页</button></div>
    </footer>
    <ConfigDrawer v-if="enableColumnSettings" v-model="showConfig" :items="localColumns" title="表格列配置" subtitle="调整列的显示、顺序与阅读重点" @update:items="localColumns = $event" @reset="resetColumns" />
  </section>
</template>

<script setup>
import { computed, getCurrentInstance, ref, watch } from 'vue'
import ConfigDrawer from './ConfigDrawer.vue'

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  rowKey: { type: [String, Function], default: null },
  loading: Boolean,
  tableTitle: { type: String, default: '数据列表' },
  tableHint: { type: String, default: '' },
  emptyText: { type: String, default: '暂无数据' },
  emptyHint: { type: String, default: '调整筛选条件或稍后再试' },
  showToolbar: Boolean,
  enableExport: Boolean,
  enableImport: Boolean,
  enableColumnSettings: Boolean,
  exportName: { type: String, default: 'polaris-export' },
  selectable: Boolean,
  sortable: { type: Boolean, default: true },
  pagination: { type: [Boolean, Object], default: false },
  serverSide: Boolean,
  pageSizeOptions: { type: Array, default: () => [10, 20, 50] },
  defaultSort: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['import', 'sort-change', 'selection-change', 'update:pagination'])
const localColumns = ref([]); const fileInput = ref(null); const showConfig = ref(false); const sortKey = ref(props.defaultSort.key || ''); const sortOrder = ref(props.defaultSort.order || 'asc'); const currentPage = ref(Number(typeof props.pagination === 'object' ? props.pagination.page : 1) || 1); const pageSize = ref(Number(typeof props.pagination === 'object' ? props.pagination.size : props.pageSizeOptions[0]) || 10); const selectedKeys = ref([])
watch(() => props.columns, value => { localColumns.value = value.map(column => ({ ...column, visible: column.visible !== false })) }, { immediate: true, deep: true })
watch(() => props.rows, () => { const keys = new Set(props.rows.map(getRowKey)); selectedKeys.value = selectedKeys.value.filter(key => keys.has(key)); if (currentPage.value > totalPages.value) currentPage.value = Math.max(1, totalPages.value) })
watch(() => props.pagination, value => { if (value && typeof value === 'object') { currentPage.value = Number(value.page || currentPage.value); pageSize.value = Number(value.size || pageSize.value) } }, { deep: true })
watch([currentPage, pageSize], () => emit('update:pagination', { page: currentPage.value, size: pageSize.value }))
const visibleColumns = computed(() => localColumns.value.filter(column => column.visible !== false))
const sortedRows = computed(() => {
  if (!sortKey.value) return [...props.rows]
  const column = localColumns.value.find(item => item.key === sortKey.value)
  return [...props.rows].sort((a, b) => {
    const av = column?.sortValue ? column.sortValue(a[sortKey.value], a) : a[sortKey.value]
    const bv = column?.sortValue ? column.sortValue(b[sortKey.value], b) : b[sortKey.value]
    if (av === bv) return 0
    if (av === null || av === undefined || av === '') return 1
    if (bv === null || bv === undefined || bv === '') return -1
    const result = typeof av === 'number' && typeof bv === 'number' ? av - bv : String(av).localeCompare(String(bv), 'zh-CN', { numeric: true })
    return sortOrder.value === 'asc' ? result : -result
  })
})
const paginationEnabled = computed(() => Boolean(props.pagination))
const totalRows = computed(() => props.serverSide && typeof props.pagination === 'object' && props.pagination.total !== undefined ? Number(props.pagination.total) : sortedRows.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(totalRows.value / pageSize.value)))
const displayRows = computed(() => paginationEnabled.value && !props.serverSide ? sortedRows.value.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value) : sortedRows.value)
const pageItems = computed(() => Array.from({ length: totalPages.value }, (_, index) => index + 1).slice(Math.max(0, currentPage.value - 3), currentPage.value + 2))
const colspan = computed(() => visibleColumns.value.length + (props.selectable ? 1 : 0) + (useActions.value ? 1 : 0))
const useActions = computed(() => Boolean(getCurrentInstance?.()?.slots?.actions))
function getRowKey(row) { if (typeof props.rowKey === 'function') return props.rowKey(row); if (props.rowKey) return row[props.rowKey]; return row.id || row.code || row.order_no || row.barcode || JSON.stringify(row) }
function formatCell(column, value, row) { return column.format ? column.format(value, row) : (value === null || value === undefined || value === '' ? '-' : value) }
function statusClass(value, column) { return `status-${String(column.statusTone ? column.statusTone(value) : value).toLowerCase().replace(/\s+/g, '-')}` }
function sortBy(column) { if (sortKey.value === column.key) sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc'; else { sortKey.value = column.key; sortOrder.value = 'asc' }; currentPage.value = 1; emit('sort-change', { key: sortKey.value, order: sortOrder.value }) }
function toggleRow(row) { const key = getRowKey(row); selectedKeys.value = selectedKeys.value.includes(key) ? selectedKeys.value.filter(item => item !== key) : [...selectedKeys.value, key]; emit('selection-change', props.rows.filter(item => selectedKeys.value.includes(getRowKey(item)))) }
const allSelected = computed(() => displayRows.value.length > 0 && displayRows.value.every(row => selectedKeys.value.includes(getRowKey(row))))
function toggleAll() { const keys = displayRows.value.map(getRowKey); selectedKeys.value = allSelected.value ? selectedKeys.value.filter(key => !keys.includes(key)) : [...new Set([...selectedKeys.value, ...keys])]; emit('selection-change', props.rows.filter(item => selectedKeys.value.includes(getRowKey(item)))) }
function exportCsv() { const rows = [visibleColumns.value.map(column => column.label), ...sortedRows.value.map(row => visibleColumns.value.map(column => formatCell(column, row[column.key], row)))]; const csv = '\ufeff' + rows.map(row => row.map(value => `"${String(value).replace(/"/g, '""')}"`).join(',')).join('\n'); const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' })); const link = document.createElement('a'); link.href = url; link.download = `${props.exportName}-${new Date().toISOString().slice(0, 10)}.csv`; link.click(); URL.revokeObjectURL(url) }
function handleImport(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (file.name.toLowerCase().endsWith('.csv')) {
    const reader = new FileReader()
    reader.onload = () => emit('import', file, parseCsv(String(reader.result || '')))
    reader.readAsText(file, 'utf-8')
  } else emit('import', file, [])
  event.target.value = ''
}
function parseCsv(text) {
  const lines = text.replace(/^\ufeff/, '').split(/\r?\n/).filter(Boolean)
  if (lines.length < 2) return []
  const headers = lines.shift().split(',').map(item => item.replace(/^"|"$/g, '').replace(/""/g, '"'))
  return lines.map(line => line.split(',').map(item => item.replace(/^"|"$/g, '').replace(/""/g, '"'))).map(values => Object.fromEntries(headers.map((header, index) => [header, values[index] || ''])))
}
function resetColumns() { localColumns.value = props.columns.map(column => ({ ...column, visible: true })) }
</script>
