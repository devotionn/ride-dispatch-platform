<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'

import { getPublicBrand } from '../api/brand'
import { cancelPassengerOrder, getPassengerOrder } from '../api/orders'
import type { OrderStatus, PassengerOrder, PlatformBrand, TripStage } from '../domain/types'
import { loadOrderToken, saveOrderToken } from '../storage/orderToken'
import SafetyCenter from '../components/SafetyCenter.vue'
import ComplaintSheet from '../components/ComplaintSheet.vue'

const route = useRoute()
const router = useRouter()
const orderNo = computed(() => String(route.params.orderNo ?? ''))
const brand = ref<PlatformBrand>({ companyName: '预约用车' })
const order = ref<PassengerOrder | null>(null)
const token = ref('')
const tokenInput = ref('')
const loading = ref(false)
const cancelling = ref(false)
const errorMessage = ref('')
const complaintOpen = ref(false)
let refreshTimer: number | undefined

const statusMeta: Record<OrderStatus, { title: string; description: string; tone: string }> = {
  PENDING_DISPATCH: { title: '等待调度', description: '订单已进入调度池，工作人员正在安排司机。', tone: 'warning' },
  PENDING_DRIVER_CONFIRM: { title: '等待司机确认', description: '订单已发送给司机，正在等待司机响应。', tone: 'warning' },
  ACCEPTED: { title: '司机已接单', description: '司机已确认订单，请按约定时间准备出发。', tone: 'success' },
  IN_SERVICE: { title: '行程进行中', description: '司机正在按履约流程执行本次行程。', tone: 'primary' },
  PENDING_PAYMENT: { title: '等待付款', description: '行程已到达目的地，请核对金额并完成付款。', tone: 'primary' },
  COMPLETED: { title: '订单已完成', description: '本次行程已经完成，感谢使用。', tone: 'success' },
  CANCELLED: { title: '订单已取消', description: '本次预约已取消。', tone: 'neutral' },
  EXCEPTION: { title: '订单处理中', description: '订单存在异常，工作人员正在人工处理。', tone: 'danger' },
}

const tripStageText: Record<TripStage, string> = {
  ARRIVED_PICKUP: '司机已到上车点',
  PASSENGER_ONBOARD: '乘客已上车',
  IN_TRANSIT: '前往目的地',
  ARRIVED_DESTINATION: '已到达目的地',
}

const currentMeta = computed(() => (order.value ? statusMeta[order.value.status] : null))
const canCancel = computed(() => order.value && ['PENDING_DISPATCH', 'PENDING_DRIVER_CONFIRM'].includes(order.value.status))
const canPay = computed(() => order.value?.status === 'PENDING_PAYMENT' && Boolean(order.value.paymentToken))

function openPayment(): void {
  if (!order.value?.paymentToken) return
  router.push({ name: 'payment', params: { paymentToken: order.value.paymentToken }, query: { orderNo: orderNo.value } })
}
const moneyText = computed(() => {
  if (order.value?.finalAmount == null) return ''
  return `¥${(order.value.finalAmount / 100).toFixed(2)}`
})

onMounted(async () => {
  token.value = loadOrderToken(orderNo.value)
  tokenInput.value = token.value
  try {
    brand.value = await getPublicBrand()
    document.title = `${brand.value.companyName} · 订单状态`
  } catch {
    // Branding failure does not block order access.
  }
  if (token.value) await refresh()
  refreshTimer = window.setInterval(() => {
    if (token.value && order.value && !['COMPLETED', 'CANCELLED'].includes(order.value.status)) {
      void refresh(false)
    }
  }, 15_000)
})

onBeforeUnmount(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
})

async function refresh(showError = true): Promise<void> {
  if (!token.value) return
  loading.value = true
  try {
    order.value = await getPassengerOrder(orderNo.value, token.value)
    errorMessage.value = ''
  } catch (error) {
    if (showError) errorMessage.value = error instanceof Error ? error.message : '订单加载失败'
  } finally {
    loading.value = false
  }
}

async function applyToken(): Promise<void> {
  const value = tokenInput.value.trim()
  if (!value) {
    showFailToast('请输入订单访问凭证')
    return
  }
  token.value = value
  saveOrderToken(orderNo.value, value)
  await refresh()
}

async function cancelOrder(): Promise<void> {
  if (!order.value || !canCancel.value || cancelling.value) return
  try {
    await showConfirmDialog({ title: '取消订单', message: '司机接单后乘客将不能自行取消。确认取消当前订单吗？' })
  } catch {
    return
  }
  cancelling.value = true
  try {
    await cancelPassengerOrder(orderNo.value, token.value)
    showSuccessToast('订单已取消')
    await refresh()
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : '取消失败')
  } finally {
    cancelling.value = false
  }
}

