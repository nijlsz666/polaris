<template>
  <main class="login-page">
    <div class="login-orbit orbit-one"></div>
    <div class="login-orbit orbit-two"></div>
    <div class="login-shell">
      <section class="login-showcase">
        <div class="showcase-brand"><span class="brand-mark">P</span><span><b>POLARIS</b><small>制造运营云</small></span></div>
        <p class="showcase-kicker">OPERATIONS CLOUD · 2026</p>
        <h1>让每一次现场动作，<br /><em>都变成可见的增长。</em></h1>
        <p class="showcase-copy">从计划、制造、仓储到质量，用一套统一的数据底座连接企业的每一个现场。</p>
        <div class="showcase-metrics"><div><b>99.98%</b><span>平台可用性</span></div><div><b>24 / 7</b><span>现场持续运行</span></div><div><b>+42%</b><span>数据决策效率</span></div></div>
        <div class="showcase-grid"><span v-for="item in 16" :key="item"></span></div>
      </section>

      <form class="login-card" @submit.prevent="submit">
        <div class="card-head"><p class="eyebrow">POLARIS WORKSPACE</p><h2>{{ mode === 'login' ? '欢迎回来' : '创建企业工作区' }}</h2><p>{{ mode === 'login' ? '登录你的制造运营空间，继续今天的现场工作。' : '14 天试用，无需信用卡，几分钟完成初始化。' }}</p></div>
        <div class="auth-tabs"><button type="button" :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</button><button type="button" :class="{ active: mode === 'register' }" @click="switchMode('register')">注册企业</button></div>

        <template v-if="mode === 'login'">
          <label>工作区<select v-model="loginForm.tenantCode"><option v-for="tenant in tenants" :key="tenant.tenant_code" :value="tenant.tenant_code">{{ tenant.tenant_name }} · {{ tenant.tenant_code }}</option></select></label>
          <label>用户名<input v-model.trim="loginForm.username" autocomplete="username" required placeholder="请输入用户名" /></label>
          <label>密码<input v-model="loginForm.password" autocomplete="current-password" required type="password" placeholder="请输入密码" /></label>
          <div v-if="captcha.required" class="captcha-field">
            <label>图形验证码
              <div class="captcha-row">
                <input v-model.trim="captcha.code" required maxlength="5" autocomplete="off" autocapitalize="characters" spellcheck="false" inputmode="text" placeholder="请输入图片中的字符" />
                <button type="button" class="captcha-image-button" :disabled="captchaLoading" aria-label="刷新验证码" @click="refreshCaptcha">
                  <img v-if="captcha.image" :src="captcha.image" alt="图形验证码" />
                  <span v-else>加载中…</span>
                </button>
              </div>
              <button type="button" class="captcha-refresh" :disabled="captchaLoading" @click="refreshCaptcha">看不清？换一张</button>
            </label>
          </div>
        </template>
        <template v-else>
          <div class="form-grid"><label>企业名称<input v-model.trim="registerForm.tenantName" required placeholder="例如：星河智能制造" /></label><label>工作区编码<input v-model.trim="registerForm.tenantCode" required pattern="[a-z][a-z0-9-]{2,31}" placeholder="例如：xinghe" /></label></div>
          <div class="form-grid"><label>管理员姓名<input v-model.trim="registerForm.displayName" required placeholder="你的姓名" /></label><label>管理员账号<input v-model.trim="registerForm.username" required placeholder="登录账号" /></label></div>
          <label>联系邮箱<input v-model.trim="registerForm.contactEmail" type="email" placeholder="用于接收平台通知（可选）" /></label>
          <label>设置密码<input v-model="registerForm.password" autocomplete="new-password" required type="password" placeholder="至少 8 位" /></label>
          <div class="password-meter"><i :class="`strength-${passwordStrength}`"></i><span>{{ passwordStrengthLabel }}</span></div>
        </template>

        <p v-if="error" class="login-error" role="alert">{{ error }}</p>
        <button class="button button-primary login-submit" :disabled="loading">{{ loading ? (mode === 'login' ? '登录中…' : '创建中…') : (mode === 'login' ? '进入工作台' : '创建工作区') }}<span>→</span></button>
        <p class="login-foot">{{ mode === 'login' ? '首次使用？切换到“注册企业”即可创建独立工作区。' : '注册即代表你同意 Polaris 的服务条款与数据安全规范。' }}</p>
      </form>
    </div>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { request } from '../api'

