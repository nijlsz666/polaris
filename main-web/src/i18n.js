import { computed, ref } from 'vue'

const messages = {
  'zh-CN': {
    common: { refresh: '刷新', save: '保存', cancel: '取消', confirm: '确认', loading: '加载中…', empty: '暂无数据', search: '搜索', language: '中文' },
    approval: { title: '审批中心', todo: '我的待办', done: '我的已办', instances: '流程实例', approve: '同意', reject: '驳回' },
    status: { RUNNING: '运行中', APPROVED: '已通过', REJECTED: '已驳回', CANCELLED: '已撤回', TODO: '待处理', DONE: '已办' }
  },
  'en-US': {
    common: { refresh: 'Refresh', save: 'Save', cancel: 'Cancel', confirm: 'Confirm', loading: 'Loading…', empty: 'No data', search: 'Search', language: 'English' },
    approval: { title: 'Approvals', todo: 'My tasks', done: 'Completed', instances: 'Instances', approve: 'Approve', reject: 'Reject' },
    status: { RUNNING: 'Running', APPROVED: 'Approved', REJECTED: 'Rejected', CANCELLED: 'Cancelled', TODO: 'To do', DONE: 'Done' }
  }
}

const locale = ref(localStorage.getItem('polaris-locale') || 'zh-CN')

export function useLocale() {
  const current = computed(() => messages[locale.value] || messages['zh-CN'])
  function t(path, fallback = path) {
    const value = path.split('.').reduce((source, key) => source?.[key], current.value)
    return value === undefined ? fallback : value
  }
  function setLocale(next) {
    locale.value = messages[next] ? next : 'zh-CN'
    localStorage.setItem('polaris-locale', locale.value)
    document.documentElement.lang = locale.value
  }
  function toggleLocale() { setLocale(locale.value === 'zh-CN' ? 'en-US' : 'zh-CN') }
  return { locale, t, setLocale, toggleLocale }
}

export { messages }
