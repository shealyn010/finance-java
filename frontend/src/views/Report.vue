<script setup>
import { ref, onMounted } from 'vue'
import { auth } from '../stores/auth.js'
import api from '../api/index.js'

const byType = ref([])
const byLocation = ref([])

onMounted(async () => {
  try {
    const [t, l] = await Promise.all([
      api.get('/work-order/stats-by-type', { params: { userId: auth.user.id } }),
      api.get('/work-order/profit-by-location', { params: { userId: auth.user.id, start: '2026-01-01', end: '2026-12-31' } })
    ])
    byType.value = t.data.data || []
    byLocation.value = l.data.data || []
  } catch(e) { console.error(e) }
})
</script>

<template>
  <div>
    <h2>📊 数据报表</h2>

    <div class="card">
      <h3>按服务类型统计</h3>
      <table>
        <thead><tr><th>服务类型</th><th>工单数</th><th>总收入</th><th>总利润</th></tr></thead>
        <tbody>
          <tr v-for="r in byType" :key="r.service_type">
            <td>{{ r.service_type }}</td>
            <td>{{ r.orders }}</td>
            <td>¥{{ (Number(r.revenue) || 0).toLocaleString() }}</td>
            <td :style="{color: (Number(r.profit)||0) > 0 ? '#2e7d32' : '#c62828'}">
              ¥{{ (Number(r.profit) || 0).toLocaleString() }}
            </td>
          </tr>
          <tr v-if="!byType.length"><td colspan="4" style="text-align:center;padding:30px;color:#ccc">暂无数据</td></tr>
        </tbody>
      </table>
    </div>

    <div class="card">
      <h3>按地点统计利润</h3>
      <table>
        <thead><tr><th>服务地点</th><th>工单数</th><th>总利润</th><th>平均利润率</th></tr></thead>
        <tbody>
          <tr v-for="r in byLocation" :key="r.location">
            <td>{{ r.location || '-' }}</td>
            <td>{{ r.orders }}</td>
            <td :style="{color: (Number(r.total_profit)||0) > 0 ? '#2e7d32' : '#c62828'}">
              ¥{{ (Number(r.total_profit) || 0).toLocaleString() }}
            </td>
            <td>{{ (Number(r.avg_rate) || 0).toFixed(1) }}%</td>
          </tr>
          <tr v-if="!byLocation.length"><td colspan="4" style="text-align:center;padding:30px;color:#ccc">暂无数据</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
