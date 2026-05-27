<script setup>
import { ref, nextTick, watch, onMounted } from 'vue'
import { auth } from '../stores/auth.js'
import api from '../api/index.js'

const STORAGE_KEY = 'fininsight_chat_' + auth.user?.id

// 从 localStorage 恢复对话
const saved = localStorage.getItem(STORAGE_KEY)
const messages = ref(saved ? JSON.parse(saved) : [
  { role: 'ai', content: '你好！我是小福 🤖，你的企业工单分析助手。\n\n试试问我：\n• "2025年维修工单总利润多少？"\n• "古镇去年利润率最高的月份？"\n• "哪种服务类型亏损最多？"', verified: null }
])
const selectedVerify = ref(null)

// 保存到 localStorage
watch(messages, (v) => localStorage.setItem(STORAGE_KEY, JSON.stringify(v)), { deep: true })

const input = ref('')
const loading = ref(false)
const chatEl = ref(null)

async function send(msg) {
  const text = msg || input.value
  if (!text.trim() || loading.value) return
  messages.value.push({ role: 'user', content: text })
  if (!msg) input.value = ''
  loading.value = true
  await scrollDown()

  try {
    const { data } = await api.post('/agent/chat', null, {
      params: { userId: auth.user.id, message: text }
    })
    const reply = data.data.reply
    const dataContext = data.data.dataContext || ''
    // 直接用AI回答时基于的原始数据作为校验
    const verified = dataContext

    messages.value.push({ role: 'ai', content: reply, verified })
  } catch(e) {
    messages.value.push({ role: 'ai', content: '抱歉，AI服务暂时不可用。', verified: null })
  }
  loading.value = false
  await scrollDown()
}

async function scrollDown() {
  await nextTick()
  if (chatEl.value) chatEl.value.scrollTop = chatEl.value.scrollHeight
}

function clearHistory() {
  messages.value = [messages.value[0]]
  localStorage.removeItem(STORAGE_KEY)
}

// 快捷问题
const quickQuestions = [
  '2025年哪种服务类型利润最高？',
  '去年亏损最多的月份是哪个月？',
  '古镇的工单利润率怎么样？',
  '材料费占比最高的工单类型是？'
]

onMounted(() => {
  // 更新 storage key (登录态变化)
  const key = 'fininsight_chat_' + auth.user?.id
  if (key !== STORAGE_KEY && localStorage.getItem(key)) {
    messages.value = JSON.parse(localStorage.getItem(key))
  }
})
</script>

<template>
  <div style="display:flex;gap:20px;height:calc(100vh - 80px)">
    <!-- 对话区 -->
    <div style="flex:1;display:flex;flex-direction:column">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
        <h2 style="margin:0">🤖 AI 工单分析助手</h2>
        <button class="btn btn-outline" @click="clearHistory" style="font-size:12px">清空对话</button>
      </div>
      <div class="card" style="flex:1;display:flex;flex-direction:column">
        <div class="chat-box" ref="chatEl" style="flex:1">
          <div v-for="(m, i) in messages" :key="i"
               :class="'msg ' + (m.role === 'user' ? 'msg-user' : 'msg-ai')"
               :style="{whiteSpace:'pre-line', cursor: m.role==='ai'&&m.verified?'pointer':'default'}"
               @click="m.role==='ai'&&m.verified?selectedVerify=selectedVerify===i?null:i:null">{{ m.content }}</div>
          <div v-if="loading" class="msg msg-ai" style="color:#999">分析中...</div>

          <!-- 快捷问题 -->
          <div v-if="messages.length <= 1" style="margin-top:16px">
            <p style="font-size:12px;color:#999;margin-bottom:8px">💡 试试这些：</p>
            <div style="display:flex;flex-wrap:wrap;gap:6px">
              <button v-for="q in quickQuestions" :key="q" class="btn btn-outline" style="font-size:12px;white-space:nowrap" @click="send(q)">{{ q }}</button>
            </div>
          </div>
        </div>
        <div class="chat-input">
          <input v-model="input" @keyup.enter="send()" placeholder="输入问题..." :disabled="loading" />
          <button class="btn btn-primary" @click="send()" :disabled="loading">发送</button>
        </div>
      </div>
    </div>

    <!-- 数据校验区 -->
    <div style="width:300px;flex-shrink:0">
      <h2 style="margin-bottom:12px">📊 实时数据</h2>
      <div class="card" style="font-size:13px;max-height:calc(100vh - 160px);overflow-y:auto">
        <p style="color:#999;font-size:12px;margin-bottom:12px">AI回复时自动对照实际数据，防止幻觉</p>
        <div v-if="selectedVerify === null" style="color:#ccc;text-align:center;padding:40px 0">
          👆 点击AI的回复<br>查看它基于的原始数据
        </div>
        <div v-else>
          <p style="font-weight:600;margin-bottom:8px;font-size:12px;color:#999">📊 AI看到的原始数据</p>
          <pre style="font-size:11px;color:#666;white-space:pre-wrap;line-height:1.6;background:#fafbfc;padding:12px;border-radius:8px;max-height:60vh;overflow-y:auto">{{ messages[selectedVerify]?.verified || '(无数据)' }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-box { overflow-y:auto; padding:16px 0; display:flex; flex-direction:column; }
.chat-input { display:flex; gap:10px; margin-top:12px; }
</style>
