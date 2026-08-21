<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  createAdminOrder,
  dispatchOrder,
  getOrderDetail,
  listNearbyDrivers,
  listOrders,
  reassignOrder,
} from '../api/orders'
import type {
  AdminCreateOrderPayload,
  NearbyDriver,
  OrderDetail,
  OrderSourceType,
  OrderStatus,
  OrderSummary,
  TripStage,
} from '../domain/types'

const loading = ref(false)
const orders = ref<OrderSummary[]>([])
const totalElements = ref(0)
const totalPages = ref(0)
const page = ref(0)
const pageSize = 50
const statusFilter = ref<OrderStatus | ''>('')

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<OrderDetail | null>(null)
const nearbyLoading = ref(false)
const nearbyDrivers = ref<NearbyDriver[]>([])
const actingDriverId = ref<number | null>(null)

const createOpen = ref(false)
const createLoading = ref(false)
const createForm = reactive({
  pickupAddress: '',
  pickupLatitude: null as number | null,
  pickupLongitude: null as number | null,
  destinationAddress: '',
  destinationLatitude: null as number | null,
  destinationLongitude: null as number | null,
  passengerCount: 1,
  departureAt: null as Date | null,
  mobile: '',
  remark: '',
})

const statusOptions: Array<{ value: OrderStatus; label: string }> = [
  { value: 'PENDING_DISPATCH', label: '待接单' },
  { value: 'PENDING_DRIVER_CONFIRM', label: '待司机确认' },
  { value: 'ACCEPTED', label: '已接单' },
  { value: 'IN_SERVICE', label: '执行中' },
  { value: 'PENDING_PAYMENT', label: '待付款' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'EXCEPTION', label: '异常' },
]

const canDispatch = computed(() => detail.value?.order.status === 'PENDING_DISPATCH')
const canReassign = computed(() => detail.value?.order.status === 'PENDING_DRIVER_CONFIRM')
const currentWaitingDriverId = computed(() => {
  const attempts = detail.value?.dispatchAttempts ?? []
  return [...attempts].reverse().find((item) => item.status === 'WAITING')?.targetDriverId ?? null
})

onMounted(() => void loadOrders())

async function loadOrders(): Promise<void> {
  loading.value = true
  try {
    const result = await listOrders(statusFilter.value || undefined, page.value, pageSize)
    orders.value = result.content
    totalElements.value = result.totalElements
    totalPages.value = result.totalPages
  } catch (error) {
    ElMessage.error(messageOf(error))
  } finally {
    loading.value = false
  }
}

async function applyStatusFilter(): Promise<void> {
  page.value = 0
  await loadOrders()
}

async function changePage(nextPage: number): Promise<void> {
  page.value = nextPage - 1
  await loadOrders()
}

async function openDetail(orderNo: string): Promise<void> {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  nearbyDrivers.value = []
  try {
    detail.value = await getOrderDetail(orderNo)
    if (detail.value.order.status === 'PENDING_DISPATCH' || detail.value.order.status === 'PENDING_DRIVER_CONFIRM') {
      await loadNearby(orderNo)
    }
  } catch (error) {
    ElMessage.error(messageOf(error))
  } finally {
    detailLoading.value = false
  }
}

async function refreshDetail(): Promise<void> {
  const orderNo = detail.value?.order.orderNo
  if (!orderNo) return
  await openDetail(orderNo)
}

async function loadNearby(orderNo: string): Promise<void> {
  nearbyLoading.value = true
  try {
    nearbyDrivers.value = await listNearbyDrivers(orderNo)
  } catch (error) {
    nearbyDrivers.value = []
    ElMessage.error(messageOf(error))
  } finally {
    nearbyLoading.value = false
  }
}

async function assign(driver: NearbyDriver): Promise<void> {
  const orderNo = detail.value?.order.orderNo
  if (!orderNo || actingDriverId.value) return
  if (canReassign.value && currentWaitingDriverId.value === driver.driverId) {
    ElMessage.warning('该司机已经是当前待确认司机')
    return
  }

  actingDriverId.value = driver.driverId
  try {
    if (canDispatch.value) {
      await ElMessageBox.confirm(
        `确认将订单 ${orderNo} 派给 ${driver.driverName}（${driver.driverNo}）？`,
        '人工派单确认',
        { type: 'warning', confirmButtonText: '确认派单', cancelButtonText: '取消' },
      )
      await dispatchOrder(orderNo, driver.driverId)
      ElMessage.success('派单成功，等待司机确认')
    } else if (canReassign.value) {
      const { value } = await ElMessageBox.prompt(
        `将订单改派给 ${driver.driverName}（${driver.driverNo}）。可填写改派原因。`,
        '改派确认',
        {
          confirmButtonText: '确认改派',
          cancelButtonText: '取消',
          inputPlaceholder: '例如：原司机临时无法接单',
          inputValue: '',
          inputType: 'textarea',
        },
      )
      await reassignOrder(orderNo, driver.driverId, value?.trim())
      ElMessage.success('改派成功，等待新司机确认')
    }
    await Promise.all([refreshDetail(), loadOrders()])
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(messageOf(error))
  } finally {
    actingDriverId.value = null
  }
}

