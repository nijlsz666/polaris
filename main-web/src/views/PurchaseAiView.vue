<template>
  <PageShell title="AI 创建采购申请审批" description="用自然语言整理采购需求，系统会在人工确认后创建采购申请并发起审批。">
    <template #actions>
      <span class="purchase-ai-context">{{ context.tenantName || '当前租户' }} · {{ context.requesterCode || '当前用户' }}</span>
      <button class="button button-ghost" @click="fallbackManual">改用手工表单</button>
      <button class="button button-ghost" @click="router.push('/approval')">审批中心</button>
    </template>

    <div class="purchase-ai-steps">
      <div v-for="(item, index) in steps" :key="item.key" :class="['purchase-ai-step', { active: step === item.key, done: stepIndex > index }]">
        <span>{{ stepIndex > index ? '✓' : index + 1 }}</span><b>{{ item.label }}</b><small>{{ item.hint }}</small>
      </div>
    </div>

    <div class="purchase-ai-layout">
      <section class="panel purchase-ai-chat">
        <div class="panel-heading"><div><p class="eyebrow">AI PURCHASE ASSISTANT</p><h3>告诉 AI 你要采购什么</h3><p>只会整理当前租户可见信息；AI 不会直接创建单据或选择审批人。</p></div><span class="purchase-ai-secure">● 会话已隔离</span></div>
        <div class="purchase-ai-examples"><button v-for="example in examples" :key="example" type="button" @click="input = example">{{ example }}</button></div>
        <textarea v-model.trim="input" class="purchase-ai-input" rows="8" placeholder="例如：为研发部 9 月试产采购 500 个 RM-MOTOR-001 无刷电机，预计单价 18 元，9 月 1 日前到货。"></textarea>
        <div class="purchase-ai-input-foot"><span>支持自然语言、物料编码、数量、单价、日期、项目号</span><span>{{ input.length }}/1000</span></div>
        <div v-if="parseError" class="purchase-ai-error">{{ parseError }}</div>
        <div class="purchase-ai-chat-actions"><button class="button button-ghost" type="button" :disabled="busy || !input" @click="fallbackManual">AI 不可用？转手工创建</button><button class="button button-primary" type="button" :disabled="busy || !input" @click="generate">{{ busy && step === 'generating' ? '正在整理…' : '生成采购申请草稿 →' }}</button></div>
        <div class="purchase-ai-boundary"><b>本期 AI 边界</b><span>金额由服务端重算</span><span>物料编码必须命中主数据</span><span>审批人来自系统配置</span><span>提交前必须人工确认</span></div>
      </section>

      <section class="panel purchase-ai-preview">
        <div v-if="!draft && !submitted" class="purchase-ai-empty"><div class="purchase-ai-empty-icon">✦</div><h3>等待生成采购草稿</h3><p>生成后，这里会展示字段来源、缺失项、风险提示、金额计算和审批路由。所有字段都可以在提交前修改。</p></div>
        <div v-else-if="submitted" class="purchase-ai-submitted"><div class="purchase-ai-success-icon">✓</div><p class="eyebrow">PURCHASE APPROVAL STARTED</p><h3>采购申请审批已发起</h3><p>只有 ERP 单据和 BPM 实例都创建成功后才会显示此状态。</p><div class="purchase-ai-result-grid"><span><small>申请编号</small><b>{{ submitted.recordNo }}</b></span><span><small>单据状态</small><b>待评审 REVIEW</b></span><span><small>流程实例</small><b>{{ submitted.processInstance?.id || submitted.processInstance?.flowable_instance_id || '-' }}</b></span></div><button class="button button-primary" @click="router.push('/approval')">查看审批轨迹</button></div>
        <template v-else>
          <div class="panel-heading purchase-ai-preview-heading"><div><p class="eyebrow">STRUCTURED DRAFT · {{ sessionId || '-' }}</p><h3>采购申请预览</h3><p>字段由 AI 整理，提交时服务端会再次校验并重新计算。</p></div><span :class="['purchase-ai-confidence', confidence < .85 ? 'is-low' : '']">置信度 {{ Math.round(confidence * 100) }}%</span></div>

          <div v-if="missingItems.length || validation.errors?.length" class="purchase-ai-alert purchase-ai-alert-warning"><b>还需要确认 {{ missingItems.length + (validation.errors || []).length }} 项</b><span v-for="item in missingItems" :key="item">{{ fieldLabel(item) }}</span><span v-for="item in validation.errors || []" :key="item.field">{{ item.message }}</span></div>
          <div v-if="(validation.riskFlags || []).length" class="purchase-ai-alert purchase-ai-alert-risk"><b>风险提示</b><span v-for="item in validation.riskFlags" :key="item.code">{{ item.message }}</span></div>

          <section class="purchase-ai-section"><div class="purchase-ai-section-head"><div><b>基本信息</b><small>申请组织、部门和原因</small></div><span>可编辑</span></div><div class="purchase-ai-form-grid"><label>采购原因<input v-model.trim="requisition.reason" @change="scheduleValidate" placeholder="为什么需要采购" /><em>用户 / AI</em></label><label>申请组织<input v-model.trim="requisition.orgCode" @change="scheduleValidate" placeholder="组织编码" /><em>系统上下文</em></label><label>申请部门<select v-model="requisition.departmentCode" @change="scheduleValidate"><option value="">请选择部门</option><option v-for="item in context.departmentOptions || []" :key="item.code" :value="item.code">{{ item.name }}（{{ item.code }}）</option></select><em>系统配置</em></label><label>申请人<input :value="requisition.requesterCode" readonly /><em>当前登录用户</em></label><label>币种<select v-model="requisition.currency" @change="scheduleValidate"><option v-for="item in context.currencyOptions || ['CNY']" :key="item" :value="item">{{ item }}</option></select><em>规则默认</em></label><label>项目号 / 预算<input v-model.trim="requisition.projectCode" @change="scheduleValidate" placeholder="可选" /><em>待补充</em></label></div></section>

          <section class="purchase-ai-section"><div class="purchase-ai-section-head"><div><b>物料明细</b><small>金额、税额由服务端计算</small></div><button class="text-button" type="button" @click="addLine">+ 添加明细</button></div><div class="purchase-ai-lines-wrap"><table class="purchase-ai-lines"><thead><tr><th>物料编码 / 名称</th><th>规格</th><th>数量</th><th>单位</th><th>预计单价</th><th>需求日期</th><th>金额</th><th></th></tr></thead><tbody><tr v-for="(line, index) in requisition.lines || []" :key="line._key || index"><td><input v-model.trim="line.materialCode" list="purchase-material-options" placeholder="编码" @change="scheduleValidate" /><input v-model.trim="line.materialName" class="purchase-ai-line-name" placeholder="物料名称" @change="scheduleValidate" /></td><td><input v-model.trim="line.specification" placeholder="规格" /></td><td><input v-model.number="line.requestedQty" type="number" min="0" step="0.000001" @change="scheduleValidate" /></td><td><input v-model.trim="line.unit" placeholder="件" @change="scheduleValidate" /></td><td><input v-model.number="line.unitPrice" type="number" min="0" step="0.01" @change="scheduleValidate" /></td><td><input v-model="line.requiredDate" type="date" @change="scheduleValidate" /></td><td class="purchase-ai-line-amount">{{ formatAmount(localLineAmount(line)) }}</td><td><button class="purchase-ai-line-remove" type="button" :disabled="requisition.lines.length <= 1" @click="removeLine(index)">×</button></td></tr><tr v-if="!(requisition.lines || []).length"><td colspan="8" class="empty-state">请添加至少一条物料明细</td></tr></tbody></table></div></section>
          <datalist id="purchase-material-options"><option v-for="item in context.materialOptions || []" :key="item.materialCode" :value="item.materialCode">{{ item.materialName }}</option></datalist>

          <section class="purchase-ai-summary"><div><span>预计金额</span><strong>{{ formatAmount(validation.calculation?.total || localTotal) }} <small>{{ requisition.currency || 'CNY' }}</small></strong><em v-if="validation.calculation?.taxAmount">含税额 {{ formatAmount(validation.calculation.taxAmount) }}</em></div><div class="purchase-ai-route-summary"><span>建议审批链路</span><div><i v-for="(item, index) in route" :key="item.nodeCode"><b>{{ index + 1 }}</b>{{ item.nodeName }}<small>{{ item.candidateGroup }}</small></i></div></div></section>

          <section class="purchase-ai-evidence"><div class="purchase-ai-section-head"><div><b>AI 证据</b><small>原始输入与字段来源可追溯</small></div><button class="text-button" type="button" @click="showEvidence = !showEvidence">{{ showEvidence ? '收起' : '展开' }}</button></div><div v-if="showEvidence" class="purchase-ai-evidence-body"><p>{{ input }}</p><div v-for="item in evidence" :key="`${item.field}-${item.quote}`"><b>{{ fieldLabel(item.field) }}</b><span>{{ item.quote }}</span><small>{{ sourceLabel(item.source) }}</small></div></div></section>

          <label class="purchase-ai-route-confirm"><input v-model="routeConfirmed" type="checkbox" />我已核对采购申请字段、金额和审批链路，确认由系统配置的候选角色处理本次审批。</label>
          <div class="purchase-ai-preview-actions"><button class="button button-ghost" type="button" :disabled="busy" @click="validateNow">↻ 重新校验</button><button class="button button-primary" type="button" :disabled="busy || !canSubmit" @click="confirmSubmit">{{ busy && step === 'submitting' ? '提交中…' : '确认采购申请并发起审批' }}</button></div>
        </template>
      </section>
    </div>
  </PageShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageShell from '../components/PageShell.vue'
