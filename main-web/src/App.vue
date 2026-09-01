<template>
  <RouterView v-if="route.path === '/login'" />
  <div v-else class="app-frame">
    <aside class="sidebar">
      <div class="brand"><div class="brand-mark">P</div><div><strong>POLARIS</strong><span>制造运营平台</span></div></div>
      <div class="workspace-switch"><span class="workspace-dot"></span><span>{{ tenantName }} · 当前租户</span><span class="chevron">⌄</span></div>
      <nav class="nav-list"><template v-for="group in visibleNavigation" :key="group.code"><div v-if="group.type === 'group'" class="nav-group-label">{{ group.label }}</div><RouterLink v-else :to="group.path" class="nav-item" active-class="is-active"><Icon :name="group.icon" /><span>{{ group.label }}</span><span v-if="group.badge" class="nav-badge">{{ group.badge }}</span></RouterLink><RouterLink v-for="item in group.children || []" :key="item.code" :to="item.path" class="nav-item nav-child" active-class="is-active"><Icon :name="item.icon" /><span>{{ item.label }}</span></RouterLink></template></nav>
      <div class="sidebar-foot"><div class="health-dot"></div><span>系统运行正常</span><span class="version">v0.1</span></div>
    </aside>
    <main class="main-area"><header class="topbar"><div class="breadcrumb"><span>{{ tenantCode }}</span><span>/</span><strong>{{ route.meta.title || '工作台' }}</strong></div><div v-if="trafficAlert" :class="['traffic-alert', trafficUsage?.exhausted ? 'traffic-alert--danger' : 'traffic-alert--warning']">{{ trafficAlert }}</div><div class="topbar-actions"><button class="command-trigger" @click="showCommand = true"><span>⌘</span> 快速查找 <kbd>⌘ K</kbd></button><button class="locale-trigger" type="button" @click="toggleLocale">{{ locale === 'zh-CN' ? '中 / EN' : 'EN / 中' }}</button><button class="icon-button notification-trigger" title="通知" @click="toggleNotifications">♢<i v-if="notificationCount"></i><b v-if="notificationCount">{{ notificationCount > 99 ? '99+' : notificationCount }}</b></button><button class="profile" @click="logout"><span class="avatar">{{ userName.slice(0, 1) }}</span><span><b>{{ userName }}</b><small>{{ roleCode }} · 点击退出登录</small></span><span>↪</span></button></div></header><div v-if="showNotificationPanel" class="notification-panel"><div class="notification-head"><div><b>通知中心</b><small>{{ notificationCount ? `${notificationCount} 条未读` : '全部已读' }}</small></div><button @click="markAllRead">全部已读</button></div><div v-for="item in notifications" :key="item.id" class="notification-item" @click="readNotification(item)"><span :class="['notification-dot', `level-${String(item.level || 'INFO').toLowerCase()}`]"></span><div><b>{{ item.title }}</b><p>{{ item.content }}</p><small>{{ formatNotificationTime(item.created_at) }}</small></div></div><div v-if="!notifications.length" class="notification-empty">暂无新通知</div></div><RouterView /><div v-if="showCommand" class="command-backdrop" @click.self="showCommand = false"><div class="command-panel"><div class="command-input"><span>⌕</span><input ref="commandInput" v-model="commandKeyword" autofocus placeholder="搜索功能、模块或操作…" @keydown.esc="showCommand = false" /></div><div class="command-list"><button v-for="item in filteredCommands" :key="item.path" @click="goCommand(item.path)"><Icon :name="item.icon" /><span><b>{{ item.label }}</b><small>{{ item.group }}</small></span><kbd>↵</kbd></button><div v-if="!filteredCommands.length" class="notification-empty">没有匹配的功能</div></div><div class="command-foot"><span>↑↓ 选择</span><span>Enter 打开</span><span>Esc 关闭</span></div></div></div><AppToast /></main>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from './components/Icon.vue'
