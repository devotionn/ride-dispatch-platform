<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { closeToast, showFailToast, showLoadingToast, showSuccessToast } from 'vant'

import { getPublicBrand } from '../api/brand'
import { getPublicDriver } from '../api/drivers'
import { createOrder } from '../api/orders'
import type { PlatformBrand, PublicDriverProfile } from '../domain/types'
import { saveOrderToken } from '../storage/orderToken'

const route = useRoute()
const router = useRouter()
const brand = ref<PlatformBrand>({ companyName: '预约用车' })
const driverProfile = ref<PublicDriverProfile | null>(null)
const driverError = ref('')
const driverLoading = ref(false)
const locating = ref(false)
const submitting = ref(false)

const form = reactive({
  pickupAddress: '',
  pickupLatitude: '',
  pickupLongitude: '',
  destinationAddress: '',
  destinationLatitude: '',
  destinationLongitude: '',
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

watch(
  driverShortCode,
  (value) => void loadDriver(value),
  { immediate: true },
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

function useCurrentLocation(): void {
  if (!navigator.geolocation) {
    showFailToast('当前浏览器不支持定位')
    return
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    (position) => {
      form.pickupLatitude = position.coords.latitude.toFixed(7)
      form.pickupLongitude = position.coords.longitude.toFixed(7)
      if (!form.pickupAddress) form.pickupAddress = '当前位置'
      locating.value = false
      showSuccessToast('已获取当前位置')
    },
    () => {
      locating.value = false
      showFailToast('定位失败，请手工填写位置')
    },
    { enableHighAccuracy: true, timeout: 10_000, maximumAge: 30_000 },
  )
}

function coordinateValid(value: string, min: number, max: number): boolean {
  const number = Number(value)
  return Number.isFinite(number) && number >= min && number <= max
}

async function submit(): Promise<void> {
  if (submitting.value) return
  if (directed.value && !driverProfile.value) {
    showFailToast(driverError.value || '正在确认司机信息，请稍后')
    return
  }
  if (!form.pickupAddress.trim() || !form.destinationAddress.trim()) {
    showFailToast('请填写上车点和目的地')
    return
  }
  if (!coordinateValid(form.pickupLatitude, -90, 90) || !coordinateValid(form.destinationLatitude, -90, 90)) {
    showFailToast('纬度格式不正确')
    return
  }
  if (!coordinateValid(form.pickupLongitude, -180, 180) || !coordinateValid(form.destinationLongitude, -180, 180)) {
    showFailToast('经度格式不正确')
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

  submitting.value = true
  showLoadingToast({ message: '正在提交…', forbidClick: true, duration: 0 })
  try {
    const result = await createOrder({
      sourceType: directed.value ? 'DRIVER_QR' : 'PUBLIC_H5',
      driverShortCode: directed.value ? driverShortCode.value : undefined,
      pickup: {
        address: form.pickupAddress.trim(),
        latitude: Number(form.pickupLatitude),
        longitude: Number(form.pickupLongitude),
      },
      destination: {
        address: form.destinationAddress.trim(),
        latitude: Number(form.destinationLatitude),
        longitude: Number(form.destinationLongitude),
      },
      passengerCount: form.passengerCount,
      departureAt: new Date(form.departureAt).toISOString(),
      mobile: form.mobile,
      remark: form.remark.trim() || undefined,
    })
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
      <div v-if="directed" class="binding-chip">司机专属二维码入口</div>
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
      <p v-if="driverError" class="hero-copy" style="color:#d84a4a; margin-top:8px">{{ driverError }}</p>
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
          <h2>从哪里出发？</h2>
        </div>
        <van-button size="small" plain round :loading="locating" @click="useCurrentLocation">使用当前位置</van-button>
      </div>

      <van-form @submit="submit">
        <van-cell-group inset>
          <van-field v-model="form.pickupAddress" label="上车点" placeholder="请输入上车地址" maxlength="255" />
          <van-field v-model="form.pickupLatitude" label="上车纬度" type="number" placeholder="例如 32.3910000" />
          <van-field v-model="form.pickupLongitude" label="上车经度" type="number" placeholder="例如 119.5080000" />
          <van-field v-model="form.destinationAddress" label="目的地" placeholder="请输入目的地" maxlength="255" />
          <van-field v-model="form.destinationLatitude" label="目的纬度" type="number" placeholder="例如 32.4200000" />
          <van-field v-model="form.destinationLongitude" label="目的经度" type="number" placeholder="例如 119.4140000" />
        </van-cell-group>

        <div class="section-spacer" />

        <van-cell-group inset>
          <van-field label="乘车人数">
            <template #input>
              <van-stepper v-model="form.passengerCount" :min="1" :max="20" integer />
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
  </main>
</template>
