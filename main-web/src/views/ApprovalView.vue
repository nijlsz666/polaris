<template>
  <PageShell title="审批中心" description="将销售、采购、生产与质量申请统一纳入可追踪的审批闭环。">
    <template #actions>
      <button class="button button-ghost" :disabled="loading" @click="load">↻ 刷新</button>
      <button class="button button-ai" @click="router.push('/procurement/ai-create')">✦ AI 创建采购申请审批</button>
      <button class="button button-primary" @click="openStart">+ 发起审批</button>
    </template>

    <div class="approval-hero">
      <div><span class="eyebrow">POLARIS WORKFLOW · FLOWABLE</span><h2>每一次业务决策，都有迹可循。</h2><p>审批动作、业务状态与操作意见统一落库，并实时回写 ERP 业务单据。</p></div>
      <div class="approval-hero-orbit"><span></span><b>BPM</b><small>CONNECTED</small></div>
    </div>
    <div class="approval-kpis"><article v-for="item in kpis" :key="item.label"><span>{{ item.label }}</span><strong>{{ item.value }}</strong><small>{{ item.hint }}</small></article></div>

    <div class="subnav approval-tabs"><button v-for="item in tabs" :key="item.key" :class="['subnav-item', { active: view === item.key }]" @click="switchView(item.key)">{{ item.label }}<i v-if="item.badge">{{ item.badge }}</i></button></div>
    <section class="panel approval-table-panel">
      <div class="panel-heading"><div><h3>{{ activeTab.label }}</h3><p>{{ activeTab.hint }}</p></div><span class="data-hint">{{ loading ? '同步中…' : `共 ${rows.length} 条` }}</span></div>
      <div class="filter-row"><input v-model.trim="keyword" placeholder="搜索标题 / 流程 / 业务编号" @keyup.enter="load" /><select v-if="view === 'instances' || view === 'initiated'" v-model="instanceStatus"><option value="">全部状态</option><option v-for="item in instanceStatuses" :key="item" :value="item">{{ statusLabel(item) }}</option></select><button class="button button-ghost button-small" @click="load">查询</button><span class="filter-spacer"></span><span class="data-hint">当前租户 · 数据库实时</span></div>
      <DataTable :columns="columns" :rows="rows" :loading="loading" :pagination="true" :show-toolbar="false" :row-key="view === 'todo' || view === 'done' ? 'taskId' : 'flowable_instance_id'">
        <template #cell-title="{ row, value }"><div class="approval-title-cell"><b>{{ value || row.title }}</b><small>{{ businessLabel(row.businessType || row.business_type) }} · {{ row.businessId || row.business_id || '-' }}</small></div></template>
        <template #cell-status="{ row, value }"><StatusBadge :value="value || row.status" :label="statusLabel(value || row.status)" /></template>
        <template #actions="{ row }"><button v-if="view === 'todo' && !row.assignee" class="table-action" @click="claim(row)">签收</button><button v-if="view === 'todo'" class="table-action" @click="openTask(row)">处理</button><button v-if="view === 'done' || view === 'instances' || view === 'initiated'" class="table-action" @click="openInstance(row.instanceId || row.flowable_instance_id)">详情</button><button v-if="(view === 'instances' || view === 'initiated') && row.status === 'RUNNING'" class="table-action danger" @click="cancel(row)">撤回</button></template>
      </DataTable>
    </section>

    <AppModal v-model="showTask" title="处理审批任务" :subtitle="activeTask.title || activeTask.name" size="medium" hide-footer>
      <div class="approval-detail-summary"><span class="approval-detail-icon">✓</span><div><b>{{ activeTask.title || '-' }}</b><small>{{ activeTask.processCode }} · {{ activeTask.name }} · {{ activeTask.candidateGroup || activeTask.assignee || '指定处理人' }}</small></div></div>
      <label class="approval-comment">审批意见<textarea v-model.trim="comment" rows="5" placeholder="请输入审批意见，驳回时建议说明原因。"></textarea></label>
      <div class="approval-modal-actions"><button class="button button-ghost" @click="showTask = false">取消</button><button class="button button-danger" :disabled="saving" @click="complete(false)">驳回</button><button class="button button-primary" :disabled="saving" @click="complete(true)">{{ saving ? '提交中…' : '同意并提交' }}</button></div>
    </AppModal>

    <AppModal v-model="showInstance" title="流程实例详情" subtitle="查看完整审批轨迹与业务关联" hide-footer size="large">
      <div v-if="instanceDetail.instance" class="instance-detail"><div class="approval-detail-summary"><span class="approval-detail-icon">⌁</span><div><b>{{ instanceDetail.instance.title }}</b><small>{{ businessLabel(instanceDetail.instance.business_type) }} · 发起人 {{ instanceDetail.instance.starter }} · <StatusBadge :value="instanceDetail.instance.status" :label="statusLabel(instanceDetail.instance.status)" /></small></div></div><div v-if="instanceDetail.erpRecord" class="approval-linked-document"><div class="approval-linked-document-head"><div><b>关联单据：{{ instanceDetail.erpRecord.no }}</b><small>{{ businessLabel(instanceDetail.erpRecord.type) }} · {{ instanceDetail.erpRecord.name }} · {{ instanceDetail.erpRecord.amount || '-' }}</small></div><StatusBadge :value="instanceDetail.erpRecord.status" :label="statusLabel(instanceDetail.erpRecord.status)" /></div><div class="approval-linked-lines"><div v-for="line in instanceDetail.erpRecord.lines || []" :key="line.id"><span>{{ line.materialCode || '-' }} · {{ line.materialName }}</span><b>{{ line.requestedQty }} {{ line.unit }}</b><small>{{ line.requiredDate || '未填写需求日期' }}</small></div></div></div><div class="approval-timeline"><div v-for="item in instanceDetail.history || []" :key="`${item.taskId}-${item.startTime}`" class="approval-timeline-item"><i></i><div><b>{{ item.name }}</b><small>{{ item.assignee || '待签收' }} · {{ formatDate(item.startTime) }}<template v-if="item.endTime"> → {{ formatDate(item.endTime) }}</template></small></div></div><div v-if="!(instanceDetail.history || []).length" class="empty-operation">暂无审批轨迹</div></div></div><div v-else class="empty-operation">正在加载实例详情…</div>
    </AppModal>

    <AppModal v-model="showStart" title="发起审批" subtitle="流程实例与业务主键会写入当前租户数据库" :loading="saving" confirm-text="发起流程" loading-text="发起中…" @confirm="start">
      <DynamicForm v-model="startForm" :schema="startSchema" />
    </AppModal>
  </PageShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import PageShell from '../components/PageShell.vue'
