<script setup>
import { ref, onMounted, computed } from 'vue'
import { auth } from '../stores/auth.js'
import api from '../api/index.js'

const orders = ref([])
const loading = ref(false)
const showCreate = ref(false)
const error = ref('')
const page = ref(1)
const total = ref(0)
const size = ref(30)
const keyword = ref('')
const typeFilter = ref('')
const statusFilter = ref('')
const totalPages = computed(() => Math.ceil(total.value / size.value))
const emptyForm = () => ({
  userId: auth.user.id, customer: '', serviceType: '维修', serviceDesc: '',
  laborHours: 0, laborCost: 0, materialCost: 0, otherCost: 0,
  totalRevenue: 0, location: '', technician: '', orderTime: new Date().toISOString().slice(0,16)
})
const form = ref(emptyForm())

async function fetchOrders() {
  loading.value = true
  try {
    const params = { userId: auth.user.id, page: page.value, size: size.value }
    if (typeFilter.value) params.serviceType = typeFilter.value
    if (statusFilter.value) params.status = statusFilter.value
    const { data } = await api.get('/work-order', { params })
    orders.value = data.data?.records || []
    total.value = data.data?.total || 0
  } catch(e) { console.error(e) }
  loading.value = false
}

function goPage(p) { page.value = p; fetchOrders() }
function onChangeType() { page.value = 1; fetchOrders() }

async function createOrder() {
  error.value = ''
  const f = form.value
  if (!f.customer.trim()) { error.value = '客户名称不能为空'; return }
  if (!f.totalRevenue || f.totalRevenue < 0) { error.value = '工单收入必须为正数'; return }
  if (f.laborCost < 0 || f.materialCost < 0 || f.otherCost < 0 || f.laborHours < 0) {
    error.value = '费用和工时不能为负数'; return
  }
  try {
    await api.post('/work-order', f)
    form.value = emptyForm()
    showCreate.value = false
    error.value = ''
    fetchOrders()
  } catch(e) { error.value = e.response?.data?.message || '创建失败' }
}

const stats = computed(() => ({
  total: total.value,
  revenue: orders.value.reduce((s, o) => s + (o.totalRevenue || 0), 0),
  profit: orders.value.reduce((s, o) => s + (o.profit || 0), 0)
}))

const filteredOrders = computed(() => {
  if (!keyword.value.trim()) return orders.value
  const kw = keyword.value.toLowerCase()
  return orders.value.filter(o =>
    (o.customer||'').includes(kw) || (o.location||'').includes(kw) ||
    (o.technician||'').includes(kw) || (o.serviceType||'').includes(kw)
  )
})

const pages = computed(() => {
  const tp = totalPages.value; if (tp <= 7) return Array.from({length:tp},(_,i)=>i+1)
  const p = page.value; const r = []
  r.push(1); if (p>3) r.push('...')
  for (let i=Math.max(2,p-1); i<=Math.min(tp-1,p+1); i++) r.push(i)
  if (p<tp-2) r.push('...'); r.push(tp); return r
})

const statusLabels = {
  pending:'待分派', assigned:'已分派', accepted:'已接单', arrived:'已到场',
  in_progress:'维修中', parts_needed:'待配件', completed:'已完成',
  confirmed:'已确认', settled:'已结算', closed:'已关单', processing:'处理中'
}
const statusColors = {
  pending:'#ffa726', assigned:'#42a5f5', accepted:'#26c6da', arrived:'#ab47bc',
  in_progress:'#ef5350', parts_needed:'#ff7043', completed:'#66bb6a',
  confirmed:'#26a69a', settled:'#78909c', closed:'#bdbdbd', processing:'#ffa726'
}

// 状态流转：点击推进到下一状态
const nextStatusMap = {
  pending: ['assigned', 'closed'],
  assigned: ['accepted', 'closed'],
  accepted: ['arrived', 'closed'],
  arrived: ['in_progress', 'closed'],
  in_progress: ['parts_needed', 'completed', 'closed'],
  parts_needed: ['in_progress', 'closed'],
  completed: ['confirmed', 'closed'],
  confirmed: ['settled', 'closed'],
  settled: [],
  closed: ['completed']
}
const actionLabel = {
  assigned:'分派技术员', accepted:'接单', arrived:'到场签到',
  in_progress:'开始维修', parts_needed:'领料出库', completed:'维修完成',
  confirmed:'客户确认', settled:'财务结算', closed:'关单'
}
}

