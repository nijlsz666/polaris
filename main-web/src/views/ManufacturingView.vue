<template>
  <PageShell :title="tabTitle" description="覆盖 BOM、计划、工单、工时与现场报工的制造执行闭环。">
    <template #actions><button class="button button-ghost" @click="load">↻ 刷新</button><button v-if="tab !== 'mrp'" class="button button-primary" @click="showCreate = true">+ {{ tab === 'bom' ? '新增 BOM' : '新建工单' }}</button><button v-else class="button button-primary" @click="runMrp">运行 MRP</button></template>
    <div class="subnav"><button v-for="item in tabs" :key="item.key" :class="['subnav-item', { active: tab === item.key }]" @click="switchTab(item.key)">{{ item.label }}</button></div>
    <div v-if="importMessage" class="notice-bar"><span>✓</span>{{ importMessage }}</div>
    <div v-if="tab === 'bom'" class="panel"><div class="filter-row"><input v-model="keyword" placeholder="搜索 BOM 编码 / 产品名称" /><button class="button button-ghost" @click="load">筛选</button></div><DataTable :columns="bomColumns" :rows="filteredBoms" :show-toolbar="true" :enable-export="true" :enable-import="true" :enable-column-settings="true" :pagination="true" table-title="BOM 清单" table-hint="支持下载、导入、排序和动态列" export-name="polaris-boms" @import="handleImport"><template #actions="{ row }"><button v-if="row.status !== 'RELEASED'" class="table-action" @click="publishBom(row)">发布</button><button class="table-action" @click="openMrp(row)">运行 MRP</button></template></DataTable></div>
    <div v-else-if="tab === 'mrp'" class="mrp-workbench">
      <div class="panel mrp-run-panel">
        <div class="panel-heading"><div><h3>物料需求计划（MRP）</h3><p>按已发布 BOM 展开需求，并纳入安全库存、可用库存、在途和未交采购订单计算净缺料。</p></div><span v-if="mrpResult" class="data-hint">{{ mrpResult.runNo }}</span></div>
        <div class="mrp-form"><label><span>产品编码</span><input v-model="mrpForm.productCode" placeholder="FG-DRONE-001" /></label><label><span>计划数量</span><input v-model.number="mrpForm.planQty" type="number" min="1" placeholder="100" /></label><label><span>需求日期</span><input v-model="mrpForm.planDate" type="date" /></label><label><span>优先级</span><select v-model="mrpForm.priority"><option value="NORMAL">普通</option><option value="HIGH">高</option><option value="URGENT">紧急</option></select></label><button class="button button-primary" :disabled="mrpLoading" @click="runMrp">{{ mrpLoading ? '计算中…' : '运行 MRP' }}</button></div>
        <div v-if="mrpResult" class="mrp-result"><div class="mrp-result-head"><div><b>{{ mrpResult.productName }}</b><span>{{ mrpResult.productCode }} · {{ mrpResult.bomCode }} / {{ mrpResult.bomVersion }} · 计划 {{ mrpResult.planDate }}</span></div><em :class="Number(mrpResult.netShortageQty || mrpResult.shortageQty) > 0 ? 'danger' : 'success'">{{ Number(mrpResult.netShortageQty || mrpResult.shortageQty) > 0 ? `净缺料 ${mrpResult.netShortageQty || mrpResult.shortageQty}` : '库存与供应可满足' }}</em></div><DataTable :columns="mrpColumns" :rows="mrpResult.requirements || []" :show-toolbar="true" :enable-export="true" :enable-column-settings="true" :pagination="true" table-title="物料需求明细" table-hint="可用库存已扣除 WMS 锁定 / 预留影响；在途与未交采购单单独展示" export-name="polaris-mrp" /></div><div v-else class="empty-operation">输入产品编码、计划数量和需求日期后运行 MRP。</div>
      </div>
      <div class="panel side-summary"><h3>MRP 结果</h3><div class="summary-item"><span>物料项</span><b>{{ mrpResult?.materialCount || 0 }}</b></div><div class="summary-item"><span>净缺料项</span><b class="text-danger">{{ mrpResult?.shortageCount || 0 }}</b></div><div class="summary-item"><span>净缺料数量</span><b class="text-danger">{{ mrpResult?.netShortageQty || mrpResult?.shortageQty || 0 }}</b></div><div class="summary-item"><span>已生成叫料</span><b>{{ materialCalls.length }}</b></div><div class="callout">每次运行都会形成可追溯的 MRP 批次。缺料进入队列后可现场叫料，也可在采购模块依据缺料单生成采购申请。</div></div>
      <div class="panel mrp-queue-panel"><div class="panel-heading"><div><h3>缺料处置队列</h3><p>按交期和优先级处理影响工单的净缺料。</p></div><span class="data-hint">{{ shortages.length }} 条</span></div><DataTable :columns="shortageColumns" :rows="shortages" :show-toolbar="true" :enable-export="true" :pagination="true" table-title="缺料清单" export-name="polaris-shortages"><template #actions="{ row }"><button v-if="!['RESOLVED','CANCELLED'].includes(row.status)" class="table-action" @click="createMaterialCall(row)">生成叫料</button><button v-if="!row.procurement_record_no && !['RESOLVED','CANCELLED'].includes(row.status)" class="table-action" @click="createPurchase(row)">转采购</button></template></DataTable></div>
      <div class="panel mrp-queue-panel"><div class="panel-heading"><div><h3>叫料执行</h3><p>从仓库拣料到线边交付的现场状态。</p></div><span class="data-hint">{{ materialCalls.length }} 张</span></div><DataTable :columns="callColumns" :rows="materialCalls" :show-toolbar="true" :enable-export="true" :pagination="true" table-title="叫料单" export-name="polaris-material-calls"><template #actions="{ row }"><button v-if="row.status === 'DRAFT'" class="table-action" @click="transitionCall(row, 'RELEASED')">下达</button><button v-else-if="row.status === 'RELEASED'" class="table-action" @click="transitionCall(row, 'IN_PICKING')">开始拣料</button><button v-else-if="row.status === 'IN_PICKING'" class="table-action" @click="transitionCall(row, 'COMPLETED')">完成发料</button></template></DataTable></div>
      <div class="panel mrp-queue-panel"><div class="panel-heading"><div><h3>供应商 ASN</h3><p>采购订单到货预告、承运信息和收货状态统一追踪。</p></div><div><span class="data-hint">{{ asns.length }} 张</span><button class="button button-primary button-compact" @click="openAsn">+ 新建 ASN</button></div></div><DataTable :columns="asnColumns" :rows="asns" :show-toolbar="true" :enable-export="true" :pagination="true" table-title="ASN 到货预告" export-name="polaris-asn"><template #actions="{ row }"><button v-if="row.status === 'DRAFT'" class="table-action" @click="transitionAsn(row, 'SUBMITTED')">提交</button><button v-else-if="row.status === 'SUBMITTED'" class="table-action" @click="transitionAsn(row, 'CONFIRMED')">确认</button><button v-else-if="row.status === 'CONFIRMED'" class="table-action" @click="transitionAsn(row, 'RECEIVING')">收货中</button><button v-else-if="row.status === 'RECEIVING'" class="table-action" @click="transitionAsn(row, 'RECEIVED')">确认收货</button></template></DataTable></div>
    </div>
    <div v-else-if="tab === 'work-order'" class="panel"><div class="filter-row"><input v-model="keyword" placeholder="搜索工单号 / 产品名称" /><button class="button button-ghost" @click="load">筛选</button></div><DataTable :columns="workOrderColumns" :rows="filteredWorkOrders" :show-toolbar="true" :enable-export="true" :enable-column-settings="true" :pagination="true" table-title="工单清单" table-hint="支持动态排序、列配置和分页" export-name="polaris-work-orders"><template #actions="{ row }"><button class="table-action" @click="report(row)">现场报工</button><button class="table-action" @click="openWorkOrder(row)">详情</button></template></DataTable></div>
    <div v-else class="planning-layout"><div class="panel"><div class="panel-heading"><div><h3>生产计划排程</h3><p>当前排程来自数据库工单的计划时间与工作中心</p></div></div><div class="calendar-board"><div class="calendar-head"><span>工单</span><b>产品</b><b>计划数量</b><b>计划开始</b><b>状态</b></div><div v-for="row in workOrders" :key="row.id" class="calendar-line"><strong>{{ row.order_no }}</strong><span>{{ row.product_name }}</span><span>{{ row.plan_qty }}</span><span>{{ formatDate(row.planned_start) }}</span><span :class="['schedule-block', statusTone(row.status)]">{{ statusLabel(row.status) }}</span></div><div v-if="!workOrders.length" class="empty-operation">暂无工单排程</div></div></div><div class="panel side-summary"><h3>排程统计</h3><div class="summary-item"><span>工单总数</span><b>{{ workOrders.length }}</b></div><div class="summary-item"><span>待排产</span><b>{{ statusCount('PLANNED') }}</b></div><div class="summary-item"><span>执行中</span><b>{{ statusCount('IN_PROGRESS') }}</b></div><div class="callout">统计和排程数据均来自当前工单接口。</div></div></div>
    <AppModal v-model="showWorkOrder" :title="selectedWorkOrder?.order_no || '工单详情'" subtitle="查看工单计划、执行进度和发布审批轨迹" size="large" hide-footer>
      <div v-if="selectedWorkOrder" class="document-detail">
        <div class="document-detail-head"><div><b>{{ selectedWorkOrder.order_no }}</b><span>{{ selectedWorkOrder.product_code }} · {{ selectedWorkOrder.product_name }} · {{ selectedWorkOrder.work_center || '未指定工作中心' }}</span></div><StatusBadge :value="selectedWorkOrder.status" :label="statusLabel(selectedWorkOrder.status)" /></div>
        <div class="document-detail-grid"><span><small>产品编码</small><b>{{ selectedWorkOrder.product_code }}</b></span><span><small>计划数量</small><b>{{ selectedWorkOrder.plan_qty }}</b></span><span><small>已完成</small><b>{{ selectedWorkOrder.completed_qty || 0 }}</b></span><span><small>剩余数量</small><b>{{ Math.max(0, Number(selectedWorkOrder.plan_qty || 0) - Number(selectedWorkOrder.completed_qty || 0)) }}</b></span><span><small>计划开始</small><b>{{ formatDate(selectedWorkOrder.planned_start) }}</b></span><span><small>计划结束</small><b>{{ formatDate(selectedWorkOrder.planned_end) }}</b></span><span><small>工作中心</small><b>{{ selectedWorkOrder.work_center || '-' }}</b></span><span><small>创建时间</small><b>{{ formatDate(selectedWorkOrder.created_at) }}</b></span></div>
        <section class="document-detail-section"><div class="document-section-title"><h3>发布审批</h3><span>{{ workOrderApprovalInstance ? statusLabel(workOrderApprovalInstance.status) : '尚未发起' }}</span></div><div v-if="workOrderApprovalDetail.history?.length" class="document-approval-timeline"><div v-for="item in workOrderApprovalDetail.history" :key="`${item.taskId}-${item.startTime}`"><i></i><span><b>{{ item.name }}</b><small>{{ item.assignee || '待签收' }} · {{ formatDate(item.startTime) }}<template v-if="item.endTime"> → {{ formatDate(item.endTime) }}</template></small></span></div></div><div v-else class="empty-operation">该工单尚未产生审批轨迹</div></section>
        <div class="modal-actions"><button v-if="selectedWorkOrder.status === 'PLANNED'" class="button button-primary" type="button" @click="submitWorkOrderApproval">提交发布审批</button><button class="button button-ghost" type="button" @click="showWorkOrder = false">关闭</button></div>
      </div>
    </AppModal>
    <AppModal v-model="showCreate" :title="tab === 'bom' ? '新增 BOM' : '新建工单'" :subtitle="tab === 'bom' ? '建立产品结构版本并进入后续维护' : '创建工单后即可进入排产与现场执行'" confirm-text="保存" @confirm="save">
      <DynamicForm v-model="form" :schema="createSchema" :columns="2" />
    </AppModal>
    <AppModal v-model="showAsnModal" title="新建 ASN 到货预告" subtitle="填写采购订单号后，系统会自动带出未交采购明细" confirm-text="创建 ASN" @confirm="saveAsn">
      <DynamicForm v-model="asnForm" :schema="asnSchema" :columns="2" />
    </AppModal>
  </PageShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageShell from '../components/PageShell.vue'
