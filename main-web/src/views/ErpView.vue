<template>
  <PageShell title="企业看板" description="从订单、物料、生产到交付与回款，统一掌握制造企业经营全局。">
    <template #actions>
      <span class="erp-live-label"><i></i> 数据更新时间 {{ updatedAt }}</span>
      <button class="button button-ghost" @click="refresh">↻ 刷新经营数据</button>
      <button class="button button-primary" @click="router.push('/sales/orders')">+ 新建销售订单</button>
    </template>

    <section class="erp-hero">
      <div class="erp-hero-copy">
        <span class="erp-kicker">POLARIS ERP · MANUFACTURING EDITION</span>
        <h2>把每一张订单，变成可交付的利润。</h2>
        <p>覆盖销售、计划、采购、生产、库存、质量与财务的统一经营视图。</p>
        <div class="erp-hero-meta"><span><i class="hero-dot green"></i> 生产系统在线</span><span><i class="hero-dot blue"></i> {{ live.production }} 个工单执行中</span><span><i class="hero-dot orange"></i> {{ live.lowStock }} 个低库存预警</span><span><i class="hero-dot violet"></i> {{ approval.pendingCount || 0 }} 条审批待办</span></div>
      </div>
      <div class="erp-hero-orbit"><div class="orbit orbit-1"></div><div class="orbit orbit-2"></div><div class="orbit-core"><b>ERP</b><small>CONNECTED</small></div></div>
    </section>

    <div class="erp-kpi-grid">
      <article v-for="item in kpis" :key="item.label" class="erp-kpi-card" :class="`erp-kpi-${item.tone}`">
        <div class="erp-kpi-top"><span>{{ item.label }}</span><b>{{ item.icon }}</b></div>
        <strong>{{ item.value }}</strong>
        <small><em :class="item.trend.startsWith('-') ? 'down' : ''">{{ item.trend }}</em> {{ item.hint }}</small>
      </article>
    </div>

    <section class="erp-section-heading"><div><span class="eyebrow">END-TO-END CONTROL</span><h3>经营链路</h3><p>当前业务单据在关键节点的分布与健康度</p></div><button class="text-button" @click="router.push('/manufacturing/operations')">进入现场控制塔 →</button></section>
    <section class="erp-flow panel">
      <div v-for="(item, index) in flow" :key="item.title" class="erp-flow-node-wrap">
        <button class="erp-flow-node" @click="router.push(item.path)"><span class="erp-flow-icon">{{ item.icon }}</span><span><b>{{ item.title }}</b><small>{{ item.subtitle }}</small></span><strong>{{ item.value }}</strong></button>
        <span v-if="index < flow.length - 1" class="erp-flow-arrow">→</span>
      </div>
    </section>

    <div class="erp-dashboard-grid">
      <article class="panel erp-chart-panel">
        <div class="panel-heading"><div><h3>订单与交付趋势</h3><p>近 6 个月销售订单金额与已交付金额</p></div><span class="status-pill status-released">经营数据</span></div>
        <div class="erp-chart"><div class="erp-chart-y"><span>1,000万</span><span>750万</span><span>500万</span><span>250万</span><span>0</span></div><div class="erp-chart-body"><div v-for="line in 4" :key="line" class="erp-chart-line" :style="{ bottom: `${(line - 1) * 25}%` }"></div><div class="erp-bars"><div v-for="month in revenueTrend" :key="month.month" class="erp-bar-group"><div class="erp-bar order" :style="{ height: `${month.order}%` }"><span>{{ month.orderLabel }}</span></div><div class="erp-bar delivered" :style="{ height: `${month.delivered}%` }"><span>{{ month.deliveredLabel }}</span></div><small>{{ month.month }}</small></div></div></div></div>
        <div class="erp-chart-legend"><span><i class="legend-dot erp-legend-order"></i>订单金额</span><span><i class="legend-dot erp-legend-delivered"></i>已交付</span><span class="erp-chart-note">按月 · 含税金额</span></div>
      </article>

      <article class="panel erp-health-panel">
        <div class="panel-heading"><div><h3>业务健康度</h3><p>关键经营指标达成情况</p></div><button class="text-button" @click="router.push('/finance/receivable')">看财务 →</button></div>
        <div class="erp-health-ring"><div class="erp-ring-progress"><div><strong>92</strong><small>综合健康分</small></div></div></div>
        <div class="erp-health-list"><div v-for="item in health" :key="item.label"><span><i :class="`health-${item.tone}`"></i>{{ item.label }}</span><b>{{ item.value }}</b><div class="erp-mini-progress"><i :class="`health-bg-${item.tone}`" :style="{ width: `${item.percent}%` }"></i></div></div></div>
      </article>

      <article class="panel erp-alert-panel"><div class="panel-heading"><div><h3>经营待办</h3><p>需要管理者关注的异常与审批</p></div><span class="erp-alert-count">{{ alerts.length }} 项</span></div><div class="erp-alert-list"><button v-for="item in alerts" :key="item.title" @click="router.push(item.path)"><span :class="['erp-alert-icon', `tone-${item.tone}`]">{{ item.icon }}</span><span><b>{{ item.title }}</b><small>{{ item.detail }}</small></span><em>›</em></button></div></article>

      <article class="panel erp-shortcuts"><div class="panel-heading"><div><h3>业务工作台</h3><p>高频业务动作一键进入</p></div></div><div class="erp-shortcut-grid"><button v-for="item in shortcuts" :key="item.title" @click="router.push(item.path)"><span>{{ item.icon }}</span><b>{{ item.title }}</b><small>{{ item.detail }}</small></button></div></article>
    </div>

    <section class="panel erp-snapshot"><div class="panel-heading"><div><h3>今日运营快照</h3><p>跨部门现场动态，帮助管理者快速判断经营节奏</p></div><button class="text-button" @click="router.push('/manufacturing/operations')">查看全部 →</button></div><div class="erp-snapshot-grid"><div v-for="item in snapshots" :key="item.label"><span class="snapshot-icon">{{ item.icon }}</span><span><b>{{ item.value }}</b><small>{{ item.label }}</small></span><em :class="item.tone">{{ item.note }}</em></div></div></section>
  </PageShell>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageShell from '../components/PageShell.vue'
