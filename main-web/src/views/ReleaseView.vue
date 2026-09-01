<template>
  <PageShell title="发版管理" description="统一生成数据包与部署包，以清单指纹保证测试系统和生产发布内容完全一致。">
    <template #actions>
      <button class="button button-ghost" @click="load">↻ 刷新</button>
      <button class="button button-primary" @click="showGenerate = true">+ 生成发版包</button>
    </template>

    <div v-if="notice" :class="['release-notice', notice.type]">{{ notice.message }}</div>

    <div class="release-kpis">
      <div class="release-kpi"><span>版本总数</span><strong>{{ overview.total }}</strong><small>已纳入版本台账</small></div>
      <div class="release-kpi"><span>待发布</span><strong class="warning-text">{{ overview.generated }}</strong><small>必须先通过一致性校验</small></div>
      <div class="release-kpi"><span>已验证</span><strong class="success-text">{{ overview.verified }}</strong><small>指纹与目标环境一致</small></div>
      <div class="release-kpi"><span>校验失败</span><strong :class="overview.failed ? 'danger-text' : 'success-text'">{{ overview.failed }}</strong><small>{{ overview.failed ? '存在环境漂移' : '当前无异常' }}</small></div>
    </div>

    <div class="release-layout">
      <section class="panel release-pipeline">
        <div class="panel-heading"><div><h3>快速发布</h3><p>每个版本必须完成生成、校验、发布三步</p></div><span class="status-pill status-released">SHA-256 清单</span></div>
        <div class="release-flow">
          <div class="release-flow-step done"><span>1</span><div><b>生成发版包</b><small>锁定当前环境数据与部署文件</small></div></div>
          <i>→</i>
          <div :class="['release-flow-step', nextRelease && nextRelease.verification_status === 'PASSED' ? 'done' : 'active']"><span>2</span><div><b>快速验证</b><small>逐项比对文件并计算环境指纹</small></div></div>
          <i>→</i>
          <div :class="['release-flow-step', nextRelease && nextRelease.status === 'PUBLISHED' ? 'done' : 'pending']"><span>3</span><div><b>快速发布</b><small>仅允许发布已验证版本</small></div></div>
        </div>
        <div v-if="nextRelease" class="release-current">
          <div><span class="eyebrow">{{ nextRelease.status === 'PUBLISHED' ? '最近发布版本' : '待发布版本' }}</span><h2>{{ nextRelease.version }}</h2><p>{{ packageLabel(nextRelease.package_type) }} · {{ nextRelease.source_environment }} → {{ nextRelease.target_environment }}</p></div>
          <div class="release-current-actions"><button class="button button-ghost" @click="verifyRelease(nextRelease)">{{ nextRelease.verification_status === 'PASSED' ? '再次校验' : '快速校验' }}</button><button class="button button-primary" :disabled="nextRelease.status === 'PUBLISHED' || nextRelease.verification_status !== 'PASSED'" @click="publishRelease(nextRelease)">快速发布</button></div>
        </div>
        <div v-else class="empty-operation release-empty">还没有发版记录，先生成一个数据包或部署包。</div>
      </section>

      <section class="panel release-integrity">
        <div class="panel-heading"><div><h3>一致性保障</h3><p>发版前后都可复核，不依赖人工记版本号</p></div><span class="integrity-icon">✓</span></div>
        <div class="integrity-item"><span class="integrity-dot blue"></span><div><b>清单固化</b><small>每个文件记录大小和 SHA-256</small></div></div>
        <div class="integrity-item"><span class="integrity-dot green"></span><div><b>环境重算</b><small>目标系统实时重算当前指纹</small></div></div>
        <div class="integrity-item"><span class="integrity-dot violet"></span><div><b>发布门禁</b><small>校验失败时发布按钮自动锁定</small></div></div>
        <div class="integrity-tip">同一份包在测试系统验证通过后，才能进入生产发布流程；任何数据或文件变更都会导致指纹变化。</div>
      </section>
    </div>

    <section class="panel release-table-panel">
      <div class="panel-heading"><div><h3>版本台账</h3><p>记录包类型、环境、校验结果和发布轨迹</p></div><div class="release-filters"><button v-for="item in filters" :key="item.key" :class="['subnav-item', { active: statusFilter === item.key }]" @click="statusFilter = item.key">{{ item.label }}</button></div></div>
      <div class="table-wrap"><table class="data-table release-table"><thead><tr><th>版本</th><th>包类型</th><th>环境路径</th><th>内容指纹</th><th>状态</th><th>创建人 / 时间</th><th>操作</th></tr></thead><tbody>
        <tr v-for="release in filteredReleases" :key="release.id">
          <td><b class="release-version">{{ release.version }}</b><small class="release-no">{{ release.release_no }}</small></td>
          <td><span :class="['package-badge', release.package_type === 'DATA' ? 'data' : 'deployment']">{{ packageLabel(release.package_type) }}</span><small class="release-file">{{ release.artifact_count || 0 }} 个清单项</small></td>
          <td><span class="env-route"><i>{{ release.source_environment }}</i><em>→</em><i>{{ release.target_environment }}</i></span></td>
          <td><code class="hash-code" :title="release.artifact_hash">{{ shortHash(release.artifact_hash) }}</code></td>
          <td><span :class="['status-pill', `status-${String(release.status || '').toLowerCase()}`]">{{ statusLabel(release.status) }}</span><small :class="['verify-state', release.verification_status === 'PASSED' ? 'passed' : release.verification_status === 'FAILED' ? 'failed' : 'waiting']">{{ verificationLabel(release.verification_status) }}</small></td>
          <td><span class="release-creator">{{ release.created_by }}</span><small class="release-file">{{ formatDate(release.created_at) }}</small></td>
          <td><button class="table-action" @click="verifyRelease(release)">{{ release.verification_status === 'PASSED' ? '复核' : '校验' }}</button><button class="table-action" :disabled="release.status === 'PUBLISHED' || release.verification_status !== 'PASSED'" @click="publishRelease(release)">发布</button><button class="table-action" @click="downloadRelease(release)">下载</button></td>
        </tr>
        <tr v-if="!filteredReleases.length"><td colspan="7" class="empty-state">暂无符合条件的版本</td></tr>
      </tbody></table></div>
    </section>

    <div v-if="showGenerate" class="modal-backdrop" @click.self="showGenerate = false"><div class="modal release-modal"><div class="modal-heading"><div><h3>生成发版包</h3><p>生成后会锁定当前租户的清单指纹</p></div><button @click="showGenerate = false">×</button></div><label>版本号<input v-model="form.version" placeholder="V2.7.0" /></label><label>包类型<select v-model="form.packageType"><option value="DATA">数据包 · 配置与业务数据</option><option value="DEPLOYMENT">部署包 · 数据库脚本与运行描述</option></select></label><div class="release-form-grid"><label>来源环境<select v-model="form.sourceEnvironment"><option value="DEV">开发环境</option><option value="TEST">测试环境</option><option value="STAGING">预发布环境</option></select></label><label>目标环境<select v-model="form.targetEnvironment"><option value="TEST">测试环境</option><option value="STAGING">预发布环境</option><option value="PRODUCTION">生产环境</option></select></label></div><div class="release-modal-tip">包内会自动生成 <code>release-manifest.json</code> 和 <code>checksums.sha256</code>。目标环境导入后点击“快速校验”，内容不一致时无法发布。</div><div class="modal-actions"><button class="button button-ghost" @click="showGenerate = false">取消</button><button class="button button-primary" :disabled="saving" @click="createRelease">{{ saving ? '生成中…' : '生成并登记' }}</button></div></div></div>
  </PageShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import PageShell from '../components/PageShell.vue'