function formatTime(value?: string | null): string {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(new Date(value))
}

function coordinateText(latitude?: number | null, longitude?: number | null): string {
  if (latitude == null || longitude == null) return '无定位坐标（文字地址）'
  return `${longitude}, ${latitude}`
}
</script>

<template>
  <main class="mobile-page">
    <header class="simple-header">
      <button class="text-button" type="button" @click="router.push('/ride')">新建预约</button>
      <span>{{ brand.companyName }}</span>
      <button class="text-button" type="button" :disabled="loading || !token" @click="refresh()">刷新</button>
    </header>

    <section v-if="!token || errorMessage" class="surface-card access-card">
      <span class="section-kicker">订单访问</span>
      <h1>{{ errorMessage || '需要订单访问凭证' }}</h1>
      <p>订单号：{{ orderNo }}</p>
      <van-field v-model="tokenInput" class="token-input" placeholder="请输入 passenger access token" clearable />
      <van-button block round type="primary" :loading="loading" @click="applyToken">查看订单</van-button>
    </section>

    <template v-if="order && currentMeta && !errorMessage">
      <section class="status-card" :data-tone="currentMeta.tone">
        <div class="status-dot" />
        <div>
          <span class="section-kicker">订单状态</span>
          <h1>{{ currentMeta.title }}</h1>
          <p>{{ currentMeta.description }}</p>
        </div>
        <div v-if="order.tripStage" class="stage-pill">{{ tripStageText[order.tripStage] }}</div>
      </section>

      <section v-if="order.finalAmount != null" class="amount-card">
        <span>本次行程金额</span>
        <strong>{{ moneyText }}</strong>
        <small>金额由司机与乘客协商后录入</small>
      </section>

      <section v-if="canPay" class="surface-card pay-entry-card">
        <div><span class="section-kicker">付款入口</span><h2>确认金额后完成付款</h2><p>支持 Mock 微信/支付宝，支付结果由服务端确认。</p></div>
        <van-button round type="primary" @click="openPayment">去付款</van-button>
      </section>

      <section class="surface-card detail-card">
        <div class="section-heading compact">
          <div>
            <span class="section-kicker">行程详情</span>
            <h2>{{ order.pickupAddress }} → {{ order.destinationAddress }}</h2>
          </div>
        </div>
        <dl class="detail-list">
          <div><dt>订单号</dt><dd>{{ order.orderNo }}</dd></div>
          <div><dt>出发时间</dt><dd>{{ formatTime(order.departureAt) }}</dd></div>
          <div><dt>乘车人数</dt><dd>{{ order.passengerCount }} 人</dd></div>
          <div><dt>订单来源</dt><dd>{{ order.sourceType === 'DRIVER_QR' ? '司机二维码' : order.sourceType === 'ADMIN_CREATED' ? '后台代客下单' : '公共预约' }}</dd></div>
          <div v-if="order.remark"><dt>备注</dt><dd>{{ order.remark }}</dd></div>
        </dl>
      </section>

      <section class="surface-card route-card">
        <div class="route-point pickup"><span>A</span><div><small>上车点</small><strong>{{ order.pickupAddress }}</strong><em>{{ coordinateText(order.pickupLatitude, order.pickupLongitude) }}</em></div></div>
        <div class="route-line" />
        <div class="route-point destination"><span>B</span><div><small>目的地</small><strong>{{ order.destinationAddress }}</strong><em>{{ coordinateText(order.destinationLatitude, order.destinationLongitude) }}</em></div></div>
      </section>

      <div class="submit-area status-actions">
        <van-button v-if="canCancel" block round plain type="danger" :loading="cancelling" @click="cancelOrder">取消订单</van-button>
        <van-button block round plain type="warning" @click="complaintOpen = true">投诉与建议</van-button>
        <p class="privacy-note">页面会每 15 秒自动刷新一次非终态订单。</p>
      </div>

      <SafetyCenter
        source-page="ORDER_STATUS"
        :order-no="orderNo"
        :passenger-token="token"
        :driver-name="order.driverName"
        :vehicle-plate-no="order.vehiclePlateNo"
        :vehicle-brand-model="order.vehicleBrandModel"
        :pickup-address="order.pickupAddress"
        :destination-address="order.destinationAddress"
      />
      <ComplaintSheet
        :open="complaintOpen"
        :order-no="orderNo"
        :passenger-token="token"
        @close="complaintOpen = false"
      />
    </template>
  </main>
</template>