import DataTable from '../components/DataTable.vue'
import AppModal from '../components/AppModal.vue'
import DynamicForm from '../components/DynamicForm.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { request } from '../api'

function notify(message, type = 'error') { window.dispatchEvent(new CustomEvent('polaris:toast', { detail: { message, type } })) }

const route = useRoute(); const router = useRouter()
const tab = ref(route.params.tab || 'bom'); const keyword = ref(''); const boms = ref([]); const workOrders = ref([]); const shortages = ref([]); const materialCalls = ref([]); const asns = ref([]); const showCreate = ref(false); const showAsnModal = ref(false); const showWorkOrder = ref(false); const selectedWorkOrder = ref(null); const workOrderApprovalInstance = ref(null); const workOrderApprovalDetail = ref({}); const importMessage = ref(''); const form = ref({ code: '', productCode: '', name: '', qty: '', itemsText: '' }); const mrpForm = ref({ productCode: '', planQty: 100, planDate: new Date().toISOString().slice(0, 10), priority: 'NORMAL' }); const mrpResult = ref(null); const mrpLoading = ref(false); const asnForm = ref({ purchaseOrderNo: '', supplierCode: '', supplierName: '', expectedArrival: new Date().toISOString().slice(0, 10), warehouseCode: 'WH-RAW', carrier: '', trackingNo: '', remark: '' })
const tabs = [{ key: 'bom', label: 'BOM 管理' }, { key: 'mrp', label: 'MRP 运算' }, { key: 'plan', label: '生产计划' }, { key: 'work-order', label: '工单管理' }]
const tabTitle = computed(() => tabs.find(item => item.key === tab.value)?.label || '制造管理')
const bomColumns = [{ key: 'bom_code', label: 'BOM 编码' }, { key: 'product_name', label: '产品名称' }, { key: 'version', label: '版本' }, { key: 'item_count', label: '物料项' }, { key: 'status', label: '状态', status: true, format: value => value === 'RELEASED' ? '已发布' : '草稿' }]
const workOrderColumns = [{ key: 'order_no', label: '工单号' }, { key: 'product_name', label: '产品' }, { key: 'work_center', label: '工作中心' }, { key: 'plan_qty', label: '计划数量' }, { key: 'completed_qty', label: '已完成' }, { key: 'status', label: '状态', status: true, format: statusLabel }]
const mrpColumns = [{ key: 'materialCode', label: '物料编码' }, { key: 'materialName', label: '物料名称' }, { key: 'quantityPer', label: '单耗' }, { key: 'requiredQty', label: '毛需求' }, { key: 'safetyStockQty', label: '安全库存' }, { key: 'availableQty', label: '可用库存' }, { key: 'inTransitQty', label: '在途' }, { key: 'openPoQty', label: '未交采购' }, { key: 'netShortageQty', label: '净缺料' }, { key: 'suggestion', label: '建议', status: true, statusTone: value => value === '库存可满足' ? 'success' : 'warning' }]
const shortageColumns = [{ key: 'shortage_no', label: '缺料单号' }, { key: 'material_code', label: '物料编码' }, { key: 'material_name', label: '物料名称' }, { key: 'shortage_qty', label: '缺料数量' }, { key: 'resolved_qty', label: '已解决' }, { key: 'required_date', label: '需求日期' }, { key: 'priority', label: '优先级', status: true }, { key: 'status', label: '状态', status: true }, { key: 'procurement_record_no', label: '采购申请' }]
const callColumns = [{ key: 'call_no', label: '叫料单号' }, { key: 'work_order_no', label: '工单号' }, { key: 'material_code', label: '物料编码' }, { key: 'requested_qty', label: '叫料数量' }, { key: 'issued_qty', label: '已发数量' }, { key: 'required_at', label: '要求时间', format: value => value ? String(value).replace('T', ' ').slice(0, 16) : '-' }, { key: 'status', label: '状态', status: true }]
const asnColumns = [{ key: 'asn_no', label: 'ASN 单号' }, { key: 'purchase_order_no', label: '采购订单' }, { key: 'supplier_name', label: '供应商' }, { key: 'expected_arrival', label: '预计到货' }, { key: 'warehouse_code', label: '收货仓库' }, { key: 'tracking_no', label: '运单号' }, { key: 'status', label: '状态', status: true }]
const createSchema = computed(() => [
  { key: 'code', label: tab.value === 'bom' ? 'BOM 编码' : '工单号', placeholder: tab.value === 'bom' ? 'BOM-20260819-003' : 'WO-20260819-004', required: true },
  { key: 'productCode', label: '产品编码', placeholder: 'FG-DRONE-001', required: true },
  { key: 'name', label: '产品名称', placeholder: '请输入产品名称', required: true },
  ...(tab.value === 'bom' ? [{ key: 'itemsText', label: '物料清单', type: 'textarea', rows: 4, span: 2, placeholder: '每行：物料编码|物料名称|用量|单位|损耗率\nRM-MOTOR-001|无刷电机|2|件|3' }] : []),
  ...(tab.value === 'bom' ? [] : [{ key: 'qty', label: '计划数量', type: 'number', placeholder: '0', min: 1, required: true }])
])
const asnSchema = [
  { key: 'purchaseOrderNo', label: '采购订单号', placeholder: 'PO-20260821-001', required: true },
  { key: 'supplierCode', label: '供应商编码', placeholder: 'SUP-001' },
  { key: 'supplierName', label: '供应商名称', placeholder: '供应商名称', required: true },
  { key: 'expectedArrival', label: '预计到货日期', type: 'date', required: true },
  { key: 'warehouseCode', label: '收货仓库', placeholder: 'WH-RAW', required: true },
  { key: 'carrier', label: '承运商', placeholder: '物流公司' },
  { key: 'trackingNo', label: '运单号', placeholder: '可选' },
  { key: 'remark', label: '备注', type: 'textarea', rows: 3, span: 2 }
]
const filteredBoms = computed(() => boms.value.filter(item => !keyword.value || `${item.bom_code}${item.product_name}`.toLowerCase().includes(keyword.value.toLowerCase())))
const filteredWorkOrders = computed(() => workOrders.value.filter(item => !keyword.value || `${item.order_no}${item.product_name}`.toLowerCase().includes(keyword.value.toLowerCase())))

