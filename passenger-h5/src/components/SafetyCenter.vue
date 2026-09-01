<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { showFailToast, showToast } from 'vant'

import { reportSafetyAlarm } from '../api/safety'

const props = withDefaults(defineProps<{
  sourcePage: string
  orderNo?: string
  passengerToken?: string
  driverName?: string | null
  vehiclePlateNo?: string | null
  vehicleBrandModel?: string | null
  pickupAddress?: string | null
  destinationAddress?: string | null
}>(), {
  orderNo: '',
  passengerToken: '',
  driverName: null,
  vehiclePlateNo: null,
  vehicleBrandModel: null,
  pickupAddress: null,
  destinationAddress: null,
})

const open = ref(false)
const locating = ref(false)
const reporting = ref(false)
const position = reactive<{ latitude: number | null; longitude: number | null }>({
  latitude: null,
  longitude: null,
})

const hasOrderContext = computed(() => Boolean(props.orderNo && props.passengerToken))
const driverLabel = computed(() => {
  const parts = [props.driverName, props.vehiclePlateNo, props.vehicleBrandModel].filter(Boolean)
  return parts.length ? parts.join(' · ') : null
})

function openSheet(): void {
  open.value = true
  locate()
}

function locate(): void {
  if (!navigator.geolocation) return
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      position.latitude = pos.coords.latitude
      position.longitude = pos.coords.longitude
      locating.value = false
    },
    () => {
      locating.value = false
    },
    { enableHighAccuracy: true, timeout: 10_000, maximumAge: 30_000 },
  )
}

async function callPolice(): Promise<void> {
  if (reporting.value) return
  reporting.value = true
  const locationText = position.latitude != null && position.longitude != null
    ? `${position.longitude.toFixed(6)},${position.latitude.toFixed(6)}`
    : null
  // Fire-and-forget: dialing 110 must never wait on the network.
  await reportSafetyAlarm({
    orderNo: props.orderNo || undefined,
    passengerToken: props.passengerToken || undefined,
    sourcePage: props.sourcePage,
    latitude: position.latitude,
    longitude: position.longitude,
    locationText,
  })
  reporting.value = false
  showToast('正在为您转接 110，请留意电话拨号盘')
  window.location.href = 'tel:110'
}
</script>

<template>
  <Teleport to="body">
    <button class="safety-fab" type="button" aria-label="安全中心" @click="openSheet">
      <span class="safety-fab-icon">🛡️</span>
      <span class="safety-fab-label">安全中心</span>
    </button>

    <div v-if="open" class="safety-overlay" role="dialog" aria-modal="true" aria-label="安全中心">
      <section class="safety-sheet">
        <header class="safety-header">
          <div>
            <p class="section-kicker">安全中心</p>
            <h2>一键报警</h2>
          </div>
          <button type="button" class="safety-close" aria-label="关闭" @click="open = false">×</button>
        </header>

        <div v-if="driverLabel || hasOrderContext" class="safety-card">
          <p class="safety-card-title">行程信息</p>
          <p v-if="driverLabel" class="safety-line"><strong>{{ driverLabel }}</strong></p>
          <p v-if="pickupAddress" class="safety-line">上车点：{{ pickupAddress }}</p>
          <p v-if="destinationAddress" class="safety-line">目的地：{{ destinationAddress }}</p>
          <p class="safety-hint">报警时请向警方说明以上车辆与行程信息。</p>
        </div>

        <div class="safety-card">
          <p class="safety-card-title">当前位置（仅供参考，GPS 弱信号时可能有偏差）</p>
          <p v-if="position.latitude != null" class="safety-line">
            {{ position.longitude?.toFixed(6) }}, {{ position.latitude?.toFixed(6) }}
          </p>
          <p v-else class="safety-line">{{ locating ? '正在获取当前位置…' : '未能获取当前位置，可直接电话报警说明周边标志物。' }}</p>
          <button type="button" class="safety-refresh" @click="locate">重新定位</button>
        </div>

        <button type="button" class="safety-call" :disabled="reporting" @click="callPolice">呼叫 110</button>
        <p class="safety-note">点击“呼叫 110”将直接拨打当地 110 报警电话，平台会同步记录本次报警事件并通知调度人员关注。</p>

        <div class="safety-legal">
          <p class="safety-legal-title">谎报警情，依法追责</p>
          <p>请不要恶意报警。若查实为恶意报警，相关机关将依照《治安管理处罚法》给予行政处罚，包括但不限于：无故拨打 110 扰乱警情、人为编造虚假警情、故意举报不存在的事实等。</p>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.safety-fab{position:fixed;right:14px;bottom:calc(96px + env(safe-area-inset-bottom));z-index:2500;display:grid;justify-items:center;gap:2px;padding:10px 12px;border:0;border-radius:18px;background:rgba(219,234,254,.92);box-shadow:0 8px 22px rgba(31,86,214,.22);cursor:pointer}
.safety-fab-icon{font-size:20px;line-height:1}
.safety-fab-label{font-size:10px;font-weight:700;color:#1d4ed8}
.safety-overlay{position:fixed;inset:0;z-index:3000;background:rgba(15,23,42,.46);display:flex;align-items:flex-end;justify-content:center;padding-top:40px}
.safety-sheet{width:min(100%,640px);max-height:92vh;overflow:auto;background:#fff;border-radius:24px 24px 0 0;padding:22px 18px calc(22px + env(safe-area-inset-bottom));box-shadow:0 -12px 40px rgba(15,23,42,.14)}
.safety-header{display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:14px}
.safety-header h2{margin:4px 0 0;font-size:22px}
.safety-close{border:0;background:#f3f4f6;width:36px;height:36px;border-radius:50%;font-size:25px;line-height:1}
.safety-card{margin-bottom:12px;padding:14px;border:1px solid #e5e7eb;border-radius:14px;display:grid;gap:6px}
.safety-card-title{margin:0;font-size:12px;font-weight:700;color:#16a36a}
.safety-line{margin:0;font-size:14px;color:#1e293b;overflow-wrap:anywhere}
.safety-hint{margin:0;font-size:12px;color:#64748b}
.safety-refresh{justify-self:start;border:0;background:#f1f5f9;color:#1e293b;border-radius:10px;padding:8px 12px;font-size:12px}
.safety-call{display:block;width:100%;border:0;border-radius:14px;background:#e5484d;color:#fff;font-size:17px;font-weight:800;padding:14px;cursor:pointer}
.safety-call:disabled{opacity:.7}
.safety-note{margin:10px 0 0;font-size:12px;color:#64748b;line-height:1.6}
.safety-legal{margin-top:14px;padding:12px;border-radius:12px;background:#fef2f2;display:grid;gap:6px}
.safety-legal-title{margin:0;font-size:13px;font-weight:800;color:#b42318}
.safety-legal p:not(.safety-legal-title){margin:0;font-size:12px;color:#7f1d1d;line-height:1.7}
</style>
