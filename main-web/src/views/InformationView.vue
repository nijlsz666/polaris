<template>
  <PageShell title="信息中心" description="平台公告、企业资料与业务单据附件统一管理，所有下载操作都会再次校验数据权限。">
    <template #actions>
      <span class="info-boundary"><i></i>{{ isPlatformAdmin ? '总管理员 · 可发布平台公告' : '当前租户 · 仅展示授权范围内资料' }}</span>
      <button class="button button-ghost" :disabled="loading" @click="load">↻ 刷新</button>
      <button v-if="isPlatformAdmin && tab === 'announcements'" class="button button-primary" @click="openAnnouncementEditor()">+ 发布公告</button>
    </template>

    <div class="subnav info-tabs">
      <button v-for="item in tabs" :key="item.key" :class="['subnav-item', { active: tab === item.key }]" @click="switchTab(item.key)">{{ item.label }}<span v-if="item.key === 'announcements' && announcements.length">{{ announcements.length }}</span></button>
    </div>
    <div v-if="notice" :class="['notice-bar', `notice-${notice.type}`]" role="status"><span>{{ notice.type === 'error' ? '!' : '✓' }}</span>{{ notice.message }}</div>

    <template v-if="tab === 'announcements'">
      <div class="info-summary-grid">
        <div><span>公告总数</span><b>{{ announcements.length }}</b><small>{{ isPlatformAdmin ? '含草稿与已发布' : '当前可见' }}</small></div>
        <div><span>最新公告</span><b>{{ latestAnnouncement ? formatDate(latestAnnouncement.publish_at || latestAnnouncement.created_at, true) : '—' }}</b><small>平台统一发布</small></div>
        <div><span>附件资料</span><b>{{ announcements.reduce((sum, item) => sum + Number(item.attachment_count || 0), 0) }}</b><small>公告可下载内容</small></div>
      </div>
      <div class="info-announcement-layout">
        <section class="panel info-announcement-list">
          <div class="panel-heading"><div><h3>平台新闻公告</h3><p>总管理员发布后，所有客户租户均可查看</p></div><span class="status-pill status-released">跨租户</span></div>
          <button v-for="item in announcements" :key="item.id" :class="['info-announcement-row', { active: selectedAnnouncement?.id === item.id }]" @click="selectAnnouncement(item)">
            <span class="info-announcement-icon">{{ item.status === 'PUBLISHED' ? '公' : '草' }}</span><span class="info-announcement-row__body"><b>{{ item.title }}</b><small>{{ item.summary || '暂无摘要' }}</small><em>{{ formatDate(item.publish_at || item.created_at) }} · {{ item.attachment_count || 0 }} 个附件</em></span><span v-if="isPlatformAdmin" :class="['status-pill', item.status === 'PUBLISHED' ? 'status-completed' : 'status-planned']">{{ item.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span>
          </button>
          <div v-if="!announcements.length" class="empty-operation"><div class="operation-icon">◌</div><h3>暂无公告</h3><p>{{ isPlatformAdmin ? '发布第一条平台公告，租户会在信息中心看到。' : '平台暂未发布新的公告。' }}</p></div>
        </section>
        <section v-if="selectedAnnouncement" class="panel info-announcement-detail">
          <div class="info-detail-head"><div><p class="eyebrow">POLARIS ANNOUNCEMENT</p><h2>{{ selectedAnnouncement.title }}</h2><p class="info-detail-meta">{{ formatDate(selectedAnnouncement.publish_at || selectedAnnouncement.created_at) }} · 发布人 {{ selectedAnnouncement.created_by || '平台' }}</p></div><button v-if="isPlatformAdmin" class="button button-ghost" @click="openAnnouncementEditor(selectedAnnouncement)">编辑</button></div>
          <div v-if="selectedAnnouncement.cover_image_url" class="info-cover"><img :src="selectedAnnouncement.cover_image_url" alt="公告配图" @error="coverError = true" /><small v-if="coverError">图片地址暂时无法加载，但不影响正文与附件下载。</small></div>
          <p v-if="selectedAnnouncement.summary" class="info-summary">{{ selectedAnnouncement.summary }}</p>
          <div class="info-rich-content">{{ selectedAnnouncement.content }}</div>
          <div class="info-attachment-section"><div class="panel-heading"><div><h3>可下载内容</h3><p>附件下载权限随公告可见范围校验</p></div><span>{{ selectedAnnouncement.attachments?.length || 0 }} 个</span></div><div v-for="file in selectedAnnouncement.attachments || []" :key="file.id" class="info-file-row"><span class="file-type">{{ fileType(file.original_name) }}</span><span><b>{{ file.original_name }}</b><small>{{ formatSize(file.file_size) }} · {{ formatDate(file.created_at) }}</small></span><button class="table-action" @click="download(`/announcements/${selectedAnnouncement.id}/attachments/${file.id}/download`, file.original_name)">下载</button></div><div v-if="!selectedAnnouncement.attachments?.length" class="info-muted">这条公告没有附件。</div></div>
        </section>
        <section v-else class="panel info-announcement-detail info-empty-detail"><div class="empty-operation"><div class="operation-icon">▤</div><h3>选择一条公告</h3><p>公告正文、配图和可下载附件会显示在这里。</p></div></section>
      </div>
    </template>

    <template v-else-if="tab === 'documents'">
      <div class="info-document-layout">
        <section class="panel info-upload-panel"><div class="panel-heading"><div><h3>上传资料</h3><p>资料仅属于当前租户，管理员可查看全部资料</p></div><span class="status-pill status-released">租户隔离</span></div><label class="info-form-label">资料文件<input type="file" @change="selectDocumentFile" /></label><label class="info-form-label">资料标题<input v-model="documentForm.title" placeholder="例如：2026 年质量检验标准" /></label><div class="info-form-grid"><label class="info-form-label">分类<select v-model="documentForm.category"><option value="GENERAL">通用资料</option><option value="QUALITY">质量标准</option><option value="PROCUREMENT">采购资料</option><option value="MANUFACTURING">生产工艺</option><option value="FINANCE">财务资料</option></select></label><label class="info-form-label">说明<input v-model="documentForm.description" placeholder="可选" /></label></div><div class="info-upload-tip">单个文件最大 100 MB，上传后会计入当前租户存储配额。</div><button class="button button-primary full-button" :disabled="saving || !documentFile" @click="uploadDocument">{{ saving ? '上传中…' : '上传到资料中心' }}</button></section>
        <section class="panel"><div class="panel-heading"><div><h3>资料清单</h3><p>支持按标题、文件名和说明搜索</p></div><div class="info-filter"><input v-model="documentKeyword" placeholder="搜索资料" @keyup.enter="loadDocuments" /><button class="button button-ghost" @click="loadDocuments">搜索</button></div></div><div class="table-wrap"><table class="data-table info-table"><thead><tr><th>资料名称</th><th>分类</th><th>文件</th><th>上传人 / 时间</th><th>操作</th></tr></thead><tbody><tr v-for="file in documents" :key="file.id"><td><b>{{ file.title }}</b><small class="info-cell-note">{{ file.description || '暂无说明' }}</small></td><td><span class="status-pill status-in_progress">{{ categoryLabel(file.category) }}</span></td><td>{{ file.original_name }}<small class="info-cell-note">{{ formatSize(file.file_size) }}</small></td><td>{{ file.uploaded_by }}<small class="info-cell-note">{{ formatDate(file.created_at) }}</small></td><td><button class="table-action" @click="download(`/documents/${file.id}/download`, file.original_name)">下载</button><button v-if="isPlatformAdmin || file.uploaded_by === currentUser" class="table-action danger-link" @click="removeDocument(file)">删除</button></td></tr><tr v-if="!documents.length"><td colspan="5" class="empty-state">暂无资料，先上传一份文件。</td></tr></tbody></table></div></section>
      </div>
    </template>

    <template v-else>
      <section class="panel info-record-panel"><div class="panel-heading"><div><h3>业务单据附件</h3><p>附件跟随业务模块和单据权限控制：普通用户仅能查看自己的单据，租户管理员可查看全部。</p></div><span class="status-pill status-released">权限随单据</span></div><div class="info-record-toolbar"><div class="info-module-switch"><button v-for="item in recordModules" :key="item.key" :class="['subnav-item', { active: selectedDomain === item.key }]" @click="switchDomain(item.key)">{{ item.label }}</button></div><input v-model="recordKeyword" placeholder="搜索单号 / 单据名称" @keyup.enter="loadRecords" /><button class="button button-ghost" @click="loadRecords">搜索</button></div><div class="info-record-layout"><div class="info-record-list"><button v-for="record in records" :key="record.id" :class="['info-record-row', { active: selectedRecord?.id === record.id }]" @click="selectRecord(record)"><span class="record-domain-mark">{{ selectedDomain.slice(0, 1) }}</span><span><b>{{ record.no }}</b><small>{{ record.name }}</small><em>{{ record.owner || record.createdBy }} · {{ statusLabel(record.status) }}</em></span></button><div v-if="!records.length" class="empty-operation"><h3>暂无可查看单据</h3><p>当前账号只能看到自己创建、负责或申请的单据。</p></div></div><div class="info-record-attachments"><template v-if="selectedRecord"><div class="info-selected-record"><div><p class="eyebrow">{{ selectedDomain }} RECORD</p><h2>{{ selectedRecord.no }}</h2><p>{{ selectedRecord.name }} · {{ selectedRecord.owner || selectedRecord.createdBy }}</p></div><span class="status-pill status-in_progress">{{ statusLabel(selectedRecord.status) }}</span></div><div class="info-attachment-upload"><input ref="recordFileInput" type="file" @change="selectRecordFile" /><button class="button button-primary" :disabled="saving || !recordFile" @click="uploadRecordAttachment">{{ saving ? '上传中…' : '上传附件' }}</button><small>单据附件只能上传到已授权的本人单据；管理员可管理所有本租户单据。</small></div><div v-for="file in recordAttachments" :key="file.id" class="info-file-row"><span class="file-type">{{ fileType(file.original_name) }}</span><span><b>{{ file.original_name }}</b><small>{{ formatSize(file.file_size) }} · {{ file.created_by }} · {{ formatDate(file.created_at) }}</small></span><button class="table-action" @click="download(`/erp/${selectedDomain.toLowerCase()}/records/${selectedRecord.id}/attachments/${file.id}/download`, file.original_name)">下载</button></div><div v-if="!recordAttachments.length" class="info-muted">该单据还没有附件。</div></template><div v-else class="empty-operation"><div class="operation-icon">⌘</div><h3>选择业务单据</h3><p>先选择业务模块，再选择单据查看或上传附件。</p></div></div></div></section>
    </template>

    <div v-if="showAnnouncementEditor" class="modal-backdrop" @click.self="showAnnouncementEditor = false"><div class="modal info-announcement-modal"><div class="modal-heading"><div><h3>{{ announcementForm.id ? '编辑公告' : '发布公告' }}</h3><p>发布后会同步展示到所有客户租户的信息中心</p></div><button @click="showAnnouncementEditor = false">×</button></div><label>公告标题<input v-model="announcementForm.title" placeholder="例如：春节期间系统服务安排" /></label><label>摘要<input v-model="announcementForm.summary" placeholder="一句话说明公告重点" /></label><label>配图地址（可选）<input v-model="announcementForm.coverImageUrl" placeholder="https://… 或 CDN 图片地址" /></label><label>正文内容<textarea v-model="announcementForm.content" rows="8" placeholder="填写公告正文；支持换行和图片链接说明"></textarea></label><label>发布状态<select v-model="announcementForm.status"><option value="DRAFT">保存为草稿</option><option value="PUBLISHED">立即发布</option></select></label><label class="info-form-label">附件（可多选）<input type="file" multiple @change="selectAnnouncementFiles" /></label><div class="info-selected-files" v-if="announcementFiles.length">已选择：{{ announcementFiles.map(file => file.name).join('、') }}</div><div class="modal-actions"><button class="button button-ghost" @click="showAnnouncementEditor = false">取消</button><button class="button button-primary" :disabled="saving" @click="saveAnnouncement">{{ saving ? '保存中…' : announcementForm.status === 'PUBLISHED' ? '保存并发布' : '保存草稿' }}</button></div></div></div>
  </PageShell>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageShell from '../components/PageShell.vue'
import { API_BASE, request } from '../api'

const route = useRoute(); const router = useRouter()
const tabs = [{ key: 'announcements', label: '新闻公告' }, { key: 'documents', label: '资料中心' }, { key: 'attachments', label: '单据附件' }]
const recordModules = [{ key: 'sales', label: '销售' }, { key: 'procurement', label: '采购' }, { key: 'finance', label: '财务' }, { key: 'master', label: '主数据' }]
const validTab = value => tabs.some(item => item.key === value) ? value : 'announcements'
const tab = computed(() => validTab(String(route.params.tab || 'announcements')))
const currentUser = ref(''); const isPlatformAdmin = ref(false); const loading = ref(false); const saving = ref(false); const notice = ref(null)
const announcements = ref([]); const selectedAnnouncement = ref(null); const coverError = ref(false); const showAnnouncementEditor = ref(false); const announcementFiles = ref([])
const announcementForm = ref({ id: null, title: '', summary: '', content: '', coverImageUrl: '', status: 'DRAFT' })
const documents = ref([]); const documentKeyword = ref(''); const documentFile = ref(null); const documentForm = ref({ title: '', category: 'GENERAL', description: '' })
const selectedDomain = ref('sales'); const records = ref([]); const recordKeyword = ref(''); const selectedRecord = ref(null); const recordAttachments = ref([]); const recordFile = ref(null)
const latestAnnouncement = computed(() => announcements.value[0])

function loadSession() { try { const user = JSON.parse(localStorage.getItem('polaris-user') || '{}'); currentUser.value = user.username || ''; isPlatformAdmin.value = user.roleCode === 'platform_admin' } catch {} }
function showNotice(message, type = 'success') { notice.value = { message, type }; window.setTimeout(() => { notice.value = null }, 3600) }
function switchTab(value) { router.push(`/information/${value}`) }
function switchDomain(value) { selectedDomain.value = value; selectedRecord.value = null; recordAttachments.value = []; loadRecords() }
function formatDate(value, short = false) { if (!value) return '—'; const text = String(value).replace('T', ' '); return short ? text.slice(0, 10) : text.slice(0, 16) }
function formatSize(value) { const bytes = Number(value || 0); if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(2)} GB`; if (bytes >= 1024 ** 2) return `${(bytes / 1024 ** 2).toFixed(1)} MB`; if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KB`; return `${bytes} B` }
function fileType(name) { const suffix = String(name || '').split('.').pop()?.toUpperCase(); return suffix && suffix.length <= 5 ? suffix : 'FILE' }
function categoryLabel(value) { return ({ GENERAL: '通用资料', QUALITY: '质量标准', PROCUREMENT: '采购资料', MANUFACTURING: '生产工艺', FINANCE: '财务资料' })[value] || value || '通用资料' }
function statusLabel(value) { return ({ DRAFT: '草稿', REVIEW: '待审核', CONFIRMED: '已确认', APPROVED: '已审批', ORDERED: '已下单', IN_TRANSIT: '运输中', RECEIVED: '已收货', PENDING: '待处理', INVOICED: '已开票', PAID: '已结清', ACTIVE: '生效' })[value] || value || '未知' }

async function loadAnnouncements() { const rows = await request('/announcements'); announcements.value = rows || []; if (announcements.value.length) { const current = announcements.value.find(item => item.id === selectedAnnouncement.value?.id) || announcements.value[0]; await selectAnnouncement(current) } else selectedAnnouncement.value = null }
async function selectAnnouncement(item) { coverError.value = false; try { selectedAnnouncement.value = await request(`/announcements/${item.id}`) } catch (error) { showNotice(error.message, 'error') } }
function openAnnouncementEditor(item = null) { announcementForm.value = item ? { id: item.id, title: item.title || '', summary: item.summary || '', content: item.content || '', coverImageUrl: item.cover_image_url || '', status: item.status || 'DRAFT' } : { id: null, title: '', summary: '', content: '', coverImageUrl: '', status: 'DRAFT' }; announcementFiles.value = []; showAnnouncementEditor.value = true }
function selectAnnouncementFiles(event) { announcementFiles.value = Array.from(event.target.files || []) }
async function saveAnnouncement() { if (!announcementForm.value.title.trim() || !announcementForm.value.content.trim()) return showNotice('公告标题和正文不能为空', 'warning'); saving.value = true; try { const payload = { title: announcementForm.value.title, summary: announcementForm.value.summary, content: announcementForm.value.content, coverImageUrl: announcementForm.value.coverImageUrl, status: announcementForm.value.status }; const saved = await request(announcementForm.value.id ? `/announcements/${announcementForm.value.id}` : '/announcements', { method: announcementForm.value.id ? 'PUT' : 'POST', body: JSON.stringify(payload) }); if (announcementFiles.value.length) { const form = new FormData(); announcementFiles.value.forEach(file => form.append('files', file)); await uploadMultipart(`/announcements/${saved.id}/attachments`, form) } showAnnouncementEditor.value = false; showNotice(saved.status === 'PUBLISHED' ? '公告已发布，所有租户均可查看' : '公告草稿已保存'); await loadAnnouncements() } catch (error) { showNotice(error.message, 'error') } finally { saving.value = false } }

async function loadDocuments() { documents.value = await request(`/documents${documentKeyword.value.trim() ? `?keyword=${encodeURIComponent(documentKeyword.value.trim())}` : ''}`) || [] }
function selectDocumentFile(event) { documentFile.value = event.target.files?.[0] || null; if (documentFile.value && !documentForm.value.title) documentForm.value.title = documentFile.value.name.replace(/\.[^.]+$/, '') }
async function uploadDocument() { if (!documentFile.value) return showNotice('请选择资料文件', 'warning'); saving.value = true; try { const form = new FormData(); form.append('file', documentFile.value); form.append('title', documentForm.value.title); form.append('category', documentForm.value.category); form.append('description', documentForm.value.description); await uploadMultipart('/documents', form); documentFile.value = null; documentForm.value = { title: '', category: 'GENERAL', description: '' }; showNotice('资料已上传'); await loadDocuments() } catch (error) { showNotice(error.message, 'error') } finally { saving.value = false } }
async function removeDocument(file) { if (!window.confirm(`确认删除资料“${file.title}”吗？`)) return; try { await request(`/documents/${file.id}`, { method: 'DELETE' }); showNotice('资料已删除'); await loadDocuments() } catch (error) { showNotice(error.message, 'error') } }

async function loadRecords() { try { records.value = await request(`/erp/${selectedDomain.value}/records${recordKeyword.value.trim() ? `?keyword=${encodeURIComponent(recordKeyword.value.trim())}` : ''}`) || []; if (!records.value.find(item => item.id === selectedRecord.value?.id)) { selectedRecord.value = records.value[0] || null; if (selectedRecord.value) await loadRecordAttachments() } } catch (error) { showNotice(error.message, 'error') } }
async function selectRecord(record) { selectedRecord.value = record; await loadRecordAttachments() }
async function loadRecordAttachments() { if (!selectedRecord.value) return; try { recordAttachments.value = await request(`/erp/${selectedDomain.value}/records/${selectedRecord.value.id}/attachments`) || [] } catch (error) { recordAttachments.value = []; showNotice(error.message, 'error') } }
function selectRecordFile(event) { recordFile.value = event.target.files?.[0] || null }
async function uploadRecordAttachment() { if (!recordFile.value || !selectedRecord.value) return showNotice('请选择附件文件', 'warning'); saving.value = true; try { const form = new FormData(); form.append('file', recordFile.value); await uploadMultipart(`/erp/${selectedDomain.value}/records/${selectedRecord.value.id}/attachments`, form); recordFile.value = null; showNotice('单据附件已上传'); await loadRecordAttachments() } catch (error) { showNotice(error.message, 'error') } finally { saving.value = false } }

async function uploadMultipart(path, form) { const response = await fetch(`${API_BASE}${path}`, { method: 'POST', headers: { Accept: 'application/json', Authorization: `Bearer ${localStorage.getItem('polaris-token') || ''}` }, body: form }); let result; try { result = await response.json() } catch { result = {} } if (!response.ok || result.success === false) throw new Error(result.message || `上传失败（HTTP ${response.status}）`); return result.data }
async function download(path, fileName) { try { const response = await fetch(`${API_BASE}${path}`, { headers: { Authorization: `Bearer ${localStorage.getItem('polaris-token') || ''}` } }); if (!response.ok) { let payload = {}; try { payload = await response.json() } catch {} throw new Error(payload.message || '下载失败') } const blob = await response.blob(); const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = fileName || 'download'; link.click(); window.setTimeout(() => URL.revokeObjectURL(url), 500) } catch (error) { showNotice(error.message, 'error') } }

async function load() { loading.value = true; try { if (tab.value === 'announcements') await loadAnnouncements(); if (tab.value === 'documents') await loadDocuments(); if (tab.value === 'attachments') await loadRecords() } catch (error) { showNotice(error.message, 'error') } finally { loading.value = false } }
loadSession(); onMounted(load); watch(() => route.params.tab, load)
</script>