import { request } from '../api'
import { useToast } from '../composables/useToast'

const router = useRouter(); const { success, warning, error: notifyError } = useToast()
const input = ref(''); const context = ref({}); const sessionId = ref(''); const submissionKey = ref(''); const draft = ref(null); const evidence = ref([]); const submitted = ref(null); const busy = ref(false); const step = ref('empty'); const parseError = ref(''); const routeConfirmed = ref(false); const showEvidence = ref(false); const validation = ref({ missing: [], errors: [], warnings: [], riskFlags: [], route: [], calculation: {} }); let validateTimer = null
const steps = [{ key: 'empty', label: '输入需求', hint: '自然语言描述' }, { key: 'generating', label: 'AI 整理', hint: '提取结构化字段' }, { key: 'review', label: '人工确认', hint: '校验字段与路由' }, { key: 'submitted', label: '审批已发起', hint: 'ERP + BPM 关联' }]
const examples = ['为研发部采购 20 台无刷电机，预计单价 18 元，9 月 1 日前到货。', '采购 500 个 RM-MOTOR-001 无刷电机，用于 9 月试产，单价 18 元。']
const requisition = computed(() => draft.value?.requisition || { lines: [] }); const route = computed(() => validation.value.route || []); const confidence = computed(() => Number(draft.value?.confidence || .94)); const missingItems = computed(() => validation.value.missing || []); const evidenceItems = computed(() => evidence.value || []); const canSubmit = computed(() => Boolean(draft.value && !missingItems.value.length && !(validation.value.errors || []).length && route.value.length && routeConfirmed.value)); const localTotal = computed(() => (requisition.value.lines || []).reduce((sum, line) => sum + localLineAmount(line), 0)); const stepIndex = computed(() => ({ empty: 0, generating: 1, review: 2, submitted: 3 }[step.value] || 0))