async function submitAdminOrder(): Promise<void> {
  if (!createForm.pickupAddress.trim() || !createForm.destinationAddress.trim()) {
    ElMessage.warning('请填写上车点和目的地')
    return
  }
  if (!coordinateValid(createForm.pickupLatitude, -90, 90) || !coordinateValid(createForm.destinationLatitude, -90, 90)) {
    ElMessage.warning('请填写正确的纬度')
    return
  }
  if (!coordinateValid(createForm.pickupLongitude, -180, 180) || !coordinateValid(createForm.destinationLongitude, -180, 180)) {
    ElMessage.warning('请填写正确的经度')
    return
  }
  if (!/^1\d{10}$/.test(createForm.mobile)) {
    ElMessage.warning('请输入正确的乘客手机号')
    return
  }
  if (!createForm.departureAt) {
    ElMessage.warning('请选择出发时间')
    return
  }

  const payload: AdminCreateOrderPayload = {
    pickup: {
      address: createForm.pickupAddress.trim(),
      latitude: createForm.pickupLatitude!,
      longitude: createForm.pickupLongitude!,
    },
    destination: {
      address: createForm.destinationAddress.trim(),
      latitude: createForm.destinationLatitude!,
      longitude: createForm.destinationLongitude!,
    },
    passengerCount: createForm.passengerCount,
    departureAt: createForm.departureAt.toISOString(),
    mobile: createForm.mobile,
    remark: createForm.remark.trim() || undefined,
  }

  createLoading.value = true
  try {
    const created = await createAdminOrder(payload)
    ElMessage.success(`订单 ${created.orderNo} 已创建`)
    createOpen.value = false
    resetCreateForm()
    statusFilter.value = 'PENDING_DISPATCH'
    page.value = 0
    await loadOrders()
    await openDetail(created.orderNo)
  } catch (error) {
    ElMessage.error(messageOf(error))
  } finally {
    createLoading.value = false
  }
}

function resetCreateForm(): void {
  createForm.pickupAddress = ''
  createForm.pickupLatitude = null
  createForm.pickupLongitude = null
  createForm.destinationAddress = ''
  createForm.destinationLatitude = null
  createForm.destinationLongitude = null
  createForm.passengerCount = 1
  createForm.departureAt = null
  createForm.mobile = ''
  createForm.remark = ''
}

function coordinateValid(value: number | null, min: number, max: number): boolean {
  return value !== null && Number.isFinite(value) && value >= min && value <= max
}

function statusLabel(status: OrderStatus): string {
  return statusOptions.find((item) => item.value === status)?.label ?? status
}

function statusType(status: OrderStatus): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'COMPLETED') return 'success'
  if (status === 'CANCELLED' || status === 'EXCEPTION') return 'danger'
  if (status === 'PENDING_DISPATCH' || status === 'PENDING_DRIVER_CONFIRM' || status === 'PENDING_PAYMENT') return 'warning'
  if (status === 'ACCEPTED' || status === 'IN_SERVICE') return 'primary'
  return 'info'
}

function sourceLabel(source: OrderSourceType): string {
  if (source === 'PUBLIC_H5') return '公共 H5'
  if (source === 'DRIVER_QR') return '司机二维码'
  return '后台代客'
}

function stageLabel(stage?: TripStage | null): string {
  if (stage === 'ARRIVED_PICKUP') return '已到上车点'
  if (stage === 'PASSENGER_ONBOARD') return '已接到乘客'
  if (stage === 'IN_TRANSIT') return '行程中'
  if (stage === 'ARRIVED_DESTINATION') return '已到目的地'
  return '—'
}

function formatTime(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(date)
}

function fullTime(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(date)
}

function money(cents?: number | null): string {
  return cents == null ? '—' : `¥ ${(cents / 100).toFixed(2)}`
}

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}
</script>

