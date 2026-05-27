<script setup>
import { computed } from 'vue'

const props = defineProps({ data: String })

const tableData = computed(() => {
  if (!props.data) return null
  try {
    // 尝试从文本中提取JSON数组
    const text = props.data
    const jsonMatch = text.match(/\[[\s\S]*\]/)
    if (!jsonMatch) return { raw: text }
    const arr = JSON.parse(jsonMatch[0])
    if (!Array.isArray(arr) || !arr.length) return { raw: text }
    const keys = Object.keys(arr[0])
    return { columns: keys, rows: arr }
  } catch {
    return { raw: props.data }
  }
})

function formatVal(v) {
  if (v === null || v === undefined) return '-'
  if (typeof v === 'number') {
    if (Number.isInteger(v)) return v.toLocaleString()
    return v.toFixed(2)
  }
  return String(v)
}
</script>

<template>
  <div v-if="tableData">
    <div v-if="tableData.raw" style="font-size:11px;color:#666;white-space:pre-wrap;line-height:1.6;background:#fafbfc;padding:12px;border-radius:8px;max-height:60vh;overflow-y:auto">
      {{ tableData.raw }}
    </div>
    <div v-else style="max-height:60vh;overflow:auto">
      <table style="width:100%;font-size:11px;border-collapse:collapse">
        <thead>
          <tr>
            <th v-for="col in tableData.columns" :key="col"
                style="text-align:left;padding:6px 8px;color:#999;font-weight:500;border-bottom:2px solid #eee;position:sticky;top:0;background:#fff">
              {{ col }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, i) in tableData.rows" :key="i">
            <td v-for="col in tableData.columns" :key="col"
                style="padding:5px 8px;border-bottom:1px solid #f5f5f5"
                :style="{color: typeof row[col]==='number'&&row[col]<0?'#c62828':'#333'}">
              {{ formatVal(row[col]) }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
  <div v-else style="color:#ccc;text-align:center;padding:20px">无数据</div>
</template>