import AppToast from './components/AppToast.vue'
import { request } from './api'
import { useLocale } from './i18n'
const route = useRoute()
const router = useRouter()
const { locale, toggleLocale } = useLocale()
const tenantName = ref('当前租户'); const tenantCode = ref(''); const userName = ref('用户'); const roleCode = ref('')
const notificationCount = ref(0); const notifications = ref([]); const trafficUsage = ref(null); const showNotificationPanel = ref(false); const showCommand = ref(false); const commandKeyword = ref(''); const commandInput = ref(null)
function loadSession() { try { const tenant = JSON.parse(localStorage.getItem('polaris-tenant') || '{}'); const user = JSON.parse(localStorage.getItem('polaris-user') || '{}'); tenantName.value = tenant.name || tenant.tenant_name || '当前租户'; tenantCode.value = tenant.code || tenant.tenant_code || ''; userName.value = user.displayName || user.username || '用户'; roleCode.value = user.roleCode || '' } catch {} }
function logout() { localStorage.removeItem('polaris-token'); localStorage.removeItem('polaris-user'); localStorage.removeItem('polaris-tenant'); router.replace('/login') }
loadSession()
async function loadNotifications() { try { const [count, list] = await Promise.all([request('/notifications/unread-count'), request('/notifications?limit=8')]); notificationCount.value = Number(count?.count || 0); notifications.value = list || [] } catch {} }
async function loadTraffic() { if (roleCode.value === 'platform_admin') { trafficUsage.value = null; return }; try { trafficUsage.value = await request('/tenant/traffic', { silent: true }) } catch { trafficUsage.value = null } }
const trafficAlert = computed(() => { if (!trafficUsage.value) return ''; if (trafficUsage.value.exhausted) return '租户流量已用尽，请联系总管理员'; if (trafficUsage.value.low_balance) return `租户流量余额不足：剩余 ${formatTrafficSize(trafficUsage.value.remaining_bytes)}`; return '' })
function formatTrafficSize(value) { const bytes = Number(value || 0); if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(2)} GB`; if (bytes >= 1024 ** 2) return `${(bytes / 1024 ** 2).toFixed(2)} MB`; return `${Math.round(bytes / 1024)} KB` }
function toggleNotifications() { showNotificationPanel.value = !showNotificationPanel.value; if (showNotificationPanel.value) loadNotifications() }
async function readNotification(item) { if (!item.read_at) { try { await request(`/notifications/${item.id}/read`, { method: 'POST' }); item.read_at = new Date().toISOString(); notificationCount.value = Math.max(0, notificationCount.value - 1) } catch {} } if (item.action_url) router.push(item.action_url) }
async function markAllRead() { try { await request('/notifications/read-all', { method: 'POST' }); notifications.value.forEach(item => { item.read_at = item.read_at || new Date().toISOString() }); notificationCount.value = 0 } catch {} }
function formatNotificationTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '刚刚' }
const filteredCommands = computed(() => { const keyword = commandKeyword.value.trim().toLowerCase(); return visibleNavigation.value.filter(item => item.path && (!keyword || `${item.label}${item.code}`.toLowerCase().includes(keyword))).slice(0, 8).map(item => ({ ...item, group: item.path.split('/')[1] || 'workspace' })) })
const visibleNavigation = computed(() => roleCode.value === 'platform_admin' ? navigation.value.filter(item => item.code === 'platform' || item.code.startsWith('information')) : navigation.value.filter(item => item.code !== 'platform' && (item.code !== 'admin' || roleCode.value === 'admin')))
function goCommand(path) { showCommand.value = false; commandKeyword.value = ''; router.push(path) }
function handleShortcut(event) { if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); showCommand.value = true } if (event.key === 'Escape') { showCommand.value = false; showNotificationPanel.value = false } }
watch(showCommand, value => { if (value) nextTick(() => commandInput.value?.focus()) })
watch(() => route.path, path => { if (path !== '/login' && localStorage.getItem('polaris-token')) { loadSession(); loadNotifications(); loadTraffic() } })
onMounted(() => { window.addEventListener('keydown', handleShortcut); if (route.path !== '/login' && localStorage.getItem('polaris-token')) { loadNotifications(); loadTraffic() } })
onUnmounted(() => window.removeEventListener('keydown', handleShortcut))
const navigation = ref([
  { code: 'erp', label: '企业看板', path: '/erp', icon: 'grid' },
  { code: 'approval', label: '审批中心', path: '/approval', icon: 'check', badge: 'BPM' },
  { code: 'dashboard', label: '生产工作台', path: '/dashboard', icon: 'factory' },
  { code: 'sales', label: '销售管理', type: 'group' },
  { code: 'sales-orders', label: '销售订单', path: '/sales/orders', icon: 'clipboard', badge: '3' },
  { code: 'sales-delivery', label: '发货计划', path: '/sales/delivery', icon: 'arrow' },
  { code: 'sales-customers', label: '客户管理', path: '/sales/customers', icon: 'users' },
  { code: 'procurement', label: '采购管理', type: 'group' },
  { code: 'procurement-requisitions', label: '采购申请', path: '/procurement/requisitions', icon: 'inbox', badge: '12' },
  { code: 'procurement-drafts', label: '我的草稿', path: '/procurement/drafts', icon: 'file' },
  { code: 'procurement-ai', label: 'AI 创建采购审批', path: '/procurement/ai-create', icon: 'activity', badge: 'AI' },
  { code: 'procurement-orders', label: '采购订单', path: '/procurement/orders', icon: 'clipboard' },
  { code: 'procurement-suppliers', label: '供应商管理', path: '/procurement/suppliers', icon: 'users' },
  { code: 'manufacturing', label: '制造管理', type: 'group' },
  { code: 'bom', label: 'BOM 管理', path: '/manufacturing/bom', icon: 'layers' },
  { code: 'mrp', label: 'MRP 运算', path: '/manufacturing/mrp', icon: 'activity' },
  { code: 'plan', label: '生产计划', path: '/manufacturing/plan', icon: 'calendar' },
  { code: 'work-order', label: '工单管理', path: '/manufacturing/work-order', icon: 'clipboard', badge: '7' },
  { code: 'operations-control', label: '现场控制塔', path: '/manufacturing/operations', icon: 'activity' },
  { code: 'warehouse', label: '仓储管理', type: 'group' },
  { code: 'warehouse-overview', label: '仓储总览', path: '/warehouse/overview', icon: 'grid' },
  { code: 'receipt', label: '收料 / 上架 / 退料', path: '/warehouse/inbound', icon: 'inbox' },
  { code: 'outbound', label: '领料 / 出库 / 报废', path: '/warehouse/outbound', icon: 'arrow' },
  { code: 'transfer', label: '调拨 / 移库', path: '/warehouse/transfer', icon: 'layers' },
  { code: 'count', label: '盘点与差异', path: '/warehouse/count', icon: 'clipboard' },
  { code: 'inventory', label: '库存与批次', path: '/warehouse/inventory', icon: 'box' },
  { code: 'trace', label: '批次追溯', path: '/warehouse/trace', icon: 'timeline' },
  { code: 'barcode', label: '条码管理', path: '/warehouse/barcode', icon: 'qr' },
  { code: 'warehouse-master', label: '仓库主数据', path: '/warehouse/master', icon: 'settings' },
  { code: 'quality', label: '质量管理', type: 'group' },
  { code: 'quality-overview', label: '质量总览', path: '/quality/overview', icon: 'shield' },
  { code: 'quality-supplier-evaluation', label: '供应商考评', path: '/quality/supplier-evaluation', icon: 'users' },
  { code: 'quality-avl', label: '质量 AVL', path: '/quality/avl', icon: 'layers' },
  { code: 'quality-plans', label: '检验计划', path: '/quality/plans', icon: 'clipboard' },
  { code: 'quality-lots', label: '检验批与结果', path: '/quality/lots', icon: 'scan' },
  { code: 'quality-ipqc', label: '产线 IPQC', path: '/quality/ipqc', icon: 'activity' },
  { code: 'quality-nc', label: '不合格与整改', path: '/quality/nonconformance', icon: 'timeline' },
  { code: 'finance', label: '财务管理', type: 'group' },
  { code: 'finance-receivable', label: '应收管理', path: '/finance/receivable', icon: 'money' },
  { code: 'finance-payable', label: '应付管理', path: '/finance/payable', icon: 'money' },
  { code: 'finance-cost', label: '成本核算', path: '/finance/cost', icon: 'chart' },
  { code: 'master', label: '主数据中心', type: 'group' },
  { code: 'master-materials', label: '物料主数据', path: '/master/materials', icon: 'box' },
  { code: 'master-bom', label: 'BOM 版本', path: '/master/bom', icon: 'layers' },
  { code: 'master-partners', label: '客户与供应商', path: '/master/partners', icon: 'users' },
  { code: 'design', label: '设计中心', type: 'group' },
  { code: 'reports', label: '快速报表', path: '/design/reports', icon: 'chart' },
  { code: 'data-sources', label: '数据源维护', path: '/design/data-sources', icon: 'database' },
  { code: 'low-code', label: '低代码页面', path: '/design/low-code', icon: 'layout' },
  { code: 'big-screen', label: '大屏展示', path: '/design/big-screen', icon: 'monitor' },
  { code: 'release', label: '发版管理', type: 'group' },
  { code: 'release-versions', label: '版本与发布', path: '/release/versions', icon: 'rocket' },
  { code: 'admin', label: '系统管理', type: 'group' },
  { code: 'users', label: '用户与角色', path: '/admin/users', icon: 'users' },
  { code: 'permissions', label: '权限管理', path: '/admin/permissions', icon: 'shield' },
  { code: 'menus', label: '菜单维护', path: '/admin/menus', icon: 'layout' },
  { code: 'tenants', label: '租户维护', path: '/admin/tenants', icon: 'building' },
  { code: 'dictionaries', label: '字典配置', path: '/admin/dictionaries', icon: 'sliders' },
  { code: 'information', label: '信息中心', type: 'group' },
  { code: 'information-announcements', label: '新闻公告', path: '/information/announcements', icon: 'bell' },
  { code: 'information-documents', label: '资料中心', path: '/information/documents', icon: 'file' },
  { code: 'information-attachments', label: '单据附件', path: '/information/attachments', icon: 'paperclip' },
  { code: 'platform', label: '平台运营中心', path: '/platform/overview', icon: 'building' }
])
</script>
