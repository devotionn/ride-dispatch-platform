<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { handleComplaint, listComplaints, listSafetyAlarms } from '../api/safety'
import type { ComplaintStatus, PassengerComplaintView, SafetyAlarmView } from '../domain/types'
import { formatDate } from '../utils/csv'

const loading = ref(false)
const alarmLoading = ref(false)
const complaints = ref<PassengerComplaintView[]>([])
const alarms = ref<SafetyAlarmView[]>([])
const statusFilter = ref<ComplaintStatus | ''>('')
const activeTab = ref('complaints')

const CATEGORY_LABELS: Record<string, string> = {
  SERVICE_ATTITUDE: '服务态度',
  ROUTE_DETOUR: '路线/绕路问题',
  FEE_DISPUTE: '收费争议',
  DRIVING_SAFETY: '驾驶安全',
  VEHICLE_CONDITION: '车辆状况',
  OTHER: '其他',
}

const SOURCE_LABELS: Record<string, string> = {
  RIDE_CREATE: '约车页',
  ORDER_STATUS: '订单状态页',
  PAYMENT: '支付页',
  UNKNOWN: '未知页面',
}

const filteredComplaints = computed(() =>
  statusFilter.value ? complaints.value.filter((item) => item.status === statusFilter.value) : complaints.value,
)

onMounted(() => {
  void loadComplaints()
  void loadAlarms()
})

async function loadComplaints(): Promise<void> {
  loading.value = true
  try {
    complaints.value = await listComplaints()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '投诉列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadAlarms(): Promise<void> {
  alarmLoading.value = true
  try {
    alarms.value = await listSafetyAlarms()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '报警记录加载失败')
  } finally {
    alarmLoading.value = false
  }
}

function isCancelled(error: unknown): boolean {
  return error === 'cancel' || error === 'close'
}

function statusLabel(status: ComplaintStatus): string {
  const labels: Record<ComplaintStatus, string> = {
    OPEN: '待处理',
    PROCESSING: '处理中',
    RESOLVED: '已解决',
    DISMISSED: '已驳回',
  }
  return labels[status]
}

function statusType(status: ComplaintStatus): 'warning' | 'primary' | 'success' | 'info' {
  if (status === 'OPEN') return 'warning'
  if (status === 'PROCESSING') return 'primary'
  if (status === 'RESOLVED') return 'success'
  return 'info'
}

function categoryLabel(category: string): string {
  return CATEGORY_LABELS[category] ?? category
}

async function handle(row: PassengerComplaintView, nextStatus: ComplaintStatus): Promise<void> {
  const title = nextStatus === 'PROCESSING' ? '标记处理中' : nextStatus === 'RESOLVED' ? '标记已解决' : '驳回投诉'
  try {
    const { value } = await ElMessageBox.prompt('请输入处理备注（可留空）', title, { inputPlaceholder: '例如 已联系乘客核实' })
    await handleComplaint(row.complaintNo, { status: nextStatus, note: value.trim() || undefined })
    ElMessage.success('投诉状态已更新')
    await loadComplaints()
  } catch (error) {
    if (!isCancelled(error)) ElMessage.error(error instanceof Error ? error.message : '处理失败')
  }
}

function alarmLocation(row: SafetyAlarmView): string {
  if (row.locationText) return row.locationText
  if (row.latitude && row.longitude) return `${row.longitude}, ${row.latitude}`
  return '—'
}
</script>

<template>
  <section class="workspace-page">
    <div class="page-heading">
      <div>
        <p class="page-kicker">PASSENGER SAFETY CONTROL</p>
        <h1>安全与投诉</h1>
        <p>处理乘客投诉并查看一键报警记录；处理动作会写入审计日志。</p>
      </div>
      <div class="page-actions">
        <el-button :loading="loading" @click="loadComplaints">刷新投诉</el-button>
        <el-button :loading="alarmLoading" @click="loadAlarms">刷新报警</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="乘客投诉" name="complaints">
        <div class="panel-card">
          <div class="table-toolbar">
            <span>共 {{ filteredComplaints.length }} 条</span>
            <el-radio-group v-model="statusFilter" size="small">
              <el-radio-button value="">全部</el-radio-button>
              <el-radio-button value="OPEN">待处理</el-radio-button>
              <el-radio-button value="PROCESSING">处理中</el-radio-button>
              <el-radio-button value="RESOLVED">已解决</el-radio-button>
              <el-radio-button value="DISMISSED">已驳回</el-radio-button>
            </el-radio-group>
          </div>
          <el-table v-loading="loading" :data="filteredComplaints" stripe>
            <el-table-column prop="complaintNo" label="投诉单号" min-width="230" />
            <el-table-column label="订单号" min-width="200">
              <template #default="{ row }">{{ row.orderNo || '—' }}</template>
            </el-table-column>
            <el-table-column label="类型" width="120">
              <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
            </el-table-column>
            <el-table-column prop="description" label="问题描述" min-width="220" show-overflow-tooltip />
            <el-table-column label="联系方式" width="130">
              <template #default="{ row }">{{ row.contactMobile || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }"><el-tag :type="statusType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="处理备注" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.handleNote || '—' }}</template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'OPEN'" size="small" type="primary" @click="handle(row, 'PROCESSING')">处理中</el-button>
                <el-button v-if="row.status !== 'RESOLVED'" size="small" type="success" @click="handle(row, 'RESOLVED')">解决</el-button>
                <el-button v-if="row.status !== 'DISMISSED'" size="small" type="danger" plain @click="handle(row, 'DISMISSED')">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && !filteredComplaints.length" description="暂无投诉记录" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="报警记录" name="alarms">
        <div class="panel-card">
          <el-table v-loading="alarmLoading" :data="alarms" stripe>
            <el-table-column prop="alarmId" label="编号" width="90" />
            <el-table-column label="订单号" min-width="200">
              <template #default="{ row }">{{ row.orderNo || '—' }}</template>
            </el-table-column>
            <el-table-column label="来源页面" width="130">
              <template #default="{ row }">{{ SOURCE_LABELS[row.sourcePage] ?? row.sourcePage }}</template>
            </el-table-column>
            <el-table-column label="位置 / 坐标" min-width="220">
              <template #default="{ row }">{{ alarmLocation(row) }}</template>
            </el-table-column>
            <el-table-column label="乘客手机" width="140">
              <template #default="{ row }">{{ row.passengerMobile || '—' }}</template>
            </el-table-column>
            <el-table-column label="报警时间" width="170">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!alarmLoading && !alarms.length" description="暂无报警记录" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<style scoped>
.panel-card { padding: 14px 16px 18px; }
</style>
