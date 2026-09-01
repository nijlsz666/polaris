<template>
  <PageShell :title="module.title" :description="module.description">
    <template #actions>
      <button class="button button-ghost" :disabled="loading" @click="resetView">{{ loading ? '同步中…' : '↻ 刷新' }}</button>
      <button class="button button-ghost" @click="router.push('/approval')">审批中心</button>
      <button v-if="moduleKey === 'procurement'" class="button button-ai" @click="router.push('/procurement/ai-create')">✦ AI 创建采购申请审批</button>
      <button class="button button-primary" @click="openCreate">+ {{ module.action }}</button>
    </template>

    <div class="subnav erp-module-tabs"><button v-for="item in module.tabs" :key="item.key" :class="['subnav-item', { active: tab === item.key }]" @click="selectTab(item.key)">{{ item.label }}<span v-if="item.count">{{ item.count }}</span></button></div>
    <div v-if="notice" :class="['notice-bar', `notice-${notice.type}`]"><span>{{ notice.type === 'warning' ? '!' : '✓' }}</span>{{ notice.message }}</div>

    <div class="business-kpis"><div v-for="item in activeKpis" :key="item.label"><span>{{ item.label }}</span><strong :class="item.tone">{{ item.value }}</strong><small>{{ item.hint }}</small></div></div>

    <div class="business-layout">
    <section class="panel business-table-panel">
        <div class="panel-heading"><div><h3>{{ activeTabLabel }}</h3><p>{{ tabHint }}</p></div><div class="table-heading-tools"><span class="data-hint">{{ loading ? '同步中…' : `${filteredRows.length} 条记录` }}</span><button class="button button-ghost button-small" @click="exportRows">下载台账</button></div></div>
        <div class="filter-row"><input v-model="keyword" :placeholder="`搜索${module.searchPlaceholder}`" /><select v-model="statusFilter"><option value="">全部状态</option><option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select><span class="filter-spacer"></span><span class="data-hint">当前租户 · 华东制造中心</span></div>
    <div class="table-wrap"><table class="data-table business-table"><thead><tr><th v-for="column in columns" :key="column.key">{{ column.label }}</th><th>操作</th></tr></thead><tbody><tr v-for="row in filteredRows" :key="row.id"><td v-for="column in columns" :key="column.key"><template v-if="column.key === 'status'"><span :class="['status-pill', `status-${String(row.status).toLowerCase()}`]">{{ statusLabel(row.status) }}</span></template><template v-else-if="column.key === 'amount'"><b class="amount-cell">{{ row.amount }}</b></template><template v-else><span :class="column.key === 'no' ? 'primary-cell' : ''">{{ row[column.key] || '-' }}</span></template></td><td><button class="table-action" @click="viewRow(row)">查看</button><button v-if="canSubmitApproval(row)" class="table-action" @click="submitApproval(row)">提交审批</button><button v-else class="table-action" @click="advanceRow(row)">{{ row.status === 'COMPLETED' || row.status === 'PAID' ? '归档' : '推进' }}</button></td></tr><tr v-if="!filteredRows.length"><td :colspan="columns.length + 1" class="empty-state">暂无符合条件的业务单据</td></tr></tbody></table></div>
      </section>

      <aside class="panel business-side-panel">
        <div class="panel-heading">
          <div><h3>业务流程</h3><p>业务流转与审批流程统一查看</p></div>
          <button class="table-action" type="button" @click="openWorkflowConfig">指定审批流程</button>
        </div>
        <div class="workflow-binding-card">
          <div class="workflow-binding-head"><span class="workflow-binding-icon">◇</span><div><b>{{ activeWorkflow.name }}</b><small>{{ activeWorkflow.code }} · V{{ activeWorkflow.version || 1 }}</small></div><span class="workflow-published">{{ activeWorkflow.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span></div>
          <p>当前业务功能提交审批时，将按此流程创建审批实例。</p>
          <WorkflowDiagram :steps="activeWorkflow.steps" :states="workflowStates" compact />
        </div>
        <div class="business-process-title"><b>业务节点</b><small>标准业务流程，可按模块扩展</small></div>
        <div class="business-process"><div v-for="(item, index) in module.process" :key="item.label" :class="['business-process-step', { done: index < 2, active: index === 2 }]" @click="showNotice(`${item.label}节点已选中，可从左侧台账继续操作`)"><span>{{ index + 1 }}</span><div><b>{{ item.label }}</b><small>{{ item.detail }}</small></div></div></div>
        <div class="business-side-callout"><b>系统提示</b><p>{{ module.callout }}</p></div>
      </aside>
    </div>

    <AppModal v-model="showModal" :title="module.action" subtitle="完善单头、行项目和来源信息后保存，单据将进入标准业务流程。" size="document" hide-footer>
      <div class="document-form">
        <div class="document-form-grid">
          <label>单据编号<input v-model.trim="form.no" placeholder="系统自动生成，可手动填写" /></label>
          <label>{{ module.formLabels.name }}<input v-model.trim="form.name" :placeholder="module.formLabels.namePlaceholder" /></label>
          <label>{{ module.formLabels.partner }}<input v-model.trim="form.partner" :placeholder="module.formLabels.partnerPlaceholder" /></label>
          <label>申请 / 业务日期<input v-model="form.businessDate" type="date" /></label>
          <label>组织编码<input v-model.trim="form.orgCode" placeholder="例如：CN-EAST-01" /></label>
          <label>部门编码<input v-model.trim="form.departmentCode" placeholder="例如：PURCHASE" /></label>
          <label>申请人 / 负责人<input v-model.trim="form.requesterCode" placeholder="工号或用户名" /></label>
          <label>币种 / {{ module.formLabels.amount }}<input v-model.trim="form.amount" placeholder="例如：128000" /></label>
          <label>要求交期<input v-model="form.deliveryDate" type="date" /></label>
          <label>付款条件<input v-model.trim="form.paymentTerms" placeholder="例如：月结 30 天" /></label>
          <label>来源类型<input v-model.trim="form.sourceType" placeholder="MRP / 部门请购 / 销售订单" /></label>
          <label>来源单号<input v-model.trim="form.sourceDocNo" placeholder="可选，关联来源单据" /></label>
        </div>
        <section class="document-lines">
          <div class="document-lines-heading"><div><b>行项目</b><small>采购申请必须填写；其他单据也建议按行维护物料、数量和金额。</small></div><button class="button button-ghost button-small" type="button" @click="addLine">+ 添加行</button></div>
          <div class="document-lines-table-wrap"><table class="document-lines-table"><thead><tr><th>#</th><th>物料 / 产品</th><th>规格</th><th>单位</th><th>数量</th><th>单价</th><th>税率%</th><th>需求日期</th><th>金额</th><th></th></tr></thead><tbody><tr v-for="(line, index) in form.lines" :key="line._key"><td>{{ index + 1 }}</td><td><input v-model.trim="line.materialCode" placeholder="编码" /><input v-model.trim="line.materialName" class="line-name-input" placeholder="名称（必填）" /></td><td><input v-model.trim="line.specification" placeholder="规格型号" /></td><td><input v-model.trim="line.unit" placeholder="件" /></td><td><input v-model.number="line.requestedQty" type="number" min="0.000001" step="0.000001" /></td><td><input v-model.number="line.unitPrice" type="number" min="0" step="0.01" /></td><td><input v-model.number="line.taxRate" type="number" min="0" step="0.01" /></td><td><input v-model="line.requiredDate" type="date" /></td><td class="line-amount">{{ formatAmount(lineAmount(line)) }}</td><td><button class="line-remove" type="button" :disabled="form.lines.length <= 1" @click="removeLine(index)">×</button></td></tr></tbody></table></div>
          <div class="document-lines-total">合计：<b>{{ formatAmount(form.lines.reduce((sum, line) => sum + lineAmount(line), 0)) }}</b></div>
        </section>
        <label class="document-remark">备注<textarea v-model.trim="form.remark" rows="3" placeholder="填写业务说明、交期、质量或特殊要求"></textarea></label>
        <div class="business-modal-tip">保存后单据进入“{{ module.process[0].label }}”节点；待评审单据可以直接发起审批。</div>
        <div class="modal-actions"><button class="button button-ghost" type="button" @click="showModal = false">取消</button><button class="button button-primary" type="button" :disabled="saving" @click="saveRow">{{ saving ? '保存中…' : '保存单据' }}</button></div>
      </div>
    </AppModal>

    <AppModal v-model="showDetail" :title="detail.title || '单据详情'" subtitle="单头信息、行项目、审批状态和流程轨迹" size="document" hide-footer>
      <div v-if="detail.id" class="document-detail">
        <div class="document-detail-head"><div><b>{{ detail.no }}</b><span>{{ businessTypeLabel(detail.type) }} · 创建人 {{ detail.createdBy || '-' }} · {{ formatDate(detail.createdAt) }}</span></div><StatusBadge :value="detail.status" :label="statusLabel(detail.status)" /></div>
        <div class="document-detail-grid"><span><small>业务内容</small><b>{{ detail.name || '-' }}</b></span><span><small>往来单位</small><b>{{ detail.partner || '-' }}</b></span><span><small>组织 / 部门</small><b>{{ detail.orgCode || '-' }} / {{ detail.departmentCode || '-' }}</b></span><span><small>申请人 / 负责人</small><b>{{ detail.requesterCode || detail.owner || '-' }}</b></span><span><small>业务日期</small><b>{{ detail.date || '-' }}</b></span><span><small>要求交期</small><b>{{ detail.deliveryDate || '-' }}</b></span><span><small>来源单据</small><b>{{ detail.sourceType || '-' }} / {{ detail.sourceDocNo || '-' }}</b></span><span><small>金额 / 税额</small><b>{{ detail.amount || '-' }} / {{ formatAmount(detail.taxAmount) }}</b></span></div>
        <section class="document-detail-section"><div class="document-section-title"><h3>行项目（{{ (detail.lines || []).length }}）</h3><span>数量、单价、税率与交期</span></div><div class="document-lines-table-wrap"><table class="document-lines-table detail-lines"><thead><tr><th>#</th><th>物料 / 产品</th><th>规格</th><th>单位</th><th>申请数量</th><th>单价</th><th>税率</th><th>需求日期</th><th>金额</th></tr></thead><tbody><tr v-for="line in detail.lines || []" :key="line.id || line.lineNo"><td>{{ line.lineNo }}</td><td><b>{{ line.materialCode || '-' }}</b><span>{{ line.materialName }}</span></td><td>{{ line.specification || '-' }}</td><td>{{ line.unit || '-' }}</td><td>{{ line.requestedQty }}</td><td>{{ formatAmount(line.unitPrice) }}</td><td>{{ line.taxRate || 0 }}%</td><td>{{ line.requiredDate || '-' }}</td><td>{{ formatAmount(line.amountValue) }}</td></tr><tr v-if="!(detail.lines || []).length"><td colspan="9" class="empty-state">暂无行项目</td></tr></tbody></table></div></section>
        <section class="document-detail-section document-workflow-section"><div class="document-section-title"><div><h3>审批流程</h3><small class="document-section-subtitle">业务功能绑定的已发布流程</small></div><span>{{ activeWorkflow.name }} · V{{ activeWorkflow.version || 1 }}</span></div><div class="document-workflow-meta"><span><b>流程编码</b>{{ activeWorkflow.code }}</span><span><b>当前状态</b>{{ approvalInstance ? statusLabel(approvalInstance.status) : '尚未发起' }}</span><span><b>定义方式</b>流程中心可视化配置</span></div><WorkflowDiagram :steps="activeWorkflow.steps" :states="workflowStates" /></section>
        <section class="document-detail-section"><div class="document-section-title"><div><h3>审批 Timeline</h3><small class="document-section-subtitle">按时间记录创建、签收、审批和意见</small></div><span v-if="approvalInstance">{{ statusLabel(approvalInstance.status) }}</span><span v-else>尚未发起</span></div><div class="document-approval-timeline"><div class="document-timeline-item document-timeline-created"><i>●</i><span><b>单据创建</b><small>{{ detail.createdBy || detail.owner || '当前用户' }} · {{ formatDate(detail.createdAt) }}</small></span></div><div v-for="item in approvalDetail.history || []" :key="`${item.taskId}-${item.startTime}`" :class="['document-timeline-item', { 'document-timeline-done': item.endTime }]" ><i>{{ item.endTime ? '✓' : '○' }}</i><span><b>{{ item.name }}</b><small>{{ item.assignee || '待签收' }} · {{ formatDate(item.startTime) }}<template v-if="item.endTime"> → {{ formatDate(item.endTime) }}</template></small><em v-if="actionFor(item)">{{ actionFor(item) }}</em></span></div><div v-if="!(approvalDetail.history || []).length" class="document-timeline-empty">尚未发起审批，提交后会在这里显示完整处理记录。</div></div></section>
        <div class="modal-actions"><button v-if="canSubmitApproval(detail)" class="button button-primary" type="button" @click="submitApproval(detail)">提交审批</button><button class="button button-ghost" type="button" @click="showDetail = false">关闭</button></div>
      </div>
    </AppModal>

    <AppModal v-model="showWorkflowConfig" title="指定审批流程" subtitle="先在流程中心定义流程，再将已发布版本绑定到当前业务功能。" size="large" hide-footer>
      <div class="workflow-config">
        <div class="workflow-config-intro"><span class="workflow-config-icon">◎</span><div><b>业务功能：{{ module.title }} · {{ activeTabLabel }}</b><p>绑定只影响新提交的单据；已运行中的流程继续按原版本执行。</p></div><span class="workflow-config-status">{{ selectedWorkflow?.status === 'PUBLISHED' ? '仅可绑定已发布版本' : '请选择已发布版本' }}</span></div>
        <div class="workflow-config-grid">
          <section class="workflow-config-options"><div class="workflow-config-section-head"><div><h3>可用流程定义</h3><small>系统模板和管理员自定义流程均可选择</small></div><span>{{ workflowCatalog.length }} 个</span></div><button v-for="flow in workflowCatalog" :key="flow.code" :class="['workflow-option', { selected: selectedWorkflowCode === flow.code }]" type="button" @click="selectedWorkflowCode = flow.code"><span class="workflow-option-radio">{{ selectedWorkflowCode === flow.code ? '✓' : '' }}</span><span><b>{{ flow.name }}</b><small>{{ flow.code }} · {{ flow.category }} · V{{ flow.version || 1 }}</small></span><em>{{ flow.status === 'PUBLISHED' ? '已发布' : '草稿' }}</em></button></section>
          <section class="workflow-config-preview"><div class="workflow-config-section-head"><div><h3>流程预览</h3><small>审批人、条件和业务动作由流程定义决定</small></div></div><div v-if="selectedWorkflow" class="workflow-preview-card"><div class="workflow-preview-title"><span class="workflow-binding-icon">◇</span><div><b>{{ selectedWorkflow.name }}</b><small>{{ selectedWorkflow.description || '通过可视化设计器配置人工审批、条件分支、业务动作和子流程。' }}</small></div></div><WorkflowDiagram :steps="selectedWorkflow.steps" :states="previewWorkflowStates" compact /><div class="workflow-preview-note"><b>怎么定义？</b><span>系统提供标准模板；管理员也可以在流程中心自由新增节点、设置候选角色、条件分支和回写动作，发布后再绑定到业务功能。</span></div></div><div v-else class="empty-operation">暂无可绑定的流程定义</div></section>
        </div>
        <div class="modal-actions"><button class="button button-ghost" type="button" @click="showWorkflowConfig = false">取消</button><button class="button button-primary" type="button" :disabled="!selectedWorkflow || selectedWorkflow.status !== 'PUBLISHED'" @click="saveWorkflowBinding">保存绑定</button></div>
      </div>
    </AppModal>
  </PageShell>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageShell from '../components/PageShell.vue'
import AppModal from '../components/AppModal.vue'
import StatusBadge from '../components/StatusBadge.vue'
import WorkflowDiagram from '../components/WorkflowDiagram.vue'
import { request } from '../api'

const route = useRoute(); const router = useRouter(); const props = defineProps({ module: { type: String, default: 'sales' } })
const moduleKey = computed(() => props.module || String(route.params.module || 'sales'))
const definitions = {
  sales: { title: '销售管理', description: '从报价、订单、交付到回款，建立客户需求与生产执行的连接。', action: '新建销售订单', searchPlaceholder: '订单号 / 客户 / 产品', tabs: [{ key: 'orders', label: '销售订单', count: 128 }, { key: 'delivery', label: '发货计划', count: 19 }, { key: 'customers', label: '客户管理' }], kpis: [{ label: '本月订单额', value: '¥ 846.0 万', hint: '较上月 +12.8%', tone: 'blue' }, { label: '待评审订单', value: '3 张', hint: '需今日处理', tone: 'orange' }, { label: '待发货订单', value: '19 张', hint: '已完工待交付', tone: 'violet' }, { label: '订单准交率', value: '96.4%', hint: '较上月 +2.1%', tone: 'green' }], process: [{ label: '订单录入', detail: '客户需求确认' }, { label: '信用 / 价格评审', detail: '风控与毛利校验' }, { label: '计划协同', detail: '锁定交期与产能' }, { label: '交付与回款', detail: '发货、开票、收款' }], callout: '销售订单审批通过后，可自动触发 MRP 需求计算，减少销售与计划之间的信息等待。', formLabels: { no: '订单编号', name: '产品 / 服务', namePlaceholder: '例如：P-AX90 控制器', partner: '客户名称', partnerPlaceholder: '例如：苏州智造科技', amount: '订单金额' } },
  procurement: { title: '采购管理', description: '围绕物料需求、供应商协同与到货质量，管理采购成本与交付风险。', action: '新建采购申请', searchPlaceholder: '单号 / 物料 / 供应商', tabs: [{ key: 'requisitions', label: '采购申请', count: 12 }, { key: 'drafts', label: '我的草稿' }, { key: 'orders', label: '采购订单', count: 34 }, { key: 'suppliers', label: '供应商管理' }], kpis: [{ label: '本月采购额', value: '¥ 368.4 万', hint: '预算执行 82%', tone: 'blue' }, { label: '待审批申请', value: '12 张', hint: '计划部门提交', tone: 'orange' }, { label: '在途采购单', value: '34 张', hint: '到货跟踪中', tone: 'violet' }, { label: '供应商准时率', value: '93.2%', hint: '较上月 +1.8%', tone: 'green' }], process: [{ label: '需求申请', detail: 'MRP / 部门请购' }, { label: '询价比价', detail: '价格与交期评审' }, { label: '采购下单', detail: '供应商协同' }, { label: '收料与对账', detail: '入库、发票、付款' }], callout: '低库存与 MRP 采购建议已统一归集，采购员可按交期风险优先处理。', formLabels: { no: '申请单号', name: '需求物料', namePlaceholder: '例如：RM-MOTOR-001 无刷电机', partner: '建议供应商', partnerPlaceholder: '例如：东莞精工电子', amount: '预计金额' } },
  finance: { title: '财务管理', description: '连接业务单据与财务结果，统一掌握应收、应付、成本与现金流。', action: '登记财务单据', searchPlaceholder: '单号 / 客户 / 供应商', tabs: [{ key: 'receivable', label: '应收管理', count: 28 }, { key: 'payable', label: '应付管理', count: 16 }, { key: 'cost', label: '成本核算' }], kpis: [{ label: '应收余额', value: '¥ 128.0 万', hint: '未来 7 天到期 ¥32.6万', tone: 'orange' }, { label: '应付余额', value: '¥ 86.4 万', hint: '本月待付款 16 笔', tone: 'violet' }, { label: '本月毛利率', value: '28.6%', hint: '较上月 +3.4%', tone: 'green' }, { label: '现金流预测', value: '¥ 246.8 万', hint: '未来 30 天净流入', tone: 'blue' }], process: [{ label: '业务确认', detail: '订单 / 入库 / 发货' }, { label: '开票与对账', detail: '票据和往来核对' }, { label: '收付结算', detail: '回款、付款、核销' }, { label: '成本与利润', detail: '归集、结转、分析' }], callout: '业务单据自动带出客户、税率与成本中心，月底结账可以直接追溯到订单和工单。', formLabels: { no: '财务单号', name: '业务摘要', namePlaceholder: '例如：8 月客户回款核销', partner: '往来单位', partnerPlaceholder: '例如：苏州智造科技', amount: '金额' } },
  master: { title: '主数据中心', description: '统一维护物料、BOM、客户、供应商与组织档案，让每一张业务单据使用同一套数据。', action: '新增主数据', searchPlaceholder: '编码 / 名称 / 版本', tabs: [{ key: 'materials', label: '物料主数据', count: 2468 }, { key: 'bom', label: 'BOM 版本', count: 318 }, { key: 'partners', label: '客户与供应商', count: 86 }], kpis: [{ label: '物料 SKU', value: '2,468', hint: '已启用 2,391 个', tone: 'blue' }, { label: '有效 BOM', value: '318', hint: '覆盖 96% 产品', tone: 'violet' }, { label: '合作伙伴', value: '86', hint: '客户 42 · 供应商 44', tone: 'green' }, { label: '待完善档案', value: '7', hint: '需主数据专员处理', tone: 'orange' }], process: [{ label: '编码申请', detail: '按分类规则生成' }, { label: '属性维护', detail: '单位、规格、税率' }, { label: '版本审核', detail: 'BOM 与工艺确认' }, { label: '发布使用', detail: '进入业务单据' }], callout: '建议所有业务人员通过主数据中心新增档案，避免出现同物料多编码和客户名称不一致。', formLabels: { no: '数据编码', name: '数据名称', namePlaceholder: '例如：P-AX90 控制器', partner: '数据分类 / 组织', partnerPlaceholder: '例如：成品 / 电控事业部', amount: '版本 / 单位' } }
}
const module = computed(() => definitions[moduleKey.value] || definitions.sales)
const workflowTemplates = {
  workOrderApproval: { code: 'workOrderApproval', name: '通用业务审批', category: '通用审批', description: '适用于销售、财务和主数据等业务功能的标准审批模板。', version: 1, status: 'PUBLISHED', steps: [{ label: '提交单据', detail: '业务发起' }, { label: '业务负责人审批', detail: '角色审批' }, { label: '专业复核', detail: '规则校验' }, { label: '完成', detail: '业务状态回写' }] },
  purchaseRequisitionApproval: { code: 'purchaseRequisitionApproval', name: '采购申请审批', category: '采购协同', description: '采购申请按部门、采购和风险条件进入不同审批节点。', version: 1, status: 'PUBLISHED', steps: [{ label: '提交申请', detail: '需求申请' }, { label: '部门负责人审批', detail: '部门负责人' }, { label: '采购经理审批', detail: '采购经理' }, { label: '财务 / 管理者审批', detail: '按金额条件分支' }, { label: '完成', detail: '业务状态回写' }] }
}
const moduleWorkflowDefaults = { sales: 'workOrderApproval', procurement: 'purchaseRequisitionApproval', finance: 'workOrderApproval', master: 'workOrderApproval' }
const moduleWorkflowNames = { sales: '销售订单审批', procurement: '采购申请审批', finance: '财务单据审批', master: '主数据变更审批' }
const moduleWorkflowSteps = {
  sales: [{ label: '提交订单', detail: '业务发起' }, { label: '信用 / 价格评审', detail: '销售负责人' }, { label: '计划协同', detail: '计划部门' }, { label: '完成', detail: '状态回写' }],
  finance: [{ label: '提交单据', detail: '业务发起' }, { label: '业务负责人审批', detail: '业务复核' }, { label: '财务复核', detail: '财务角色' }, { label: '完成', detail: '状态回写' }],
  master: [{ label: '提交变更', detail: '主数据申请' }, { label: '数据专员审核', detail: '字段校验' }, { label: '业务负责人审批', detail: '版本确认' }, { label: '发布使用', detail: '状态回写' }]
}
const tab = ref(module.value.tabs.some(item => item.key === route.params.tab) ? route.params.tab : module.value.tabs[0].key); const keyword = ref(''); const statusFilter = ref(''); const showModal = ref(false); const showDetail = ref(false); const notice = ref(null)
const saving = ref(false); const detail = ref({}); const approvalDetail = ref({}); const approvalInstance = ref(null)
const processDefinitions = ref([]); const workflowBindings = ref({}); const selectedWorkflowCode = ref(''); const showWorkflowConfig = ref(false)
const form = ref(newDocumentForm()); const rows = ref([]); const loading = ref(false)
const activeTabLabel = computed(() => module.value.tabs.find(item => item.key === tab.value)?.label || module.value.tabs[0].label)
const activeKpis = computed(() => module.value.kpis)
const workflowCatalog = computed(() => {
  const catalog = new Map(Object.values(workflowTemplates).map(item => [item.code, item]))
  processDefinitions.value.forEach(item => {
    const code = item.process_code || item.processCode
    if (code) catalog.set(code, { ...catalog.get(code), code, name: item.process_name || item.processName || catalog.get(code)?.name || code, category: item.category || catalog.get(code)?.category || '通用审批', description: item.description || catalog.get(code)?.description, version: item.version || catalog.get(code)?.version || 1, status: item.status || 'PUBLISHED' })
  })
  return [...catalog.values()].map(item => ({ ...item, steps: stepsForWorkflow(item.code) }))
})
const activeWorkflow = computed(() => {
  const defaultCode = moduleWorkflowDefaults[moduleKey.value] || 'workOrderApproval'
  const code = workflowBindings.value[moduleKey.value] || defaultCode
  const remote = workflowCatalog.value.find(item => item.code === code) || workflowTemplates[code]
  const fallback = { code, name: '未指定审批流程', category: '待配置', version: 1, status: 'DRAFT', steps: [{ label: '提交单据', detail: '业务发起' }, { label: '待指定审批流程', detail: '请先完成流程绑定' }, { label: '完成', detail: '业务状态回写' }] }
  return { ...fallback, ...(remote || {}), name: code === defaultCode ? (moduleWorkflowNames[moduleKey.value] || remote?.name || code) : (remote?.name || code), steps: stepsForWorkflow(code) }
})
const selectedWorkflow = computed(() => workflowCatalog.value.find(item => item.code === selectedWorkflowCode.value) || null)
const workflowStates = computed(() => showDetail.value ? workflowStateList(activeWorkflow.value.steps) : activeWorkflow.value.steps.map((_, index) => index === 0 ? 'active' : 'upcoming'))
const previewWorkflowStates = computed(() => activeWorkflow.value.steps.map((_, index) => index === 0 ? 'active' : 'upcoming'))
const tabHint = computed(() => ({ orders: '销售订单从评审到交付的完整台账', delivery: '按交期、生产状态与客户优先级排定发货', customers: '客户信用、价格与合作状态统一维护', requisitions: '来自 MRP 与部门请购的物料需求', drafts: '当前账号保存但尚未提交审批的采购申请', suppliers: '供应商交期、质量与价格绩效', payable: '采购入库、发票和付款核销', receivable: '销售发货、开票和回款跟踪', cost: '订单、工单与物料成本归集', materials: '物料编码、规格、单位与库存策略', bom: '产品结构、版本与生效范围', partners: '客户与供应商统一档案' }[tab.value] || '跨部门业务台账与执行状态'))
const columns = computed(() => moduleKey.value === 'master' ? [{ key: 'no', label: '编码' }, { key: 'name', label: '名称' }, { key: 'partner', label: '分类 / 属性' }, { key: 'amount', label: '版本 / 单位' }, { key: 'date', label: '更新时间' }, { key: 'status', label: '状态' }] : [{ key: 'no', label: '单据编号' }, { key: 'name', label: '业务内容' }, { key: 'partner', label: moduleKey.value === 'finance' ? '往来单位' : moduleKey.value === 'procurement' ? '供应商' : '客户' }, { key: 'amount', label: '金额' }, ...(moduleKey.value === 'procurement' && tab.value === 'requisitions' ? [{ key: 'lineCount', label: '行数' }] : []), { key: 'date', label: '业务日期' }, { key: 'owner', label: '负责人' }, { key: 'status', label: '状态' }])
const filteredRows = computed(() => rows.value.filter(row => (!statusFilter.value || row.status === statusFilter.value) && (!keyword.value || `${row.no}${row.name}${row.partner}${row.owner}`.toLowerCase().includes(keyword.value.toLowerCase()))))
const statusOptions = computed(() => [...new Set(rows.value.map(row => row.status))].map(value => ({ value, label: statusLabel(value) })))

watch(moduleKey, value => { const definition = definitions[value] || definitions.sales; tab.value = definition.tabs.some(item => item.key === route.params.tab) ? route.params.tab : definition.tabs[0].key; rows.value = []; keyword.value = ''; statusFilter.value = ''; loadRecords() })
watch(() => route.params.tab, value => { if (value && module.value.tabs.some(item => item.key === value)) { tab.value = value; loadRecords() } })

function stepsForWorkflow(code) {
  if (code === moduleWorkflowDefaults[moduleKey.value] && moduleWorkflowSteps[moduleKey.value]) return moduleWorkflowSteps[moduleKey.value]
  return workflowTemplates[code]?.steps || [{ label: '提交单据', detail: '业务发起' }, { label: '人工审批', detail: '候选角色处理' }, { label: '完成', detail: '业务状态回写' }]
}
function workflowStateList(steps) {
  const instanceStatus = String(approvalInstance.value?.status || '').toUpperCase()
  if (!approvalInstance.value) return steps.map((_, index) => index === 0 ? 'active' : 'upcoming')
  if (instanceStatus === 'APPROVED') return steps.map(() => 'done')
  if (instanceStatus === 'REJECTED' || instanceStatus === 'CANCELLED') {
    const historyCount = (approvalDetail.value.history || []).length
    return steps.map((_, index) => index < Math.max(1, historyCount) ? 'done' : index === Math.max(1, historyCount) ? 'active' : 'upcoming')
  }
  const history = approvalDetail.value.history || []
  return steps.map((_, index) => {
    if (index === 0) return 'done'
    const task = history[index - 1]
    if (task?.endTime) return 'done'
    if (task) return 'active'
    return 'upcoming'
  })
}
function actionFor(item) {
  const action = (approvalDetail.value.actions || []).find(row => String(row.flowable_task_id || row.taskId) === String(item.taskId))
  if (!action) return ''
  const actionLabel = action.action_code === 'CLAIM' ? '已签收' : action.action_code === 'COMPLETE' ? '已提交审批结果' : action.action_code
  return `${actionLabel}${action.comment_text ? ` · ${action.comment_text}` : ''}`
}
function loadLocalWorkflowBindings() {
  try { workflowBindings.value = JSON.parse(localStorage.getItem('polaris-workflow-bindings') || '{}') || {} } catch { workflowBindings.value = {} }
}
async function loadWorkflowConfig() {
  loadLocalWorkflowBindings()
  const [definitionResult, bindingResult] = await Promise.allSettled([request('/bpm/process-definitions', { silent: true }), request('/bpm/bindings', { silent: true })])
  if (definitionResult.status === 'fulfilled') processDefinitions.value = definitionResult.value || []
  if (bindingResult.status === 'fulfilled') {
    const bindings = bindingResult.value || []
    workflowBindings.value = bindings.reduce((result, item) => { result[String(item.business_function || item.businessFunction).toLowerCase()] = item.process_code || item.processCode; return result }, { ...workflowBindings.value })
  }
}
function openWorkflowConfig() { selectedWorkflowCode.value = activeWorkflow.value.code; showWorkflowConfig.value = true; if (!processDefinitions.value.length) loadWorkflowConfig() }
async function saveWorkflowBinding() {
  if (!selectedWorkflow.value || selectedWorkflow.value.status !== 'PUBLISHED') return
  try {
    await request(`/bpm/bindings/${encodeURIComponent(moduleKey.value)}`, { method: 'PUT', body: JSON.stringify({ processCode: selectedWorkflow.value.code }) })
  } catch (error) {
    showNotice(`流程绑定保存失败：${error.message}`, 'error')
    return
  }
  workflowBindings.value = { ...workflowBindings.value, [moduleKey.value]: selectedWorkflow.value.code }
  localStorage.setItem('polaris-workflow-bindings', JSON.stringify(workflowBindings.value))
  showWorkflowConfig.value = false
  showNotice(`已将“${selectedWorkflow.value.name}”绑定到${module.value.title}`)
}
function newLine() { return { _key: `${Date.now()}-${Math.random()}`, materialCode: '', materialName: '', specification: '', unit: '件', requestedQty: 1, unitPrice: 0, taxRate: 13, requiredDate: '', warehouseCode: '', remark: '' } }
function newDocumentForm() { return { no: '', name: '', partner: '', amount: '', businessDate: new Date().toISOString().slice(0, 10), deliveryDate: '', orgCode: '', departmentCode: '', requesterCode: '', currency: 'CNY', paymentTerms: '', sourceType: '', sourceDocNo: '', remark: '', lines: [newLine()] } }
function statusLabel(value) { return ({ DRAFT: '草稿', REVIEW: '待评审', CONFIRMED: '已确认', APPROVED: '已通过', DELIVERING: '交付中', COMPLETED: '已完成', ORDERED: '已下单', IN_TRANSIT: '在途', RECEIVED: '已收货', PENDING: '待处理', INVOICED: '已开票', PAID: '已结算', ACTIVE: '已启用', PENDING_APPROVAL: '审批中', REJECTED: '已驳回' }[value] || value || '-') }
function businessTypeLabel(value) { return ({ orders: '销售订单', requisitions: '采购申请', delivery: '发货计划', payable: '应付单', receivable: '应收单', cost: '成本单', materials: '物料档案', bom: 'BOM 版本', partners: '合作伙伴' }[String(value || '').toLowerCase()] || value || '-') }
function lineAmount(line) { return Number(line.requestedQty || 0) * Number(line.unitPrice || 0) }
function formatAmount(value) { const amount = Number(value || 0); return Number.isFinite(amount) ? amount.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '0.00' }
function formatDate(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '-' }
function addLine() { form.value.lines.push(newLine()) }
function removeLine(index) { if (form.value.lines.length > 1) form.value.lines.splice(index, 1) }
function selectTab(value) { tab.value = value; keyword.value = ''; statusFilter.value = ''; router.replace(`/${moduleKey.value}/${value}`) }
async function resetView() { keyword.value = ''; statusFilter.value = ''; await loadRecords(); showNotice('台账数据已刷新') }
function openCreate() { form.value = newDocumentForm(); const preservedInput = localStorage.getItem('polaris-purchase-ai-input'); if (preservedInput && moduleKey.value === 'procurement') { form.value.remark = `AI 输入（待人工整理）：${preservedInput}`; localStorage.removeItem('polaris-purchase-ai-input') }; showModal.value = true }
function canSubmitApproval(row) { return ['REVIEW', 'DRAFT'].includes(String(row?.status || '').toUpperCase()) }
async function saveRow() {
  if (!form.value.name.trim()) return showNotice('请先填写业务内容', 'warning')
  if (!form.value.lines.length || form.value.lines.some(line => !line.materialName.trim() || Number(line.requestedQty) <= 0)) return showNotice('请完善所有行项目的物料名称和数量', 'warning')
  saving.value = true
  const payload = { type: tab.value === 'drafts' ? 'requisitions' : tab.value, no: form.value.no, name: form.value.name, partner: form.value.partner, amount: form.value.amount, businessDate: form.value.businessDate, deliveryDate: form.value.deliveryDate, orgCode: form.value.orgCode, departmentCode: form.value.departmentCode, requesterCode: form.value.requesterCode, currency: form.value.currency, paymentTerms: form.value.paymentTerms, sourceType: form.value.sourceType, sourceDocNo: form.value.sourceDocNo, remark: form.value.remark, lines: form.value.lines.map(({ _key, ...line }) => line) }
  try {
    const endpoint = moduleKey.value === 'procurement' ? `/erp/${moduleKey.value}/records/draft` : `/erp/${moduleKey.value}/records`
    const saved = await request(endpoint, { method: 'POST', body: JSON.stringify(payload) })
    rows.value.unshift(saved); showModal.value = false; showNotice(moduleKey.value === 'procurement' ? '采购申请已保存到我的草稿' : `${module.value.action}已创建，已进入${module.value.process[0].label}节点`); await viewRow(saved)
  } catch (error) { showNotice(`保存失败：${error.message}`, 'error') } finally { saving.value = false }
}
async function viewRow(row) {
  try {
    const [recordResult, approvalResult] = await Promise.allSettled([request(`/erp/${moduleKey.value}/records/${row.id}`), request(`/bpm/instances/by-business?businessType=ERP_RECORD&businessId=${encodeURIComponent(row.id)}`)])
    detail.value = recordResult.status === 'fulfilled' ? recordResult.value : row
    const instances = approvalResult.status === 'fulfilled' ? approvalResult.value : []
    approvalInstance.value = instances[0] || null; approvalDetail.value = approvalInstance.value ? await request(`/bpm/instances/${approvalInstance.value.flowable_instance_id}`) : {}
    showDetail.value = true
  } catch (error) { showNotice(`打开单据失败：${error.message}`, 'error') }
}
async function submitApproval(row) {
  try {
    if (!activeWorkflow.value.code || activeWorkflow.value.status !== 'PUBLISHED') return showNotice('当前业务功能尚未绑定可用的已发布审批流程', 'warning')
    await request('/bpm/process-instances', { method: 'POST', body: JSON.stringify({ processCode: activeWorkflow.value.code, businessType: 'ERP_RECORD', businessId: String(row.id), title: `${row.no} · ${row.name} 审批`, variables: { domain: moduleKey.value.toUpperCase(), recordType: row.type, purchaseHighRisk: Number(row.amountValue || 0) > 50000, purchaseManagementRisk: Number(row.amountValue || 0) > 200000 } }) })
    showNotice(`${row.no} 已提交审批，已进入我的发起和审批人待办`); await loadRecords(); const refreshed = rows.value.find(item => String(item.id) === String(row.id)); await viewRow(refreshed || row)
  } catch (error) { showNotice(`提交审批失败：${error.message}`, 'error') }
}
async function advanceRow(row) {
  const nextStatus = moduleKey.value === 'sales' ? ({ REVIEW: 'CONFIRMED', CONFIRMED: 'DELIVERING', DELIVERING: 'COMPLETED' }[row.status]) : moduleKey.value === 'procurement' ? ({ REVIEW: 'ORDERED', ORDERED: 'IN_TRANSIT', IN_TRANSIT: 'RECEIVED' }[row.status]) : moduleKey.value === 'finance' ? ({ PENDING: 'INVOICED', INVOICED: 'PAID' }[row.status]) : ({ REVIEW: 'ACTIVE' }[row.status])
  if (!nextStatus) return showNotice(`${row.no} 当前已处于“${statusLabel(row.status)}”`, 'warning')
  try { const updated = await request(`/erp/${moduleKey.value}/records/${row.id}/transition`, { method: 'POST', body: JSON.stringify({ status: nextStatus }) }); Object.assign(row, updated); showNotice(`${row.no} 已推进至“${statusLabel(row.status)}”`) }
  catch (error) { showNotice(`推进失败：${error.message}`, 'error') }
}
function showNotice(message, type = 'success') { notice.value = { message, type }; window.clearTimeout(showNotice.timer); showNotice.timer = window.setTimeout(() => { notice.value = null }, 3200) }
function exportRows() { const content = [columns.value.map(column => column.label).join(','), ...filteredRows.value.map(row => columns.value.map(column => JSON.stringify(row[column.key] || '')).join(','))].join('\n'); const link = document.createElement('a'); link.href = `data:text/csv;charset=utf-8,${encodeURIComponent(content)}`; link.download = `${moduleKey.value}-${tab.value}.csv`; link.click(); showNotice('台账已下载为 CSV') }
async function loadRecords() {
  loading.value = true
  try {
    const recordType = tab.value === 'drafts' ? 'requisitions' : tab.value
    const scope = tab.value === 'drafts' ? '&scope=drafts' : ''
    const data = await request(`/erp/${moduleKey.value}/records?type=${encodeURIComponent(recordType)}&keyword=${encodeURIComponent(keyword.value)}&status=${encodeURIComponent(statusFilter.value)}${scope}`)
    rows.value = data || []
  } catch (error) { showNotice(`台账同步失败：${error.message}`, 'error') }
  finally { loading.value = false }
}
onMounted(() => { loadRecords(); loadWorkflowConfig() })
</script>
