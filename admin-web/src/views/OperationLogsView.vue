<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listOperationLogs } from '../api/operationLogs'
import type { OperationLogView } from '../domain/types'
import { downloadCsv, formatDate } from '../utils/csv'

const loading = ref(false)
const logs = ref<OperationLogView[]>([])
const filters = reactive({ operatorType: '', objectType: '', objectId: '', action: '' })

onMounted(() => void load())
async function load(): Promise<void> {
  loading.value = true
  try { logs.value = (await listOperationLogs(filters)).content } catch (error) { ElMessage.error(error instanceof Error ? error.message : '操作日志加载失败') } finally { loading.value = false }
}
function exportLogs(): void {
  downloadCsv('operation-logs.csv', logs.value, [
    { header: '时间', value: (row) => formatDate(row.createdAt) },
    { header: '操作人', value: (row) => `${row.operatorType} #${row.operatorId ?? ''}` },
    { header: '对象', value: (row) => `${row.objectType ?? ''} ${row.objectId ?? ''}` },
    { header: '动作', value: (row) => row.action },
    { header: '原因', value: (row) => row.reason ?? '' },
    { header: '请求号', value: (row) => row.requestId ?? '' },
  ])
}
</script>

<template>
  <section class="workspace-page">
    <header class="page-heading"><div><p class="page-kicker">AUDIT TRAIL</p><h1>操作日志</h1><p>按操作人、业务对象和动作筛选全局审计记录。</p></div><div class="page-actions"><el-button :disabled="!logs.length" @click="exportLogs">导出 CSV</el-button><el-button type="primary" :loading="loading" @click="load">查询</el-button></div></header>
    <section class="panel-card">
      <div class="table-toolbar log-filters">
        <el-input v-model="filters.operatorType" clearable placeholder="操作人类型，如 ADMIN" />
        <el-input v-model="filters.objectType" clearable placeholder="对象类型，如 ORDER" />
        <el-input v-model="filters.objectId" clearable placeholder="对象编号" />
        <el-input v-model="filters.action" clearable placeholder="动作，如 ORDER_DISPATCHED" />
      </div>
      <el-table v-loading="loading" :data="logs" stripe>
        <el-table-column label="时间" width="175"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作人" width="130"><template #default="{ row }">{{ row.operatorType }} #{{ row.operatorId ?? '—' }}</template></el-table-column>
        <el-table-column label="对象" width="180"><template #default="{ row }">{{ row.objectType }} / {{ row.objectId }}</template></el-table-column>
        <el-table-column prop="action" label="动作" min-width="240" />
        <el-table-column prop="reason" label="原因" min-width="220" />
        <el-table-column prop="requestId" label="请求号" min-width="180" />
      </el-table>
    </section>
  </section>
</template>