import { API_BASE, request } from '../api'

const overview = ref({ total: 0, generated: 0, published: 0, verified: 0, failed: 0 }); const releases = ref([]); const statusFilter = ref('ALL'); const showGenerate = ref(false); const saving = ref(false); const notice = ref(null)
const form = ref({ version: '', packageType: 'DEPLOYMENT', sourceEnvironment: 'TEST', targetEnvironment: 'PRODUCTION' })
const filters = [{ key: 'ALL', label: '全部' }, { key: 'GENERATED', label: '待发布' }, { key: 'PUBLISHED', label: '已发布' }, { key: 'FAILED', label: '校验异常' }]
const filteredReleases = computed(() => releases.value.filter(item => statusFilter.value === 'ALL' || (statusFilter.value === 'FAILED' ? item.verification_status === 'FAILED' : item.status === statusFilter.value)))
const nextRelease = computed(() => releases.value.find(item => item.status === 'GENERATED') || releases.value[0])

function packageLabel(type) { return type === 'DATA' ? '数据包' : '部署包' }
function statusLabel(status) { return status === 'PUBLISHED' ? '已发布' : status === 'GENERATED' ? '待发布' : status || '未知' }
function verificationLabel(status) { return status === 'PASSED' ? '校验通过' : status === 'FAILED' ? '校验失败' : '待校验' }
function shortHash(hash) { return hash && hash.length > 18 ? `${hash.slice(0, 10)}…${hash.slice(-8)}` : hash || '-' }
function formatDate(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '-' }
function showNotice(message, type = 'success') { notice.value = { message, type }; window.setTimeout(() => { notice.value = null }, 3200) }

async function load() {
  try {
    const [summary, rows] = await Promise.all([request('/releases/overview'), request('/releases')])
    overview.value = summary || { total: 0, generated: 0, published: 0, verified: 0, failed: 0 }; releases.value = rows || []
  } catch (error) { showNotice(`发版服务暂不可用：${error.message}`, 'error') }
}
async function createRelease() {
  if (!form.value.version.trim()) return showNotice('请先填写版本号', 'warning')
  saving.value = true
  try { const release = await request('/releases', { method: 'POST', body: JSON.stringify(form.value) }); releases.value = [release, ...releases.value]; overview.value.total += 1; overview.value.generated += 1; showGenerate.value = false; form.value.version = ''; showNotice('发版包已生成，等待目标环境校验') }
  catch (error) { showNotice(error.message, 'error') } finally { saving.value = false }
}
async function verifyRelease(release) {
  try { const result = await request(`/releases/${release.id}/verify`, { method: 'POST', body: JSON.stringify({ environment: release.target_environment }) }); Object.assign(release, result); showNotice(result.consistent ? '一致性校验通过，可以发布' : '校验失败，目标环境存在差异', result.consistent ? 'success' : 'error'); await load() }
  catch (error) { showNotice(error.message, 'error') }
}
async function publishRelease(release) {
  try { const result = await request(`/releases/${release.id}/publish`, { method: 'POST', body: JSON.stringify({}) }); Object.assign(release, result); showNotice('版本已快速发布'); await load() }
  catch (error) { showNotice(error.message, 'error') }
}
async function downloadRelease(release) {
  try { const response = await fetch(`${API_BASE}/releases/${release.id}/download`, { headers: { Authorization: `Bearer ${localStorage.getItem('polaris-token') || ''}` } }); if (!response.ok) throw new Error('发版包下载失败'); const blob = await response.blob(); const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = release.package_name || `${release.version}.zip`; link.click(); URL.revokeObjectURL(url) }
  catch (error) { showNotice(error.message, 'error') }
}
onMounted(load)
</script>
