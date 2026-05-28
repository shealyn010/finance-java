<script setup>
import { ref } from 'vue'
import { auth } from './stores/auth.js'
import Login from './views/Login.vue'
import WorkOrders from './views/WorkOrders.vue'
import AgentChat from './views/AgentChat.vue'
import Report from './views/Report.vue'
import Inventory from './views/Inventory.vue'

const loggedIn = ref(auth.isLoggedIn())

function onLogin() { loggedIn.value = true }

function doLogout() { auth.logout(); loggedIn.value = false }

const nav = [
  { key: 'orders', label: '工单管理', icon: '📋' },
  { key: 'inventory', label: '配件库存', icon: '📦' },
  { key: 'agent', label: 'AI 助手', icon: '🤖' },
  { key: 'report', label: '报表', icon: '📊' },
]
const active = ref('orders')
</script>

<template>
  <Login v-if="!loggedIn" @loggedIn="onLogin" />
  <div v-else class="app">
    <aside class="sidebar">
      <div class="logo">Fin<span>Insight</span></div>
      <nav>
        <a v-for="n in nav" :key="n.key"
           :class="{ active: active === n.key }"
           @click="active = n.key">
          <span class="i">{{ n.icon }}</span>
          <span>{{ n.label }}</span>
        </a>
      </nav>
      <div class="user" style="cursor:pointer" @click="doLogout">👤 {{ auth.user?.username }} | 退出</div>
    </aside>
    <main class="main">
      <WorkOrders v-if="active === 'orders'" />
      <Inventory v-if="active === 'inventory'" />
      <AgentChat v-if="active === 'agent'" />
      <Report v-if="active === 'report'" />
    </main>
  </div>
</template>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; background: #f5f6fa; }
.app { display: flex; height: 100vh; }
.sidebar {
  width: 220px; background: #fff; border-right: 1px solid #eef0f5;
  display: flex; flex-direction: column; padding: 20px;
}
.logo { font-size: 20px; font-weight: 700; color: #333; margin-bottom: 30px; }
.logo span { color: #5b7fff; }
nav a {
  display: flex; align-items: center; gap: 10px; padding: 12px 14px;
  border-radius: 10px; cursor: pointer; color: #666; font-size: 15px;
  margin-bottom: 4px; transition: all .2s; text-decoration: none;
}
nav a:hover { background: #f5f7ff; color: #5b7fff; }
nav a.active { background: #5b7fff; color: #fff; }
.i { font-size: 18px; }
.user { margin-top: auto; padding: 12px; color: #999; font-size: 13px; border-top: 1px solid #f0f0f0; }
.main { flex: 1; padding: 30px; overflow-y: auto; }
.card {
  background: #fff; border-radius: 14px; padding: 24px;
  box-shadow: 0 1px 3px rgba(0,0,0,.04); margin-bottom: 20px;
}
.btn {
  padding: 8px 20px; border-radius: 8px; border: none; cursor: pointer;
  font-size: 14px; font-weight: 500; transition: all .2s;
}
.btn-primary { background: #5b7fff; color: #fff; }
.btn-primary:hover { background: #4a6eef; }
.btn-outline { background: #fff; color: #5b7fff; border: 1.5px solid #5b7fff; }
.tag {
  display: inline-block; padding: 3px 10px; border-radius: 20px;
  font-size: 12px; font-weight: 500;
}
.tag-high { background: #e8f5e9; color: #2e7d32; }
.tag-loss { background: #fce4ec; color: #c62828; }
.tag-normal { background: #fff3e0; color: #ef6c00; }
input, select, textarea {
  width: 100%; padding: 10px 14px; border: 1.5px solid #e0e3eb;
  border-radius: 8px; font-size: 14px; outline: none; transition: border .2s;
}
input:focus, select:focus, textarea:focus { border-color: #5b7fff; }
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.grid-4 { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 20px; }
.metric {
  background: #fff; border-radius: 14px; padding: 20px 24px;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
}
.metric .val { font-size: 28px; font-weight: 700; color: #333; }
.metric .lbl { font-size: 13px; color: #999; margin-top: 4px; }
table { width: 100%; border-collapse: collapse; }
th, td { text-align: left; padding: 12px 16px; border-bottom: 1px solid #f0f0f0; font-size: 14px; }
th { color: #999; font-weight: 500; font-size: 13px; }
h2 { font-size: 22px; font-weight: 600; color: #333; margin-bottom: 20px; }
h3 { font-size: 16px; font-weight: 600; color: #333; margin-bottom: 12px; }
.msg { padding: 10px 14px; border-radius: 10px; margin-bottom: 8px; max-width: 80%; font-size: 14px; line-height: 1.6; }
.msg-user { background: #5b7fff; color: #fff; margin-left: auto; }
.msg-ai { background: #f0f2f5; color: #333; }
.chat-box { height: 400px; overflow-y: auto; padding: 16px 0; display: flex; flex-direction: column; }
.chat-input { display: flex; gap: 10px; margin-top: 12px; }
</style>

<style>
@keyframes fade { 0%,70%{opacity:1} 100%{opacity:0;transform:translateY(-10px)} }
</style>
