<script setup>
import { ref } from 'vue'
import api from '../api/index.js'
import { auth } from '../stores/auth.js'

const emit = defineEmits(['loggedIn'])
const isRegister = ref(false)
const username = ref('')
const password = ref('')
const confirmPwd = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  if (!username.value || !password.value) { error.value = '用户名和密码不能为空'; return }
  if (isRegister.value && password.value !== confirmPwd.value) { error.value = '两次密码不一致'; return }
  loading.value = true
  try {
    const url = isRegister.value ? '/auth/register' : '/auth/login'
    const { data } = await api.post(url, { username: username.value, password: password.value })
    if (data.code === 200) {
      const token = data.data.token
      const user = { id: data.data.userId, username: username.value }
      auth.setAuth(token, user)
      emit('loggedIn')
    } else {
      error.value = data.message || '操作失败'
    }
  } catch(e) {
    error.value = e.response?.data?.message || '网络错误'
  }
  loading.value = false
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="logo">Fin<span>Insight</span></div>
      <p class="sub">企业工单AI分析平台</p>
      <div class="form">
        <input v-model="username" placeholder="用户名" />
        <input v-model="password" type="password" placeholder="密码" />
        <input v-if="isRegister" v-model="confirmPwd" type="password" placeholder="确认密码" />
        <p v-if="error" class="err">{{ error }}</p>
        <button class="btn btn-primary" style="width:100%;padding:12px" @click="submit" :disabled="loading">
          {{ loading ? '请稍候...' : (isRegister ? '注册' : '登录') }}
        </button>
      </div>
      <p class="toggle">
        {{ isRegister ? '已有账号？' : '没有账号？' }}
        <a @click="isRegister=!isRegister;error=''">{{ isRegister ? '去登录' : '去注册' }}</a>
      </p>
    </div>
  </div>
</template>

<style scoped>
.login-page { display:flex; align-items:center; justify-content:center; height:100vh; background:#f5f6fa; }
.login-card { background:#fff; border-radius:16px; padding:40px; width:380px; box-shadow:0 2px 12px rgba(0,0,0,.06); text-align:center; }
.login-card .logo { font-size:24px; font-weight:700; color:#333; margin-bottom:4px; }
.login-card .logo span { color:#5b7fff; }
.sub { color:#999; font-size:13px; margin-bottom:24px; }
.form input { margin-bottom:12px; }
.toggle { margin-top:16px; font-size:13px; color:#999; }
.toggle a { color:#5b7fff; cursor:pointer; text-decoration:none; }
.err { color:#e74c3c; font-size:13px; margin-bottom:8px; text-align:left; }
</style>