function switchTab(value) { tab.value = value; keyword.value = ''; router.push(`/manufacturing/${value}`); load() }
function handleImport(file, rows = []) { importMessage.value = '已读取 ' + file.name + '，识别 ' + (rows.length || 0) + ' 行，导入模板校验通道已就绪'; window.clearTimeout(handleImport.timer); handleImport.timer = window.setTimeout(() => { importMessage.value = '' }, 3600) }
function statusLabel(value) { return ({ IN_PROGRESS: '执行中', COMPLETED: '已完成', PLANNED: '待排产', PENDING_APPROVAL: '审批中', REJECTED: '已驳回' }[value] || value) }
function statusTone(value) { return value === 'COMPLETED' ? 'green' : value === 'IN_PROGRESS' ? 'blue' : 'violet' }
function statusCount(value) { return workOrders.value.filter(item => item.status === value).length }
function formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '未排程' }
async function openWorkOrder(row) {
  try {
    selectedWorkOrder.value = await request(`/manufacturing/work-orders/${row.id}`)
    const instances = await request(`/bpm/instances/by-business?businessType=WORK_ORDER&businessId=${encodeURIComponent(row.id)}`)
    workOrderApprovalInstance.value = instances?.[0] || null
    workOrderApprovalDetail.value = workOrderApprovalInstance.value ? await request(`/bpm/instances/${workOrderApprovalInstance.value.flowable_instance_id}`) : {}
    showWorkOrder.value = true
  } catch (error) { notify(error.message) }
}
async function submitWorkOrderApproval() {
  if (!selectedWorkOrder.value) return
  try { await request(`/manufacturing/work-orders/${selectedWorkOrder.value.id}/submit-approval`, { method: 'POST' }); notify('工单已提交发布审批', 'success'); await openWorkOrder(selectedWorkOrder.value); await load() } catch (error) { notify(error.message) }
}
function parseBomItems(text) { return String(text || '').split(/\r?\n/).map(line => line.trim()).filter(Boolean).map(line => line.split('|').map(item => item.trim())).filter(parts => parts.length >= 3 && parts[0] && parts[1] && Number(parts[2]) > 0).map(parts => ({ materialCode: parts[0], materialName: parts[1], quantity: Number(parts[2]), unit: parts[3] || '件', lossRate: Number(parts[4] || 0) })) }
function openMrp(row) { mrpForm.value = { ...mrpForm.value, productCode: row.product_code, planQty: 100 }; mrpResult.value = null; tab.value = 'mrp'; router.push('/manufacturing/mrp'); runMrp() }
async function runMrp() { if (!mrpForm.value.productCode || Number(mrpForm.value.planQty) <= 0) return notify('请填写产品编码和计划数量'); mrpLoading.value = true; try { mrpResult.value = await request('/manufacturing/mrp/run', { method: 'POST', body: JSON.stringify({ productCode: mrpForm.value.productCode, planQty: Number(mrpForm.value.planQty), planDate: mrpForm.value.planDate, priority: mrpForm.value.priority }) }); await loadMrpQueues() } catch (error) { notify(error.message) } finally { mrpLoading.value = false } }
async function loadMrpQueues() { try { const [shortageRows, callRows, asnRows] = await Promise.all([request('/manufacturing/shortages'), request('/manufacturing/material-calls'), request('/manufacturing/asns')]); shortages.value = shortageRows || []; materialCalls.value = callRows || []; asns.value = asnRows || [] } catch (error) { notify(error.message) } }
async function createMaterialCall(row) { const workOrderNo = window.prompt(`请输入 ${row.material_code} 对应的工单号`, ''); if (workOrderNo === null) return; const requestedQty = Number(window.prompt(`请输入叫料数量（未解决 ${Number(row.shortage_qty || 0) - Number(row.resolved_qty || 0)}）`, String(Number(row.shortage_qty || 0) - Number(row.resolved_qty || 0)))); if (!Number.isFinite(requestedQty) || requestedQty <= 0) return; try { await request(`/manufacturing/shortages/${row.id}/material-call`, { method: 'POST', body: JSON.stringify({ workOrderNo, requestedQty, priority: row.priority }) }); notify('叫料单已创建', 'success'); await loadMrpQueues() } catch (error) { notify(error.message) } }
async function createPurchase(row) { try { await request(`/manufacturing/shortages/${row.id}/purchase-requisition`, { method: 'POST', body: JSON.stringify({}) }); notify('采购申请已创建', 'success'); await loadMrpQueues() } catch (error) { notify(error.message) } }
async function transitionCall(row, status) { try { await request(`/manufacturing/material-calls/${row.id}/transition`, { method: 'POST', body: JSON.stringify({ status, issuedQty: status === 'COMPLETED' ? row.requested_qty : row.issued_qty }) }); notify('叫料单状态已更新', 'success'); await loadMrpQueues() } catch (error) { notify(error.message) } }
function openAsn() { asnForm.value = { purchaseOrderNo: '', supplierCode: '', supplierName: '', expectedArrival: new Date().toISOString().slice(0, 10), warehouseCode: 'WH-RAW', carrier: '', trackingNo: '', remark: '' }; showAsnModal.value = true }
async function saveAsn() { try { await request('/manufacturing/asns', { method: 'POST', body: JSON.stringify(asnForm.value) }); showAsnModal.value = false; notify('ASN 单已创建', 'success'); await loadMrpQueues() } catch (error) { notify(error.message) } }
async function transitionAsn(row, status) { try { await request(`/manufacturing/asns/${row.id}/transition`, { method: 'POST', body: JSON.stringify({ status }) }); notify('ASN 状态已更新', 'success'); await loadMrpQueues() } catch (error) { notify(error.message) } }
async function publishBom(row) { if (!window.confirm(`确认发布 ${row.bom_code}？发布后 MRP 才会读取该版本。`)) return; try { await request(`/manufacturing/boms/${row.id}/publish`, { method: 'POST' }); await load() } catch (error) { notify(error.message) } }
async function load() {
  try { [boms.value, workOrders.value] = await Promise.all([request('/manufacturing/boms'), request('/manufacturing/work-orders')]); if (tab.value === 'mrp') await loadMrpQueues() } catch (error) { notify(error.message) }
}
async function save() {
  const isBom = tab.value === 'bom'
  const payload = isBom
    ? { bomCode: form.value.code, productCode: form.value.productCode, productName: form.value.name, version: 'V1', items: parseBomItems(form.value.itemsText) }
    : { orderNo: form.value.code, productCode: form.value.productCode, productName: form.value.name, planQty: form.value.qty, workCenter: '装配一线' }
  try { await request(isBom ? '/manufacturing/boms' : '/manufacturing/work-orders', { method: 'POST', body: JSON.stringify(payload) }); showCreate.value = false; form.value = { code: '', productCode: '', name: '', qty: '', itemsText: '' }; await load() } catch (error) { notify(error.message) }
}
async function report(row) {
  const quantity = Number(window.prompt(`请输入 ${row.order_no} 本次报工数量`, '1'))
  if (!Number.isInteger(quantity) || quantity <= 0) return
  try { await request(`/manufacturing/work-orders/${row.id}/report`, { method: 'POST', body: JSON.stringify({ quantity }) }); await load() } catch (error) { notify(error.message) }
}
onMounted(load)
</script>

