<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { closeToast, showFailToast, showLoadingToast, showSuccessToast } from 'vant'

import { getPublicBrand } from '../api/brand'
import { getPublicDriver } from '../api/drivers'
import { createOrder } from '../api/orders'
import MapPointPicker from '../components/MapPointPicker.vue'
import type { CreateOrderPayload, PlatformBrand, PublicDriverProfile } from '../domain/types'
import type { MapPoint } from '../map/types'
import { clearOrderIdempotencyKey, getOrCreateOrderIdempotencyKey } from '../storage/orderIdempotency'
import { saveOrderToken } from '../storage/orderToken'

const route = useRoute()
const router = useRouter()
const brand = ref<PlatformBrand>({ companyName: '预约用车' })
const driverProfile = ref<PublicDriverProfile | null>(null)
const driverError = ref('')
const driverLoading = ref(false)
const submitting = ref(false)
const pickup = ref<MapPoint | null>(null)
const destination = ref<MapPoint | null>(null)
const pickerTarget = ref<'pickup' | 'destination' | null>(null)

const form = reactive({
  passengerCount: 1,
  departureAt: defaultDepartureTime(),
  mobile: '',
  remark: '',
})

const driverShortCode = computed(() => {
  const value = route.params.driverShortCode
  return typeof value === 'string' ? value.trim() : ''
})
const directed = computed(() => Boolean(driverShortCode.value))
const passengerMax = computed(() => (directed.value && driverProfile.value ? driverProfile.value.maxPassengers : 20))
const pickerTitle = computed(() => (pickerTarget.value === 'destination' ? '选择目的地' : '选择上车点'))
const pickerPoint = computed(() => (pickerTarget.value === 'destination' ? destination.value : pickup.value))

watch(
  driverShortCode,
  (value) => void loadDriver(value),
  { immediate: true },
)

watch(passengerMax, (max) => {
  if (form.passengerCount > max) form.passengerCount = max
})

watch(
  () => route.fullPath,
  () => window.scrollTo({ top: 0 }),
)

onMounted(async () => {
  try {
    brand.value = await getPublicBrand()
    document.title = `${brand.value.companyName} · 预约用车`
  } catch {
    // Branding failure must not block ordering.
  }
})

async function loadDriver(shortCode: string): Promise<void> {
  driverProfile.value = null
  driverError.value = ''
  if (!shortCode) return
  driverLoading.value = true
  try {
    driverProfile.value = await getPublicDriver(shortCode)
  } catch (error) {
    driverError.value = error instanceof Error ? error.message : '司机二维码已失效'
  } finally {
    driverLoading.value = false
  }
}