async function loadContext() { try { context.value = await request('/ai/purchase-requisitions/context') } catch (error) { notifyError(`加载采购上下文失败：${error.message}`) } }
async function generate() { busy.value = true; parseError.value = ''; step.value = 'generating'; routeConfirmed.value = false; submissionKey.value = ''; submitted.value = null; try { const result = await request('/ai/purchase-requisitions/parse', { method: 'POST', body: JSON.stringify({ input: input.value, context: { source: 'procurement' } }) }); sessionId.value = result.sessionId; draft.value = result.draft; evidence.value = result.evidence || []; validation.value = { missing: result.missing || [], errors: result.errors || [], warnings: result.warnings || [], riskFlags: result.riskFlags || [], route: result.routeExplanation || [], calculation: result.calculation || {} }; step.value = 'review'; await validateNow() } catch (error) { parseError.value = error.message; step.value = 'empty'; } finally { busy.value = false } }
async function validateNow() { if (!draft.value) return; const wasBusy = busy.value; const confidenceValue = confidence.value; busy.value = true; try { const result = await request('/ai/purchase-requisitions/validate', { method: 'POST', body: JSON.stringify({ draft: draft.value }) }); draft.value = { ...result.normalizedDraft, confidence: confidenceValue }; validation.value = { missing: result.missing || [], errors: result.errors || [], warnings: result.warnings || [], riskFlags: result.riskFlags || [], route: result.route || [], calculation: result.calculation || {} }; } catch (error) { parseError.value = error.message } finally { busy.value = wasBusy } }
function scheduleValidate() { routeConfirmed.value = false; window.clearTimeout(validateTimer); validateTimer = window.setTimeout(validateNow, 220) }
async function confirmSubmit() { if (!routeConfirmed.value) return warning('请先确认审批链路'); if (!canSubmit.value) return warning('请先补齐缺失项并通过服务端校验'); busy.value = true; step.value = 'submitting'; try { if (!submissionKey.value) submissionKey.value = `${sessionId.value}:confirm`; submitted.value = await request('/ai/purchase-requisitions/confirm', { method: 'POST', headers: { 'Idempotency-Key': submissionKey.value }, body: JSON.stringify({ sessionId: sessionId.value, draftVersion: 1, routeConfirmation: true, draft: draft.value }) }); step.value = 'submitted'; success('采购申请审批已发起') } catch (error) { step.value = 'review'; notifyError(`提交失败：${error.message}`) } finally { busy.value = false } }
function addLine() { requisition.value.lines.push({ _key: `${Date.now()}-${Math.random()}`, materialCode: '', materialName: '', specification: '', requestedQty: 1, unit: '件', unitPrice: 0, taxRate: 0, requiredDate: '', warehouseCode: '', sourceRef: '', remark: '' }); scheduleValidate() }
function removeLine(index) { if (requisition.value.lines.length > 1) requisition.value.lines.splice(index, 1); scheduleValidate() }
function localLineAmount(line) { return Number(line.requestedQty || 0) * Number(line.unitPrice || 0) }
function formatAmount(value) { return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function fieldLabel(field) { return ({ 'requisition.reason': '采购原因', 'requisition.orgCode': '申请组织', 'requisition.departmentCode': '申请部门', 'requisition.lines': '物料明细' }[field] || String(field || '').replace('requisition.', '').replaceAll('lines[', '第 ').replaceAll('].', ' 行 ')) }
function sourceLabel(value) { return ({ user_text: '用户原话', attachment: '附件', system_context: '系统上下文', rule: '规则推导' }[value] || value || '待确认') }
function fallbackManual() { if (input.value) localStorage.setItem('polaris-purchase-ai-input', input.value); router.push('/procurement/requisitions') }
onMounted(loadContext)
</script>