<style scoped>
.mrp-workbench{display:grid;grid-template-columns:minmax(0,1fr) 270px;gap:18px}.mrp-run-panel,.mrp-queue-panel{grid-column:1 / -1}.mrp-run-panel{grid-column:1}.mrp-workbench>.side-summary{grid-column:2;grid-row:1}.mrp-form{display:grid;grid-template-columns:1.1fr 130px 160px 120px auto;align-items:end;gap:12px;margin:18px 0}.mrp-form label{display:block;color:#687b8d;font-size:10px}.mrp-form label span{display:block;margin-bottom:6px}.mrp-form input,.mrp-form select{width:100%}.mrp-result{border-top:1px solid #e8eef4;padding-top:16px}.mrp-result-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:12px}.mrp-result-head b,.mrp-result-head span{display:block}.mrp-result-head b{font-size:15px;color:#294c6c}.mrp-result-head span{font-size:10px;color:#91a0ad;margin-top:4px}.mrp-result-head em{font-style:normal;font-size:12px}.mrp-result-head .danger,.text-danger{color:#d56657}.mrp-result-head .success{color:#1e9b69}.mrp-queue-panel .panel-heading>div:last-child{display:flex;align-items:center;gap:12px}.button-compact{padding:7px 12px;font-size:11px}@media(max-width:1100px){.mrp-workbench{grid-template-columns:1fr}.mrp-run-panel,.mrp-queue-panel,.mrp-workbench>.side-summary{grid-column:1;grid-row:auto}.mrp-form{grid-template-columns:1fr 1fr}.mrp-form .button{grid-column:span 2}}@media(max-width:650px){.mrp-form{grid-template-columns:1fr}.mrp-form .button{grid-column:auto}}
</style>
