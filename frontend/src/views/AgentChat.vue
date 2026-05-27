<script setup>
import { ref, nextTick } from 'vue'
import axios from 'axios'
const API = '/api'

const messages = ref([
  { role: 'ai', content: '你好！我是小福 🤖，你的企业工单分析助手。\n\n你可以问我：\n• "这个月维修工单利润怎么样？"\n• "古镇那边的工单情况如何？"\n• "哪种服务利润率最高？"' }
])
const input = ref('')
const loading = ref(false)
const chatEl = ref(null)

async function send() {
  if (!input.value.trim() || loading.value) return
  const msg = input.value
  messages.value.push({ role: 'user', content: msg })
  input.value = ''
  loading.value = true
  await nextTick()
  if (chatEl.value) chatEl.value.scrollTop = chatEl.value.scrollHeight

  try {
    const { data } = await axios.post(`${API}/agent/chat`, null, {
      params: { userId: 1, message: msg }
    })
    messages.value.push({ role: 'ai', content: data.data.reply })
  } catch(e) {
    messages.value.push({ role: 'ai', content: '抱歉，AI服务暂时不可用，请确认后端已启动。' })
  }
  loading.value = false
  await nextTick()
  if (chatEl.value) chatEl.value.scrollTop = chatEl.value.scrollHeight
}
</script>

<template>
  <div>
    <h2>🤖 AI 工单分析助手</h2>
    <div class="card">
      <div class="chat-box" ref="chatEl">
        <div v-for="(m, i) in messages" :key="i"
             :class="'msg ' + (m.role === 'user' ? 'msg-user' : 'msg-ai')"
             style="white-space:pre-line">{{ m.content }}</div>
        <div v-if="loading" class="msg msg-ai" style="color:#999">思考中...</div>
      </div>
      <div class="chat-input">
        <input v-model="input" @keyup.enter="send" placeholder="输入问题，如：这个月古镇维修利润怎么样？" />
        <button class="btn btn-primary" @click="send" :disabled="loading">发送</button>
      </div>
    </div>
  </div>
</template>