import { request } from '../api'

const router = useRouter()
const updatedAt = ref('刚刚')
const live = ref({ production: 18, lowStock: 6 })
const approval = ref({ pendingCount: 0, runningCount: 0 })
const kpis = ref([
  { label: '本月销售额', value: '¥ 846.0 万', trend: '+12.8%', hint: '较上月', icon: '↗', tone: 'blue' },
  { label: '订单准交率', value: '96.4%', trend: '+2.1%', hint: '较上月', icon: '◎', tone: 'green' },
  { label: '生产达成率', value: '91.8%', trend: '+4.6%', hint: '本月计划', icon: '◫', tone: 'violet' },
  { label: '应收余额', value: '¥ 128.0 万', trend: '-8.3%', hint: '较上月', icon: '◇', tone: 'orange' }
])
const flow = ref([
  { title: '销售订单', subtitle: '本月新增', value: '128', icon: '▤', path: '/sales/orders' },
  { title: 'MRP 运算', subtitle: '待确认需求', value: '42', icon: '⌁', path: '/manufacturing/plan' },
  { title: '生产工单', subtitle: '正在执行', value: '76', icon: '⚙', path: '/manufacturing/work-order' },
  { title: '待发货', subtitle: '已完工待交付', value: '19', icon: '➜', path: '/warehouse/outbound' },
  { title: '回款达成', subtitle: '本月目标', value: '86%', icon: '✓', path: '/finance/receivable' }
])
const revenueTrend = [
  { month: '03月', order: 54, delivered: 46, orderLabel: '540', deliveredLabel: '460' },
  { month: '04月', order: 68, delivered: 61, orderLabel: '680', deliveredLabel: '610' },
  { month: '05月', order: 62, delivered: 58, orderLabel: '620', deliveredLabel: '580' },
  { month: '06月', order: 77, delivered: 68, orderLabel: '770', deliveredLabel: '680' },
  { month: '07月', order: 72, delivered: 66, orderLabel: '720', deliveredLabel: '660' },
  { month: '08月', order: 88, delivered: 76, orderLabel: '880', deliveredLabel: '760' }
]
const health = [
  { label: '销售订单履约', value: '96.4%', percent: 96, tone: 'green' },
  { label: '生产计划达成', value: '91.8%', percent: 92, tone: 'blue' },
  { label: '库存周转健康', value: '88.2%', percent: 88, tone: 'violet' },
  { label: '质量一次合格', value: '98.6%', percent: 99, tone: 'orange' }
]
const alerts = [
  { title: '3 张销售订单待评审', detail: '销售管理 · 今天 17:00 前完成', tone: 'blue', icon: '!', path: '/sales/orders' },
  { title: '6 个物料低于安全库存', detail: '采购管理 · 建议立即补货', tone: 'orange', icon: '↓', path: '/procurement/orders' },
  { title: '2 个检验批待质量判定', detail: '质量管理 · 影响成品入库', tone: 'red', icon: '◇', path: '/quality/lots' },
  { title: '¥ 32.6 万应收即将到期', detail: '财务管理 · 未来 7 天', tone: 'violet', icon: '¥', path: '/finance/receivable' }
]
const shortcuts = [
  { title: '销售订单', detail: '报价 / 下单 / 交付', icon: '▤', path: '/sales/orders' },
  { title: '采购申请', detail: '请购 / 询价 / 下单', icon: '⇩', path: '/procurement/requisitions' },
  { title: 'MRP 运算', detail: '需求展开 / 采购建议', icon: '⌁', path: '/manufacturing/plan' },
  { title: '财务台账', detail: '应收 / 应付 / 成本', icon: '◇', path: '/finance/receivable' },
  { title: '物料主数据', detail: '编码 / 版本 / 单位', icon: '□', path: '/master/materials' },
  { title: '经营报表', detail: '收入 / 毛利 / 交付', icon: '◒', path: '/design/reports' }
]
const snapshots = [
  { label: '今日新增订单', value: '12 张', note: '+ 3 张', tone: 'positive', icon: '↗' },
  { label: '今日入库数量', value: '2,846 件', note: '+ 8.4%', tone: 'positive', icon: '⇩' },
  { label: '今日完工数量', value: '1,920 件', note: '达成 94%', tone: 'positive', icon: '✓' },
  { label: '待处理异常', value: '8 项', note: '需关注', tone: 'warning', icon: '!' },
  { label: '今日开票金额', value: '¥ 46.8 万', note: '达成 78%', tone: 'positive', icon: '¥' }
]