const router = useRouter(); const route = useRoute()
const mode = ref('login'); const tenants = ref([]); const loading = ref(false); const error = ref('')
const loginForm = reactive({ tenantCode: 'demo', username: '', password: '' })
const registerForm = reactive({ tenantCode: '', tenantName: '', displayName: '', username: '', contactEmail: '', password: '' })
const captchaLoading = ref(false)
const captcha = reactive({ required: false, id: '', image: '', code: '' })
const passwordStrength = computed(() => {
  const value = registerForm.password
  if (!value) return 'empty'
  let score = 0
  if (value.length >= 8) score++
  if (/[A-Z]/.test(value) && /[a-z]/.test(value)) score++
  if (/\d/.test(value) && /[^A-Za-z0-9]/.test(value)) score++
  return score >= 3 ? 'strong' : score === 2 ? 'medium' : 'weak'
})
const passwordStrengthLabel = computed(() => ({ empty: '建议使用 8 位以上的复杂密码', weak: '密码强度较弱', medium: '密码强度中等', strong: '密码强度良好' }[passwordStrength.value]))

async function loadTenants() {
  try { tenants.value = await request('/auth/tenants'); if (tenants.value[0] && !tenants.value.some(item => item.tenant_code === loginForm.tenantCode)) loginForm.tenantCode = tenants.value[0].tenant_code } catch (requestError) { error.value = requestError.message }
}
function resetCaptcha() { Object.assign(captcha, { required: false, id: '', image: '', code: '' }) }
function applyCaptcha(data) {
  if (!data?.captchaId || !data.image) return false
  Object.assign(captcha, { required: true, id: data.captchaId, image: data.image, code: '' })
  return true
}
function switchMode(nextMode) { mode.value = nextMode; error.value = ''; if (nextMode !== 'login') resetCaptcha() }
async function refreshCaptcha() {
  if (!loginForm.tenantCode || !loginForm.username) { error.value = '请先填写工作区和用户名'; return }
  captchaLoading.value = true
  try {
    const query = `?tenantCode=${encodeURIComponent(loginForm.tenantCode)}&username=${encodeURIComponent(loginForm.username)}`
    applyCaptcha(await request(`/auth/captcha${query}`))
  } catch (requestError) { error.value = requestError.message } finally { captchaLoading.value = false }
}
function saveSession(data) {
  localStorage.setItem('polaris-token', data.token); localStorage.setItem('polaris-user', JSON.stringify(data.user)); localStorage.setItem('polaris-tenant', JSON.stringify(data.tenant))
}
async function submit() {
  loading.value = true; error.value = ''
  try {
    const payload = mode.value === 'login'
      ? { ...loginForm, ...(captcha.required ? { captchaId: captcha.id, captchaCode: captcha.code } : {}) }
      : registerForm
    const data = await request(mode.value === 'login' ? '/auth/login' : '/auth/register', { method: 'POST', body: JSON.stringify(payload) })
    saveSession(data); const defaultPath = data.user?.roleCode === 'platform_admin' ? '/platform/overview' : '/erp'; await router.replace(String(route.query.redirect || defaultPath))
  } catch (requestError) {
    if (mode.value === 'login') applyCaptcha(requestError.payload?.data)
    error.value = requestError.message
  } finally { loading.value = false }
}
onMounted(loadTenants)
</script>

