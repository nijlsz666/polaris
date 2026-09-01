<script setup>
import AppIcon from './AppIcon.vue'
import BrandMark from './BrandMark.vue'

const props = defineProps({
  current: { type: String, default: 'home' },
  taskCount: { type: Number, default: 0 }
})

const emit = defineEmits(['navigate', 'scan'])

const navItems = [
  { value: 'home', label: '工作台', caption: 'Overview', icon: 'home' },
  { value: 'inventory', label: '库存中心', caption: 'Inventory', icon: 'inventory' },
  { value: 'tasks', label: '现场任务', caption: 'Work center', icon: 'task' },
  { value: 'exceptions', label: '现场异常', caption: 'Andon closure', icon: 'activity' },
  { value: 'history', label: '事务记录', caption: 'Ledger', icon: 'clipboard' },
  { value: 'profile', label: '我的账户', caption: 'Account', icon: 'user' }
]

function isActive(value) {
  return value === props.current || (value === 'profile' && ['profile', 'tasks', 'history'].includes(props.current))
}
</script>

<template>
  <aside class="workspace-sidebar">
    <BrandMark />
    <div class="workspace-context">
      <span class="context-label">当前工作区</span>
      <b>生产运营中心</b>
      <small>DEMO / PDA CLIENT</small>
    </div>

    <button class="sidebar-scan" type="button" @click="emit('scan')">
      <span class="sidebar-scan-icon"><AppIcon name="scan" :size="18" /></span>
      <span><b>开始扫码作业</b><small>Scan to operate</small></span>
      <AppIcon name="arrowUpRight" :size="16" />
    </button>

    <nav class="workspace-nav" aria-label="主导航">
      <span class="nav-group-label">WORKSPACE</span>
      <button v-for="item in navItems" :key="item.value" type="button" :class="{ active: isActive(item.value) }" @click="emit('navigate', item.value)">
        <span class="nav-icon"><AppIcon :name="item.icon" :size="17" /></span>
        <span class="nav-label"><b>{{ item.label }}</b><small>{{ item.caption }}</small></span>
        <span v-if="item.value === 'tasks' && taskCount" class="nav-badge">{{ taskCount }}</span>
        <AppIcon v-else name="chevron" :size="14" class="nav-chevron" />
      </button>
    </nav>

    <div class="sidebar-bottom">
      <div class="sidebar-health"><span class="health-dot"></span><span><b>终端状态正常</b><small>Last sync just now</small></span></div>
      <div class="sidebar-version">POLARIS PDA <span>v0.1</span></div>
    </div>
  </aside>

  <nav class="mobile-tabbar" aria-label="移动端主导航">
    <button type="button" :class="{ active: current === 'home' }" @click="emit('navigate', 'home')"><AppIcon name="home" :size="19" /><span>工作台</span></button>
    <button type="button" :class="{ active: current === 'inventory' }" @click="emit('navigate', 'inventory')"><AppIcon name="inventory" :size="19" /><span>库存</span></button>
    <button class="mobile-scan" type="button" @click="emit('scan')"><span><AppIcon name="scan" :size="21" /></span><small>扫码</small></button>
    <button type="button" :class="{ active: ['tasks', 'history'].includes(current) }" @click="emit('navigate', 'tasks')"><AppIcon name="task" :size="19" /><span>任务</span></button>
    <button type="button" :class="{ active: current === 'profile' }" @click="emit('navigate', 'profile')"><AppIcon name="user" :size="19" /><span>我的</span></button>
  </nav>
</template>

