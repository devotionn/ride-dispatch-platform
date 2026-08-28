<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { adjustOfflinePayment, listPayments } from '../api/payments'
import type { PaymentView } from '../domain/types'
import { downloadCsv, formatDate, yuan } from '../utils/csv'

const loading = ref(false)
const payments = ref<PaymentView[]>([])

onMounted(() => void load())
async function load(): Promise<void> {
  loading.value = true
  try { payments.value = await listPayments() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '支付列表加载失败') } finally { loading.value = false }
}
function exportPayments(): void {
  downloadCsv('payments.csv', payments.value, [
    { header: '支付单号', value: (row) => row.paymentNo },
    { header: '金额（元）', value: (row) => yuan(row.amount) },
    { header: '支付状态', value: (row) => row.status },
    { header: '结算方式', value: (row) => row.settlementMethod ?? '' },
    { header: '尝试次数', value: (row) => row.attempts.length },
    { header: '最近渠道', value: (row) => row.attempts.at(-1)?.channel ?? '' },
    { header: '最近尝试状态', value: (row) => row.attempts.at(-1)?.status ?? '' },
    { header: '最近流水号', value: (row) => row.attempts.at(-1)?.thirdPartyTransactionNo ?? '' },
    { header: '创建时间', value: (row) => formatDate(row.attempts[0]?.createdAt) },
  ])
}

async function adjustOffline(row: PaymentView): Promise<void> {
  if (row.status !== 'PAID' || row.settlementMethod !== 'OFFLINE') {
    ElMessage.warning('只有已完成线下收款的支付单可以纠偏')
    return
  }
  try {
    const amountPrompt = await ElMessageBox.prompt('请输入纠偏金额（元，可填负数；例如 -5.00 表示扣减 5 元）', '线下收款纠偏', {
      confirmButtonText: '下一步', cancelButtonText: '取消', inputType: 'number',
      inputValidator: (input: string) => Number.isFinite(Number(input)) && Number(input) !== 0 || '请输入非 0 金额',
    })
    const reasonPrompt = await ElMessageBox.prompt('请输入纠偏原因，操作会写入不可变账本和审计日志', '填写原因', {
      confirmButtonText: '确认提交', cancelButtonText: '取消', inputType: 'textarea',
      inputValidator: (input: string) => Boolean(input.trim()) || '必须填写原因',
    })
    const cents = Math.round(Number(amountPrompt.value) * 100)
    await adjustOfflinePayment(row.paymentNo, cents, reasonPrompt.value!.trim())
    ElMessage.success(`支付单 ${row.paymentNo} 已追加纠偏 ${amountPrompt.value} 元`)
    await load()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '纠偏失败')
  }
}
</script>

<template>
  <section class="workspace-page">
    <div class="page-heading"><div><p class="page-kicker">PAYMENT OPERATIONS</p><h1>支付记录</h1><p>查看支付单、尝试渠道和服务端最终状态。</p></div><div class="page-actions"><el-button :disabled="!payments.length" @click="exportPayments">导出 CSV</el-button><el-button :loading="loading" @click="load">刷新</el-button></div></div>
    <div class="panel-card"><el-table v-loading="loading" :data="payments" stripe>
      <el-table-column prop="paymentNo" label="支付单" min-width="230" />
      <el-table-column label="金额" width="120"><template #default="{ row }"><strong>{{ yuan(row.amount) }}</strong></template></el-table-column>
      <el-table-column prop="status" label="状态" width="150" />
      <el-table-column prop="settlementMethod" label="结算方式" width="160" />
      <el-table-column label="尝试" width="110"><template #default="{ row }">{{ row.attempts.length }} 次</template></el-table-column>
      <el-table-column label="最近流水" min-width="220"><template #default="{ row }">{{ row.attempts.at(-1)?.thirdPartyTransactionNo || '—' }}</template></el-table-column>
      <el-table-column label="操作" width="120" fixed="right"><template #default="{ row }"><el-button v-if="row.status === 'PAID' && row.settlementMethod === 'OFFLINE'" link type="warning" @click="adjustOffline(row)">线下纠偏</el-button></template></el-table-column>
    </el-table></div>
  </section>
</template>