<template>
  <section class="workspace-page">
    <header class="page-heading">
      <div>
        <p class="page-kicker">DISPATCH CENTER</p>
        <h1>订单调度</h1>
        <p>先处理待接单和待司机确认订单，系统只做筛选和排序，最终由调度员决定。</p>
      </div>
      <div class="page-actions">
        <el-button @click="loadOrders">刷新</el-button>
        <el-button type="primary" @click="createOpen = true">后台代客建单</el-button>
      </div>
    </header>

    <div class="metric-strip">
      <div><strong>{{ totalElements }}</strong><span>当前筛选订单</span></div>
      <div><strong>{{ orders.filter((item) => item.status === 'PENDING_DISPATCH').length }}</strong><span>本页待接单</span></div>
      <div><strong>{{ orders.filter((item) => item.status === 'PENDING_DRIVER_CONFIRM').length }}</strong><span>本页待确认</span></div>
    </div>

    <section class="panel-card">
      <div class="table-toolbar">
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 180px" @change="applyStatusFilter">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <span>共 {{ totalElements }} 单 · 第 {{ page + 1 }} / {{ Math.max(totalPages, 1) }} 页</span>
      </div>

      <el-table v-loading="loading" :data="orders" row-key="orderNo" class="orders-table" @row-dblclick="(row: OrderSummary) => openDetail(row.orderNo)">
        <el-table-column label="创建 / 出发" width="155">
          <template #default="scope">
            <div class="stack-cell"><strong>{{ formatTime(scope.row.createdAt) }}</strong><span>出发 {{ formatTime(scope.row.departureAt) }}</span></div>
          </template>
        </el-table-column>
        <el-table-column prop="orderNo" label="订单号" min-width="205" />
        <el-table-column label="来源" width="110"><template #default="scope">{{ sourceLabel(scope.row.sourceType) }}</template></el-table-column>
        <el-table-column label="行程" min-width="300">
          <template #default="scope">
            <div class="route-cell"><span class="point-a">A</span><strong>{{ scope.row.pickupAddress }}</strong><i>→</i><span class="point-b">B</span><strong>{{ scope.row.destinationAddress }}</strong></div>
          </template>
        </el-table-column>
        <el-table-column label="人数" width="70"><template #default="scope">{{ scope.row.passengerCount }} 人</template></el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="scope"><el-tag :type="statusType(scope.row.status)" effect="light">{{ statusLabel(scope.row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="当前司机" width="105"><template #default="scope">{{ scope.row.currentDriverId || '—' }}</template></el-table-column>
        <el-table-column label="操作" width="90" fixed="right"><template #default="scope"><el-button link type="primary" @click="openDetail(scope.row.orderNo)">查看 / 调度</el-button></template></el-table-column>
      </el-table>

      <div class="pagination-row" v-if="totalPages > 1">
        <el-pagination background layout="prev, pager, next" :page-count="totalPages" :current-page="page + 1" @current-change="changePage" />
      </div>
    </section>

    <el-drawer v-model="detailOpen" title="订单详情与调度" size="min(860px, 92vw)" destroy-on-close>
      <div v-loading="detailLoading" class="order-drawer" v-if="detail">
        <div class="drawer-status-line">
          <div><span>订单号</span><strong>{{ detail.order.orderNo }}</strong></div>
          <el-tag :type="statusType(detail.order.status)" size="large">{{ statusLabel(detail.order.status) }}</el-tag>
          <el-button plain @click="refreshDetail">刷新</el-button>
        </div>

        <div class="detail-grid">
          <div><span>订单来源</span><strong>{{ sourceLabel(detail.order.sourceType) }}</strong></div>
          <div><span>乘客手机</span><strong>{{ detail.order.passengerMobile }}</strong></div>
          <div><span>乘车人数</span><strong>{{ detail.order.passengerCount }} 人</strong></div>
          <div><span>出发时间</span><strong>{{ fullTime(detail.order.departureAt) }}</strong></div>
          <div><span>当前司机</span><strong>{{ detail.order.currentDriverId || '未确定' }}</strong></div>
          <div><span>履约阶段</span><strong>{{ stageLabel(detail.order.tripStage) }}</strong></div>
          <div><span>最终金额</span><strong>{{ money(detail.order.finalAmount) }}</strong></div>
          <div><span>创建时间</span><strong>{{ fullTime(detail.order.createdAt) }}</strong></div>
        </div>

        <div class="drawer-route-card">
          <div><span class="point-a">A</span><section><small>上车点</small><strong>{{ detail.order.pickupAddress }}</strong><em>{{ detail.order.pickupLongitude }}, {{ detail.order.pickupLatitude }}</em></section></div>
          <div class="route-rail" />
          <div><span class="point-b">B</span><section><small>目的地</small><strong>{{ detail.order.destinationAddress }}</strong><em>{{ detail.order.destinationLongitude }}, {{ detail.order.destinationLatitude }}</em></section></div>
        </div>

        <section v-if="canDispatch || canReassign" class="drawer-section dispatch-section">
          <div class="drawer-section-heading">
            <div><span>人工调度</span><h3>{{ canDispatch ? '选择附近司机派单' : '等待司机确认，可直接改派' }}</h3></div>
            <el-button size="small" :loading="nearbyLoading" @click="loadNearby(detail.order.orderNo)">刷新附近司机</el-button>
          </div>
          <el-alert v-if="canReassign && currentWaitingDriverId" type="warning" :closable="false" show-icon :title="`当前待确认司机 ID：${currentWaitingDriverId}。改派后原派单立即失效。`" />
          <el-table v-loading="nearbyLoading" :data="nearbyDrivers" empty-text="10km 内暂无符合条件司机" class="nearby-table">
            <el-table-column label="司机" min-width="150"><template #default="scope"><div class="stack-cell"><strong>{{ scope.row.driverName }}</strong><span>{{ scope.row.driverNo }}</span></div></template></el-table-column>
            <el-table-column label="可接人数" width="100"><template #default="scope">{{ scope.row.availablePassengers }} 人</template></el-table-column>
            <el-table-column label="直线距离" width="110"><template #default="scope">{{ scope.row.straightLineDistanceKm.toFixed(2) }} km</template></el-table-column>
            <el-table-column label="定位时间" width="145"><template #default="scope">{{ formatTime(scope.row.locatedAt) }}</template></el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="scope">
                <el-button
                  type="primary"
                  size="small"
                  :plain="canReassign"
                  :loading="actingDriverId === scope.row.driverId"
                  :disabled="canReassign && currentWaitingDriverId === scope.row.driverId"
                  @click="assign(scope.row)"
                >{{ canDispatch ? '派给他' : '改派给他' }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section class="drawer-section">
          <div class="drawer-section-heading"><div><span>派单历史</span><h3>每一次派单尝试</h3></div></div>
          <el-table :data="detail.dispatchAttempts" empty-text="暂无派单记录">
            <el-table-column prop="dispatchType" label="类型" width="130" />
            <el-table-column prop="targetDriverId" label="司机 ID" width="100" />
            <el-table-column prop="status" label="结果" width="175" />
            <el-table-column label="派单时间" width="155"><template #default="scope">{{ fullTime(scope.row.dispatchedAt) }}</template></el-table-column>
            <el-table-column label="拒绝原因" min-width="180"><template #default="scope">{{ scope.row.rejectReasonText || scope.row.rejectReasonCode || '—' }}</template></el-table-column>
          </el-table>
        </section>

        <section class="drawer-section">
          <div class="drawer-section-heading"><div><span>履约进度</span><h3>真实业务事件时间线</h3></div></div>
          <el-timeline v-if="detail.progressEvents.length">
            <el-timeline-item v-for="event in detail.progressEvents" :key="event.id" :timestamp="fullTime(event.occurredAt)" placement="top">
              <strong>{{ stageLabel(event.stage) }}</strong> · 司机 {{ event.driverId }}
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="司机尚未开始履约" :image-size="70" />
        </section>
      </div>
    </el-drawer>

    <el-dialog v-model="createOpen" title="后台代客创建订单" width="min(720px, 92vw)" destroy-on-close @closed="resetCreateForm">
      <el-alert type="info" :closable="false" title="首版后台建单使用地址 + 经纬度。调度员地图选点将在同一地图 Provider 上继续接入。" />
      <el-form label-position="top" class="create-order-form">
        <div class="form-grid two">
          <el-form-item label="上车点地址"><el-input v-model="createForm.pickupAddress" placeholder="请输入上车点" /></el-form-item>
          <el-form-item label="目的地地址"><el-input v-model="createForm.destinationAddress" placeholder="请输入目的地" /></el-form-item>
        </div>
        <div class="form-grid four">
          <el-form-item label="上车经度"><el-input-number v-model="createForm.pickupLongitude" :controls="false" :precision="7" /></el-form-item>
          <el-form-item label="上车纬度"><el-input-number v-model="createForm.pickupLatitude" :controls="false" :precision="7" /></el-form-item>
          <el-form-item label="目的经度"><el-input-number v-model="createForm.destinationLongitude" :controls="false" :precision="7" /></el-form-item>
          <el-form-item label="目的纬度"><el-input-number v-model="createForm.destinationLatitude" :controls="false" :precision="7" /></el-form-item>
        </div>
        <div class="form-grid three">
          <el-form-item label="乘车人数"><el-input-number v-model="createForm.passengerCount" :min="1" :max="20" /></el-form-item>
          <el-form-item label="出发时间"><el-date-picker v-model="createForm.departureAt" type="datetime" placeholder="选择日期时间" style="width:100%" /></el-form-item>
          <el-form-item label="乘客手机号"><el-input v-model="createForm.mobile" maxlength="11" placeholder="11 位手机号" /></el-form-item>
        </div>
        <el-form-item label="备注"><el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createOpen = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitAdminOrder">创建并进入待接单</el-button>
      </template>
    </el-dialog>
  </section>
</template>