<style scoped>
.login-page{min-height:100vh;display:grid;place-items:center;position:relative;overflow:hidden;background:#07182c;padding:30px;color:#fff}.login-page:before{content:"";position:absolute;inset:0;background:radial-gradient(circle at 18% 20%,#1f71c933,transparent 28%),radial-gradient(circle at 85% 80%,#53c5b522,transparent 24%),linear-gradient(135deg,#07172a,#0c2c50 55%,#08172b)}.login-shell{width:min(1100px,100%);display:grid;grid-template-columns:1fr 470px;gap:70px;align-items:center;position:relative;z-index:1}.login-showcase{padding:14px 0}.showcase-brand{display:flex;gap:12px;align-items:center}.showcase-brand>span:last-child{display:flex;flex-direction:column}.showcase-brand b{font-size:15px;letter-spacing:3px}.showcase-brand small{font-size:10px;color:#86b5df;margin-top:4px;letter-spacing:1px}.brand-mark{width:35px;height:35px;display:grid;place-items:center;border-radius:10px;background:linear-gradient(135deg,#55b6ec,#2b67d9);font-weight:800;box-shadow:0 0 30px #3c9bea77}.showcase-kicker{font-size:10px;letter-spacing:3px;color:#6fa7d8;margin:76px 0 18px}.login-showcase h1{font-size:42px;line-height:1.2;letter-spacing:-1.5px;margin:0;font-weight:500}.login-showcase h1 em{font-style:normal;color:#70d8c0}.showcase-copy{max-width:420px;color:#91b5d5;font-size:13px;line-height:1.8;margin:22px 0 45px}.showcase-metrics{display:flex;gap:34px}.showcase-metrics div{display:flex;flex-direction:column;gap:5px}.showcase-metrics b{font-size:18px;color:#e6f4ff}.showcase-metrics span{font-size:10px;color:#6e96b9}.showcase-grid{display:grid;grid-template-columns:repeat(8,16px);gap:8px;position:absolute;left:0;bottom:-15px;opacity:.55}.showcase-grid span{height:2px;background:#2f80be;box-shadow:0 0 9px #2b84c9}.login-card{background:#f9fbfe;color:#173451;padding:34px;border-radius:18px;box-shadow:0 25px 90px #020d1c88;border:1px solid #ffffff22}.card-head h2{margin:0;font-size:26px;letter-spacing:-.5px}.card-head>p:last-child{font-size:12px;color:#8294a7;line-height:1.7;margin:9px 0 23px}.eyebrow{font-size:9px;letter-spacing:1.8px;color:#6290bb;margin:0 0 9px;font-weight:700}.auth-tabs{display:flex;border-bottom:1px solid #e6edf4;margin-bottom:22px;gap:22px}.auth-tabs button{font-size:12px;color:#97a6b5;padding:0 0 11px;position:relative}.auth-tabs button.active{color:#2466cf;font-weight:700}.auth-tabs button.active:after{content:"";position:absolute;height:2px;background:#2d73d5;bottom:-1px;left:0;right:0;border-radius:2px}.login-card label{display:block;color:#5e7184;font-size:11px;margin:14px 0}.login-card input,.login-card select{display:block;width:100%;height:40px;margin-top:7px;border:1px solid #dce5ed;border-radius:7px;padding:0 11px;background:#fff;color:#60758c;font-size:12px;outline:none}.login-card input:focus,.login-card select:focus{border-color:#6b9fe3;box-shadow:0 0 0 3px #6b9fe31c}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.form-grid label{min-width:0}.password-meter{display:flex;align-items:center;gap:8px;margin-top:-5px;font-size:10px;color:#9aa8b6}.password-meter i{display:block;width:52px;height:4px;border-radius:4px;background:#e3e9ef}.password-meter i.strength-weak{background:#e98e74}.password-meter i.strength-medium{background:#e5b15f}.password-meter i.strength-strong{background:#3cba91}.login-submit{width:100%;margin-top:19px;display:flex;justify-content:center;gap:12px;align-items:center;border-radius:7px;height:42px}.login-submit span{font-size:17px;line-height:0}.login-submit:disabled{opacity:.65;cursor:wait}.login-error{color:#c75f4f;background:#fff1ed;border-radius:6px;padding:9px;font-size:11px;margin:15px 0 0}.login-foot{text-align:center;color:#9aa9b7;font-size:10px;line-height:1.6;margin:16px 0 0}.login-orbit{position:absolute;border:1px solid #66b6e51c;border-radius:50%;z-index:0}.orbit-one{width:640px;height:640px;right:-260px;top:-240px}.orbit-two{width:420px;height:420px;left:-250px;bottom:-230px;border-color:#58cbb21c}@media(max-width:900px){.login-shell{grid-template-columns:1fr;max-width:520px}.login-showcase{display:none}.login-card{width:100%}}@media(max-width:520px){.login-page{padding:16px}.login-card{padding:25px 20px}.form-grid{grid-template-columns:1fr}}
.captcha-field{margin-top:14px}.captcha-field>label{margin:0}.captcha-row{display:flex;align-items:center;gap:10px;margin-top:7px}.captcha-row input{flex:1;min-width:0;width:auto;margin-top:0}.captcha-image-button{width:160px;height:40px;flex:none;padding:0;border:1px solid #dce5ed;border-radius:7px;overflow:hidden;background:#f4f8fc;color:#6d8296;font-size:11px;cursor:pointer}.captcha-image-button:disabled,.captcha-refresh:disabled{opacity:.6;cursor:wait}.captcha-image-button img{display:block;width:100%;height:100%;object-fit:cover}.captcha-refresh{display:block;padding:0;border:0;background:transparent;color:#4c83c6;font-size:10px;margin-top:6px;cursor:pointer}
</style>
