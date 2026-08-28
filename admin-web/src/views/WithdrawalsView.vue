<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { approveWithdrawal, listWithdrawals, markWithdrawalPaid, rejectWithdrawal } from '../api/settlements'
import type { WithdrawalView } from '../domain/types'
import { downloadCsv, formatDate, maskAccount, yuan } from '../utils/csv'

const loading = ref(false)
const withdrawals = ref<WithdrawalView[]>([])

onMounted(() => void load())
async function load(): Promise<void> {
  loading.value = true
  try { withdrawals.value = await listWithdrawals() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '提现列表加载失败') } finally { loading.value = false }
}
function exportWithdrawals(): void {
  downloadCsv('withdrawals.csv', withdrawals.value, [
    { header: '申请单号', value: (row) => row.withdrawalNo },
    { header: '金额（元）', value: (row) => yuan(row.amount) },
    { header: '提现方式', value: (row) => row.channel },
    { header: '收款账号（脱敏）', value: (row) => maskAccount(row.account) },
    { header: '状态', value: (row) => row.status },
    { header: '驳回原因', value: (row) => row.reason ?? '' },
    { header: '申请时间', value: (row) => formatDate(row.createdAt) },
    { header: '审核时间', value: (row) => formatDate(row.reviewedAt) },
    { header: '打款时间', value: (row) => formatDate(row.paidAt) },
  ])
}
async function approve(row: WithdrawalView): Promise<void> { try { await approveWithdrawal(row.withdrawalNo); ElMessage.success('已审核通过'); await load() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '审核失败') } }
async function reject(row: WithdrawalView): Promise<void> {
  try { const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回提现', { inputValidator: (v: string) => Boolean(v.trim()) || '必须填写原因' }); await rejectWithdrawal(row.withdrawalNo, value.trim()); ElMessage.success('已驳回'); await load() } catch (error) { if (error === 'cancel' || error === 'close') return; ElMessage.error(error instanceof Error ? error.message : '操作失败') }
}
async function markPaid(row: WithdrawalView): Promise<void> { try { await markWithdrawalPaid(row.withdrawalNo); ElMessage.success('已标记人工打款'); await load() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '标记失败') } }
</script>

<template>
  <section class="workspace-page">
    <div class="page-heading"><div><p class="page-kicker">SETTLEMENT OPERATIONS</p><h1>提现审核</h1><p>审核冻结余额，驳回会自动解冻，人工打款后核销冻结。</p></div><div class="page-actions"><el-button :disabled="!withdrawals.length" @click="exportWithdrawals">导出 CSV</el-button><el-button :loading="loading" @click="load">刷新</el-button></div></div>
    <div class="panel-card"><el-table v-loading="loading" :data="withdrawals" stripe>
      <el-table-column prop="withdrawalNo" label="申请单" min-width="230" />
      <el-table-column label="金额" width="120"><template #default="{ row }"><strong>{{ yuan(row.amount) }}</strong></template></el-table-column>
      <el-table-column prop="channel" label="方式" width="110" /><el-table-column prop="account" label="账号" min-width="210" />
      <el-table-column prop="status" label="状态" width="190" />
      <el-table-column label="操作" width="250"><template #default="{ row }"><el-button v-if="row.status === 'PENDING_REVIEW'" size="small" type="primary" @click="approve(row)">通过</el-button><el-button v-if="row.status === 'PENDING_REVIEW'" size="small" type="danger" plain @click="reject(row)">驳回</el-button><el-button v-if="row.status === 'APPROVED_PENDING_PAYMENT'" size="small" type="success" @click="markPaid(row)">标记已打款</el-button></template></el-table-column>
    </el-table></div>
  </section>
</template>
