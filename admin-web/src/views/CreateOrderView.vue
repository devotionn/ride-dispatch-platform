<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { createAdminOrder } from '../api/orders'
import AdminMapPointPicker from '../components/AdminMapPointPicker.vue'
import type { AdminCreateOrderPayload } from '../domain/types'
import type { MapPoint } from '../map/types'

const router = useRouter()
const submitting = ref(false)
const pickerOpen = ref(false)
const pickerTarget = ref<'pickup' | 'destination'>('pickup')
const pickup = ref<MapPoint | null>(null)
const destination = ref<MapPoint | null>(null)

const form = reactive({
  passengerCount: 1,
  departureAt: null as Date | null,
  mobile: '',
  remark: '',
  pickupAddress: '',
  pickupLongitude: null as number | null,
  pickupLatitude: null as number | null,
  destinationAddress: '',
  destinationLongitude: null as number | null,
  destinationLatitude: null as number | null,
})

const pickerTitle = computed(() => pickerTarget.value === 'pickup' ? '选择上车点' : '选择目的地')
const pickerPoint = computed(() => pickerTarget.value === 'pickup' ? pickup.value : destination.value)

function openPicker(target: 'pickup' | 'destination'): void {
  pickerTarget.value = target
  syncManualToPoint(target)
  pickerOpen.value = true
}

function applyPoint(point: MapPoint): void {
  if (pickerTarget.value === 'pickup') {
    pickup.value = point
    form.pickupAddress = point.address
    form.pickupLongitude = point.longitude
    form.pickupLatitude = point.latitude
  } else {
    destination.value = point
    form.destinationAddress = point.address
    form.destinationLongitude = point.longitude
    form.destinationLatitude = point.latitude
  }
}

function syncManualToPoint(target: 'pickup' | 'destination'): void {
  if (target === 'pickup') {
    if (validPoint(form.pickupAddress, form.pickupLatitude, form.pickupLongitude)) {
      pickup.value = { address: form.pickupAddress.trim(), latitude: form.pickupLatitude!, longitude: form.pickupLongitude! }
    }
  } else if (validPoint(form.destinationAddress, form.destinationLatitude, form.destinationLongitude)) {
    destination.value = { address: form.destinationAddress.trim(), latitude: form.destinationLatitude!, longitude: form.destinationLongitude! }
  }
}

async function submit(): Promise<void> {
  if (!validPoint(form.pickupAddress, form.pickupLatitude, form.pickupLongitude)) {
    ElMessage.warning('请通过地图选择或完整填写上车点地址与坐标')
    return
  }
  if (!validPoint(form.destinationAddress, form.destinationLatitude, form.destinationLongitude)) {
    ElMessage.warning('请通过地图选择或完整填写目的地地址与坐标')
    return
  }
  if (!/^1\d{10}$/.test(form.mobile)) {
    ElMessage.warning('请输入正确的乘客手机号')
    return
  }
  if (!form.departureAt) {
    ElMessage.warning('请选择出发时间')
    return
  }

  const payload: AdminCreateOrderPayload = {
    pickup: {
      address: form.pickupAddress.trim(),
      latitude: form.pickupLatitude!,
      longitude: form.pickupLongitude!,
    },
    destination: {
      address: form.destinationAddress.trim(),
      latitude: form.destinationLatitude!,
      longitude: form.destinationLongitude!,
    },
    passengerCount: form.passengerCount,
    departureAt: form.departureAt.toISOString(),
    mobile: form.mobile,
    remark: form.remark.trim() || undefined,
  }

  submitting.value = true
  try {
    const result = await createAdminOrder(payload)
    ElMessage.success(`订单 ${result.orderNo} 已创建，已进入待接单`)
    await router.push({ name: 'orders' })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建订单失败')
  } finally {
    submitting.value = false
  }
}

function validPoint(address: string, latitude: number | null, longitude: number | null): boolean {
  return Boolean(address.trim())
    && latitude !== null && Number.isFinite(latitude) && latitude >= -90 && latitude <= 90
    && longitude !== null && Number.isFinite(longitude) && longitude >= -180 && longitude <= 180
}
</script>

<template>
  <section class="workspace-page create-order-page">
    <header class="page-heading">
      <div>
        <p class="page-kicker">ASSISTED BOOKING</p>
        <h1>后台代客建单</h1>
        <p>适用于电话、现场或其他非 H5 渠道的乘客，由调度员代为录入真实行程。</p>
      </div>
      <div class="page-actions">
        <el-button @click="router.push({ name: 'orders' })">返回订单中心</el-button>
      </div>
    </header>

    <section class="panel-card create-order-workbench">
      <div class="create-order-route-grid">
        <div class="location-work-card">
          <div class="location-card-head">
            <div><span class="point-a">A</span><section><small>上车点</small><h3>{{ form.pickupAddress || '尚未选择' }}</h3></section></div>
            <el-button type="primary" plain @click="openPicker('pickup')">地图选择上车点</el-button>
          </div>
          <el-form label-position="top">
            <el-form-item label="地址"><el-input v-model="form.pickupAddress" placeholder="地图选择后自动回填，也可手工修正" /></el-form-item>
            <div class="form-grid two">
              <el-form-item label="经度"><el-input-number v-model="form.pickupLongitude" :controls="false" :precision="7" /></el-form-item>
              <el-form-item label="纬度"><el-input-number v-model="form.pickupLatitude" :controls="false" :precision="7" /></el-form-item>
            </div>
          </el-form>
        </div>

        <div class="location-work-card">
          <div class="location-card-head">
            <div><span class="point-b">B</span><section><small>目的地</small><h3>{{ form.destinationAddress || '尚未选择' }}</h3></section></div>
            <el-button type="primary" plain @click="openPicker('destination')">地图选择目的地</el-button>
          </div>
          <el-form label-position="top">
            <el-form-item label="地址"><el-input v-model="form.destinationAddress" placeholder="地图选择后自动回填，也可手工修正" /></el-form-item>
            <div class="form-grid two">
              <el-form-item label="经度"><el-input-number v-model="form.destinationLongitude" :controls="false" :precision="7" /></el-form-item>
              <el-form-item label="纬度"><el-input-number v-model="form.destinationLatitude" :controls="false" :precision="7" /></el-form-item>
            </div>
          </el-form>
        </div>
      </div>

      <div class="create-order-passenger-card">
        <div class="drawer-section-heading"><div><span>乘客与时间</span><h3>补全预约信息</h3></div></div>
        <el-form label-position="top">
          <div class="form-grid three">
            <el-form-item label="乘车人数"><el-input-number v-model="form.passengerCount" :min="1" :max="20" /></el-form-item>
            <el-form-item label="出发时间"><el-date-picker v-model="form.departureAt" type="datetime" placeholder="选择日期与时间" style="width:100%" /></el-form-item>
            <el-form-item label="乘客手机号"><el-input v-model="form.mobile" maxlength="11" placeholder="11 位手机号" /></el-form-item>
          </div>
          <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="选填，例如大件行李、老人乘车等" /></el-form-item>
        </el-form>
      </div>

      <div class="create-order-submit-row">
        <div><strong>创建后状态：待接单</strong><span>随后可在订单中心查询 10km 内有效司机并人工派单。</span></div>
        <el-button type="primary" size="large" :loading="submitting" @click="submit">创建订单</el-button>
      </div>
    </section>

    <AdminMapPointPicker
      v-model="pickerOpen"
      :title="pickerTitle"
      :point="pickerPoint"
      @select="applyPoint"
    />
  </section>
</template>
