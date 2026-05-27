<script setup>
import { ref, onMounted, computed } from 'vue'
import axios from 'axios'
const API = '/api'

const orders = ref([])
const loading = ref(false)
const filter = ref('')
const showCreate = ref(false)
const form = ref({
  userId: 1, customer: '', serviceType: '维修', serviceDesc: '',
  laborHours: 0, laborCost: 0, materialCost: 0, otherCost: 0,
  totalRevenue: 0, location: '', technician: '', orderTime: new Date().toISOString().slice(0,16)
})

async function fetchOrders() {
  loading.value = true
  try {
    const { data } = await axios.get(`${API}/work-order`, {
      params: { userId: 1, page: 1, size: 50, serviceType: filter.value || undefined }
    })
    orders.value = data.data?.records || []
  } catch(e) { console.error(e) }
  loading.value = false
}

async function createOrder() {
  await axios.post(`${API}/work-order`, form.value)
  showCreate.value = false
  fetchOrders()
}

const stats = computed(() => {
  const total = orders.value.length
  const revenue = orders.value.reduce((s, o) => s + (o.totalRevenue || 0), 0)
  const profit = orders.value.reduce((s, o) => s + (o.profit || 0), 0)
  return { total, revenue, profit }
})

onMounted(fetchOrders)
</script>

<template>
  <div>
    <div class="grid-4">
      <div class="metric"><div class="val">{{ stats.total }}</div><div class="lbl">工单总数</div></div>
      <div class="metric"><div class="val">¥{{ stats.revenue.toLocaleString() }}</div><div class="lbl">总收入</div></div>
      <div class="metric"><div class="val">¥{{ stats.profit.toLocaleString() }}</div><div class="lbl">总利润</div></div>
      <div class="metric"><div class="val">{{ stats.total ? (stats.profit / stats.revenue * 100).toFixed(1) : 0 }}%</div><div class="lbl">平均利润率</div></div>
    </div>

    <div class="card">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px">
        <h2 style="margin-bottom:0">📋 工单列表</h2>
        <div style="display:flex; gap:10px">
          <select v-model="filter" @change="fetchOrders" style="width:auto; padding:8px 12px">
            <option value="">全部类型</option>
            <option value="安装">安装</option>
            <option value="维修">维修</option>
            <option value="巡检">巡检</option>
            <option value="保养">保养</option>
            <option value="定制">定制</option>
          </select>
          <button class="btn btn-primary" @click="showCreate = true">+ 新建工单</button>
        </div>
      </div>

      <table>
        <thead>
          <tr><th>工单编号</th><th>客户</th><th>类型</th><th>地点</th><th>技术员</th><th>收入</th><th>利润</th><th>利润率</th><th>AI标签</th><th>日期</th></tr>
        </thead>
        <tbody>
          <tr v-for="o in orders" :key="o.orderId">
            <td style="font-family:monospace; font-size:12px; color:#999">{{ o.orderId?.slice(0,12) }}</td>
            <td>{{ o.customer }}</td>
            <td>{{ o.serviceType }}</td>
            <td>{{ o.location || '-' }}</td>
            <td>{{ o.technician || '-' }}</td>
            <td>¥{{ (o.totalRevenue || 0).toLocaleString() }}</td>
            <td :style="{color: (o.profit||0) > 0 ? '#2e7d32' : '#c62828'}">¥{{ (o.profit || 0).toLocaleString() }}</td>
            <td>{{ (o.profitRate || 0).toFixed(1) }}%</td>
            <td>
              <span :class="'tag ' + (o.aiCategory === '高利润' ? 'tag-high' : o.aiCategory === '亏损' ? 'tag-loss' : 'tag-normal')">
                {{ o.aiCategory || '未分类' }}
              </span>
            </td>
            <td style="font-size:13px; color:#999">{{ o.orderTime?.slice(0,10) }}</td>
          </tr>
          <tr v-if="!orders.length"><td colspan="10" style="text-align:center; padding:40px; color:#ccc">暂无工单，点击右上角"新建工单"</td></tr>
        </tbody>
      </table>
    </div>

    <!-- Create Modal -->
    <div v-if="showCreate" class="modal-overlay" @click.self="showCreate=false">
      <div class="modal">
        <h3>新建工单</h3>
        <div class="grid-2">
          <input v-model="form.customer" placeholder="客户名称 *" />
          <select v-model="form.serviceType">
            <option>安装</option><option>维修</option><option>巡检</option><option>保养</option><option>定制</option>
          </select>
          <input v-model="form.location" placeholder="服务地点 (如: 中山市古镇)" />
          <input v-model="form.technician" placeholder="技术员" />
          <input v-model.number="form.totalRevenue" type="number" placeholder="工单收入 *" />
          <input v-model.number="form.laborCost" type="number" placeholder="人工费" />
          <input v-model.number="form.materialCost" type="number" placeholder="材料费" />
          <input v-model.number="form.otherCost" type="number" placeholder="其他费用" />
          <input v-model.number="form.laborHours" type="number" placeholder="工时(h)" step="0.5" />
          <input v-model="form.orderTime" type="datetime-local" />
          <textarea v-model="form.serviceDesc" placeholder="服务描述" style="grid-column:1/3; height:60px" />
        </div>
        <div style="display:flex; gap:10px; justify-content:flex-end; margin-top:16px">
          <button class="btn btn-outline" @click="showCreate=false">取消</button>
          <button class="btn btn-primary" @click="createOrder">创建</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.3); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { background: #fff; border-radius: 16px; padding: 30px; width: 600px; max-height: 80vh; overflow-y: auto; }
</style>