import DataTable from '../components/DataTable.vue'
import AppModal from '../components/AppModal.vue'
import DynamicForm from '../components/DynamicForm.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { request } from '../api'
import { useToast } from '../composables/useToast'
import { useRouter } from 'vue-router'

const { success, warning, error: notifyError } = useToast()
const router = useRouter()
const view = ref('todo'); const loading = ref(false); const saving = ref(false); const keyword = ref(''); const instanceStatus = ref('')
const overview = ref({}); const tasks = ref([]); const done = ref([]); const instances = ref([]); const initiated = ref([]); const showTask = ref(false); const showInstance = ref(false); const showStart = ref(false); const activeTask = ref({}); const instanceDetail = ref({}); const comment = ref('')
const startForm = ref({ processCode: 'workOrderApproval', businessType: 'WORK_ORDER', businessId: '', title: '' })
const tabs = [{ key: 'todo', label: '我的待办', hint: '当前账号有权处理的审批任务', badge: null }, { key: 'done', label: '我的已办', hint: '当前账号完成过的审批动作' }, { key: 'initiated', label: '我的发起', hint: '当前账号提交的审批流程' }, { key: 'instances', label: '流程实例', hint: '按状态监控业务审批实例' }]
const columns = computed(() => ['instances', 'initiated'].includes(view.value) ? [{ key: 'title', label: '业务标题' }, { key: 'process_code', label: '流程编码' }, { key: 'business_type', label: '业务类型', format: value => businessLabel(value) }, { key: 'starter', label: '发起人' }, { key: 'status', label: '状态', status: true }] : [{ key: 'title', label: '业务标题' }, { key: 'name', label: '当前节点' }, { key: 'processCode', label: '流程编码' }, { key: 'candidateGroup', label: '候选角色' }, { key: 'status', label: '状态', status: true }])
const activeTab = computed(() => tabs.find(item => item.key === view.value) || tabs[0])
const rows = computed(() => { const source = view.value === 'todo' ? tasks.value : view.value === 'done' ? done.value : view.value === 'initiated' ? initiated.value : instances.value; return source.filter(row => { const text = JSON.stringify(row).toLowerCase(); return (!keyword.value || text.includes(keyword.value.toLowerCase())) && (!instanceStatus.value || row.status === instanceStatus.value) }) })
const kpis = computed(() => [{ label: '我的待办', value: overview.value.pendingCount || 0, hint: '按当前角色权限' }, { label: '运行中实例', value: overview.value.runningCount || 0, hint: '业务审批进行中' }, { label: '已通过流程', value: overview.value.approvedCount || 0, hint: '当前租户累计' }, { label: '今日审批动作', value: overview.value.todayActionCount || 0, hint: '同意 / 驳回 / 签收' }])
const instanceStatuses = ['RUNNING', 'APPROVED', 'REJECTED', 'CANCELLED']
const startSchema = [{ key: 'businessType', label: '业务类型', type: 'select', options: [{ value: 'WORK_ORDER', label: '工单发布审批' }, { value: 'BUSINESS', label: '业务流转' }, { value: 'COMMON', label: '通用审批' }], required: true }, { key: 'businessId', label: '业务主键', placeholder: '工单审批填写工单 ID；其他流程可留空', required: true }, { key: 'title', label: '实例标题', placeholder: '留空则由服务端自动生成', span: 2 }]
async function load() { loading.value = true; try { const instanceQuery = instanceStatus.value ? `?status=${encodeURIComponent(instanceStatus.value)}` : ''; const [summary, todo, completed, allInstances, myInstances] = await Promise.all([request('/bpm/overview'), request('/bpm/tasks?scope=todo'), request('/bpm/tasks?scope=done'), request(`/bpm/instances${instanceQuery}`), request(`/bpm/instances?scope=mine${instanceStatus.value ? `&status=${encodeURIComponent(instanceStatus.value)}` : ''}`)]); overview.value = summary || {}; tasks.value = todo || []; done.value = completed || []; instances.value = allInstances || []; initiated.value = myInstances || [] } catch (requestError) { notifyError(requestError.message) } finally { loading.value = false } }
function switchView(next) { view.value = next; keyword.value = ''; if (next !== 'instances') instanceStatus.value = ''; load() }
function openTask(row) { activeTask.value = row; comment.value = ''; showTask.value = true }
async function claim(row) { try { await request(`/bpm/tasks/${row.taskId}/claim`, { method: 'POST' }); success('任务已签收'); await load() } catch (requestError) { notifyError(requestError.message) } }
async function complete(approved) { if (!approved && !comment.value) return warning('驳回时请填写审批意见') ; saving.value = true; try { await request(`/bpm/tasks/${activeTask.value.taskId}/complete`, { method: 'POST', body: JSON.stringify({ approved, comment: comment.value }) }); showTask.value = false; success(approved ? '审批已通过，业务状态将自动回写' : '审批已驳回'); await load() } catch (requestError) { notifyError(requestError.message) } finally { saving.value = false } }
async function openInstance(id) { if (!id) return; try { const result = await request(`/bpm/instances/${id}`) || {}; if (result.instance?.business_type === 'ERP_RECORD' && result.instance.business_id) { try { result.erpRecord = await request(`/erp/records/${result.instance.business_id}`) } catch {} } instanceDetail.value = result; showInstance.value = true } catch (requestError) { notifyError(requestError.message) } }
async function cancel(row) { if (!window.confirm(`确认撤回“${row.title}”吗？`)) return; try { await request(`/bpm/instances/${row.flowable_instance_id}/cancel`, { method: 'POST' }); success('流程已撤回'); await load() } catch (requestError) { notifyError(requestError.message) } }
function openStart() { startForm.value = { processCode: 'workOrderApproval', businessType: 'WORK_ORDER', businessId: '', title: '' }; showStart.value = true }
async function start() { if (!startForm.value.businessId) return warning('请填写业务主键，确保审批可以回写业务单据'); saving.value = true; try { await request('/bpm/process-instances', { method: 'POST', body: JSON.stringify(startForm.value) }); showStart.value = false; success('审批流程已发起'); view.value = 'instances'; await load() } catch (requestError) { notifyError(requestError.message) } finally { saving.value = false } }
function businessLabel(value) { return ({ WORK_ORDER: '工单', BUSINESS: '业务流转', PURCHASE: '采购', QUALITY: '质量', COMMON: '通用' }[value] || value || '-') }
function statusLabel(value) { return ({ RUNNING: '运行中', APPROVED: '已通过', REJECTED: '已驳回', CANCELLED: '已撤回', TODO: '待处理', DONE: '已办' }[value] || value || '-') }
function formatDate(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '-' }
onMounted(load)
</script>