const toast = ref('')
async function changeStatus(order, newStatus) {
  toast.value = ''
  if (newStatus === 'parts_needed') {
    const partId = prompt('输入配件编号(如 PART001):', 'PART001')
    const qty = prompt('数量:', '1')
    if (!partId || !qty) return
    try {
      await api.post('/inventory/deduct', null, { params: { partId, qty: parseInt(qty), orderId: order.orderId, userId: auth.user?.username || 'admin' } })
    } catch(e) { toast.value = '❌ ' + (e.response?.data?.message || '领料失败'); return }
  }
  try {
    await api.put(`/work-order/${order.orderId}/status?status=${newStatus}`)
    toast.value = '✅ ' + (statusLabels[newStatus] || newStatus) + ' 操作成功'
    fetchOrders()
  } catch(e) {
    toast.value = '❌ ' + (e.response?.data?.message || e.message || '操作失败')
  }
}

function canTransition(status) {
  return (nextStatusMap[status] || []).length > 0
}
// 关单超7天不可撤销
function isExpired(order) {
  if (!order.orderTime) return false
  const days = (Date.now() - new Date(order.orderTime).getTime()) / 86400000
  return days > 7
}

onMounted(fetchOrders)
</script>

<template>
  <div>
    <div v-if="toast" style="position:fixed;top:20px;right:20px;background:#333;color:#fff;padding:12px 20px;border-radius:8px;z-index:999;font-size:14px;animation:fade 3s forwards" @click="toast=''">{{ toast }}</div>
    <div class="grid-4">
      <div class="metric"><div class="val">{{ stats.total.toLocaleString() }}</div><div class="lbl">工单总数</div></div>
      <div class="metric"><div class="val">¥{{ stats.revenue.toLocaleString() }}</div><div class="lbl">总收入</div></div>
      <div class="metric"><div class="val">¥{{ stats.profit.toLocaleString() }}</div><div class="lbl">{{ stats.total ? ((stats.profit/stats.revenue*100)||0).toFixed(1) : 0 }}%</div></div>
      <div class="metric"><div class="val">{{ page }} / {{ totalPages }}</div><div class="lbl">当前页 / 总页</div></div>
    </div>

    <div class="card">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px">
        <h2 style="margin:0">📋 工单列表</h2>
        <button class="btn btn-primary" @click="showCreate=true">+ 新建</button>
      </div>
      <div style="display:flex; gap:10px; margin-bottom:16px">
        <input v-model="keyword" placeholder="🔍 搜索客户/地点/技术员..." style="flex:1" />
        <select v-model="typeFilter" @change="onChangeType" style="width:110px">
          <option value="">全部类型</option>
          <option v-for="t in ['安装','维修','巡检','保养','定制']" :key="t" :value="t">{{ t }}</option>
        </select>
        <select v-model="statusFilter" @change="page=1;fetchOrders()" style="width:110px">
          <option value="">全部状态</option>
          <option v-for="(label,key) in statusLabels" :key="key" :value="key">{{ label }}</option>
        </select>
      </div>

      <table>
        <thead><tr><th>客户</th><th>类型</th><th>状态</th><th>地点</th><th>收入</th><th>利润</th><th>AI</th><th>日期</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="o in filteredOrders" :key="o.orderId">
            <td><b>{{ o.customer }}</b></td>
            <td>{{ o.serviceType }}</td>
            <td>
              <span :style="{background:statusColors[o.status]||'#999',color:'#fff',padding:'2px 8px',borderRadius:'10px',fontSize:'11px',fontWeight:500}">
                {{ statusLabels[o.status] || o.status }}
              </span>
            </td>
            <td style="font-size:13px;color:#888">{{ o.location||'-' }}</td>
            <td>¥{{ (o.totalRevenue||0).toLocaleString() }}</td>
            <td :style="{color:(o.profit||0)>=0?'#2e7d32':'#c62828'}">¥{{ (o.profit||0).toLocaleString() }}</td>
            <td><span :class="'tag '+(o.aiCategory==='高利润'?'tag-high':o.aiCategory==='亏损'?'tag-loss':'tag-normal')">{{ o.aiCategory||'未分类' }}</span></td>
            <td style="font-size:13px;color:#999">{{ o.orderTime?.slice(0,10) }}</td>
            <td>
              <template v-if="canTransition(o.status)">
                <button v-for="ns in (nextStatusMap[o.status]||[])" :key="ns"
                  v-if="!(o.status==='closed' && ns==='completed' && isExpired(o))"
                  class="btn btn-outline" style="font-size:11px;padding:3px 8px;margin:1px"
                  @click="changeStatus(o, ns)">{{ o.status==='closed' ? '撤销关单' : (actionLabel[ns] || ns) }}</button>
                <span v-if="o.status==='closed' && isExpired(o)" style="color:#ccc;font-size:11px">超7天不可撤</span>
              </template>
              <span v-else style="color:#ccc;font-size:11px">-</span>
            </td>
          </tr>
          <tr v-if="!filteredOrders.length"><td colspan="8" style="text-align:center;padding:40px;color:#ccc">{{ loading?'加载中...':'暂无数据' }}</td></tr>
        </tbody>
      </table>

      <!-- 分页 -->
      <div style="display:flex;justify-content:center;align-items:center;gap:6px;margin-top:16px">
        <button class="btn btn-outline" :disabled="page<=1" @click="goPage(page-1)">‹</button>
        <template v-for="p in pages" :key="p">
          <span v-if="p==='...'" style="padding:0 6px;color:#ccc">…</span>
          <button v-else class="btn" :class="p===page?'btn-primary':'btn-outline'" @click="goPage(p)">{{ p }}</button>
        </template>
        <button class="btn btn-outline" :disabled="page>=totalPages" @click="goPage(page+1)">›</button>
        <span style="margin-left:12px;font-size:13px;color:#999">共 {{ total.toLocaleString() }} 条</span>
      </div>
    </div>

    <!-- 新建弹窗 -->
    <div v-if="showCreate" class="modal-overlay">
      <div class="modal">
        <h3>新建工单</h3>
        <div class="form-grid">
          <label>客户名称 <span class="req">*</span></label><label>服务类型</label>
          <input v-model="form.customer" placeholder="例如: 古镇灯饰厂" />
          <select v-model="form.serviceType"><option>安装</option><option>维修</option><option>巡检</option><option>保养</option><option>定制</option></select>
          <label>服务地点</label><label>技术员</label>
          <input v-model="form.location" placeholder="例如: 中山市古镇" /><input v-model="form.technician" placeholder="例如: 张工" />
          <label>工单收入 (¥) <span class="req">*</span></label><label>人工费 (¥)</label>
          <input v-model.number="form.totalRevenue" type="number" min="0" /><input v-model.number="form.laborCost" type="number" min="0" />
          <label>材料费 (¥)</label><label>其他费用 (¥)</label>
          <input v-model.number="form.materialCost" type="number" min="0" /><input v-model.number="form.otherCost" type="number" min="0" />
          <label>工时 (h)</label><label>工单日期</label>
          <input v-model.number="form.laborHours" type="number" min="0" step="0.5" /><input v-model="form.orderTime" type="datetime-local" />
        </div>
        <p v-if="error" class="err">{{ error }}</p>
        <div style="display:flex;gap:10px;justify-content:flex-end;margin-top:16px">
          <button class="btn btn-outline" @click="showCreate=false;error=''">取消</button>
          <button class="btn btn-primary" @click="createOrder">创建</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay { position:fixed;inset:0;background:rgba(0,0,0,.3);display:flex;align-items:center;justify-content:center;z-index:100; }
.modal { background:#fff;border-radius:16px;padding:30px;width:640px;max-height:85vh;overflow-y:auto; }
.form-grid { display:grid;grid-template-columns:1fr 1fr;gap:6px 16px; }
.form-grid label { font-size:13px;color:#666;font-weight:500;padding-top:8px; }
.form-grid .req { color:#e74c3c; }
.err { color:#e74c3c;font-size:13px;margin-top:8px; }
</style>
