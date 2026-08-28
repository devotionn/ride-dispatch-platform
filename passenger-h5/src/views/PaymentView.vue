<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import QRCode from 'qrcode'

import { createPaymentAttempt, getPaymentStatus, mockPaymentFailure, mockPaymentSuccess, type PaymentChannel, type PaymentResponse } from '../api/payments'

const route = useRoute()
const router = useRouter()
const paymentToken = computed(() => String(route.params.paymentToken ?? ''))
const orderNo = computed(() => String(route.query.orderNo ?? ''))
const payment = ref<PaymentResponse | null>(null)
const selectedChannel = ref<PaymentChannel>('MOCK_WECHAT')
const activeAttemptNo = ref('')
const loading = ref(false)
const actionLoading = ref(false)
const errorMessage = ref('')
const isLocalMock = import.meta.env.DEV || import.meta.env.VITE_APP_PROFILE === 'local'
let refreshTimer: number | undefined
let pollCount = 0

const amountText = computed(() => payment.value ? '¥' + (payment.value.amount / 100).toFixed(2) : '—')
const activeAttempt = computed(() => payment.value?.attempts.find((item) => item.attemptNo === activeAttemptNo.value) ?? null)
const isPaid = computed(() => payment.value?.status === 'PAID')
const paymentQr = ref('')

onMounted(() => {
  document.title = '订单付款'
  void refresh()
})

onBeforeUnmount(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
})

async function refresh(showError = true): Promise<void> {
  if (!paymentToken.value) return
  loading.value = true
  try {
    payment.value = await getPaymentStatus(paymentToken.value)
    if (payment.value.status === 'PENDING') {
      paymentQr.value = await QRCode.toDataURL(`ride-dispatch://payment/${payment.value.paymentNo}?token=${paymentToken.value}`, {
        width: 220, margin: 1, errorCorrectionLevel: 'M', color: { dark: '#173B77', light: '#FFFFFF' },
      })
    } else {
      paymentQr.value = ''
    }
    const latest = payment.value.attempts.at(-1)
    if (latest && ['PROCESSING', 'CREATED'].includes(latest.status)) activeAttemptNo.value = latest.attemptNo
    errorMessage.value = ''
    if (payment.value.status === 'PENDING' && pollCount < 30) {
      pollCount += 1
      if (!refreshTimer) refreshTimer = window.setInterval(() => void refresh(false), 2000)
    } else if (refreshTimer) {
      window.clearInterval(refreshTimer)
      refreshTimer = undefined
    }
  } catch (error) {
    if (showError) errorMessage.value = error instanceof Error ? error.message : '付款信息加载失败'
  } finally {
    loading.value = false
  }
}

async function startPayment(): Promise<void> {
  if (actionLoading.value || isPaid.value) return
  actionLoading.value = true
  try {
    const attempt = await createPaymentAttempt(paymentToken.value, selectedChannel.value)
    activeAttemptNo.value = attempt.attemptNo
    await refresh()
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : '发起支付失败')
  } finally {
    actionLoading.value = false
  }
}

async function mockSuccess(): Promise<void> {
  if (!activeAttemptNo.value || actionLoading.value) return
  actionLoading.value = true
  try {
    await mockPaymentSuccess(activeAttemptNo.value)
    showSuccessToast('模拟支付成功')
    await refresh()
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : '模拟支付失败')
  } finally {
    actionLoading.value = false
  }
}

async function mockFailure(): Promise<void> {
  if (!activeAttemptNo.value || actionLoading.value) return
  actionLoading.value = true
  try {
    await mockPaymentFailure(activeAttemptNo.value)
    showFailToast('已模拟支付失败，可切换渠道重试')
    activeAttemptNo.value = ''
    await refresh()
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : '模拟失败操作未完成')
  } finally {
    actionLoading.value = false
  }
}

function goBackToOrder(): void {
  if (orderNo.value) router.push({ name: 'order-status', params: { orderNo: orderNo.value } })
  else router.back()
}
</script>