async function refresh() {
  updatedAt.value = '同步中…'
  const [dashboardResult, erpResult, approvalResult] = await Promise.allSettled([request('/dashboard/overview'), request('/erp/overview'), request('/bpm/overview')])
  const dashboard = dashboardResult.status === 'fulfilled' ? dashboardResult.value : {}
  const erpOverview = erpResult.status === 'fulfilled' ? erpResult.value : {}
  approval.value = approvalResult.status === 'fulfilled' ? (approvalResult.value || {}) : approval.value
  live.value.production = Number(dashboard?.inProgressOrders || erpOverview?.inProgressOrders || live.value.production)
  live.value.lowStock = Number(dashboard?.lowStock || live.value.lowStock)
  if (Number(erpOverview?.salesAmount || 0) > 0) kpis.value[0].value = formatWan(erpOverview.salesAmount)
  if (Number(erpOverview?.receivableAmount || 0) > 0) kpis.value[3].value = formatWan(erpOverview.receivableAmount)
  if (Number(erpOverview?.salesOrders || 0) > 0) flow.value[0].value = String(erpOverview.salesOrders)
  if (Number(erpOverview?.pendingProcurement || 0) > 0) flow.value[1].value = String(erpOverview.pendingProcurement)
  if (Number(erpOverview?.inProgressOrders || 0) > 0) flow.value[2].value = String(erpOverview.inProgressOrders)
  updatedAt.value = new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(new Date())
}
function formatWan(value) { return `¥ ${(Number(value) / 10000).toFixed(1)} 万` }
onMounted(refresh)
</script>
