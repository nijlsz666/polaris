import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import App from './App.vue'
import DashboardView from './views/DashboardView.vue'
import ErpView from './views/ErpView.vue'
import BusinessView from './views/BusinessView.vue'
import ManufacturingView from './views/ManufacturingView.vue'
import OperationsView from './views/OperationsView.vue'
import WarehouseView from './views/WarehouseView.vue'
import DesignView from './views/DesignView.vue'
import AdminView from './views/AdminView.vue'
import ReleaseView from './views/ReleaseView.vue'
import QualityView from './views/QualityView.vue'
import LoginView from './views/LoginView.vue'
import ApprovalView from './views/ApprovalView.vue'
import PurchaseAiView from './views/PurchaseAiView.vue'
import PlatformAdminView from './views/PlatformAdminView.vue'
import InformationView from './views/InformationView.vue'
import './styles.css'
import './enterprise.css'
import './release.css'
import './workspace.css'
import './admin.css'
import './purchase-ai.css'
import './information.css'

const routes = [
  { path: '/login', component: LoginView, meta: { public: true, title: '登录' } },
  { path: '/', redirect: '/erp' },
  { path: '/erp', component: ErpView, meta: { title: '企业看板' } },
  { path: '/approval', component: ApprovalView, meta: { title: '审批中心' } },
  { path: '/procurement/ai-create', component: PurchaseAiView, meta: { title: 'AI 创建采购申请审批' } },
  { path: '/dashboard', component: DashboardView, meta: { title: '工作台' } },
  { path: '/sales/:tab?', component: BusinessView, props: { module: 'sales' }, meta: { title: '销售管理' } },
  { path: '/procurement/:tab?', component: BusinessView, props: { module: 'procurement' }, meta: { title: '采购管理' } },
  { path: '/finance/:tab?', component: BusinessView, props: { module: 'finance' }, meta: { title: '财务管理' } },
  { path: '/master/:tab?', component: BusinessView, props: { module: 'master' }, meta: { title: '主数据中心' } },
  { path: '/manufacturing/operations', component: OperationsView, meta: { title: '现场控制塔' } },
  { path: '/manufacturing/:tab?', component: ManufacturingView, meta: { title: '制造管理' } },
  { path: '/warehouse/:tab?', component: WarehouseView, meta: { title: '仓储管理' } },
  { path: '/quality/:tab?', component: QualityView, meta: { title: '质量管理' } },
  { path: '/design/:tab?', component: DesignView, meta: { title: '设计中心' } },
  { path: '/release/:tab?', component: ReleaseView, meta: { title: '发版管理' } },
  { path: '/admin/:tab?', component: AdminView, meta: { title: '系统管理' } },
  { path: '/platform/:tab?', component: PlatformAdminView, meta: { title: '平台运营中心' } },
  { path: '/information/:tab?', component: InformationView, meta: { title: '信息中心' } }
]
const router = createRouter({ history: createWebHashHistory(), routes })
router.beforeEach((to) => {
  const authenticated = Boolean(localStorage.getItem('polaris-token'))
  if (to.meta.public) return authenticated ? '/dashboard' : true
  return authenticated ? true : { path: '/login', query: { redirect: to.fullPath } }
})
createApp(App).use(router).mount('#app')
