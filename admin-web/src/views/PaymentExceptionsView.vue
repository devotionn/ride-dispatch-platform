<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { listPaymentExceptions, openPaymentException, rejectPaymentException, resolvePaymentException } from '../api/paymentExceptions'
import type { PaymentExceptionStatus, PaymentExceptionView } from '../domain/types'
import { formatDate, yuan } from '../utils/csv'

const loading = ref(false)
const exceptions = ref<PaymentExceptionView[]>([])

onMounted(() => void load())

async function load(): Promise<void> {
  loading.value = true
  try {
    exceptions.value = await listPaymentExceptions()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '退款异常加载失败')
  } finally {
    loading.value = false
  }
}

function yuanToFen(value: string): number {
  const normalized = value.trim()
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) throw new Error('金额请输入最多两位小数的正数')
  const [yuanPart, fenPart = ''] = normalized.split('.')
  const fen = Number(`${yuanPart}${fenPart.padEnd(2, '0')}`)
  if (!Number.isSafeInteger(fen) || fen <= 0) throw new Error('金额必须大于 0')
  return fen
}

function isCancelled(error: unknown): boolean {
  return error === 'cancel' || error === 'close'
}

function statusLabel(status: PaymentExceptionStatus): string {
  if (status === 'OPEN') return '待处理'
  if (status === 'RESOLVED') return '已解决'
  return '已驳回'
}

function statusType(status: PaymentExceptionStatus): 'warning' | 'success' | 'info' {
  if (status === 'OPEN') return 'warning'
  if (status === 'RESOLVED') return 'success'
  return 'info'
}

async function createException(): Promise<void> {
  try {
    const payment = await ElMessageBox.prompt('请输入支付单号', '登记退款异常', { inputPlaceholder: '例如 PAY202608240001' })
    const amount = await ElMessageBox.prompt('请输入退款金额（元）', '登记退款异常', { inputPlaceholder: '例如 600.00' })
    const reason = await ElMessageBox.prompt('请输入异常原因', '登记退款异常', { inputPlaceholder: '例如 乘客申请部分退款' })
    await openPaymentException({ paymentNo: payment.value.trim(), requestedAmount: yuanToFen(amount.value), reason: reason.value.trim() })
    ElMessage.success('退款异常已登记')
    await load()
  } catch (error) {
    if (!isCancelled(error)) ElMessage.error(error instanceof Error ? error.message : '登记失败')
  }
}

async function resolve(row: PaymentExceptionView): Promise<void> {
  try {
    const externalRef = await ElMessageBox.prompt('请输入外部退款凭证号', '解决退款异常', { inputPlaceholder: '例如 WX-REFUND-20260824' })
    const note = await ElMessageBox.prompt('请输入处理备注', '解决退款异常', { inputPlaceholder: '例如 财务线下退款已核验' })
    await resolvePaymentException(row.exceptionNo, { externalRefundRef: externalRef.value.trim(), note: note.value.trim() })
    ElMessage.success('退款异常已解决')
    await load()
  } catch (error) {
    if (!isCancelled(error)) ElMessage.error(error instanceof Error ? error.message : '处理失败')
  }
}

async function reject(row: PaymentExceptionView): Promise<void> {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回备注', '驳回退款异常', { inputPlaceholder: '例如 材料不完整' })
    await rejectPaymentException(row.exceptionNo, value.trim())
    ElMessage.success('退款异常已驳回')
    await load()
  } catch (error) {
    if (!isCancelled(error)) ElMessage.error(error instanceof Error ? error.message : '处理失败')
  }
}
</script>

<template>
  <section class="workspace-page">
    <div class="page-heading">
      <div>
        <p class="page-kicker">REFUND EXCEPTION CONTROL</p>
        <h1>退款异常</h1>
        <p>登记需要人工核验的退款申请，处理结果会写入审计日志，不会自动改动支付和司机余额。</p>
      </div>
      <div class="page-actions">
        <el-button type="primary" @click="createException">新建异常</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>
    <div class="panel-card">
      <el-table v-loading="loading" :data="exceptions" stripe>
        <el-table-column prop="exceptionNo" label="异常单" min-width="230" />
        <el-table-column label="支付单 / 订单" min-width="230">
          <template #default="{ row }">{{ row.paymentNo || row.paymentId }}<small class="cell-subline">订单 {{ row.orderId }}</small></template>
        </el-table-column>
        <el-table-column label="金额" width="120"><template #default="{ row }"><strong>¥{{ yuan(row.requestedAmount) }}</strong></template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="reason" label="异常原因" min-width="190" show-overflow-tooltip />
        <el-table-column label="处理结果" min-width="220"><template #default="{ row }">{{ row.externalRefundRef || row.resolutionNote || '—' }}</template></el-table-column>
        <el-table-column label="创建时间" width="170"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'OPEN'">
              <el-button size="small" type="success" @click="resolve(row)">解决</el-button>
              <el-button size="small" type="danger" plain @click="reject(row)">驳回</el-button>
            </template>
            <span v-else class="muted-text">已完成</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !exceptions.length" description="暂无退款异常记录" />
    </div>
  </section>
</template>

<style scoped>
.cell-subline { display: block; margin-top: 4px; color: #8b97a8; font-size: 11px; }
.muted-text { color: #8b97a8; font-size: 12px; }
</style>