function defaultDepartureTime(): string {
  const value = new Date(Date.now() + 30 * 60 * 1000)
  const local = new Date(value.getTime() - value.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

function openPicker(target: 'pickup' | 'destination'): void {
  pickerTarget.value = target
}

function applyPoint(point: MapPoint): void {
  if (pickerTarget.value === 'destination') destination.value = point
  else pickup.value = point
  pickerTarget.value = null
}

async function submit(): Promise<void> {
  if (submitting.value) return
  if (directed.value && !driverProfile.value) {
    showFailToast(driverError.value || '正在确认司机信息，请稍后')
    return
  }
  if (!pickup.value || !destination.value) {
    showFailToast('请选择上车点和目的地')
    return
  }
  if (!/^1\d{10}$/.test(form.mobile)) {
    showFailToast('请输入正确的手机号')
    return
  }
  if (!form.departureAt) {
    showFailToast('请选择出发时间')
    return
  }

  const payload: CreateOrderPayload = {
    sourceType: directed.value ? 'DRIVER_QR' : 'PUBLIC_H5',
    driverShortCode: directed.value ? driverShortCode.value : undefined,
    pickup: pickup.value,
    destination: destination.value,
    passengerCount: form.passengerCount,
    departureAt: new Date(form.departureAt).toISOString(),
    mobile: form.mobile,
    remark: form.remark.trim() || undefined,
  }

  submitting.value = true
  showLoadingToast({ message: '正在提交…', forbidClick: true, duration: 0 })
  try {
    const idempotencyKey = await getOrCreateOrderIdempotencyKey(payload)
    const result = await createOrder(payload, idempotencyKey)
    clearOrderIdempotencyKey(idempotencyKey)
    saveOrderToken(result.orderNo, result.passengerAccessToken)
    closeToast()
    showSuccessToast('订单已提交')
    await router.replace({ name: 'order-status', params: { orderNo: result.orderNo } })
  } catch (error) {
    closeToast()
    showFailToast(error instanceof Error ? error.message : '提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="mobile-page">
    <header class="hero-card">
      <div class="brand-row">
        <img v-if="brand.logoUrl" class="brand-logo" :src="brand.logoUrl" alt="品牌 Logo" />
        <div>
          <p class="eyebrow">{{ directed ? '司机专属预约' : '便捷预约用车' }}</p>
          <h1>{{ brand.companyName }}</h1>
        </div>
      </div>
      <p class="hero-copy">
        {{ directed ? '本次订单将优先发送给二维码对应司机确认；司机拒绝后转入调度池。' : '提交行程后由调度人员根据上车位置安排合适司机。' }}
      </p>
    </header>

    <section v-if="directed" class="surface-card detail-card">
      <div class="section-heading compact">
        <div>
          <span class="section-kicker">绑定司机</span>
          <h2 v-if="driverProfile">{{ driverProfile.name }}</h2>
          <h2 v-else-if="driverError">二维码不可用</h2>
          <h2 v-else>正在确认司机…</h2>
        </div>
        <van-button v-if="driverError" size="small" plain round :loading="driverLoading" @click="loadDriver(driverShortCode)">重试</van-button>
      </div>
      <p v-if="driverError" class="driver-error-copy">{{ driverError }}</p>
      <dl v-if="driverProfile" class="detail-list">
        <div><dt>司机</dt><dd>{{ driverProfile.name }}</dd></div>
        <div><dt>车辆</dt><dd>{{ driverProfile.brandModel || '车辆信息待完善' }}</dd></div>
        <div><dt>车牌</dt><dd>{{ driverProfile.plateNo || '—' }}</dd></div>
        <div><dt>最大载客</dt><dd>{{ driverProfile.maxPassengers }} 人</dd></div>
      </dl>
    </section>

    <section class="surface-card form-card">
      <div class="section-heading">
        <div>
          <span class="section-kicker">行程信息</span>
          <h2>选择本次行程</h2>
        </div>
      </div>

      <div class="route-selector">
        <button class="route-select-row" type="button" @click="openPicker('pickup')">
          <span class="route-badge pickup">A</span>
          <div>
            <small>上车点</small>
            <strong :class="{ placeholder: !pickup }">{{ pickup?.address || '定位、搜索或地图选点' }}</strong>
          </div>
          <b>选择</b>
        </button>
        <div class="route-selector-line" />
        <button class="route-select-row" type="button" @click="openPicker('destination')">
          <span class="route-badge destination">B</span>
          <div>
            <small>目的地</small>
            <strong :class="{ placeholder: !destination }">{{ destination?.address || '搜索或地图选点' }}</strong>
          </div>
          <b>选择</b>
        </button>
      </div>

      <div class="section-spacer" />

      <van-form @submit="submit">
        <van-cell-group inset>
          <van-field label="乘车人数">
            <template #input>
              <van-stepper v-model="form.passengerCount" :min="1" :max="passengerMax" integer />
            </template>
          </van-field>
          <van-field v-model="form.departureAt" label="出发时间" type="datetime-local" />
          <van-field v-model="form.mobile" label="联系电话" type="tel" maxlength="11" placeholder="请输入 11 位手机号" />
          <van-field v-model="form.remark" label="备注" type="textarea" rows="2" autosize maxlength="500" show-word-limit placeholder="选填，例如携带大件行李" />
        </van-cell-group>

        <div class="submit-area">
          <van-button block round type="primary" native-type="submit" :loading="submitting" :disabled="directed && !driverProfile" loading-text="提交中…">
            {{ directed ? '提交给该司机确认' : '提交预约' }}
          </van-button>
          <p class="privacy-note">仅收集完成本次用车服务所需的订单信息。</p>
        </div>
      </van-form>
    </section>

    <MapPointPicker
      :open="pickerTarget !== null"
      :title="pickerTitle"
      :model-value="pickerPoint"
      @close="pickerTarget = null"
      @select="applyPoint"
    />
  </main>
</template>