<style scoped>
.workspace-sidebar{width:248px;flex:none;min-height:100vh;background:#102b43;color:#d9e8f2;padding:27px 17px 20px;display:flex;flex-direction:column;position:relative;z-index:3}.workspace-sidebar .brand-symbol{width:30px;height:30px}.workspace-sidebar .brand-copy b{color:#fff}.workspace-sidebar .brand-copy small{color:#7d9aad}.workspace-context{border:1px solid #ffffff12;background:#ffffff08;border-radius:11px;padding:13px 14px;margin:35px 1px 18px}.context-label{font-size:8px;color:#7893a8;letter-spacing:1px;display:block;margin-bottom:8px}.workspace-context b{display:block;font-size:11px;color:#edf7fa}.workspace-context small{display:block;font-size:8px;color:#6e8ba0;margin-top:5px;letter-spacing:.8px}.sidebar-scan{height:55px;border-radius:11px;padding:0 12px;display:flex;align-items:center;text-align:left;gap:10px;background:linear-gradient(100deg,#2e77d7,#268eb2);color:#fff;box-shadow:0 9px 20px #081b2d44;margin-bottom:28px}.sidebar-scan-icon{width:31px;height:31px;display:grid;place-items:center;border-radius:8px;background:#ffffff1c}.sidebar-scan>span:nth-child(2){flex:1}.sidebar-scan b,.sidebar-scan small{display:block}.sidebar-scan b{font-size:10px}.sidebar-scan small{font-size:8px;color:#c5e3ee;margin-top:4px}.workspace-nav{display:flex;flex-direction:column;gap:5px}.nav-group-label{font-size:8px;letter-spacing:1.4px;color:#66869a;margin:0 11px 9px}.workspace-nav button{width:100%;height:55px;border-radius:10px;padding:0 11px;display:flex;align-items:center;gap:11px;text-align:left;color:#8ca8b9;transition:.18s}.workspace-nav button:hover{background:#ffffff0d;color:#dcebf1}.workspace-nav button.active{background:#ffffff12;color:#fff;box-shadow:inset 3px 0 #4b9ee0}.nav-icon{width:28px;height:28px;display:grid;place-items:center;border-radius:8px}.workspace-nav button.active .nav-icon{color:#8cd9ce;background:#ffffff12}.nav-label{flex:1}.nav-label b,.nav-label small{display:block}.nav-label b{font-size:11px;font-weight:600}.nav-label small{font-size:8px;color:#64849a;margin-top:4px}.workspace-nav button.active .nav-label small{color:#8eb1c1}.nav-chevron{opacity:.35}.nav-badge{min-width:19px;height:19px;border-radius:10px;padding:0 5px;background:#df9360;color:#fff;font-size:9px;display:grid;place-items:center}.sidebar-bottom{margin-top:auto}.sidebar-health{display:flex;align-items:center;gap:9px;background:#0b2137;border:1px solid #ffffff0c;border-radius:9px;padding:10px 11px}.health-dot{width:7px;height:7px;border-radius:50%;background:#31c29c;box-shadow:0 0 0 4px #31c29c18;display:inline-block}.sidebar-health b,.sidebar-health small{display:block}.sidebar-health b{font-size:9px;color:#c4dce4}.sidebar-health small{font-size:8px;color:#638397;margin-top:4px}.sidebar-version{margin:17px 3px 0;color:#648499;font-size:8px;letter-spacing:1px}.sidebar-version span{float:right;color:#476c83}.mobile-tabbar{display:none}@media(max-width:1040px){.workspace-sidebar{width:218px}}@media(max-width:720px){.workspace-sidebar{display:none}.mobile-tabbar{position:fixed;z-index:10;bottom:0;left:0;width:100%;height:69px;background:#fff;border-top:1px solid #dce6ef;display:grid;grid-template-columns:repeat(5,1fr);padding-bottom:env(safe-area-inset-bottom);box-shadow:0 -8px 22px #183c5c0b}.mobile-tabbar button{color:#9baab8;font-size:8px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:5px}.mobile-tabbar button.active{color:#2b72d3;font-weight:600}.mobile-scan{position:relative;top:-9px}.mobile-scan span{width:43px;height:43px;border-radius:15px;background:#2d73d2;color:#fff;display:grid;place-items:center;box-shadow:0 6px 14px #2d73d255;border:4px solid #f4f7fb}.mobile-scan small{color:#2d73d2}}
</style>