<template>
  <main class="mobile-page payment-page">
    <header class="simple-header">
      <button class="text-button" type="button" @click="goBackToOrder">返回订单</button>
      <span>安全付款</span>
      <button class="text-button" type="button" :disabled="loading" @click="refresh()">刷新</button>
    </header>

    <section v-if="errorMessage" class="surface-card access-card">
      <span class="section-kicker">付款状态</span>
      <h1>{{ errorMessage }}</h1>
      <van-button block round type="primary" @click="refresh()">重试</van-button>
    </section>

    <template v-else-if="payment">
      <section class="payment-hero" :data-paid="isPaid">
        <span class="section-kicker">应付金额</span>
        <strong>{{ amountText }}</strong>
        <p>{{ isPaid ? '支付已完成，订单正在同步' : '金额由司机确认，平台不会在付款页修改金额' }}</p>
      </section>

      <section class="surface-card payment-status-panel">
        <div class="payment-status-row"><span>支付单</span><strong>{{ payment.paymentNo }}</strong></div>
        <div class="payment-status-row"><span>当前状态</span><b :data-status="payment.status">{{ isPaid ? '已支付' : payment.status === 'PENDING' ? '待付款' : payment.status }}</b></div>
        <div v-if="payment.settlementMethod" class="payment-status-row"><span>结算方式</span><strong>{{ payment.settlementMethod }}</strong></div>
      </section>

      <section v-if="!isPaid && paymentQr" class="surface-card payment-qr-card">
        <div class="section-heading compact"><div><span class="section-kicker">付款凭证</span><h2>扫码打开付款页</h2></div></div>
        <img :src="paymentQr" alt="本地付款二维码" class="payment-qr" />
        <p class="privacy-note">当前为本地测试二维码，真实微信 / 支付宝收款码需接入商户参数和签名回调。</p>
      </section>

      <section v-if="!isPaid" class="surface-card payment-method-card">
        <span class="section-kicker">选择支付方式</span>
        <div class="payment-methods">
          <button class="payment-method" :class="{ selected: selectedChannel === 'MOCK_WECHAT' }" type="button" @click="selectedChannel = 'MOCK_WECHAT'">微信支付<small>Mock 通道</small></button>
          <button class="payment-method" :class="{ selected: selectedChannel === 'MOCK_ALIPAY' }" type="button" @click="selectedChannel = 'MOCK_ALIPAY'">支付宝<small>Mock 通道</small></button>
        </div>
        <van-button block round type="primary" :loading="actionLoading" :disabled="!isLocalMock" @click="startPayment">发起支付</van-button>
        <div v-if="isLocalMock && activeAttempt" class="mock-actions">
          <p>本地测试：{{ activeAttempt.status === 'FAILED' ? '本次尝试失败，可重新发起' : '等待模拟回调' }}</p>
          <div class="mock-action-row">
            <van-button size="small" plain type="success" :loading="actionLoading" @click="mockSuccess">模拟成功</van-button>
            <van-button size="small" plain type="danger" :loading="actionLoading" @click="mockFailure">模拟失败</van-button>
          </div>
        </div>
      </section>

      <section v-if="payment.attempts.length" class="surface-card attempts-card">
        <div class="section-heading compact"><div><span class="section-kicker">支付尝试</span><h2>服务端记录</h2></div></div>
        <div v-for="attempt in payment.attempts" :key="attempt.attemptNo" class="attempt-row">
          <span>{{ attempt.channel === 'MOCK_WECHAT' ? '微信' : '支付宝' }}</span>
          <strong>{{ attempt.status }}</strong>
        </div>
      </section>

      <div class="submit-area"><van-button block round plain type="primary" @click="goBackToOrder">返回订单状态</van-button><p class="privacy-note">支付结果以服务端状态为准，关闭页面后可从订单状态继续查询。</p></div>
    </template>
  </main>
</template>
