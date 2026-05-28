<script setup>
import { ref, onMounted } from 'vue'
import api from '../api/index.js'
import { auth } from '../stores/auth.js'

const parts = ref([])
const loading = ref(false)
const pickForm = ref({ partId: '', qty: 1, orderId: '' })
const pickMsg = ref('')

async function loadInventory() {
  loading.value = true
  try {
    const { data } = await api.get('/inventory')
    parts.value = data.data || []
  } catch(e) { console.error(e) }
  loading.value = false
}

async function pickPart() {
  pickMsg.value = ''
  try {
    const { data } = await api.post('/inventory/deduct', null, {
      params: {
        partId: pickForm.value.partId,
        qty: pickForm.value.qty,
        orderId: pickForm.value.orderId || ('WO-' + Date.now()),
        userId: auth.user?.username || 'user1'
      }
    })
    pickMsg.value = '✅ 领料成功, 剩余: ' + data.data.remaining
    loadInventory()
  } catch(e) {
    pickMsg.value = '❌ ' + (e.response?.data?.message || '失败')
  }
}

async function refundPart(partId, qty, orderId) {
  try {
    await api.post('/inventory/refund', null, {
      params: { partId, qty, orderId }
    })
    loadInventory()
  } catch(e) {
    alert(e.response?.data?.message || '退库失败')
  }
}

onMounted(loadInventory)
</script>

<template>
  <div>
    <h2>📦 配件库存管理</h2>
    <p style="color:#999;font-size:13px;margin-bottom:16px">Redis Lua原子扣 + 一人一单防重 + 退库幂等</p>

    <div class="grid-4">
      <div class="metric"><div class="val">{{ parts.length }}</div><div class="lbl">配件种类</div></div>
      <div class="metric">
        <div class="val">{{ parts.reduce((s,p) => s + (p.redis_stock||0), 0) }}</div>
        <div class="lbl">Redis库存总量</div>
      </div>
      <div class="metric">
        <div class="val">{{ parts.reduce((s,p) => s + (p.stock||0), 0) }}</div>
        <div class="lbl">DB库存总量</div>
      </div>
      <div class="metric">
        <div class="val" :style="{color: parts.every(p => p.redis_stock === p.stock) ? '#2e7d32' : '#e74c3c'}">
          {{ parts.every(p => p.redis_stock === p.stock) ? '一致' : '不一致' }}
        </div>
        <div class="lbl">Redis↔DB</div>
      </div>
    </div>

    <!-- 领料表单 -->
    <div class="card">
      <h3>🔧 领料出库</h3>
      <div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap">
        <select v-model="pickForm.partId" style="width:200px">
          <option value="">选择配件</option>
          <option v-for="p in parts" :key="p.part_id" :value="p.part_id">
            {{ p.part_name }} ({{ p.redis_stock || p.stock }})
          </option>
        </select>
        <input v-model.number="pickForm.qty" type="number" min="1" placeholder="数量" style="width:80px" />
        <input v-model="pickForm.orderId" placeholder="工单号(可选)" style="width:200px" />
        <button class="btn btn-primary" @click="pickPart" :disabled="!pickForm.partId">领料</button>
      </div>
      <p v-if="pickMsg" style="margin-top:10px;font-size:13px">{{ pickMsg }}</p>
    </div>

    <!-- 库存列表 -->
    <div class="card">
      <h3>📋 库存清单</h3>
      <table>
        <thead><tr><th>配件编号</th><th>名称</th><th>DB库存</th><th>Redis库存</th><th>单价</th><th>版本</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="p in parts" :key="p.part_id">
            <td style="font-family:monospace;font-size:12px;color:#999">{{ p.part_id }}</td>
            <td><b>{{ p.part_name }}</b></td>
            <td>{{ p.stock }}</td>
            <td :style="{color: p.redis_stock !== p.stock ? '#e74c3c' : '#333'}">{{ p.redis_stock }}</td>
            <td>¥{{ p.price }}</td>
            <td style="font-size:12px;color:#999">v{{ p.version }}</td>
            <td>
              <button class="btn btn-outline" style="font-size:11px;padding:4px 10px" @click="refundPart(p.part_id, 1, 'MANUAL')">退1个</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
