<template>
  <PageShell title="运营工作台" description="掌握生产、库存与现场执行的关键动态。">
    <template #actions><button class="button button-ghost" @click="refresh">↻ 刷新数据</button><button class="button button-primary" @click="go('/manufacturing/work-order')">+ 新建工单</button></template>
    <div class="welcome-band"><div><span class="welcome-kicker">{{ todayLabel }} · LIVE OPERATIONS</span><h2>早上好，{{ userName }} <span>✦</span></h2><p>当前有 {{ overview.inProgressOrders ?? 0 }} 个工单正在执行，{{ overview.lowStock ?? 0 }} 个物料库存低于安全线。</p></div><div class="welcome-figure"><div class="ring ring-a"></div><div class="ring ring-b"></div><div class="figure-box">MES<br><small>LIVE</small></div></div></div>
    <div class="stats-grid"><StatCard label="已完成数量" :value="overview.completedQty ?? 0" hint="当前工单累计完成量" icon="↗" tone="blue" /><StatCard label="在制工单" :value="overview.inProgressOrders ?? 0" hint="来自当前工单状态" icon="◉" tone="violet" /><StatCard label="库存 SKU" :value="overview.inventorySkus ?? 0" hint="低库存 {{ overview.lowStock ?? 0 }} 个" icon="□" tone="orange" /><StatCard label="今日库存事务" :value="overview.todayTransactions ?? 0" hint="来自库存事务记录" icon="⇄" tone="green" /></div>
    <div class="dashboard-grid">
      <article class="panel panel-wide"><div class="panel-heading"><div><h3>生产达成趋势</h3><p>按工单计划日期汇总计划数量与已完成数量</p></div><span class="status-pill status-released">数据库实时</span></div><div class="trend-chart"><div class="chart-y"><span>{{ chartMax }}</span><span>{{ Math.round(chartMax * 2 / 3) }}</span><span>{{ Math.round(chartMax / 3) }}</span><span>0</span></div><div class="chart-body"><div class="chart-gridline line-1"></div><div class="chart-gridline line-2"></div><div class="chart-gridline line-3"></div><div class="chart-bars"><div v-for="day in trend" :key="day.day" class="bar-group"><div class="bar planned" :style="{ height: `${day.planned / chartMax * 100}%` }"><em>{{ day.planned }}</em></div><div class="bar actual" :style="{ height: `${day.actual / chartMax * 100}%` }"><em>{{ day.actual }}</em></div><small>{{ dayLabel(day.day) }}</small></div><div v-if="!trend.length" class="empty-operation">暂无趋势数据</div></div></div></div><div class="chart-legend"><span><i class="legend-dot planned"></i>计划数量</span><span><i class="legend-dot actual"></i>实际完成</span></div></article>
      <article class="panel"><div class="panel-heading"><div><h3>工单状态</h3><p>当前全部工单分布</p></div><button class="text-button" @click="go('/manufacturing/work-order')">查看全部 →</button></div><div class="donut-wrap"><div class="donut"><div class="donut-center"><strong>{{ overview.workOrders ?? 0 }}</strong><small>总工单</small></div></div><div class="donut-legend"><div><i class="dot dot-blue"></i><span>执行中</span><b>{{ overview.inProgressOrders ?? 0 }}</b></div><div><i class="dot dot-green"></i><span>已完成</span><b>{{ overview.completedOrders ?? 0 }}</b></div><div><i class="dot dot-gray"></i><span>待排产</span><b>{{ overview.plannedOrders ?? 0 }}</b></div></div></div></article>
      <article class="panel panel-wide"><div class="panel-heading"><div><h3>库存预警</h3><p>低于安全库存的物料需要关注</p></div><button class="text-button" @click="go('/warehouse/inventory')">去处理 →</button></div><div class="alert-list"><div v-for="item in alerts" :key="item.code" class="alert-row"><div class="alert-material"><span class="material-icon">▣</span><span><b>{{ item.name }}</b><small>{{ item.code }} · {{ item.warehouse }}</small></span></div><div class="progress"><div class="progress-bar" :style="{ width: `${Math.min(item.percent, 100)}%` }"></div></div><span class="alert-qty">{{ item.qty }} <small>/ {{ item.safe }} {{ item.unit }}</small></span><span class="alert-state">需补货</span></div><div v-if="!alerts.length" class="empty-operation">暂无低库存物料</div></div></article>
      <article class="panel"><div class="panel-heading"><div><h3>现场动态</h3><p>最近的库存事务</p></div><button class="text-button" @click="go('/warehouse/receipt')">查看事务 →</button></div><div class="timeline"><div v-for="(event, index) in events" :key="`${event.time}-${event.material_code}-${index}`" class="timeline-item"><span class="timeline-dot" :class="eventTone(event.type)"></span><div><p><b>{{ event.actor }}</b> {{ eventAction(event) }}</p><small>{{ event.time }}</small></div></div><div v-if="!events.length" class="empty-operation">暂无现场动态</div></div></article>
    </div>
  </PageShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageShell from '../components/PageShell.vue'
import StatCard from '../components/StatCard.vue'
import { request } from '../api'

function notify(message, type = 'error') { window.dispatchEvent(new CustomEvent('polaris:toast', { detail: { message, type } })) }

const router = useRouter()
const overview = ref({})
const error = ref('')
const userName = computed(() => { try { const user = JSON.parse(localStorage.getItem('polaris-user') || '{}'); return user.displayName || user.username || '运营同事' } catch { return '运营同事' } })
const trend = computed(() => overview.value.trend || [])
const alerts = computed(() => overview.value.alerts || [])
const events = computed(() => overview.value.events || [])
const chartMax = computed(() => Math.max(1, ...trend.value.flatMap(item => [Number(item.planned) || 0, Number(item.actual) || 0])))
const todayLabel = new Intl.DateTimeFormat('zh-CN', { weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date()).toUpperCase()

function go(path) { router.push(path) }
function dayLabel(value) { return String(value || '').slice(5).replace('-', '/') }
function eventTone(type) { return type === 'ISSUE' || type === 'MOVE_OUT' ? 'orange' : 'green' }
function eventAction(event) {
  const action = event.type === 'ISSUE' ? '完成生产领料' : event.type === 'MOVE_OUT' ? '完成移库出库' : '完成收料入库'
  return `${action} ${event.material_code} ${event.quantity} ${event.unit}`
}
async function refresh() {
  error.value = ''
  try { overview.value = await request('/dashboard/overview') } catch (requestError) { error.value = requestError.message; notify(error.value) }
}
onMounted(refresh)
</script>
